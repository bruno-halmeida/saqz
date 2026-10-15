package br.com.saqz.subscriptions.presentation.myplan

import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.Receipt
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.myplan_pending_note
import br.com.saqz.subscriptions.resources.myplan_status_active
import br.com.saqz.subscriptions.resources.myplan_status_canceled
import br.com.saqz.subscriptions.resources.myplan_usage_at_limit
import br.com.saqz.subscriptions.resources.myplan_usage_can_create
import br.com.saqz.subscriptions.resources.myplan_usage_pending_helper
import br.com.saqz.subscriptions.resources.myplan_usage_ratio
import br.com.saqz.subscriptions.resources.myplan_usage_unlimited
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MyPlanMappersTest {
    @Test
    fun `subscription maps identity and next charge without a catalog price`() {
        val card = subscription().toCardUi()

        assertEquals("Organizador", card.name)
        assertEquals(UiText.Res(Res.string.myplan_status_active), card.statusLabel)
        assertEquals(MyPlanStatusTone.Active, card.statusTone)
        assertEquals("30/08/2026", card.nextChargeDate)
        assertNull(card.accessUntilDate)
        assertNull(card.pendingNote)
    }

    @Test
    fun `pending downgrade keeps the current plan name and dated note`() {
        val card = subscription(
            pendingPlan = Plan.Titular,
            pendingPlanEffectiveAt = "2026-08-30T00:00:00Z",
        ).toCardUi()

        assertEquals("Organizador", card.name)
        assertEquals(
            UiText.Res(Res.string.myplan_pending_note, listOf("Titular", "30/08/2026")),
            card.pendingNote,
        )
    }

    @Test
    fun `pending downgrade explains the tighter usage limit`() {
        val usage = subscription(
            usage = SubscriptionUsage(groupsUsed = 1, groupsLimit = 1),
            pendingPlan = Plan.Titular,
            pendingPlanEffectiveAt = "2026-08-30T00:00:00Z",
        ).toUsageUi()

        assertEquals(UiText.Plural(Res.plurals.myplan_usage_ratio, 1, listOf(1, 1)), usage.ratioLabel)
        assertEquals(
            UiText.Res(Res.string.myplan_usage_pending_helper, listOf("Titular", "30/08/2026")),
            usage.helperText,
        )
    }

    @Test
    fun `canceled subscription maps access until instead of next charge`() {
        val card = subscription(canceledAt = "2026-08-01T00:00:00Z").toCardUi()

        assertEquals(UiText.Res(Res.string.myplan_status_canceled), card.statusLabel)
        assertEquals(MyPlanStatusTone.Canceled, card.statusTone)
        assertNull(card.nextChargeDate)
        assertEquals("30/08/2026", card.accessUntilDate)
    }

    @Test
    fun `canceled access still running keeps the guaranteed-until wording`() {
        val card = subscription(canceledAt = "2026-08-01T00:00:00Z").toCardUi(now = Instant.parse("2026-08-29T12:00:00Z"))

        assertFalse(card.accessEnded)
    }

    @Test
    fun `canceled access whose date has passed says it ended`() {
        val card = subscription(canceledAt = "2026-08-01T00:00:00Z").toCardUi(now = Instant.parse("2026-09-01T00:00:00Z"))

        assertTrue(card.accessEnded)
        assertEquals("30/08/2026", card.accessUntilDate)
    }

    @Test
    fun `active subscription never reports ended access`() {
        val card = subscription().toCardUi(now = Instant.parse("2030-01-01T00:00:00Z"))

        assertFalse(card.accessEnded)
    }

    @Test
    fun `expired subscription without cancel date has no next charge`() {
        val card = subscription(status = SubscriptionStatus.Canceled).toCardUi()

        assertEquals(MyPlanStatusTone.Canceled, card.statusTone)
        assertNull(card.nextChargeDate)
        assertEquals("30/08/2026", card.accessUntilDate)
    }

    @Test
    fun `subscription maps bounded usage`() {
        val usage = subscription(usage = SubscriptionUsage(groupsUsed = 2, groupsLimit = 3)).toUsageUi()

        assertEquals(UiText.Plural(Res.plurals.myplan_usage_ratio, 3, listOf(2, 3)), usage.ratioLabel)
        assertEquals(2f / 3f, usage.progress)
    }

    @Test
    fun `usage below the limit says how many groups can still be created`() {
        val usage = subscription(usage = SubscriptionUsage(groupsUsed = 2, groupsLimit = 3)).toUsageUi()

        assertEquals(UiText.Plural(Res.plurals.myplan_usage_can_create, 1, listOf(1)), usage.helperText)
    }

    @Test
    fun `usage below the limit pluralizes the remaining groups`() {
        val usage = subscription(usage = SubscriptionUsage(groupsUsed = 1, groupsLimit = 4)).toUsageUi()

        assertEquals(UiText.Plural(Res.plurals.myplan_usage_can_create, 3, listOf(3)), usage.helperText)
    }

    @Test
    fun `usage at the limit asks to change plan`() {
        val usage = subscription(usage = SubscriptionUsage(groupsUsed = 3, groupsLimit = 3)).toUsageUi()

        assertEquals(UiText.Res(Res.string.myplan_usage_at_limit), usage.helperText)
    }

    @Test
    fun `unlimited usage has no helper text`() {
        val usage = subscription(usage = SubscriptionUsage(groupsUsed = 4, groupsLimit = null)).toUsageUi()

        assertNull(usage.helperText)
    }

    @Test
    fun `subscription maps unlimited usage without a progress bar`() {
        val usage = subscription(usage = SubscriptionUsage(groupsUsed = 4, groupsLimit = null)).toUsageUi()

        assertEquals(UiText.Res(Res.string.myplan_usage_unlimited), usage.ratioLabel)
        assertNull(usage.progress)
    }

    @Test
    fun `receipt maps date and amount`() {
        val receipt = Receipt(
            asaasEventId = "evt-1",
            asaasPaymentId = "pay-1",
            valueCents = 4990,
            confirmedAt = "2026-07-01T00:00:00Z",
            processedAt = "2026-07-01T00:05:00Z",
        ).toUi()

        assertEquals(MyPlanReceiptUi("evt-1", "01/07/2026", "R$ 49,90"), receipt)
    }

    private fun subscription(
        status: SubscriptionStatus = SubscriptionStatus.Active,
        canceledAt: String? = null,
        usage: SubscriptionUsage = SubscriptionUsage(groupsUsed = 2, groupsLimit = 3),
        pendingPlan: Plan? = null,
        pendingPlanEffectiveAt: String? = null,
    ) = MySubscription(
        status = status,
        entitled = true,
        plan = Plan.Organizador,
        cycle = SubscriptionCycle.Monthly,
        currentPeriodEnd = "2026-08-30T00:00:00Z",
        usage = usage,
        canceledAt = canceledAt,
        pendingPlan = pendingPlan,
        pendingPlanEffectiveAt = pendingPlanEffectiveAt,
    )
}
