package br.com.saqz.subscriptions.presentation.planactive

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.UiText

/** 8d — confirmação após a criação da assinatura (VUL-111). */
@Immutable
data class PlanActiveState(
    val isLoading: Boolean = true,
    val planName: String = "",
    val priceLabel: String = "",
    val nextBillingLabel: String = "",
    val groupsAvailableLabel: String = "",
    val error: UiText? = null,
)

sealed interface PlanActiveIntent {
    data object Retry : PlanActiveIntent

    data object CreateGroup : PlanActiveIntent

    data object ViewMyPlan : PlanActiveIntent
}

sealed interface PlanActiveEffect {
    /** Segue para o cadastro de grupo existente (2a). Navegação real fica pro VUL-113. */
    data object NavigateToCreateGroup : PlanActiveEffect

    /** Segue para 8e (`SubscriptionsRoute.MyPlan`, VUL-112). Navegação real fica pro VUL-113. */
    data object NavigateToMyPlan : PlanActiveEffect
}
