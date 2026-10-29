package br.com.saqz.groups.presentation.statement

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.presentation.GroupUiError

enum class StatementFilter {
    All,
    In,
    Out,
}

@Immutable
data class StatementItemUi(
    val id: String,
    val direction: FinanceDirection,
    val title: String,
    val meta: String,
    val amountLabel: String,
    /** Lançamento feito à mão (Entrada ou Saída): é o que se corrige e se anula. Cobrança não. */
    val manual: Boolean = false,
    /** O dia do lançamento ("2026-10-27"), que agrupa a lista sob "Terça, 27/10". */
    val dayKey: String = "",
)

@Immutable
data class StatementSummaryUi(
    val totalInCents: Long = 0,
    val totalOutCents: Long = 0,
    val periodBalanceCents: Long = 0,
    val accumulatedBalanceCents: Long = 0,
    /** "outubro": o mês do resumo ("Outubro · entrou R$ 880 · saiu R$ 640"). */
    val monthName: String = "",
)

@Immutable
data class StatementState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val loadFailed: Boolean = false,
    val paginationFailed: GroupUiError? = null,
    val error: GroupUiError? = null,
    val filter: StatementFilter = StatementFilter.All,
    val items: List<StatementItemUi> = emptyList(),
    val summary: StatementSummaryUi = StatementSummaryUi(),
    val hasMore: Boolean = false,
    val nextOffset: Int = 0,
    val voidTargetId: String? = null,
    val isVoiding: Boolean = false,
    val actionError: StatementActionError? = null,
    /** Saldo inicial fixo no topo (FIN-006); `openingLoaded = false` esconde o cartão. */
    val openingLoaded: Boolean = false,
    val openingBalanceCents: Long? = null,
    val openingVersion: String? = null,
    val openingSheetOpen: Boolean = false,
    val openingInput: String = "",
    val openingSaving: Boolean = false,
    val openingInvalid: Boolean = false,
    val openingFailed: Boolean = false,
    val openingConflict: Boolean = false,
)

enum class StatementActionError {
    Conflict,
    Forbidden,
    Failed,
}

sealed interface StatementIntent {
    data object Retry : StatementIntent

    /** Primeira abertura e volta à tela: carrega; já carregada, recarrega por baixo. */
    data object Refresh : StatementIntent
    data class SelectFilter(val filter: StatementFilter) : StatementIntent
    data object LoadMore : StatementIntent
    data object NewEntry : StatementIntent
    data object NewExpense : StatementIntent
    data object EditOpening : StatementIntent
    data object DismissOpening : StatementIntent
    data class OpeningInputChanged(val value: String) : StatementIntent
    data object SaveOpening : StatementIntent
    data class Edit(val itemId: String) : StatementIntent
    data class AskVoid(val itemId: String) : StatementIntent
    data object DismissVoid : StatementIntent
    data object ConfirmVoid : StatementIntent
}

sealed interface StatementEffect {
    data class OpenNewEntry(val groupId: String, val out: Boolean = false) : StatementEffect
    data class OpenEditEntry(val groupId: String, val expenseId: String) : StatementEffect

    /** Lançamento anulado ou saldo inicial mudado: o caixa e o resumo dos outros ecrãs mudaram. */
    data object Voided : StatementEffect
}
