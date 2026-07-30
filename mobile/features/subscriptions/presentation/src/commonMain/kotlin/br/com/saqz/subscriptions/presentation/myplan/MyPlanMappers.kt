package br.com.saqz.subscriptions.presentation.myplan

import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.PlanDetails
import br.com.saqz.subscriptions.domain.subscription.Receipt
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.myplan_billing_credit_card
import br.com.saqz.subscriptions.resources.myplan_billing_pix
import br.com.saqz.subscriptions.resources.myplan_pending_change
import br.com.saqz.subscriptions.resources.myplan_price_annual
import br.com.saqz.subscriptions.resources.myplan_price_monthly
import br.com.saqz.subscriptions.resources.myplan_status_active
import br.com.saqz.subscriptions.resources.myplan_status_canceled
import br.com.saqz.subscriptions.resources.myplan_status_past_due
import br.com.saqz.subscriptions.resources.myplan_usage_helper
import br.com.saqz.subscriptions.resources.myplan_usage_ratio
import br.com.saqz.subscriptions.resources.myplan_usage_unlimited

internal fun PlanDetails?.displayName(fallback: Plan): String = this?.name ?: fallback.name

internal fun PlanDetails.priceCents(cycle: SubscriptionCycle): Long =
    if (cycle == SubscriptionCycle.Monthly) monthlyPriceCents else annualPriceCents

internal fun priceLineFor(cents: Long, cycle: SubscriptionCycle): UiText {
    val amount = cents.toBrlString()
    val res = if (cycle == SubscriptionCycle.Monthly) Res.string.myplan_price_monthly else Res.string.myplan_price_annual
    return UiText.Res(res, listOf(amount))
}

internal fun BillingType.toUiText(): UiText = when (this) {
    BillingType.Pix -> UiText.Res(Res.string.myplan_billing_pix)
    BillingType.CreditCard -> UiText.Res(Res.string.myplan_billing_credit_card)
}

internal fun SubscriptionStatus.toTone(): MyPlanStatusTone = when (this) {
    SubscriptionStatus.Active -> MyPlanStatusTone.Active
    SubscriptionStatus.PastDue -> MyPlanStatusTone.PastDue
    SubscriptionStatus.Canceled -> MyPlanStatusTone.Canceled
}

internal fun SubscriptionStatus.toUiText(): UiText = when (this) {
    SubscriptionStatus.Active -> UiText.Res(Res.string.myplan_status_active)
    SubscriptionStatus.PastDue -> UiText.Res(Res.string.myplan_status_past_due)
    SubscriptionStatus.Canceled -> UiText.Res(Res.string.myplan_status_canceled)
}

internal fun MySubscription.toCardUi(plans: List<PlanDetails>): MyPlanCardUi {
    val details = plans.firstOrNull { it.id == plan }
    val pendingLine = pendingPlan?.let { pendingId ->
        val pendingName = plans.firstOrNull { it.id == pendingId }.displayName(pendingId)
        UiText.Res(
            Res.string.myplan_pending_change,
            listOf(pendingName, isoDateToPtBr(pendingPlanEffectiveAt.orEmpty())),
        )
    }
    return MyPlanCardUi(
        name = details.displayName(plan),
        statusLabel = status.toUiText(),
        statusTone = status.toTone(),
        priceLine = details?.let { priceLineFor(it.priceCents(cycle), cycle) } ?: UiText.Raw(""),
        nextChargeDate = isoDateToPtBr(currentPeriodEnd),
        paymentMethodLabel = paymentMethod?.toUiText(),
        pendingChangeLine = pendingLine,
    )
}

internal fun MySubscription.toUsageUi(): MyPlanUsageUi {
    val limit = usage.groupsLimit
    return MyPlanUsageUi(
        ratioLabel = if (limit != null) {
            UiText.Res(Res.string.myplan_usage_ratio, listOf(usage.groupsUsed, limit))
        } else {
            UiText.Res(Res.string.myplan_usage_unlimited)
        },
        progress = limit?.let { if (it == 0) 0f else usage.groupsUsed.toFloat() / it },
        helperText = UiText.Res(Res.string.myplan_usage_helper),
    )
}

internal fun PlanDetails.toChangeOptionUi(current: MySubscription): MyPlanChangeOptionUi = MyPlanChangeOptionUi(
    planId = id,
    name = name,
    priceLine = priceLineFor(priceCents(current.cycle), current.cycle),
    isCurrent = id == current.plan,
)

internal fun Receipt.toUi(): MyPlanReceiptUi = MyPlanReceiptUi(
    id = asaasEventId,
    dateLabel = (confirmedAt ?: processedAt).let(::isoDateToPtBr),
    valueLabel = valueCents?.toBrlString() ?: "—",
)
