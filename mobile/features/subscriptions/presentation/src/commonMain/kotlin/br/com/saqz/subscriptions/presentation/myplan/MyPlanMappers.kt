package br.com.saqz.subscriptions.presentation.myplan

import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Receipt
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.myplan_status_active
import br.com.saqz.subscriptions.resources.myplan_status_canceled
import br.com.saqz.subscriptions.resources.myplan_status_past_due
import br.com.saqz.subscriptions.resources.myplan_usage_helper
import br.com.saqz.subscriptions.resources.myplan_usage_ratio
import br.com.saqz.subscriptions.resources.myplan_usage_unlimited

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

internal fun MySubscription.toCardUi(): MyPlanCardUi {
    // O backend marca `canceledAt` sem tocar em `status` — o acesso segue até
    // `currentPeriodEnd`, e é o webhook quem migra o status pra CANCELED depois (achado
    // do Codex no PR #93, confirmado em CancelSubscriptionTest). A tela não pode esperar
    // o webhook para parar de mostrar "Ativo" com o botão de cancelar habilitado de novo.
    val canceled = canceledAt != null
    val effectiveStatus = if (canceled) SubscriptionStatus.Canceled else status
    return MyPlanCardUi(
        name = plan.name,
        statusLabel = effectiveStatus.toUiText(),
        statusTone = effectiveStatus.toTone(),
        // `currentPeriodEnd` cancelada não é cobrança futura — cancelar já parou a cobrança
        // no Asaas (CancelSubscription.kt: "Stop future Asaas charges now; local access
        // still follows currentPeriodEnd"). Mostrar como próxima cobrança seria falso.
        nextChargeDate = if (canceled) null else isoDateToPtBr(currentPeriodEnd),
        accessUntilDate = if (canceled) isoDateToPtBr(currentPeriodEnd) else null,
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

internal fun Receipt.toUi(): MyPlanReceiptUi = MyPlanReceiptUi(
    id = asaasEventId,
    dateLabel = (confirmedAt ?: processedAt).let(::isoDateToPtBr),
    valueLabel = valueCents?.toBrlString() ?: "—",
)
