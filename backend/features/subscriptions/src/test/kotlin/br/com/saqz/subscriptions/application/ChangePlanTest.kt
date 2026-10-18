package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.OwnerPlanUsage
import br.com.saqz.sharedkernel.subscription.OwnerPlanUsageLookup
import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangePlanTest {
    private val fixedNow = Instant.parse("2026-07-15T12:00:00Z")
    private val periodEnd = Instant.parse("2026-07-30T12:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val requestId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val couponId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")

    private lateinit var subscriptions: FakeSubscriptionRepository
    private lateinit var gateway: FakeAsaasGateway
    private lateinit var usage: MutableOwnerPlanUsageLookup
    private lateinit var coupons: FakeCouponRepository
    private lateinit var useCase: ChangePlan

    @BeforeEach
    fun setUp() {
        subscriptions = FakeSubscriptionRepository()
        gateway = FakeAsaasGateway()
        usage = MutableOwnerPlanUsageLookup(OwnerPlanUsage(ownedGroupCount = 1, largestGroupAthleteCount = 5))
        coupons = FakeCouponRepository()
        useCase = ChangePlan(
            subscriptions,
            gateway,
            usage,
            coupons,
            transaction = object : SubscriptionsTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = block()
            },
            clock,
        )
        subscriptions.save(baseSubscription())
    }

    @Test
    fun `upgrade creates one-off charge and keeps current plan until webhook`() {
        val result = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        val pending = assertIs<ChangePlanResult.UpgradePendingPayment>(result)
        assertEquals(Plan.TITULAR, pending.subscription.plan)
        assertEquals(Plan.ORGANIZADOR, pending.subscription.pendingUpgradePlan)
        assertEquals("pay_upgrade_1", pending.subscription.pendingUpgradeChargeId)
        assertTrue(pending.chargedCents > 0L)
        assertEquals("000201PIX-UPG", pending.pixCopyPaste)
        assertEquals("QR-PNG-UPG", pending.pixQrCodeBase64)
        assertTrue(gateway.valueUpdates.isEmpty())
        assertEquals(
            listOf("subscription-upgrade:$ownerId:$requestId"),
            gateway.oneOffIdempotencyKeys,
        )
    }

    @Test
    fun `second upgrade request reuses existing pending charge instead of creating another`() {
        val first = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))
        val pendingFirst = assertIs<ChangePlanResult.UpgradePendingPayment>(first)
        assertEquals(1, gateway.oneOffIdempotencyKeys.size)

        val second = useCase.execute(
            ChangePlanCommand(ownerId, UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"), Plan.ORGANIZADOR),
        )
        val pendingSecond = assertIs<ChangePlanResult.UpgradePendingPayment>(second)

        assertEquals(pendingFirst.oneOffChargeId, pendingSecond.oneOffChargeId)
        assertEquals(1, gateway.oneOffIdempotencyKeys.size)
        assertEquals("pay_upgrade_1", subscriptions.findByOwnerUserId(ownerId)!!.pendingUpgradeChargeId)
    }

    @Test
    fun `upgrade request to a different target before payment creates a new charge instead of reusing`() {
        val first = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))
        val pendingFirst = assertIs<ChangePlanResult.UpgradePendingPayment>(first)
        assertEquals(1, gateway.oneOffIdempotencyKeys.size)

        val second = useCase.execute(
            ChangePlanCommand(ownerId, UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"), Plan.ILIMITADO),
        )
        val pendingSecond = assertIs<ChangePlanResult.UpgradePendingPayment>(second)

        assertTrue(pendingFirst.oneOffChargeId != pendingSecond.oneOffChargeId)
        assertEquals(2, gateway.oneOffIdempotencyKeys.size)
        assertEquals(Plan.ILIMITADO, pendingSecond.subscription.pendingUpgradePlan)
        val stored = subscriptions.findByOwnerUserId(ownerId)!!
        assertEquals(Plan.ILIMITADO, stored.pendingUpgradePlan)
        assertEquals(pendingSecond.oneOffChargeId, stored.pendingUpgradeChargeId)
    }

    @Test
    fun `upgrade with remaining coupon charges prorata on discounted prices and keeps plan`() {
        coupons.byId[couponId] = Coupon(id = couponId, code = "GALERA10", discountPercent = 10, durationCycles = 3)
        subscriptions.save(baseSubscription().copy(couponId = couponId, couponCyclesRemaining = 2))

        val result = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        val pending = assertIs<ChangePlanResult.UpgradePendingPayment>(result)
        assertEquals(Plan.TITULAR, pending.subscription.plan)
        assertEquals(Plan.ORGANIZADOR, pending.subscription.pendingUpgradePlan)
        // discounted: 3990*0.9=3591, 5990*0.9=5391, delta 1800, ~half period → 900
        assertEquals(900L, pending.chargedCents)
        assertTrue(gateway.valueUpdates.isEmpty())
    }

    @Test
    fun `upgrade with permanent coupon keeps discount when scheduling charge`() {
        coupons.byId[couponId] = Coupon(id = couponId, code = "FOREVER20", discountPercent = 20, durationCycles = null)
        subscriptions.save(baseSubscription().copy(couponId = couponId, couponCyclesRemaining = null))

        val result = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        val pending = assertIs<ChangePlanResult.UpgradePendingPayment>(result)
        // 3990*0.8=3192, 5990*0.8=4792, delta 1600, ~half → 800
        assertEquals(800L, pending.chargedCents)
        assertEquals(couponId, pending.subscription.couponId)
        assertNull(pending.subscription.couponCyclesRemaining)
    }

    @Test
    fun `downgrade with permanent coupon pushes discounted target price`() {
        coupons.byId[couponId] = Coupon(id = couponId, code = "FOREVER20", discountPercent = 20, durationCycles = null)
        subscriptions.save(
            baseSubscription().copy(
                plan = Plan.ORGANIZADOR,
                couponId = couponId,
                couponCyclesRemaining = null,
            ),
        )
        usage.usage = OwnerPlanUsage(ownedGroupCount = 1, largestGroupAthleteCount = 5)

        useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.TITULAR))

        // 3990 * 0.8 = 3192
        assertEquals(listOf("sub_1" to 3_192L), gateway.valueUpdates)
    }

    @Test
    fun `downgrade is blocked while an upgrade charge is pending payment`() {
        subscriptions.save(
            baseSubscription().copy(
                plan = Plan.ORGANIZADOR,
                pendingUpgradePlan = Plan.ILIMITADO,
                pendingUpgradeChargeId = "pay_upg_1",
            ),
        )
        usage.usage = OwnerPlanUsage(ownedGroupCount = 1, largestGroupAthleteCount = 5)

        val result = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.TITULAR))

        assertEquals(ChangePlanResult.UpgradePendingBlocksChange, result)
        assertTrue(gateway.valueUpdates.isEmpty())
        val stored = subscriptions.findByOwnerUserId(ownerId)!!
        assertEquals(Plan.ORGANIZADOR, stored.plan)
        assertNull(stored.pendingPlan)
        assertEquals("pay_upg_1", stored.pendingUpgradeChargeId)
    }

    @Test
    fun `upgrade checkout enrichment runs outside the transaction and survives gateway failures`() {
        val recordingTransaction = RecordingTransactionRunner()
        val throwingGateway = ThrowingCheckoutAsaasGateway(recordingTransaction)
        val useCaseWithRecording = ChangePlan(subscriptions, throwingGateway, usage, coupons, recordingTransaction, clock)

        val result = useCaseWithRecording.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        val pending = assertIs<ChangePlanResult.UpgradePendingPayment>(result)
        assertNull(pending.pixCopyPaste)
        assertNull(pending.invoiceUrl)
        assertEquals("pay_upgrade_1", subscriptions.findByOwnerUserId(ownerId)!!.pendingUpgradeChargeId)
        assertEquals(false, throwingGateway.checkoutCalledInsideTransaction)
    }

    @Test
    fun `upgrade checkout keeps the pix payload when only the invoice lookup fails`() {
        val gatewayWithFailingInvoice = PixOkInvoiceFailingAsaasGateway()
        val useCaseWithFailingInvoice = ChangePlan(
            subscriptions,
            gatewayWithFailingInvoice,
            usage,
            coupons,
            transaction = object : SubscriptionsTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = block()
            },
            clock,
        )

        val result = useCaseWithFailingInvoice.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        val pending = assertIs<ChangePlanResult.UpgradePendingPayment>(result)
        assertEquals("000201PIX-OK", pending.pixCopyPaste)
        assertNull(pending.invoiceUrl)
    }

    @Test
    fun `downgrade blocked when owner exceeds target group limit`() {
        subscriptions.save(baseSubscription().copy(plan = Plan.ORGANIZADOR))
        usage.usage = OwnerPlanUsage(ownedGroupCount = 3, largestGroupAthleteCount = 5)

        val result = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.TITULAR))

        assertEquals(ChangePlanResult.DowngradeBlockedByUsage, result)
        assertTrue(gateway.oneOffIdempotencyKeys.isEmpty())
        assertTrue(gateway.valueUpdates.isEmpty())
    }

    @Test
    fun `downgrade schedules pending plan without one-off charge`() {
        subscriptions.save(baseSubscription().copy(plan = Plan.ORGANIZADOR))
        usage.usage = OwnerPlanUsage(ownedGroupCount = 1, largestGroupAthleteCount = 5)

        val result = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.TITULAR))

        val scheduled = assertIs<ChangePlanResult.DowngradeScheduled>(result)
        assertEquals(Plan.ORGANIZADOR, scheduled.subscription.plan)
        assertEquals(Plan.TITULAR, scheduled.subscription.pendingPlan)
        assertEquals(periodEnd, scheduled.subscription.pendingPlanEffectiveAt)
        assertEquals(listOf("sub_1" to Plan.TITULAR.monthlyPriceCents), gateway.valueUpdates)
    }

    @Test
    fun `downgrade with remaining coupon pushes discounted target price`() {
        coupons.byId[couponId] = Coupon(id = couponId, code = "GALERA10", discountPercent = 20, durationCycles = 3)
        subscriptions.save(
            baseSubscription().copy(
                plan = Plan.ORGANIZADOR,
                couponId = couponId,
                couponCyclesRemaining = 1,
            ),
        )
        usage.usage = OwnerPlanUsage(ownedGroupCount = 1, largestGroupAthleteCount = 5)

        useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.TITULAR))

        assertEquals(listOf("sub_1" to 3_192L), gateway.valueUpdates)
    }

    @Test
    fun `downgrade blocked when athlete count exceeds target max athletes`() {
        subscriptions.save(baseSubscription().copy(plan = Plan.ORGANIZADOR))
        usage.usage = OwnerPlanUsage(ownedGroupCount = 1, largestGroupAthleteCount = 30)

        assertEquals(
            ChangePlanResult.DowngradeBlockedByUsage,
            useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.TITULAR)),
        )
    }

    private fun useCaseAt(now: Instant) = ChangePlan(
        subscriptions,
        gateway,
        usage,
        coupons,
        transaction = object : SubscriptionsTransactionRunner {
            override fun <T> inTransaction(block: () -> T): T = block()
        },
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `owner without a subscription gets NotFound`() {
        val other = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")

        assertEquals(ChangePlanResult.NotFound, useCase.execute(ChangePlanCommand(other, requestId, Plan.ORGANIZADOR)))
    }

    @Test
    fun `canceled subscription cannot change plan`() {
        subscriptions.save(baseSubscription().copy(status = SubscriptionStatus.CANCELED))

        assertEquals(
            ChangePlanResult.NotActive,
            useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR)),
        )
        assertTrue(gateway.oneOffIdempotencyKeys.isEmpty())
    }

    @Test
    fun `choosing the current plan is SamePlan and changes nothing`() {
        assertEquals(ChangePlanResult.SamePlan, useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.TITULAR)))

        assertTrue(gateway.oneOffIdempotencyKeys.isEmpty())
        assertTrue(gateway.valueUpdates.isEmpty())
        assertNull(subscriptions.findByOwnerUserId(ownerId)?.pendingPlan)
    }

    @Test
    fun `upgrade charges the exact prorata of the remaining half cycle`() {
        val result = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        // delta 5990 - 3990 = 2000; 15 dias restantes de 30 → 1000
        assertEquals(1_000L, assertIs<ChangePlanResult.UpgradePendingPayment>(result).chargedCents)
        assertEquals("pay_upgrade_1", subscriptions.findByOwnerUserId(ownerId)?.pendingUpgradeChargeId)
        assertEquals(Plan.ORGANIZADOR, subscriptions.findByOwnerUserId(ownerId)?.pendingUpgradePlan)
    }

    @Test
    fun `upgrade with an exhausted coupon is charged on full prices`() {
        coupons.byId[couponId] = Coupon(id = couponId, code = "GALERA10", discountPercent = 10, durationCycles = 3)
        subscriptions.save(baseSubscription().copy(couponId = couponId, couponCyclesRemaining = 0))

        val result = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        assertEquals(1_000L, assertIs<ChangePlanResult.UpgradePendingPayment>(result).chargedCents)
    }

    @Test
    fun `upgrade whose coupon no longer exists is charged on full prices`() {
        subscriptions.save(baseSubscription().copy(couponId = couponId, couponCyclesRemaining = 2))

        val result = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        assertEquals(1_000L, assertIs<ChangePlanResult.UpgradePendingPayment>(result).chargedCents)
    }

    @Test
    fun `upgrade in the last second of the cycle has a zero prorata and applies the plan at once`() {
        val almostEnd = periodEnd.minusSeconds(1)

        val result = useCaseAt(almostEnd).execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        val upgraded = assertIs<ChangePlanResult.Upgraded>(result)
        assertEquals(0L, upgraded.chargedCents)
        assertEquals(Plan.ORGANIZADOR, upgraded.subscription.plan)
        assertEquals(Plan.ORGANIZADOR, subscriptions.findByOwnerUserId(ownerId)?.plan)
        assertEquals(listOf("sub_1" to 5_990L), gateway.valueUpdates)
        assertTrue(gateway.oneOffIdempotencyKeys.isEmpty())
    }

    @Test
    fun `immediate upgrade clears any scheduled downgrade and pending upgrade`() {
        subscriptions.save(
            baseSubscription().copy(
                pendingPlan = Plan.TITULAR,
                pendingPlanEffectiveAt = periodEnd,
                pendingUpgradePlan = Plan.ILIMITADO,
                pendingUpgradeChargeId = "pay_old",
            ),
        )

        useCaseAt(periodEnd.minusSeconds(1)).execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        val stored = subscriptions.findByOwnerUserId(ownerId)!!
        assertEquals(Plan.ORGANIZADOR, stored.plan)
        assertNull(stored.pendingPlan)
        assertNull(stored.pendingPlanEffectiveAt)
        assertNull(stored.pendingUpgradePlan)
        assertNull(stored.pendingUpgradeChargeId)
    }

    @Test
    fun `immediate upgrade with a coupon pushes the discounted target price`() {
        coupons.byId[couponId] = Coupon(id = couponId, code = "FOREVER20", discountPercent = 20)
        subscriptions.save(baseSubscription().copy(couponId = couponId, couponCyclesRemaining = null))

        useCaseAt(periodEnd.minusSeconds(1)).execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        // 5990 - 20% (1198) = 4792
        assertEquals(listOf("sub_1" to 4_792L), gateway.valueUpdates)
    }

    @Test
    fun `upgrade after the period end charges the whole price difference`() {
        val result = useCaseAt(periodEnd.plusSeconds(60)).execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        assertEquals(2_000L, assertIs<ChangePlanResult.UpgradePendingPayment>(result).chargedCents)
    }

    @Test
    fun `upgrade describes the charge with the plan names`() {
        useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ILIMITADO))

        assertEquals(listOf("Upgrade Saqz TITULAR → ILIMITADO"), gateway.oneOffDescriptions)
        // delta 8990 - 3990 = 5000; metade do ciclo → 2500
        assertEquals(2_500L, gateway.oneOffValues.single())
    }

    @Test
    fun `downgrade is stored for the end of the paid period and lowers the recurring price`() {
        subscriptions.save(baseSubscription().copy(plan = Plan.ORGANIZADOR))

        val result = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.TITULAR))

        val scheduled = assertIs<ChangePlanResult.DowngradeScheduled>(result)
        assertEquals(Plan.TITULAR, scheduled.subscription.pendingPlan)
        assertEquals(periodEnd, scheduled.subscription.pendingPlanEffectiveAt)
        val stored = subscriptions.findByOwnerUserId(ownerId)!!
        assertEquals(Plan.ORGANIZADOR, stored.plan)
        assertEquals(Plan.TITULAR, stored.pendingPlan)
        assertEquals(periodEnd, stored.pendingPlanEffectiveAt)
        assertEquals(listOf("sub_1" to 3_990L), gateway.valueUpdates)
    }

    @Test
    fun `downgrade is allowed when usage is exactly at the target limits`() {
        subscriptions.save(baseSubscription().copy(plan = Plan.ORGANIZADOR))
        usage.usage = OwnerPlanUsage(ownedGroupCount = 1, largestGroupAthleteCount = 25)

        assertIs<ChangePlanResult.DowngradeScheduled>(
            useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.TITULAR)),
        )

        subscriptions.save(baseSubscription().copy(plan = Plan.ILIMITADO))
        usage.usage = OwnerPlanUsage(ownedGroupCount = 3, largestGroupAthleteCount = 500)

        assertIs<ChangePlanResult.DowngradeScheduled>(
            useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR)),
        )
    }

    @Test
    fun `downgrade is blocked one above the target limits`() {
        subscriptions.save(baseSubscription().copy(plan = Plan.ILIMITADO))
        usage.usage = OwnerPlanUsage(ownedGroupCount = 4, largestGroupAthleteCount = 10)

        assertEquals(
            ChangePlanResult.DowngradeBlockedByUsage,
            useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR)),
        )
        assertTrue(gateway.valueUpdates.isEmpty())
        assertNull(subscriptions.findByOwnerUserId(ownerId)?.pendingPlan)
    }

    @Test
    fun `changing to a plan with the same discounted price is not an upgrade`() {
        coupons.byId[couponId] = Coupon(id = couponId, code = "TUDO", discountPercent = 100)
        subscriptions.save(baseSubscription().copy(couponId = couponId, couponCyclesRemaining = null))

        val result = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        // Preços iguais (zero) não geram cobrança avulsa: segue o caminho de troca agendada.
        assertIs<ChangePlanResult.DowngradeScheduled>(result)
        assertTrue(gateway.oneOffIdempotencyKeys.isEmpty())
    }

    private fun baseSubscription() = Subscription(
        ownerUserId = ownerId,
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_1",
        asaasSubscriptionId = "sub_1",
        billingType = AsaasBillingType.PIX,
        currentPeriodEnd = periodEnd,
        status = SubscriptionStatus.ACTIVE,
        firstConfirmedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private class FakeSubscriptionRepository : SubscriptionRepository {
        private val byOwner = linkedMapOf<UUID, Subscription>()
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) =
            byOwner.values.firstOrNull { it.asaasSubscriptionId == asaasSubscriptionId }

        override fun findByOwnerUserId(ownerUserId: UUID) = byOwner[ownerUserId]
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID) = byOwner[ownerUserId]
        override fun findByPendingUpgradeChargeId(chargeId: String) =
            byOwner.values.firstOrNull { it.pendingUpgradeChargeId == chargeId }

        override fun findByLastConfirmedPaymentId(paymentId: String) =
            byOwner.values.firstOrNull { it.lastConfirmedPaymentId == paymentId }

        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = save(subscription)
        override fun save(subscription: Subscription) {
            byOwner[subscription.ownerUserId] = subscription
        }
    }

    private class MutableOwnerPlanUsageLookup(var usage: OwnerPlanUsage) : OwnerPlanUsageLookup {
        override fun usageFor(ownerUserId: UUID) = usage
    }

    private class FakeCouponRepository : CouponRepository {
        val byId = linkedMapOf<UUID, Coupon>()
        override fun findByCode(code: String) = byId.values.firstOrNull { it.code.equals(code, ignoreCase = true) }
        override fun findById(couponId: UUID) = byId[couponId]
        override fun hasRedemption(couponId: UUID, userId: UUID) = false
        override fun saveRedemption(redemption: CouponRedemption) = error("unused")
    }

    private class FakeAsaasGateway : AsaasGateway {
        val oneOffIdempotencyKeys = mutableListOf<String>()
        val oneOffDescriptions = mutableListOf<String>()
        val oneOffValues = mutableListOf<Long>()
        val valueUpdates = mutableListOf<Pair<String, Long>>()
        private var chargeCounter = 0

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
        ): String {
            oneOffIdempotencyKeys += idempotencyKey
            oneOffDescriptions += description
            oneOffValues += valueCents
            return "pay_upgrade_${++chargeCounter}"
        }

        override fun regeneratePixPayload(asaasChargeId: String) = PixCode("000201PIX-UPG", "QR-PNG-UPG")
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = null
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = null
        override fun findPayment(asaasPaymentId: String) = null
    }

    /** Pix succeeds; invoice lookup throws — the successful pix must not be discarded. */
    private class PixOkInvoiceFailingAsaasGateway : AsaasGateway {
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

        override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) = Unit
        override fun cancelSubscription(asaasSubscriptionId: String) = error("unused")

        override fun createOneOffCharge(
            asaasCustomerId: String,
            valueCents: Long,
            description: String,
            idempotencyKey: String,
        ) = "pay_upgrade_1"

        override fun regeneratePixPayload(asaasChargeId: String) = PixCode("000201PIX-OK", null)
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = null
        override fun findPaymentInvoiceUrl(asaasPaymentId: String): String =
            throw RuntimeException("invoice lookup failed")

        override fun findPayment(asaasPaymentId: String) = throw RuntimeException("payment lookup failed")
    }

    private class RecordingTransactionRunner : SubscriptionsTransactionRunner {
        var active: Boolean = false
        override fun <T> inTransaction(block: () -> T): T {
            active = true
            try {
                return block()
            } finally {
                active = false
            }
        }
    }

    /** Same-target upgrade reuse path, but the Pix/invoice calls always throw. */
    private class ThrowingCheckoutAsaasGateway(
        private val transaction: RecordingTransactionRunner,
    ) : AsaasGateway {
        var checkoutCalledInsideTransaction = false

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

        override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) = Unit
        override fun cancelSubscription(asaasSubscriptionId: String) = error("unused")

        override fun createOneOffCharge(
            asaasCustomerId: String,
            valueCents: Long,
            description: String,
            idempotencyKey: String,
        ) = "pay_upgrade_1"

        override fun regeneratePixPayload(asaasChargeId: String): PixCode {
            checkoutCalledInsideTransaction = checkoutCalledInsideTransaction || transaction.active
            throw RuntimeException("pix lookup failed")
        }

        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = null

        override fun findPaymentInvoiceUrl(asaasPaymentId: String): String {
            checkoutCalledInsideTransaction = checkoutCalledInsideTransaction || transaction.active
            throw RuntimeException("invoice lookup failed")
        }

        override fun findPayment(asaasPaymentId: String): AsaasPaymentSnapshot {
            checkoutCalledInsideTransaction = checkoutCalledInsideTransaction || transaction.active
            throw RuntimeException("payment lookup failed")
        }
    }
}
