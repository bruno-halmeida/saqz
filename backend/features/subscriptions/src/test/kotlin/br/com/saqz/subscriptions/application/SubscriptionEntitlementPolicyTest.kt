package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mirrors the entitlement predicate in [br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionPlanLookup]:
 * ACTIVE always; PAST_DUE only after at least one confirmed payment (firstConfirmedAt set).
 * Unpaid creates stay PAST_DUE with null firstConfirmedAt and must not grant plan limits.
 */
class SubscriptionEntitlementPolicyTest {
    @Test
    fun `active always entitles`() {
        assertTrue(entitles(SubscriptionStatus.ACTIVE, firstConfirmedAt = null))
        assertTrue(entitles(SubscriptionStatus.ACTIVE, Instant.parse("2026-01-01T00:00:00Z")))
    }

    @Test
    fun `past due without first confirmation does not entitle`() {
        assertFalse(entitles(SubscriptionStatus.PAST_DUE, firstConfirmedAt = null))
    }

    @Test
    fun `past due after a confirmed payment still entitles during grace`() {
        assertTrue(entitles(SubscriptionStatus.PAST_DUE, Instant.parse("2026-01-01T00:00:00Z")))
    }

    @Test
    fun `canceled never entitles`() {
        assertFalse(entitles(SubscriptionStatus.CANCELED, Instant.parse("2026-01-01T00:00:00Z")))
        assertFalse(entitles(SubscriptionStatus.CANCELED, firstConfirmedAt = null))
    }

    private fun entitles(status: SubscriptionStatus, firstConfirmedAt: Instant?): Boolean =
        status == SubscriptionStatus.ACTIVE ||
            (status == SubscriptionStatus.PAST_DUE && firstConfirmedAt != null)
}
