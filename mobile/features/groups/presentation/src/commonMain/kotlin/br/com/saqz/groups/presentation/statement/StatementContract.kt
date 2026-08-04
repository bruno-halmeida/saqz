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
    val error: GroupUiError? = null,
    val filter: StatementFilter = StatementFilter.All,
    val items: List<StatementItemUi> = emptyList(),
    val summary: StatementSummaryUi = StatementSummaryUi(),
    val hasMore: Boolean = false,
    val nextOffset: Int = 0,
)

sealed interface StatementIntent {
    data object Retry : StatementIntent
    data class SelectFilter(val filter: StatementFilter) : StatementIntent
    data object LoadMore : StatementIntent
    data object NewEntry : StatementIntent
}

sealed interface StatementEffect {
    data class OpenNewEntry(val groupId: String) : StatementEffect
}
