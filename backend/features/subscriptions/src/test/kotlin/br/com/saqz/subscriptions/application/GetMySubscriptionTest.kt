package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.OwnedGroupCounter
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetMySubscriptionTest {
    private val now = Instant.parse("2026-07-30T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    @Test
    fun `reports groups used against plan limit via moreRestrictive`() {
        val subscriptions = MemorySubscriptions(
            baseSubscription().copy(
                plan = Plan.ORGANIZADOR,
                pendingPlan = Plan.TITULAR,
            ),
        )
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(2), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))

        assertEquals(2, found.subscription.usage.groupsUsed)
        assertEquals(1, found.subscription.usage.groupsLimit)
        assertEquals(Plan.ORGANIZADOR, found.subscription.plan)
        assertEquals(Plan.TITULAR, found.subscription.pendingPlan)
        assertFalse(found.subscription.readOnly)
    }

    @Test
    fun `unlimited plan reports null groups limit`() {
        val subscriptions = MemorySubscriptions(baseSubscription().copy(plan = Plan.ILIMITADO))
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(5), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))
        assertEquals(5, found.subscription.usage.groupsUsed)
        assertNull(found.subscription.usage.groupsLimit)
    }

    @Test
    fun `past due within 7 day grace is not read only`() {
        val subscriptions = MemorySubscriptions(
            baseSubscription().copy(
                status = SubscriptionStatus.PAST_DUE,
                pastDueSince = Instant.parse("2026-07-23T12:00:00Z"),
            ),
        )
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(1), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))
        assertFalse(found.subscription.readOnly)
    }

    @Test
    fun `past due for more than 7 days becomes read only at read time`() {
        val subscriptions = MemorySubscriptions(
            baseSubscription().copy(
                status = SubscriptionStatus.PAST_DUE,
                pastDueSince = Instant.parse("2026-07-23T11:59:59Z"),
            ),
        )
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(1), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))
        assertTrue(found.subscription.readOnly)
    }

    @Test
    fun `canceled within 30 day grace is not read only`() {
        val subscriptions = MemorySubscriptions(
            baseSubscription().copy(
                status = SubscriptionStatus.CANCELED,
                canceledAt = Instant.parse("2026-06-30T12:00:00Z"),
                pastDueSince = null,
            ),
        )
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(1), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))
        assertFalse(found.subscription.readOnly)
    }

    @Test
    fun `canceled for more than 30 days becomes read only at read time`() {
        val subscriptions = MemorySubscriptions(
            baseSubscription().copy(
                status = SubscriptionStatus.CANCELED,
                canceledAt = Instant.parse("2026-06-30T11:59:59Z"),
                pastDueSince = null,
            ),
        )
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(1), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))
        assertTrue(found.subscription.readOnly)
    }

    @Test
    fun `active subscription is never read only`() {
        val subscriptions = MemorySubscriptions(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                canceledAt = null,
            ),
        )
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(0), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))
        assertFalse(found.subscription.readOnly)
        assertEquals(SubscriptionStatus.ACTIVE, found.subscription.status)
        assertEquals(Instant.parse("2026-08-30T00:00:00Z"), found.subscription.currentPeriodEnd)
    }

    @Test
    fun `missing subscription is not found`() {
        val useCase = GetMySubscription(MemorySubscriptions(null), FixedOwnedGroups(0), clock)
        assertEquals(GetMySubscriptionResult.NotFound, useCase.execute(ownerId))
    }

    @Test
    fun `isReadOnly is computed from now not from event time`() {
        val pastDueSince = Instant.parse("2026-07-20T00:00:00Z")
        val subscription = baseSubscription().copy(
            status = SubscriptionStatus.PAST_DUE,
            pastDueSince = pastDueSince,
        )
        assertFalse(GetMySubscription.isReadOnly(subscription, Instant.parse("2026-07-27T00:00:00Z")))
        assertTrue(GetMySubscription.isReadOnly(subscription, Instant.parse("2026-07-27T00:00:01Z")))
    }

    private fun baseSubscription() = Subscription(
        ownerUserId = ownerId,
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_1",
        asaasSubscriptionId = "sub_1",
        currentPeriodEnd = Instant.parse("2026-08-30T00:00:00Z"),
        status = SubscriptionStatus.ACTIVE,
    )

    private class MemorySubscriptions(private val subscription: Subscription?) : SubscriptionRepository {
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String): Subscription? = null
        override fun findByOwnerUserId(ownerUserId: UUID): Subscription? =
            subscription?.takeIf { it.ownerUserId == ownerUserId }
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = error("unused")
        override fun save(subscription: Subscription) = error("unused")
    }

    private class FixedOwnedGroups(private val count: Int) : OwnedGroupCounter {
        override fun countOwnedGroups(ownerUserId: UUID): Int = count
    }
}
