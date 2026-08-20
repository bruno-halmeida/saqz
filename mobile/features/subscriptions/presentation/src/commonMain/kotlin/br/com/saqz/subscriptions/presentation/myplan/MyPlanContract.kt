package br.com.saqz.subscriptions.presentation.myplan

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.UiText

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
    val receiptsError: UiText? = null,
    val loadMoreReceiptsError: UiText? = null,
    // Paginação de recibos (VUL-120): sem total no contrato do backend, "tem mais" é a
    // página ter vindo cheia. Começa `false` — antes da primeira página não há o que pedir.
    val hasMoreReceipts: Boolean = false,
    val isLoadingMoreReceipts: Boolean = false,
    val isReceiptsSheetOpen: Boolean = false,
    val isCancelSheetOpen: Boolean = false,
    val isCanceling: Boolean = false,
    val cancelError: UiText? = null,
)

enum class MyPlanStatusTone { Active, PastDue, Canceled }

/** O card "PLANO ATUAL" do 8e. */
@Immutable
data class MyPlanCardUi(
    val name: String,
    val statusLabel: UiText,
    val statusTone: MyPlanStatusTone,
    val nextChargeDate: String?,
    // Só numa assinatura cancelada (achado do Codex no PR #93): `currentPeriodEnd` não é
    // cobrança futura nenhuma quando `canceledAt != null` — é até quando o acesso dura,
    // porque o cancelamento já parou a cobrança no Asaas. `nextChargeDate` fica nulo nesse
    // caso e este campo assume, com um rótulo diferente ("acesso garantido até").
    val accessUntilDate: String? = null,
)

/** [progress] nulo é o plano sem limite de grupos (Quadra Cheia): a barra não desenha. */
@Immutable
data class MyPlanUsageUi(
    val ratioLabel: UiText,
    val progress: Float?,
    val helperText: UiText,
)

@Immutable
data class MyPlanReceiptUi(val id: String, val dateLabel: String, val valueLabel: String)

sealed interface MyPlanIntent {
    data object Retry : MyPlanIntent
    data object OpenReceipts : MyPlanIntent
    data object DismissReceipts : MyPlanIntent
    data object RetryReceipts : MyPlanIntent
    data object LoadMoreReceipts : MyPlanIntent
    data object RetryLoadMore : MyPlanIntent
    data object OpenCancel : MyPlanIntent
    data object DismissCancel : MyPlanIntent
    data object ConfirmCancel : MyPlanIntent
    data object OpenChangePlan : MyPlanIntent
}

sealed interface MyPlanEffect {
    data object OpenChangePlan : MyPlanEffect
}
