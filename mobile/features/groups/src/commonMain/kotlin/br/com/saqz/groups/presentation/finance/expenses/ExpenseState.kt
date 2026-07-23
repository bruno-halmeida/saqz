package br.com.saqz.groups.presentation.finance.expenses

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.finance.Expense
import br.com.saqz.groups.domain.finance.FinanceTotals
import br.com.saqz.groups.domain.group.GroupRole

@Immutable
data class ExpenseState(
    val groupId: String,
    val role: GroupRole,
    val expenses: List<Expense> = emptyList(),
    val totals: FinanceTotals? = null,
    val draft: ExpenseDraft? = null,
    val pendingVoid: Expense? = null,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val isLoading: Boolean = true,
    val isMutating: Boolean = false,
    val error: ExpenseError? = null,
    val reloadAvailable: Boolean = false,
    val retryAvailable: Boolean = false,
    val lastAuditOutcome: String? = null,
) {
    val organizer: Boolean
        get() = role == GroupRole.OWNER || role == GroupRole.ADMIN

    val routeAvailable: Boolean
        get() = organizer
}
