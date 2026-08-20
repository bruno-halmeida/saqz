package br.com.saqz.subscriptions.application

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

    private class RecordingAsaas(private val statusById: Map<String, String>) : AsaasGateway {
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
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = error("unused")
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = null
        override fun findPayment(asaasPaymentId: String) =
            AsaasPaymentSnapshot(
                id = asaasPaymentId,
                status = statusById[asaasPaymentId],
                invoiceUrl = null,
            )
    }
}
