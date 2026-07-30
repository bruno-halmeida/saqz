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
        useCase = ChangePlan(subscriptions, gateway, usage, coupons, clock)
        subscriptions.save(baseSubscription())
    }

    @Test
    fun `upgrade charges prorata and applies target plan immediately`() {
        val result = useCase.execute(
            ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR),
        )

        val upgraded = assertIs<ChangePlanResult.Upgraded>(result)
        assertEquals(Plan.ORGANIZADOR, upgraded.subscription.plan)
        assertNull(upgraded.subscription.pendingPlan)
        assertTrue(upgraded.chargedCents > 0L)
        assertEquals("pay_upgrade", upgraded.oneOffChargeId)
        assertEquals(
            listOf("subscription-upgrade:$ownerId:$requestId"),
            gateway.oneOffIdempotencyKeys,
        )
        assertEquals(listOf("sub_1" to Plan.ORGANIZADOR.monthlyPriceCents), gateway.valueUpdates)
    }

    @Test
    fun `upgrade with remaining coupon cycles pushes discounted target price`() {
        coupons.byId[couponId] = Coupon(id = couponId, code = "GALERA10", discountPercent = 10, durationCycles = 3)
        subscriptions.save(
            baseSubscription().copy(
                couponId = couponId,
                couponCyclesRemaining = 2,
            ),
        )

        useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.ORGANIZADOR))

        // 5990 * 0.9 = 5391
        assertEquals(listOf("sub_1" to 5_391L), gateway.valueUpdates)
    }

    @Test
    fun `downgrade blocked when owner exceeds target group limit`() {
        subscriptions.save(baseSubscription().copy(plan = Plan.ORGANIZADOR))
        usage.usage = OwnerPlanUsage(ownedGroupCount = 3, occupyingAthleteCount = 5)

        val result = useCase.execute(ChangePlanCommand(ownerId, requestId, Plan.TITULAR))

        assertEquals(ChangePlanResult.DowngradeBlockedByUsage, result)
        assertTrue(gateway.oneOffIdempotencyKeys.isEmpty())
        assertTrue(gateway.valueUpdates.isEmpty())
        assertEquals(Plan.ORGANIZADOR, subscriptions.findByOwnerUserId(ownerId)!!.plan)
        assertNull(subscriptions.findByOwnerUserId(ownerId)!!.pendingPlan)
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
        assertTrue(gateway.oneOffIdempotencyKeys.isEmpty())
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

        // 3990 * 0.8 = 3192
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
    )

    private class FakeSubscriptionRepository : SubscriptionRepository {
        private val byOwner = linkedMapOf<UUID, Subscription>()
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) =
            byOwner.values.firstOrNull { it.asaasSubscriptionId == asaasSubscriptionId }

        override fun findByOwnerUserId(ownerUserId: UUID) = byOwner[ownerUserId]
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
            return "pay_upgrade"
        }

        override fun regeneratePixPayload(asaasChargeId: String) = error("unused")
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = null
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = null
    }
}
