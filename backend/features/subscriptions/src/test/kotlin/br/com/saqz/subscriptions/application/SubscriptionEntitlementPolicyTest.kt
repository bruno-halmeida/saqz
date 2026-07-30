package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mirrors the entitlement predicate in [br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionPlanLookup]:
 * ACTIVE always; PAST_DUE only after firstConfirmedAt; CANCELED while still inside currentPeriodEnd.
 */
class SubscriptionEntitlementPolicyTest {
    private val now = Instant.parse("2026-07-30T12:00:00Z")
    private val periodEndFuture = Instant.parse("2026-08-30T12:00:00Z")
    private val periodEndPast = Instant.parse("2026-07-01T12:00:00Z")

    @Test
    fun `active always entitles`() {
        assertTrue(entitles(SubscriptionStatus.ACTIVE, firstConfirmedAt = null, periodEndFuture))
        assertTrue(entitles(SubscriptionStatus.ACTIVE, Instant.parse("2026-01-01T00:00:00Z"), periodEndPast))
    }

    @Test
    fun `past due without first confirmation does not entitle`() {
        assertFalse(entitles(SubscriptionStatus.PAST_DUE, firstConfirmedAt = null, periodEndFuture))
    }

    @Test
    fun `past due after a confirmed payment still entitles during grace`() {
        assertTrue(entitles(SubscriptionStatus.PAST_DUE, Instant.parse("2026-01-01T00:00:00Z"), periodEndFuture))
    }

    @Test
    fun `canceled still entitles until paid period end`() {
        assertTrue(entitles(SubscriptionStatus.CANCELED, Instant.parse("2026-01-01T00:00:00Z"), periodEndFuture))
    }

    @Test
    fun `canceled after period end does not entitle`() {
        assertFalse(entitles(SubscriptionStatus.CANCELED, Instant.parse("2026-01-01T00:00:00Z"), periodEndPast))
    }

    private fun entitles(
        status: SubscriptionStatus,
        firstConfirmedAt: Instant?,
        currentPeriodEnd: Instant,
    ): Boolean =
        status == SubscriptionStatus.ACTIVE ||
            (status == SubscriptionStatus.PAST_DUE && firstConfirmedAt != null) ||
            (status == SubscriptionStatus.CANCELED && currentPeriodEnd.isAfter(now))
}
