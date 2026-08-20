package br.com.saqz.subscriptions.presentation.changeplan

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle

enum class ChangePlanPhase { Catalog, Pix, Scheduled, Upgraded }

@Immutable
data class ChangePlanState(
    val isLoading: Boolean = true,
    val loadError: UiText? = null,
    val cycle: SubscriptionCycle = SubscriptionCycle.Monthly,
    val currentPlan: Plan? = null,
    val pendingNote: UiText? = null,
    val plans: List<ChangePlanCardUi> = emptyList(),
    val confirmTarget: ChangePlanCardUi? = null,
    val isSubmitting: Boolean = false,
    val submitError: UiText? = null,
    val phase: ChangePlanPhase = ChangePlanPhase.Catalog,
    val pix: ChangePlanPixUi? = null,
    val scheduled: ChangePlanScheduledUi? = null,
)

@Immutable
data class ChangePlanCardUi(
    val plan: Plan,
    val name: String,
    val priceLabel: UiText,
    val benefits: List<UiText>,
    val isCurrent: Boolean,
)

@Immutable
data class ChangePlanPixUi(
    val targetPlan: Plan,
    val summary: UiText,
    val copyPaste: String,
    val invoiceUrl: String?,
)

@Immutable
data class ChangePlanScheduledUi(
    val title: UiText,
    val subtitle: UiText,
)

sealed interface ChangePlanIntent {
    data object Retry : ChangePlanIntent
    data class SelectPlan(val plan: Plan) : ChangePlanIntent
    data object DismissConfirm : ChangePlanIntent
    data object ConfirmChange : ChangePlanIntent
    data object PixPaid : ChangePlanIntent
    data object BackToCatalog : ChangePlanIntent
}
