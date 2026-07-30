package br.com.saqz.subscriptions.presentation.planactive

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.onFailure
import br.com.saqz.domain.onSuccess
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.plan_active_error
import br.com.saqz.subscriptions.resources.plan_active_groups_unlimited
import br.com.saqz.subscriptions.resources.plan_active_value_month
import br.com.saqz.subscriptions.resources.plan_active_value_year
import kotlinx.coroutines.launch

private val MonthsPtBr = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
)

/**
 * 8d — busca o estado real em vez de recebê-lo pela rota: `PlanActive` é `data object`
 * sem parâmetros, então a tela sempre reflete o que o backend confirmou.
 *
 * **`MySubscription` não carrega preço** (achado técnico, VUL-111): é `gateway.plans()`
 * quem sabe `monthlyPriceCents`/`annualPriceCents` por [br.com.saqz.subscriptions.domain.subscription.Plan].
 * Por isso o `load()` busca os dois e cruza pelo `plan`/`cycle` da assinatura.
 */
class PlanActiveViewModel(
    private val gateway: SubscriptionGateway,
) : MviViewModel<PlanActiveState, PlanActiveIntent, PlanActiveEffect>(PlanActiveState()) {

    init {
        load()
    }

    override fun onIntent(intent: PlanActiveIntent) {
        when (intent) {
            PlanActiveIntent.Retry -> load()
            PlanActiveIntent.CreateGroup -> emit(PlanActiveEffect.NavigateToCreateGroup)
            PlanActiveIntent.ViewMyPlan -> emit(PlanActiveEffect.NavigateToMyPlan)
        }
    }

    private fun load() {
        update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            gateway.mySubscription()
                .onSuccess { subscription -> applySubscription(subscription) }
                .onFailure {
                    update { state -> state.copy(isLoading = false, error = UiText.Res(Res.string.plan_active_error)) }
                }
        }
    }

    private suspend fun applySubscription(subscription: MySubscription) {
        val plansResult = gateway.plans()
        val priceCents = if (plansResult is SaqzResult.Success) {
            plansResult.value.firstOrNull { it.id == subscription.plan }
                ?.let { details ->
                    if (subscription.cycle == SubscriptionCycle.Monthly) details.monthlyPriceCents else details.annualPriceCents
                }
        } else {
            null
        }
        val priceRes = if (subscription.cycle == SubscriptionCycle.Monthly) {
            Res.string.plan_active_value_month
        } else {
            Res.string.plan_active_value_year
        }
        update {
            it.copy(
                isLoading = false,
                planName = subscription.plan.name,
                priceLabel = priceCents?.let { cents -> UiText.Res(priceRes, listOf(formatBrl(cents))) } ?: UiText.Raw(""),
                nextBillingLabel = formatNextBillingDate(subscription.currentPeriodEnd),
                groupsAvailableLabel = groupsAvailableLabel(subscription.usage),
            )
        }
    }
}

// ponytail: split de string, não kotlinx-datetime — `currentPeriodEnd` chega como
// "yyyy-MM-ddTHH:mm:ssZ" e só o dia interessa; nenhuma tela do fluxo 8 fez parsing de
// verdade ainda pra justificar puxar a lib pro módulo (ela nem está nas deps daqui).
private fun formatNextBillingDate(iso: String): String {
    val (_, month, day) = iso.substringBefore('T').split('-')
    return "${day.toInt()} de ${MonthsPtBr[month.toInt() - 1]}"
}

private fun groupsAvailableLabel(usage: SubscriptionUsage): UiText {
    val limit = usage.groupsLimit ?: return UiText.Res(Res.string.plan_active_groups_unlimited)
    return UiText.Raw((limit - usage.groupsUsed).coerceAtLeast(0).toString())
}
