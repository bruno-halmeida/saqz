package br.com.saqz.subscriptions.presentation.changeplan

import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.PlanCatalogItem
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.changeplan_benefit_admins
import br.com.saqz.subscriptions.resources.changeplan_benefit_athletes
import br.com.saqz.subscriptions.resources.changeplan_benefit_athletes_unlimited
import br.com.saqz.subscriptions.resources.changeplan_benefit_groups
import br.com.saqz.subscriptions.resources.changeplan_benefit_groups_one
import br.com.saqz.subscriptions.resources.changeplan_benefit_groups_unlimited
import br.com.saqz.subscriptions.resources.changeplan_benefit_reports
import br.com.saqz.subscriptions.resources.changeplan_benefit_whatsapp
import br.com.saqz.subscriptions.resources.changeplan_pending_note
import br.com.saqz.subscriptions.resources.changeplan_price_month
import br.com.saqz.subscriptions.resources.changeplan_price_year

internal fun MySubscription.toPendingNote(): UiText? {
    val pending = pendingPlan ?: return null
    val at = pendingPlanEffectiveAt ?: return null
    return UiText.Res(Res.string.changeplan_pending_note, listOf(pending.name, isoDateToPtBr(at)))
}

internal fun PlanCatalogItem.toCardUi(currentPlan: Plan, cycle: SubscriptionCycle): ChangePlanCardUi {
    val priceCents = if (cycle == SubscriptionCycle.Annual) annualPriceCents else monthlyPriceCents
    return ChangePlanCardUi(
        plan = id,
        name = id.name,
        priceLabel = UiText.Res(
            if (cycle == SubscriptionCycle.Annual) Res.string.changeplan_price_year else Res.string.changeplan_price_month,
            listOf(priceCents.toBrlString()),
        ),
        benefits = benefits(),
        isCurrent = id == currentPlan,
    )
}

private fun PlanCatalogItem.benefits(): List<UiText> = buildList {
    val groups = maxGroups
    add(
        when (groups) {
            null -> UiText.Res(Res.string.changeplan_benefit_groups_unlimited)
            1 -> UiText.Res(Res.string.changeplan_benefit_groups_one)
            else -> UiText.Res(Res.string.changeplan_benefit_groups, listOf(groups))
        },
    )
    val athletes = maxAthletes
    add(
        if (athletes == null) {
            UiText.Res(Res.string.changeplan_benefit_athletes_unlimited)
        } else {
            UiText.Res(Res.string.changeplan_benefit_athletes, listOf(athletes))
        },
    )
    if (multiAdmin) add(UiText.Res(Res.string.changeplan_benefit_admins))
    if (reports) add(UiText.Res(Res.string.changeplan_benefit_reports))
    if (whatsappSla) add(UiText.Res(Res.string.changeplan_benefit_whatsapp))
}
