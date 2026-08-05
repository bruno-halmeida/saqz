package br.com.saqz.subscriptions.domain

import br.com.saqz.subscriptions.application.AsaasBillingType
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionTest {

    @Test
    fun `past due keeps access inside the grace window`() {
        val since = Instant.parse("2026-07-30T12:00:00Z")
        val sub = pastDue(pastDueSince = since, firstConfirmedAt = Instant.parse("2026-06-01T00:00:00Z"))

        assertTrue(sub.isEntitlingAt(since.plus(6, ChronoUnit.DAYS)))
    }

    @Test
    fun `past due loses access once the grace window expires`() {
        // Antes disto a regra era so `firstConfirmedAt != null`: quem pagasse uma vez ficava
        // com o plano para sempre sem nunca mais pagar.
        val since = Instant.parse("2026-07-30T12:00:00Z")
        val sub = pastDue(pastDueSince = since, firstConfirmedAt = Instant.parse("2026-06-01T00:00:00Z"))

        assertFalse(sub.isEntitlingAt(since.plus(8, ChronoUnit.DAYS)))
    }

    @Test
    fun `past due without a first payment never entitles, grace or not`() {
        val since = Instant.parse("2026-07-30T12:00:00Z")
        val sub = pastDue(pastDueSince = since, firstConfirmedAt = null)

        assertFalse(sub.isEntitlingAt(since.plus(1, ChronoUnit.DAYS)))
    }

    @Test
    fun `legacy row without pastDueSince keeps access`() {
        val sub = pastDue(pastDueSince = null, firstConfirmedAt = Instant.parse("2026-06-01T00:00:00Z"))

        assertTrue(sub.isEntitlingAt(Instant.parse("2027-01-01T00:00:00Z")))
    }

    private fun pastDue(pastDueSince: Instant?, firstConfirmedAt: Instant?) = Subscription(
        ownerUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_1",
        asaasSubscriptionId = "sub_1",
        billingType = null,
        currentPeriodEnd = Instant.parse("2026-08-30T00:00:00Z"),
        status = SubscriptionStatus.PAST_DUE,
        pastDueSince = pastDueSince,
        firstConfirmedAt = firstConfirmedAt,
    )

    @Test
    fun `new subscription defaults to active with no pending plan coupon or past due state`() {
        val subscription = Subscription(
            ownerUserId = UUID.randomUUID(),
            plan = Plan.TITULAR,
            cycle = SubscriptionCycle.MONTHLY,
            asaasCustomerId = "cus_123",
            asaasSubscriptionId = "sub_123",
            billingType = AsaasBillingType.PIX,
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
