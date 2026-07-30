package br.com.saqz.subscriptions.presentation.planselection

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.CouponValidation
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.PlanDetails
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.plan_selection_badge_free
import br.com.saqz.subscriptions.resources.plan_selection_badge_highlighted
import br.com.saqz.subscriptions.resources.plan_selection_coupon_generic_error
import br.com.saqz.subscriptions.resources.plan_selection_feature_athletes_limited
import br.com.saqz.subscriptions.resources.plan_selection_feature_athletes_unlimited
import br.com.saqz.subscriptions.resources.plan_selection_feature_groups_limited
import br.com.saqz.subscriptions.resources.plan_selection_feature_groups_unlimited
import br.com.saqz.subscriptions.resources.plan_selection_feature_multi_admin
import br.com.saqz.subscriptions.resources.plan_selection_feature_reports
import br.com.saqz.subscriptions.resources.plan_selection_feature_whatsapp_sla
import br.com.saqz.subscriptions.resources.plan_selection_load_error
import kotlinx.coroutines.launch

/**
 * 8a/8b — uma tela só, o mock trata cupom como estado da mesma jornada. `init` carrega
 * os planos; nada mais nesta tela dispara rede sem um toque (cupom só valida no
 * `ApplyCoupon`, nunca enquanto a pessoa digita).
 */
class PlanSelectionViewModel(
    private val gateway: SubscriptionGateway,
) : MviViewModel<PlanSelectionState, PlanSelectionIntent, PlanSelectionEffect>(PlanSelectionState()) {

    /**
     * Contador monotônico, não os campos do estado: uma sequência ABA (valida X pro plano
     * P, troca de plano, volta pro P, digita X de novo) faz plano/ciclo/código baterem de
     * novo com a pergunta antiga, e uma guarda por igualdade de valor aceitaria a resposta
     * velha como se fosse a nova. Só um número que nunca repete distingue "é a mesma
     * pergunta em voo" de "é uma pergunta igual, mas mais nova". Toda mutação que
     * invalidaria uma validação em voo incrementa — inclusive as que só limpam o estado,
     * sem disparar rede.
     */
    private var couponValidationGeneration = 0

    init { loadPlans() }

    override fun onIntent(intent: PlanSelectionIntent) {
        when (intent) {
            is PlanSelectionIntent.SelectCycle -> selectCycle(intent.cycle)
            is PlanSelectionIntent.SelectPlan -> selectPlan(intent.planId)
            is PlanSelectionIntent.UpdateCouponCode -> {
                couponValidationGeneration++
                update { it.copy(couponCode = intent.value, coupon = CouponUiState.Idle, isValidatingCoupon = false) }
            }

            PlanSelectionIntent.ApplyCoupon -> applyCoupon()
            PlanSelectionIntent.RemoveCoupon -> {
                couponValidationGeneration++
                update { it.copy(couponCode = "", coupon = CouponUiState.Idle, isValidatingCoupon = false) }
            }

            PlanSelectionIntent.Confirm -> confirm()
            PlanSelectionIntent.Retry -> loadPlans()
        }
    }

    private fun loadPlans() {
        update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = gateway.plans()) {
                is SaqzResult.Success -> update { current ->
                    val plans = result.value.map { it.toPlanUi() }
                    current.copy(
                        isLoading = false,
                        plans = plans,
                        selectedPlanId = current.selectedPlanId
                            ?: plans.find { it.isHighlighted }?.id
                            ?: plans.firstOrNull()?.id,
                    )
                }

                is SaqzResult.Failure -> update {
                    it.copy(isLoading = false, loadError = UiText.Res(Res.string.plan_selection_load_error))
                }
            }
        }
    }

    private fun selectCycle(cycle: SubscriptionCycle) {
        val current = state.value
        if (cycle == current.cycle) return
        // Cupom validado é por plano+ciclo (contrato do `SubscriptionGateway`); trocar o
        // ciclo invalida a validação anterior em vez de mostrar um desconto que não vale
        // mais para o preço novo.
        couponValidationGeneration++
        update { it.copy(cycle = cycle, couponCode = "", coupon = CouponUiState.Idle, isValidatingCoupon = false) }
    }

    private fun selectPlan(planId: Plan) {
        val current = state.value
        if (planId == current.selectedPlanId) return
        couponValidationGeneration++
        update {
            it.copy(selectedPlanId = planId, couponCode = "", coupon = CouponUiState.Idle, isValidatingCoupon = false)
        }
    }

    private fun applyCoupon() {
        val current = state.value
        val planId = current.selectedPlanId ?: return
        val code = current.couponCode.trim()
        if (code.isEmpty() || current.isValidatingCoupon) return
        val cycle = current.cycle
        val generation = ++couponValidationGeneration
        update { it.copy(isValidatingCoupon = true) }
        viewModelScope.launch {
            val result = gateway.validateCoupon(code, planId, cycle)
            // Só aceita se ninguém disparou uma validação mais nova enquanto esta estava
            // no ar — nem outra chamada, nem uma mudança que só limpou o estado.
            if (generation != couponValidationGeneration) return@launch
            update {
                it.copy(
                    isValidatingCoupon = false,
                    coupon = when (result) {
                        is SaqzResult.Success -> result.value.toCouponUiState(code)
                        is SaqzResult.Failure ->
                            CouponUiState.Error(UiText.Res(Res.string.plan_selection_coupon_generic_error))
                    },
                )
            }
        }
    }

    private fun confirm() {
        val current = state.value
        val planId = current.selectedPlanId ?: return
        val appliedCode = (current.coupon as? CouponUiState.Applied)?.code
        emit(PlanSelectionEffect.NavigateToPayment(planId, current.cycle, appliedCode))
    }
}

private fun CouponValidation.toCouponUiState(typedCode: String): CouponUiState = when (this) {
    is CouponValidation.Applied -> CouponUiState.Applied(
        code = code,
        discountPercent = discountPercent,
        listPriceCents = listPriceCents,
        finalPriceCents = finalPriceCents,
    )
    // `Expired` não carrega o código no domínio; o que a pessoa digitou é o que venceu.
    CouponValidation.Expired -> CouponUiState.Expired(code = typedCode)
    CouponValidation.NotFound -> CouponUiState.NotFound
}

private fun PlanDetails.toPlanUi(): PlanUi {
    val isFree = monthlyPriceCents == 0L && annualPriceCents == 0L
    return PlanUi(
        id = id,
        name = name,
        monthlyPriceCents = monthlyPriceCents,
        annualPriceCents = annualPriceCents,
        isFree = isFree,
        isHighlighted = id == Plan.Organizador,
        badge = when {
            isFree -> UiText.Res(Res.string.plan_selection_badge_free)
            id == Plan.Organizador -> UiText.Res(Res.string.plan_selection_badge_highlighted)
            else -> null
        },
        features = buildList {
            add(
                maxGroups?.let { UiText.Res(Res.string.plan_selection_feature_groups_limited, listOf(it)) }
                    ?: UiText.Res(Res.string.plan_selection_feature_groups_unlimited),
            )
            add(
                maxAthletes?.let { UiText.Res(Res.string.plan_selection_feature_athletes_limited, listOf(it)) }
                    ?: UiText.Res(Res.string.plan_selection_feature_athletes_unlimited),
            )
            if (multiAdmin) add(UiText.Res(Res.string.plan_selection_feature_multi_admin))
            if (reports) add(UiText.Res(Res.string.plan_selection_feature_reports))
            if (whatsappSla) add(UiText.Res(Res.string.plan_selection_feature_whatsapp_sla))
        },
    )
}
