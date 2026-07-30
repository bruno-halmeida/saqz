package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.actor.AuthenticatedActor
import br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver
import br.com.saqz.subscriptions.application.GetMySubscription
import br.com.saqz.subscriptions.application.OwnedGroupCounter
import br.com.saqz.subscriptions.application.SubscriptionRepository
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscriptionQueryControllerTest {
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val now = Instant.parse("2026-07-30T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val identity = RequestIdentity("subject")

    @Test
    fun `returns current subscription for authenticated owner`() {
        val controller = SubscriptionQueryController(
            FixedActor(ownerId),
            GetMySubscription(
                FixedSubscriptions(
                    Subscription(
                        ownerUserId = ownerId,
                        plan = Plan.ORGANIZADOR,
                        cycle = SubscriptionCycle.MONTHLY,
                        asaasCustomerId = "cus",
                        asaasSubscriptionId = "sub",
                        currentPeriodEnd = Instant.parse("2026-08-15T00:00:00Z"),
                        status = SubscriptionStatus.PAST_DUE,
                        pastDueSince = Instant.parse("2026-07-01T00:00:00Z"),
                    ),
                ),
                FixedGroups(2),
                clock,
            ),
        )

        val response = controller.me(identity)

        assertEquals(SubscriptionStatus.PAST_DUE, response.status)
        assertEquals(Plan.ORGANIZADOR, response.plan)
        assertEquals(2, response.usage.groupsUsed)
        assertEquals(3, response.usage.groupsLimit)
        assertTrue(response.readOnly)
    }

    @Test
    fun `missing subscription throws not found`() {
        val controller = SubscriptionQueryController(
            FixedActor(ownerId),
            GetMySubscription(FixedSubscriptions(null), FixedGroups(0), clock),
        )

        assertThrows<SubscriptionNotFoundException> { controller.me(identity) }
    }

    private class FixedActor(private val userId: UUID) : AuthenticatedActorResolver {
        override fun resolve(identity: RequestIdentity) = AuthenticatedActor(userId)
    }

    private class FixedSubscriptions(private val subscription: Subscription?) : SubscriptionRepository {
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) = null
        override fun findByOwnerUserId(ownerUserId: UUID) = subscription
        override fun save(subscription: Subscription) = error("unused")
    }

    private class FixedGroups(private val count: Int) : OwnedGroupCounter {
        override fun countOwnedGroups(ownerUserId: UUID) = count
    }
}
