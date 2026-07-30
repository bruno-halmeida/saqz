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
        usage = MutableOwnerPlanUsageLookup(OwnerPlanUsage(ownedGroupCount = 1, occupyingAthleteCount = 5))
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
        usage.usage = OwnerPlanUsage(ownedGroupCount = 1, occupyingAthleteCount = 5)

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
        usage.usage = OwnerPlanUsage(ownedGroupCount = 1, occupyingAthleteCount = 5)

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
    fun `downgrade blocked when owner exceeds target group limit`() {
        subscriptions.save(baseSubscription().copy(plan = Plan.ORGANIZADOR))
        usage.usage = OwnerPlanUsage(ownedGroupCount = 3, occupyingAthleteCount = 5)

        val result = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.TITULAR))

        assertEquals(ChangePlanResult.DowngradeBlockedByUsage, result)
        assertTrue(gateway.oneOffIdempotencyKeys.isEmpty())
        assertTrue(gateway.valueUpdates.isEmpty())
    }

    @Test
    fun `downgrade schedules pending plan without one-off charge`() {
        subscriptions.save(baseSubscription().copy(plan = Plan.ORGANIZADOR))
        usage.usage = OwnerPlanUsage(ownedGroupCount = 1, occupyingAthleteCount = 5)

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
        usage.usage = OwnerPlanUsage(ownedGroupCount = 1, occupyingAthleteCount = 5)

        useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.TITULAR))

        assertEquals(listOf("sub_1" to 3_192L), gateway.valueUpdates)
    }

    @Test
    fun `downgrade blocked when athlete count exceeds target max athletes`() {
        subscriptions.save(baseSubscription().copy(plan = Plan.ORGANIZADOR))
        usage.usage = OwnerPlanUsage(ownedGroupCount = 1, occupyingAthleteCount = 30)

        assertEquals(
            ChangePlanResult.DowngradeBlockedByUsage,
            useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.TITULAR)),
        )
    }

    private fun baseSubscription() = Subscription(
        ownerUserId = ownerId,
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_1",
        asaasSubscriptionId = "sub_1",
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
            return "pay_upgrade_${++chargeCounter}"
        }

        override fun regeneratePixPayload(asaasChargeId: String) = "000201PIX-UPG"
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = null
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = null
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
        ) = error("unused")

        override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) = Unit
        override fun cancelSubscription(asaasSubscriptionId: String) = error("unused")

        override fun createOneOffCharge(
            asaasCustomerId: String,
            valueCents: Long,
            description: String,
            idempotencyKey: String,
        ) = "pay_upgrade_1"

        override fun regeneratePixPayload(asaasChargeId: String): String {
            checkoutCalledInsideTransaction = checkoutCalledInsideTransaction || transaction.active
            throw RuntimeException("pix lookup failed")
        }

        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = null

        override fun findPaymentInvoiceUrl(asaasPaymentId: String): String {
            checkoutCalledInsideTransaction = checkoutCalledInsideTransaction || transaction.active
            throw RuntimeException("invoice lookup failed")
        }
    }
}
