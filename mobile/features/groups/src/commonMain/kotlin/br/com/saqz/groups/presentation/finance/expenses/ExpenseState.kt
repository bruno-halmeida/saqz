package br.com.saqz.groups.presentation.finance.expenses

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.finance.ExpenseDto
import br.com.saqz.groups.data.finance.FinanceTotalsDto

@Immutable
data class ExpenseState(
    val groupId: String,
    val role: GroupRoleDto,
    val expenses: List<ExpenseDto> = emptyList(),
    val totals: FinanceTotalsDto? = null,
    val draft: ExpenseDraft? = null,
    val pendingVoid: ExpenseDto? = null,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val isLoading: Boolean = true,
    val isMutating: Boolean = false,
    val error: ExpenseError? = null,
    val reloadAvailable: Boolean = false,
    val retryAvailable: Boolean = false,
    val lastAuditOutcome: String? = null,
) {
    val organizer: Boolean
        get() = role == GroupRoleDto.OWNER || role == GroupRoleDto.ADMIN

    val routeAvailable: Boolean
        get() = organizer
}
