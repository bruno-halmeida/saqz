package br.com.saqz.subscriptions.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionTest {
    @Test
    fun `new subscription defaults to active with no pending plan coupon or past due state`() {
        val subscription = Subscription(
            ownerUserId = "firebase-uid-1",
            plan = Plan.TITULAR,
            cycle = SubscriptionCycle.MONTHLY,
            asaasSubscriptionId = "sub_123",
            currentPeriodEnd = Instant.parse("2026-08-30T00:00:00Z"),
        )

        assertEquals(SubscriptionStatus.ACTIVE, subscription.status)
        assertNull(subscription.canceledAt)
        assertNull(subscription.pendingPlan)
        assertNull(subscription.pendingPlanEffectiveAt)
        assertNull(subscription.couponId)
        assertNull(subscription.couponCyclesRemaining)
        assertNull(subscription.pastDueSince)
    }
}
