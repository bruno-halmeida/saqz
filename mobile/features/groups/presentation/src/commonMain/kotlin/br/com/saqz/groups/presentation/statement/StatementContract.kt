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
)

@Immutable
data class StatementSummaryUi(
    val totalInCents: Long = 0,
    val totalOutCents: Long = 0,
    val periodBalanceCents: Long = 0,
    val accumulatedBalanceCents: Long = 0,
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
    data class Edit(val itemId: String) : StatementIntent
    data class AskVoid(val itemId: String) : StatementIntent
    data object DismissVoid : StatementIntent
    data object ConfirmVoid : StatementIntent
}

sealed interface StatementEffect {
    data class OpenNewEntry(val groupId: String) : StatementEffect
    data class OpenEditEntry(val groupId: String, val expenseId: String) : StatementEffect

    /** Lançamento anulado: o caixa e o resumo dos outros ecrãs mudaram. */
    data object Voided : StatementEffect
}
