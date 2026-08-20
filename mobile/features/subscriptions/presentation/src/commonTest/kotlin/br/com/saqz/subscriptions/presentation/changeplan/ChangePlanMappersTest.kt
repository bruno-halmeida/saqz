package br.com.saqz.subscriptions.presentation.changeplan

import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.PlanCatalogItem
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.changeplan_benefit_groups
import br.com.saqz.subscriptions.resources.changeplan_benefit_groups_unlimited
import br.com.saqz.subscriptions.resources.changeplan_pending_note
import br.com.saqz.subscriptions.resources.changeplan_price_month
import br.com.saqz.subscriptions.resources.changeplan_price_year
import br.com.saqz.subscriptions.resources.changeplan_scheduled_chip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangePlanMappersTest {
    @Test
    fun `monthly catalog card marks the current plan and monthly price`() {
        val card = ORGANIZADOR.toCardUi(currentPlan = Plan.Organizador, cycle = SubscriptionCycle.Monthly)

        assertEquals("Organizador", card.name)
        assertTrue(card.isCurrent)
        assertEquals(UiText.Res(Res.string.changeplan_price_month, listOf("R$ 59,90")), card.priceLabel)
        assertEquals(UiText.Res(Res.string.changeplan_benefit_groups, listOf(3)), card.benefits.first())
    }

    @Test
    fun `annual unlimited card is not current and uses yearly price`() {
        val card = ILIMITADO.toCardUi(currentPlan = Plan.Organizador, cycle = SubscriptionCycle.Annual)

        assertEquals("Ilimitado", card.name)
        assertFalse(card.isCurrent)
        assertEquals(UiText.Res(Res.string.changeplan_price_year, listOf("R$ 89,90")), card.priceLabel)
        assertEquals(UiText.Res(Res.string.changeplan_benefit_groups_unlimited), card.benefits.first())
    }

    @Test
    fun `pending plan is scheduled on its card and not current`() {
        val titular = PlanCatalogItem(
            id = Plan.Titular,
            monthlyPriceCents = 3_990,
            annualPriceCents = 39_900,
            maxGroups = 1,
            maxAthletes = 25,
            multiAdmin = false,
            reports = false,
            whatsappSla = false,
        )
        val card = titular.toCardUi(
            currentPlan = Plan.Organizador,
            cycle = SubscriptionCycle.Monthly,
            pendingPlan = Plan.Titular,
            pendingPlanEffectiveAt = "2026-08-30T00:00:00Z",
        )

        assertFalse(card.isCurrent)
        assertTrue(card.isScheduled)
        assertEquals(
            UiText.Res(Res.string.changeplan_scheduled_chip, listOf("30/08/2026")),
            card.scheduledLabel,
        )
    }

    @Test
    fun `pending plan becomes a dated note`() {
        val note = MySubscription(
            status = SubscriptionStatus.Active,
            entitled = true,
            plan = Plan.Organizador,
            cycle = SubscriptionCycle.Monthly,
            currentPeriodEnd = "2026-08-30T00:00:00Z",
            usage = SubscriptionUsage(2, 3),
            canceledAt = null,
            pendingPlan = Plan.Titular,
            pendingPlanEffectiveAt = "2026-08-30T00:00:00Z",
        ).toPendingNote()

        assertEquals(
            UiText.Res(Res.string.changeplan_pending_note, listOf("Titular", "30/08/2026")),
            note,
        )
    }

    @Test
    fun `missing pending plan has no note`() {
        assertNull(
            MySubscription(
                status = SubscriptionStatus.Active,
                entitled = true,
                plan = Plan.Organizador,
                cycle = SubscriptionCycle.Monthly,
                currentPeriodEnd = "2026-08-30T00:00:00Z",
                usage = SubscriptionUsage(2, 3),
                canceledAt = null,
            ).toPendingNote(),
        )
    }
}

private val ORGANIZADOR = PlanCatalogItem(
    id = Plan.Organizador,
    monthlyPriceCents = 5_990,
    annualPriceCents = 59_900,
    maxGroups = 3,
    maxAthletes = null,
    multiAdmin = false,
    reports = false,
    whatsappSla = false,
)

private val ILIMITADO = PlanCatalogItem(
    id = Plan.Ilimitado,
    monthlyPriceCents = 8_990,
    annualPriceCents = 89_900,
    maxGroups = null,
    maxAthletes = null,
    multiAdmin = true,
    reports = true,
    whatsappSla = true,
)
