package br.com.saqz.groups.presentation.finance.overview

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.finance.FinanceOverviewQuery

enum class FinanceOverviewPeriodSelection {
    CurrentMonth,
    PreviousMonth,
    Year,
}

@Immutable
data class FinanceOverviewPeriodOption(
    val selection: FinanceOverviewPeriodSelection,
    val month: Int?,
    val year: Int,
    val query: FinanceOverviewQuery,
)

@Immutable
data class FinanceOverviewGroupUi(
    val id: String,
    val name: String,
    val balance: String,
    val pendingMonthlyCount: Int,
    val hasBillingConfigured: Boolean,
)

@Immutable
data class FinanceOverviewTransactionUi(
    val id: String,
    val title: String?,
    val groupAndDate: String,
    val amount: String,
    val isIncoming: Boolean?,
)

@Immutable
data class FinanceOverviewState(
    val isLoading: Boolean = true,
    /** Puxar para atualizar em andamento (GER-002); o caixa continua na tela. */
    val isRefreshing: Boolean = false,
    /** O último puxar falhou: o caixa fica e o aviso aparece. */
    val refreshFailed: Boolean = false,
    val loadFailed: Boolean = false,
    val selectedPeriod: FinanceOverviewPeriodSelection = FinanceOverviewPeriodSelection.CurrentMonth,
    val periods: List<FinanceOverviewPeriodOption> = emptyList(),
    val balance: String = "",
    val entered: String = "",
    val left: String = "",
    val receivable: String = "",
    val groups: List<FinanceOverviewGroupUi> = emptyList(),
    val recentTransactions: List<FinanceOverviewTransactionUi> = emptyList(),
) {
    val isEmpty: Boolean
        get() = !isLoading && !loadFailed && groups.isEmpty()
}

sealed interface FinanceOverviewIntent {
    data class SelectPeriod(val selection: FinanceOverviewPeriodSelection) : FinanceOverviewIntent
    data class OpenGroup(val groupId: String) : FinanceOverviewIntent
    data object TabActive : FinanceOverviewIntent

    /** Puxar para atualizar (GER-002): recarga por baixo com a bola na tela e aviso se falhar. */
    data object PullRefresh : FinanceOverviewIntent

    data object DismissRefreshFailed : FinanceOverviewIntent
    data object Retry : FinanceOverviewIntent
}

sealed interface FinanceOverviewEffect {
    data class OpenGroup(val groupId: String) : FinanceOverviewEffect
}
