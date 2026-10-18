package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecoverUnconfirmedPaymentTest {
    private val now = Instant.parse("2026-08-18T16:40:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    @Test
    fun `received pix activates a never-confirmed past due subscription`() {
        val pending = pendingSubscription()
        val subscriptions = MemorySubscriptions(pending)
        val useCase = RecoverUnconfirmedPayment(
            subscriptions,
            FixedAsaas(status = "RECEIVED"),
            ImmediateTransaction(),
            clock,
        )

        val recovered = useCase.recoverIfPaid(pending)

        assertEquals(SubscriptionStatus.ACTIVE, recovered.status)
        assertEquals(now, recovered.firstConfirmedAt)
        assertEquals("pay_1", recovered.lastConfirmedPaymentId)
        assertNull(recovered.pastDueSince)
        assertEquals(SubscriptionStatus.ACTIVE, subscriptions.findByOwnerUserId(ownerId)!!.status)
    }

    @Test
    fun `pending pix leaves the subscription past due`() {
        val pending = pendingSubscription()
        val subscriptions = MemorySubscriptions(pending)
        val useCase = RecoverUnconfirmedPayment(
            subscriptions,
            FixedAsaas(status = "PENDING"),
            ImmediateTransaction(),
            clock,
        )

        val recovered = useCase.recoverIfPaid(pending)

        assertEquals(SubscriptionStatus.PAST_DUE, recovered.status)
        assertNull(recovered.firstConfirmedAt)
        assertEquals(SubscriptionStatus.PAST_DUE, subscriptions.findByOwnerUserId(ownerId)!!.status)
    }

    @Test
    fun `already confirmed subscription is left untouched`() {
        val active = pendingSubscription().copy(
            status = SubscriptionStatus.ACTIVE,
            firstConfirmedAt = now.minusSeconds(60),
            pastDueSince = null,
        )
        val useCase = RecoverUnconfirmedPayment(
            MemorySubscriptions(active),
            FixedAsaas(status = "RECEIVED"),
            ImmediateTransaction(),
            clock,
        )

        val recovered = useCase.recoverIfPaid(active)

        assertEquals(active, recovered)
    }

    @Test
    fun `paid upgrade charge applies the target plan without advancing the period`() {
        val periodEnd = Instant.parse("2026-09-18T00:00:00Z")
        val pendingUpgrade = pendingSubscription().copy(
            status = SubscriptionStatus.ACTIVE,
            firstConfirmedAt = now.minusSeconds(3_600),
            pastDueSince = null,
            plan = Plan.TITULAR,
            currentPeriodEnd = periodEnd,
            pendingUpgradePlan = Plan.ORGANIZADOR,
            pendingUpgradeChargeId = "pay_upgrade_1",
        )
        val subscriptions = MemorySubscriptions(pendingUpgrade)
        val gateway = RecordingAsaas(statusById = mapOf("pay_upgrade_1" to "RECEIVED"))
        val useCase = RecoverUnconfirmedPayment(
            subscriptions,
            gateway,
            ImmediateTransaction(),
            clock,
        )

        val recovered = useCase.recoverIfPaid(pendingUpgrade)

        assertEquals(Plan.ORGANIZADOR, recovered.plan)
        assertNull(recovered.pendingUpgradePlan)
        assertNull(recovered.pendingUpgradeChargeId)
        assertEquals(periodEnd, recovered.currentPeriodEnd)
        assertEquals("pay_upgrade_1", recovered.lastConfirmedPaymentId)
        assertEquals(listOf("sub_1" to Plan.ORGANIZADOR.monthlyPriceCents), gateway.valueUpdates)
        assertEquals(Plan.ORGANIZADOR, subscriptions.findByOwnerUserId(ownerId)!!.plan)
    }

    @Test
    fun `pending upgrade pix leaves the current plan in place`() {
        val pendingUpgrade = pendingSubscription().copy(
            status = SubscriptionStatus.ACTIVE,
            firstConfirmedAt = now.minusSeconds(3_600),
            pastDueSince = null,
            plan = Plan.TITULAR,
            pendingUpgradePlan = Plan.ORGANIZADOR,
            pendingUpgradeChargeId = "pay_upgrade_1",
        )
        val useCase = RecoverUnconfirmedPayment(
            MemorySubscriptions(pendingUpgrade),
            RecordingAsaas(statusById = mapOf("pay_upgrade_1" to "PENDING")),
            ImmediateTransaction(),
            clock,
        )

        val recovered = useCase.recoverIfPaid(pendingUpgrade)

        assertEquals(Plan.TITULAR, recovered.plan)
        assertEquals(Plan.ORGANIZADOR, recovered.pendingUpgradePlan)
        assertEquals("pay_upgrade_1", recovered.pendingUpgradeChargeId)
    }

    @Test
    fun `canceled subscription does not apply a paid upgrade charge`() {
        val pendingUpgrade = pendingSubscription().copy(
            status = SubscriptionStatus.ACTIVE,
            firstConfirmedAt = now.minusSeconds(3_600),
            pastDueSince = null,
            canceledAt = now.minusSeconds(10),
            plan = Plan.TITULAR,
            pendingUpgradePlan = Plan.ORGANIZADOR,
            pendingUpgradeChargeId = "pay_upgrade_1",
        )
        val useCase = RecoverUnconfirmedPayment(
            MemorySubscriptions(pendingUpgrade),
            RecordingAsaas(statusById = mapOf("pay_upgrade_1" to "RECEIVED")),
            ImmediateTransaction(),
            clock,
        )

        val recovered = useCase.recoverIfPaid(pendingUpgrade)

        assertEquals(pendingUpgrade, recovered)
    }

    private val couponId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val tenPercent = Coupon(couponId, "DEZ", discountPercent = 10)

    private fun paidUpgrade(
        couponId: UUID? = null,
        couponCyclesRemaining: Int? = null,
        cycle: SubscriptionCycle = SubscriptionCycle.MONTHLY,
        coupons: CouponRepository? = CouponsById(tenPercent),
    ): Pair<Subscription, RecordingAsaas> {
        val pendingUpgrade = pendingSubscription().copy(
            status = SubscriptionStatus.ACTIVE,
            firstConfirmedAt = now.minusSeconds(3_600),
            pastDueSince = null,
            cycle = cycle,
            plan = Plan.TITULAR,
            pendingUpgradePlan = Plan.ORGANIZADOR,
            pendingUpgradeChargeId = "pay_upgrade_1",
            couponId = couponId,
            couponCyclesRemaining = couponCyclesRemaining,
        )
        val gateway = RecordingAsaas(statusById = mapOf("pay_upgrade_1" to "CONFIRMED"))
        val recovered = RecoverUnconfirmedPayment(
            MemorySubscriptions(pendingUpgrade),
            gateway,
            ImmediateTransaction(),
            clock,
            coupons,
        ).recoverIfPaid(pendingUpgrade)
        return recovered to gateway
    }

    @Test
    fun `recovered upgrade without coupon pushes the full price of the new plan`() {
        val (recovered, gateway) = paidUpgrade()

        assertEquals(Plan.ORGANIZADOR, recovered.plan)
        assertEquals(listOf("sub_1" to 5_990L), gateway.valueUpdates)
    }

    @Test
    fun `recovered upgrade with a coupon that still has cycles pushes the discounted price`() {
        val (_, gateway) = paidUpgrade(couponId = couponId, couponCyclesRemaining = 2)

        assertEquals(listOf("sub_1" to 5_391L), gateway.valueUpdates)
    }

    @Test
    fun `recovered upgrade with a permanent coupon pushes the discounted price`() {
        val (_, gateway) = paidUpgrade(couponId = couponId, couponCyclesRemaining = null)

        assertEquals(listOf("sub_1" to 5_391L), gateway.valueUpdates)
    }

    @Test
    fun `recovered annual upgrade pushes the annual discounted price`() {
        val (_, gateway) = paidUpgrade(
            couponId = couponId,
            couponCyclesRemaining = 1,
            cycle = SubscriptionCycle.ANNUAL,
        )

        // 5990 * 9 = 53910; menos 10% (5391) = 48519
        assertEquals(listOf("sub_1" to 48_519L), gateway.valueUpdates)
    }

    @Test
    fun `recovered upgrade with an exhausted coupon pushes the full price`() {
        val (_, gateway) = paidUpgrade(couponId = couponId, couponCyclesRemaining = 0)

        assertEquals(listOf("sub_1" to 5_990L), gateway.valueUpdates)
    }

    @Test
    fun `recovered upgrade whose coupon no longer exists pushes the full price`() {
        val (_, gateway) = paidUpgrade(
            couponId = couponId,
            couponCyclesRemaining = 2,
            coupons = CouponsById(),
        )

        assertEquals(listOf("sub_1" to 5_990L), gateway.valueUpdates)
    }

    @Test
    fun `recovered upgrade without a coupon repository pushes the full price`() {
        val (_, gateway) = paidUpgrade(couponId = couponId, couponCyclesRemaining = 2, coupons = null)

        assertEquals(listOf("sub_1" to 5_990L), gateway.valueUpdates)
    }

    @Test
    fun `upgrade charge looked up with a failing gateway leaves the subscription as it was`() {
        val pendingUpgrade = pendingSubscription().copy(
            status = SubscriptionStatus.ACTIVE,
            firstConfirmedAt = now.minusSeconds(3_600),
            pendingUpgradePlan = Plan.ORGANIZADOR,
            pendingUpgradeChargeId = "pay_upgrade_1",
        )
        val useCase = RecoverUnconfirmedPayment(
            MemorySubscriptions(pendingUpgrade),
            RecordingAsaas(statusById = emptyMap(), failOnPaymentLookup = true),
            ImmediateTransaction(),
            clock,
        )

        assertEquals(pendingUpgrade, useCase.recoverIfPaid(pendingUpgrade))
    }

    @Test
    fun `upgrade already applied by the webhook meanwhile is not applied again`() {
        val pendingUpgrade = pendingSubscription().copy(
            status = SubscriptionStatus.ACTIVE,
            firstConfirmedAt = now.minusSeconds(3_600),
            pendingUpgradePlan = Plan.ORGANIZADOR,
            pendingUpgradeChargeId = "pay_upgrade_1",
        )
        val appliedByWebhook = pendingUpgrade.copy(
            plan = Plan.ORGANIZADOR,
            pendingUpgradePlan = null,
            pendingUpgradeChargeId = null,
            lastConfirmedPaymentId = "pay_upgrade_1",
        )
        val gateway = RecordingAsaas(statusById = mapOf("pay_upgrade_1" to "RECEIVED"))
        val subscriptions = MemorySubscriptions(appliedByWebhook)

        val recovered = RecoverUnconfirmedPayment(subscriptions, gateway, ImmediateTransaction(), clock)
            .recoverIfPaid(pendingUpgrade)

        assertEquals(appliedByWebhook, recovered)
        assertTrue(gateway.valueUpdates.isEmpty())
    }

    @Test
    fun `upgrade charge canceled meanwhile is not applied`() {
        val pendingUpgrade = pendingSubscription().copy(
            status = SubscriptionStatus.ACTIVE,
            firstConfirmedAt = now.minusSeconds(3_600),
            pendingUpgradePlan = Plan.ORGANIZADOR,
            pendingUpgradeChargeId = "pay_upgrade_1",
        )
        val canceledMeanwhile = pendingUpgrade.copy(canceledAt = now.minusSeconds(10))
        val gateway = RecordingAsaas(statusById = mapOf("pay_upgrade_1" to "RECEIVED"))

        val recovered = RecoverUnconfirmedPayment(
            MemorySubscriptions(canceledMeanwhile),
            gateway,
            ImmediateTransaction(),
            clock,
        ).recoverIfPaid(pendingUpgrade)

        assertEquals(canceledMeanwhile, recovered)
        assertTrue(gateway.valueUpdates.isEmpty())
    }

    @Test
    fun `upgrade with a different pending charge than the paid one is not applied`() {
        val pendingUpgrade = pendingSubscription().copy(
            status = SubscriptionStatus.ACTIVE,
            firstConfirmedAt = now.minusSeconds(3_600),
            pendingUpgradePlan = Plan.ORGANIZADOR,
            pendingUpgradeChargeId = "pay_upgrade_1",
        )
        val replacedCharge = pendingUpgrade.copy(pendingUpgradeChargeId = "pay_upgrade_2")
        val gateway = RecordingAsaas(statusById = mapOf("pay_upgrade_1" to "RECEIVED"))

        val recovered = RecoverUnconfirmedPayment(
            MemorySubscriptions(replacedCharge),
            gateway,
            ImmediateTransaction(),
            clock,
        ).recoverIfPaid(pendingUpgrade)

        assertEquals(replacedCharge, recovered)
        assertTrue(gateway.valueUpdates.isEmpty())
    }

    @Test
    fun `upgrade charge without a target plan is not applied`() {
        val pendingUpgrade = pendingSubscription().copy(
            status = SubscriptionStatus.ACTIVE,
            firstConfirmedAt = now.minusSeconds(3_600),
            pendingUpgradePlan = null,
            pendingUpgradeChargeId = "pay_upgrade_1",
        )
        val gateway = RecordingAsaas(statusById = mapOf("pay_upgrade_1" to "RECEIVED"))

        val recovered = RecoverUnconfirmedPayment(
            MemorySubscriptions(pendingUpgrade),
            gateway,
            ImmediateTransaction(),
            clock,
        ).recoverIfPaid(pendingUpgrade)

        assertEquals(pendingUpgrade, recovered)
        assertTrue(gateway.valueUpdates.isEmpty())
    }

    // --- Primeiro checkout ---

    @Test
    fun `first checkout paid with the last coupon cycle pushes the full price back to asaas`() {
        val pending = pendingSubscription().copy(plan = Plan.ORGANIZADOR, couponId = couponId, couponCyclesRemaining = 1)
        val gateway = RecordingAsaas(statusById = mapOf("pay_1" to "CONFIRMED"), latestPaymentId = "pay_1")

        val recovered = RecoverUnconfirmedPayment(MemorySubscriptions(pending), gateway, ImmediateTransaction(), clock)
            .recoverIfPaid(pending)

        assertEquals(SubscriptionStatus.ACTIVE, recovered.status)
        assertNull(recovered.couponCyclesRemaining)
        assertEquals(listOf("sub_1" to 5_990L), gateway.valueUpdates)
    }

    @Test
    fun `first checkout stays confirmed when pushing the full price to asaas fails`() {
        val pending = pendingSubscription().copy(plan = Plan.ORGANIZADOR, couponId = couponId, couponCyclesRemaining = 1)
        val subscriptions = MemorySubscriptions(pending)
        val gateway = RecordingAsaas(
            statusById = mapOf("pay_1" to "CONFIRMED"),
            latestPaymentId = "pay_1",
            failOnValueUpdate = true,
        )

        val recovered = RecoverUnconfirmedPayment(subscriptions, gateway, ImmediateTransaction(), clock)
            .recoverIfPaid(pending)

        assertEquals(SubscriptionStatus.ACTIVE, recovered.status)
        assertEquals(SubscriptionStatus.ACTIVE, subscriptions.findByOwnerUserId(ownerId)!!.status)
    }

    @Test
    fun `first checkout already confirmed by the webhook meanwhile is not confirmed twice`() {
        val pending = pendingSubscription()
        val confirmedByWebhook = pending.copy(
            status = SubscriptionStatus.ACTIVE,
            pastDueSince = null,
            firstConfirmedAt = now.minusSeconds(5),
            lastConfirmedPaymentId = "pay_1",
        )
        val gateway = RecordingAsaas(statusById = mapOf("pay_1" to "RECEIVED"), latestPaymentId = "pay_1")

        val recovered = RecoverUnconfirmedPayment(
            MemorySubscriptions(confirmedByWebhook),
            gateway,
            ImmediateTransaction(),
            clock,
        ).recoverIfPaid(pending)

        assertEquals(confirmedByWebhook, recovered)
    }

    @Test
    fun `first checkout whose charge was already applied is not confirmed again`() {
        val pending = pendingSubscription()
        val sameChargeApplied = pending.copy(lastConfirmedPaymentId = "pay_1")
        val subscriptions = MemorySubscriptions(sameChargeApplied)
        val gateway = RecordingAsaas(statusById = mapOf("pay_1" to "RECEIVED"), latestPaymentId = "pay_1")

        val recovered = RecoverUnconfirmedPayment(subscriptions, gateway, ImmediateTransaction(), clock)
            .recoverIfPaid(pending)

        assertEquals(sameChargeApplied, recovered)
        assertEquals(sameChargeApplied, subscriptions.findByOwnerUserId(ownerId))
    }

    @Test
    fun `first checkout with an unreachable asaas stays past due`() {
        val pending = pendingSubscription()
        val gateway = RecordingAsaas(statusById = emptyMap(), failOnPaymentLookup = true)

        val recovered = RecoverUnconfirmedPayment(MemorySubscriptions(pending), gateway, ImmediateTransaction(), clock)
            .recoverIfPaid(pending)

        assertEquals(pending, recovered)
    }

    @Test
    fun `first checkout without any charge yet stays past due`() {
        val pending = pendingSubscription()
        val gateway = object : AsaasGateway by FixedAsaas(status = "RECEIVED") {
            override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String): String? = null
        }

        val recovered = RecoverUnconfirmedPayment(MemorySubscriptions(pending), gateway, ImmediateTransaction(), clock)
            .recoverIfPaid(pending)

        assertEquals(pending, recovered)
    }

    private fun pendingSubscription() = Subscription(
        ownerUserId = ownerId,
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_1",
        asaasSubscriptionId = "sub_1",
        billingType = AsaasBillingType.PIX,
        currentPeriodEnd = Instant.parse("2026-09-18T00:00:00Z"),
        status = SubscriptionStatus.PAST_DUE,
        pastDueSince = now.minusSeconds(600),
        firstConfirmedAt = null,
    )

    private class ImmediateTransaction : SubscriptionsTransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private class MemorySubscriptions(initial: Subscription) : SubscriptionRepository {
        private var current: Subscription = initial
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) = null
        override fun findByOwnerUserId(ownerUserId: UUID) = current.takeIf { it.ownerUserId == ownerUserId }
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID) = findByOwnerUserId(ownerUserId)
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun findByLastConfirmedPaymentId(paymentId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = error("unused")
        override fun save(subscription: Subscription) {
            current = subscription
        }
    }

    private class FixedAsaas(private val status: String) : AsaasGateway {
        override fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String) = error("unused")
        override fun createSubscription(
            asaasCustomerId: String,
            plan: Plan,
            cycle: SubscriptionCycle,
            valueCents: Long,
            billingType: AsaasBillingType,
            idempotencyKey: String,
            creditCard: CreditCardDetails?,
            creditCardHolderInfo: CreditCardHolderInfo?,
            remoteIp: String?,
        ) = error("unused")
        override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) = error("unused")
        override fun cancelSubscription(asaasSubscriptionId: String) = error("unused")
        override fun createOneOffCharge(
            asaasCustomerId: String,
            valueCents: Long,
            description: String,
            idempotencyKey: String,
        ) = error("unused")
        override fun regeneratePixPayload(asaasChargeId: String) = error("unused")
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = "pay_1"
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = null
        override fun findPayment(asaasPaymentId: String) =
            AsaasPaymentSnapshot(id = asaasPaymentId, status = status, invoiceUrl = null)
    }

    private class RecordingAsaas(
        private val statusById: Map<String, String>,
        private val latestPaymentId: String? = null,
        private val failOnValueUpdate: Boolean = false,
        private val failOnPaymentLookup: Boolean = false,
    ) : AsaasGateway {
        val valueUpdates = mutableListOf<Pair<String, Long>>()
        override fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String) = error("unused")
        override fun createSubscription(
            asaasCustomerId: String,
            plan: Plan,
            cycle: SubscriptionCycle,
            valueCents: Long,
            billingType: AsaasBillingType,
            idempotencyKey: String,
            creditCard: CreditCardDetails?,
            creditCardHolderInfo: CreditCardHolderInfo?,
            remoteIp: String?,
        ) = error("unused")
        override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) {
            if (failOnValueUpdate) throw RuntimeException("asaas fora do ar")
            valueUpdates += asaasSubscriptionId to valueCents
        }
        override fun cancelSubscription(asaasSubscriptionId: String) = error("unused")
        override fun createOneOffCharge(
            asaasCustomerId: String,
            valueCents: Long,
            description: String,
            idempotencyKey: String,
        ) = error("unused")
        override fun regeneratePixPayload(asaasChargeId: String) = error("unused")
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String): String? {
            if (failOnPaymentLookup) throw RuntimeException("asaas fora do ar")
            return latestPaymentId ?: error("unused")
        }
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = null
        override fun findPayment(asaasPaymentId: String): AsaasPaymentSnapshot {
            if (failOnPaymentLookup) throw RuntimeException("asaas fora do ar")
            return AsaasPaymentSnapshot(
                id = asaasPaymentId,
                status = statusById[asaasPaymentId],
                invoiceUrl = null,
            )
        }
    }

    private class CouponsById(vararg coupons: Coupon) : CouponRepository {
        private val byId = coupons.associateBy { it.id }
        override fun findByCode(code: String) = byId.values.firstOrNull { it.code == code }
        override fun findById(couponId: UUID) = byId[couponId]
        override fun hasRedemption(couponId: UUID, userId: UUID) = false
        override fun saveRedemption(redemption: CouponRedemption) = Unit
    }
}
