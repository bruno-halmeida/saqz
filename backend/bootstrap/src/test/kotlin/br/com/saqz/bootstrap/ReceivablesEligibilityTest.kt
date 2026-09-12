package br.com.saqz.bootstrap

import br.com.saqz.bootstrap.configuration.CentralReceivablesEligibility
import br.com.saqz.receivables.application.ReceivablesEntitlement
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccess
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccessLookup
import br.com.saqz.sharedkernel.subscription.TrialStatus
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceivablesEligibilityTest {
    private val now = Instant.parse("2026-09-12T12:00:00Z")
    private val owner = UUID.randomUUID()
    private val end = now.plusSeconds(86400)
    private var status = TrialStatus.ACTIVE
    private var subscription: Subscription? = null
    private val eligibility = CentralReceivablesEligibility(
        OrganizerTrialAccessLookup { OrganizerTrialAccess(status, now.minusSeconds(60), end, now, true) },
        { subscription },
    )

    @Test
    fun `only active trial is eligible and its cutoff comes from central contract`() {
        assertEquals(ReceivablesEntitlement(true, end), eligibility.forOwner(owner, now))
        listOf(TrialStatus.AVAILABLE, TrialStatus.EXPIRED, TrialStatus.INELIGIBLE, TrialStatus.SUBSCRIBED).forEach {
            status = it
            assertEquals(ReceivablesEntitlement(false, end), eligibility.forOwner(owner, now))
        }
    }

    @Test
    fun `Organizador and Ilimitado are eligible but effective Titular overrides remaining trial`() {
        subscription = paid(Plan.ORGANIZADOR)
        assertEquals(ReceivablesEntitlement(true, null), eligibility.forOwner(owner, now))
        subscription = paid(Plan.ILIMITADO)
        assertTrue(eligibility.forOwner(owner, now).eligible)
        subscription = paid(Plan.TITULAR)
        assertFalse(eligibility.forOwner(owner, now).eligible)
    }

    @Test
    fun `scheduled Titular downgrade cuts exactly on effective date before job changes plan`() {
        subscription = paid(Plan.ORGANIZADOR).copy(pendingPlan = Plan.TITULAR, pendingPlanEffectiveAt = end)
        assertEquals(ReceivablesEntitlement(true, end), eligibility.forOwner(owner, now))
        assertEquals(ReceivablesEntitlement(false, end), eligibility.forOwner(owner, end))
        assertFalse(eligibility.forOwner(owner, end.plusSeconds(1)).eligible)
    }

    @Test
    fun `cancellation and delinquency reuse subscription entitlements and preserve declared cutoff`() {
        status = TrialStatus.EXPIRED
        subscription = paid(Plan.ORGANIZADOR).copy(status = SubscriptionStatus.CANCELED, firstConfirmedAt = now.minusSeconds(60))
        assertEquals(ReceivablesEntitlement(true, end), eligibility.forOwner(owner, now))
        assertFalse(eligibility.forOwner(owner, end).eligible)
        subscription = paid(Plan.ORGANIZADOR).copy(status = SubscriptionStatus.PAST_DUE,
            firstConfirmedAt = now.minusSeconds(60), pastDueSince = now)
        assertEquals(ReceivablesEntitlement(true, now.plus(Subscription.PAST_DUE_GRACE)), eligibility.forOwner(owner, now))
        assertFalse(eligibility.forOwner(owner, now.plus(Subscription.PAST_DUE_GRACE)).eligible)
    }

    private fun paid(plan: Plan) = Subscription(owner, plan, SubscriptionCycle.MONTHLY, "customer", "subscription", null, end)
}
