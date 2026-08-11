package br.com.saqz.subscriptions.presentation.myplan

import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.Receipt
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.myplan_status_active
import br.com.saqz.subscriptions.resources.myplan_status_canceled
import br.com.saqz.subscriptions.resources.myplan_usage_ratio
import br.com.saqz.subscriptions.resources.myplan_usage_unlimited
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MyPlanMappersTest {
    @Test
    fun `subscription maps identity and next charge without a catalog price`() {
        val card = subscription().toCardUi()

        assertEquals("Organizador", card.name)
        assertEquals(UiText.Res(Res.string.myplan_status_active), card.statusLabel)
        assertEquals(MyPlanStatusTone.Active, card.statusTone)
        assertEquals("30/08/2026", card.nextChargeDate)
        assertNull(card.accessUntilDate)
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
    fun `subscription maps bounded usage`() {
        val usage = subscription(usage = SubscriptionUsage(groupsUsed = 2, groupsLimit = 3)).toUsageUi()

        assertEquals(UiText.Res(Res.string.myplan_usage_ratio, listOf(2, 3)), usage.ratioLabel)
        assertEquals(2f / 3f, usage.progress)
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
    ) = MySubscription(
        status = status,
        entitled = true,
        plan = Plan.Organizador,
        cycle = SubscriptionCycle.Monthly,
        currentPeriodEnd = "2026-08-30T00:00:00Z",
        usage = usage,
        canceledAt = canceledAt,
    )
}
