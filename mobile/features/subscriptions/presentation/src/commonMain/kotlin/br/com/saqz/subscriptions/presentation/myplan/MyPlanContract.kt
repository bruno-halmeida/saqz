package br.com.saqz.subscriptions.presentation.myplan

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.Plan

/**
 * 8e — plano atual, uso, recibos e o menu Gerenciar. Todo texto composto (modelo + valor
 * dinâmico) chega como [UiText.Res] com `args`: quem resolve o template pt-BR é o Screen,
 * via `asString()`, porque o ViewModel não está em contexto `@Composable` (AGENTS.md §8).
 * Valor já pronto e sem template — data formatada, nome do plano — é `String` puro.
 */
@Immutable
data class MyPlanState(
    val isLoading: Boolean = true,
    val loadError: UiText? = null,
    val plan: MyPlanCardUi? = null,
    val usage: MyPlanUsageUi? = null,
    val receipts: List<MyPlanReceiptUi> = emptyList(),
    val changeOptions: List<MyPlanChangeOptionUi> = emptyList(),
    val isChangeSheetOpen: Boolean = false,
    val isChangingPlan: Boolean = false,
    val changeError: UiText? = null,
    val pendingPayment: MyPlanPendingPaymentUi? = null,
    val isReceiptsSheetOpen: Boolean = false,
    val isCancelSheetOpen: Boolean = false,
    val isCanceling: Boolean = false,
    val cancelError: UiText? = null,
)

enum class MyPlanStatusTone { Active, PastDue, Canceled }

/** O card "PLANO ATUAL" do 8e. [pendingChangeLine] só existe com downgrade agendado. */
@Immutable
data class MyPlanCardUi(
    val name: String,
    val statusLabel: UiText,
    val statusTone: MyPlanStatusTone,
    val priceLine: UiText,
    // Data e forma de pagamento vêm de fontes separadas (uma já formatada, a outra um enum
    // localizado) — o Screen as combina numa linha só, porque um único UiText.Res não
    // aninha o resultado de outro (stringResource só aceita args primitivos).
    val nextChargeDate: String?,
    val paymentMethodLabel: UiText?,
    val pendingChangeLine: UiText?,
)

/** [progress] nulo é o plano sem limite de grupos (Quadra Cheia): a barra não desenha. */
@Immutable
data class MyPlanUsageUi(
    val ratioLabel: UiText,
    val progress: Float?,
    val helperText: UiText,
)

@Immutable
data class MyPlanChangeOptionUi(
    val planId: Plan,
    val name: String,
    val priceLine: UiText,
    val isCurrent: Boolean,
)

@Immutable
data class MyPlanReceiptUi(val id: String, val dateLabel: String, val valueLabel: String)

@Immutable
data class MyPlanPendingPaymentUi(
    val message: UiText,
    val pixCopyPaste: String?,
    val invoiceUrl: String?,
)

sealed interface MyPlanIntent {
    data object Retry : MyPlanIntent
    data object OpenChangePlan : MyPlanIntent
    data object DismissChangePlan : MyPlanIntent
    data class SelectPlan(val planId: Plan) : MyPlanIntent
    data object OpenReceipts : MyPlanIntent
    data object DismissReceipts : MyPlanIntent
    data object OpenCancel : MyPlanIntent
    data object DismissCancel : MyPlanIntent
    data object ConfirmCancel : MyPlanIntent
    data object DismissPendingPayment : MyPlanIntent
}
