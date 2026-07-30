package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.OwnerPlanUsage
import br.com.saqz.sharedkernel.subscription.OwnerPlanUsageLookup
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

    private lateinit var subscriptions: FakeSubscriptionRepository
    private lateinit var gateway: FakeAsaasGateway
    private lateinit var usage: MutableOwnerPlanUsageLookup
    private lateinit var useCase: ChangePlan

    @BeforeEach
    fun setUp() {
        subscriptions = FakeSubscriptionRepository()
        gateway = FakeAsaasGateway()
        usage = MutableOwnerPlanUsageLookup(OwnerPlanUsage(ownedGroupCount = 1, occupyingAthleteCount = 5))
        useCase = ChangePlan(subscriptions, gateway, usage, clock)
        subscriptions.save(baseSubscription())
    }

    @Test
    fun `upgrade charges prorata and applies target plan immediately`() {
        // Half of remaining period (~15 of ~30 days): delta 2000 * ~0.5 ≈ 1000
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
