package br.com.saqz.subscriptions.presentation.planselection

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle

@Immutable
data class PlanUi(
    val id: Plan,
    val name: String,
    val monthlyPriceCents: Long,
    val annualPriceCents: Long,
    val isFree: Boolean,
    val isHighlighted: Boolean,
    val badge: UiText?,
    val features: List<UiText>,
)

fun PlanUi.priceCentsFor(cycle: SubscriptionCycle): Long =
    if (cycle == SubscriptionCycle.Monthly) monthlyPriceCents else annualPriceCents

sealed interface CouponUiState {
    data object Idle : CouponUiState

    data class Applied(
        val code: String,
        val discountPercent: Int,
        val listPriceCents: Long,
        val finalPriceCents: Long,
    ) : CouponUiState

    data object NotFound : CouponUiState

    data class Expired(val code: String) : CouponUiState

    data class Error(val message: UiText) : CouponUiState
}

@Immutable
data class PlanSelectionState(
    val cycle: SubscriptionCycle = SubscriptionCycle.Monthly,
    val plans: List<PlanUi> = emptyList(),
    val selectedPlanId: Plan? = null,
    val couponCode: String = "",
    val coupon: CouponUiState = CouponUiState.Idle,
    val isLoading: Boolean = true,
    val isValidatingCoupon: Boolean = false,
    val loadError: UiText? = null,
) {
    val selectedPlan: PlanUi? = plans.find { it.id == selectedPlanId }

    /** Reage ao plano, ao ciclo e ao cupom aplicado — nunca lida com um dos três sozinho. */
    val totalCents: Long? = (coupon as? CouponUiState.Applied)?.finalPriceCents
        ?: selectedPlan?.let { it.priceCentsFor(cycle) }
}

sealed interface PlanSelectionIntent {
    data class SelectCycle(val cycle: SubscriptionCycle) : PlanSelectionIntent

    data class SelectPlan(val planId: Plan) : PlanSelectionIntent

    data class UpdateCouponCode(val value: String) : PlanSelectionIntent

    data object ApplyCoupon : PlanSelectionIntent

    data object RemoveCoupon : PlanSelectionIntent

    data object Confirm : PlanSelectionIntent

    data object Retry : PlanSelectionIntent
}

/** O único efeito da 8a/8b: quem escuta é o `SaqzNavHost` (VUL-113), fora do escopo daqui. */
sealed interface PlanSelectionEffect {
    data class NavigateToPayment(
        val planId: Plan,
        val cycle: SubscriptionCycle,
        val couponCode: String?,
    ) : PlanSelectionEffect
}
