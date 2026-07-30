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
import kotlin.test.assertIs
import kotlin.test.assertNull

class CancelSubscriptionTest {
    private val fixedNow = Instant.parse("2026-07-30T12:00:00Z")
    private val periodEnd = Instant.parse("2026-08-30T12:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    @Test
    fun `cancel sets canceledAt and keeps paid period end and status`() {
        val repo = FakeSubscriptionRepository()
        repo.save(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_1",
                asaasSubscriptionId = "sub_1",
                currentPeriodEnd = periodEnd,
                status = SubscriptionStatus.ACTIVE,
            ),
        )

        val result = CancelSubscription(repo, clock).execute(ownerId)

        val success = assertIs<CancelSubscriptionResult.Success>(result)
        assertEquals(fixedNow, success.subscription.canceledAt)
        assertEquals(periodEnd, success.subscription.currentPeriodEnd)
        assertEquals(SubscriptionStatus.ACTIVE, success.subscription.status)
    }

    @Test
    fun `cancel is idempotent conflict when already canceled`() {
        val repo = FakeSubscriptionRepository()
        repo.save(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_1",
                asaasSubscriptionId = "sub_1",
                currentPeriodEnd = periodEnd,
                status = SubscriptionStatus.ACTIVE,
                canceledAt = fixedNow.minusSeconds(60),
            ),
        )

        assertEquals(CancelSubscriptionResult.AlreadyCanceled, CancelSubscription(repo, clock).execute(ownerId))
    }

    @Test
    fun `cancel returns not found when owner has no subscription`() {
        assertEquals(
            CancelSubscriptionResult.NotFound,
            CancelSubscription(FakeSubscriptionRepository(), clock).execute(ownerId),
        )
        assertNull(FakeSubscriptionRepository().findByOwnerUserId(ownerId))
    }

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
}
