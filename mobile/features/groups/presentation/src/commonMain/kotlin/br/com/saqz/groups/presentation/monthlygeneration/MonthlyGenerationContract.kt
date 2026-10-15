package br.com.saqz.groups.presentation.monthlygeneration

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError
import kotlinx.serialization.Serializable

@Immutable
data class MonthlyMemberUi(val id: String, val name: String)

@Serializable
data class MonthlyForm(
    val month: String = "",
    val amount: String = "",
    val dueDate: String = "",
    val selectedIds: Set<String> = emptySet(),
)

@Immutable
data class MonthlyGenerationState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val members: List<MonthlyMemberUi> = emptyList(),
    val form: MonthlyForm = MonthlyForm(),
    /** Primeiro e último mês (AAAA-MM) que o seletor oferece. */
    val earliestMonth: String = "",
    val latestMonth: String = "",
    val reviewing: Boolean = false,
    val isSaving: Boolean = false,
    val error: GroupUiError? = null,
) {
    val canGoToPreviousMonth: Boolean get() = form.month > earliestMonth
    val canGoToNextMonth: Boolean get() = form.month < latestMonth
}

sealed interface MonthlyGenerationIntent {
    data object Retry : MonthlyGenerationIntent
    data object PreviousMonth : MonthlyGenerationIntent
    data object NextMonth : MonthlyGenerationIntent
    data class AmountChanged(val value: String) : MonthlyGenerationIntent
    data class DueDateChanged(val value: String) : MonthlyGenerationIntent
    data class ToggleMember(val id: String) : MonthlyGenerationIntent
    data object Review : MonthlyGenerationIntent
    data object Edit : MonthlyGenerationIntent
    data object Confirm : MonthlyGenerationIntent
}

sealed interface MonthlyGenerationEffect {
    data object Generated : MonthlyGenerationEffect
}
