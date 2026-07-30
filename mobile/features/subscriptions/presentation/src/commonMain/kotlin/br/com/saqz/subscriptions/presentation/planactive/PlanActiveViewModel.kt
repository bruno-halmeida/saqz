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
import org.jetbrains.compose.resources.getString

private val MonthsPtBr = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
)

/**
 * 8d — busca o estado real em vez de recebê-lo pela rota: `PlanActive` é `data object`
 * sem parâmetros, então a tela sempre reflete o que o backend confirmou.
 *
 * **`MySubscription` não carrega preço** (achado técnico, VUL-111): nem o catálogo
 * (`gateway.plans()`) serve — ele ignora cupom, e o cobrado pode ser menor que o de
 * catálogo. Quem sabe o valor real é `gateway.receipts()`: o recibo mais recente
 * (`processedAt` ordena como string ISO-8601) é o que o Asaas efetivamente cobrou.
 */
class PlanActiveViewModel(
    private val gateway: SubscriptionGateway,
) : MviViewModel<PlanActiveState, PlanActiveIntent, PlanActiveEffect>(PlanActiveState()) {

    // Guarda de geração (AGENTS.md §4): um segundo "Tentar novamente" antes do primeiro
    // load responder não pode deixar a resposta mais velha sobrescrever a mais nova.
    private var loadGeneration = 0L

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
        val generation = ++loadGeneration
        update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            gateway.mySubscription()
                .onSuccess { subscription -> applySubscription(subscription, generation) }
                .onFailure { failWith(generation) }
        }
    }

    private suspend fun applySubscription(subscription: MySubscription, generation: Long) {
        val latestReceiptCents = (gateway.receipts() as? SaqzResult.Success)
            ?.value
            ?.maxByOrNull { receipt -> receipt.processedAt }
            ?.valueCents

        if (latestReceiptCents == null) {
            failWith(generation)
            return
        }

        val priceRes = if (subscription.cycle == SubscriptionCycle.Monthly) {
            Res.string.plan_active_value_month
        } else {
            Res.string.plan_active_value_year
        }
        val priceLabel = getString(priceRes, formatBrl(latestReceiptCents))
        val groupsAvailableLabel = groupsAvailableLabel(subscription.usage)

        if (generation != loadGeneration) return
        update {
            it.copy(
                isLoading = false,
                planName = subscription.plan.name,
                priceLabel = priceLabel,
                nextBillingLabel = formatNextBillingDate(subscription.currentPeriodEnd),
                groupsAvailableLabel = groupsAvailableLabel,
            )
        }
    }

    private fun failWith(generation: Long) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, error = UiText.Res(Res.string.plan_active_error)) }
    }
}

// ponytail: split de string, não kotlinx-datetime — `currentPeriodEnd` chega como
// "yyyy-MM-ddTHH:mm:ssZ" e só o dia interessa; nenhuma tela do fluxo 8 fez parsing de
// verdade ainda pra justificar puxar a lib pro módulo (ela nem está nas deps daqui).
private fun formatNextBillingDate(iso: String): String {
    val (_, month, day) = iso.substringBefore('T').split('-')
    return "${day.toInt()} de ${MonthsPtBr[month.toInt() - 1]}"
}

private suspend fun groupsAvailableLabel(usage: SubscriptionUsage): String {
    val limit = usage.groupsLimit ?: return getString(Res.string.plan_active_groups_unlimited)
    return (limit - usage.groupsUsed).coerceAtLeast(0).toString()
}
