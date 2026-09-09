package br.com.saqz.groups.presentation.monthlypayments

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.details.OwnChargesUi

@Immutable
data class OwnMonthlyPaymentsState(
    val loading: Boolean = true,
    val error: GroupUiError? = null,
    val groups: List<MonthlyPaymentsGroupUi> = emptyList(),
)

@Immutable
data class MonthlyPaymentsGroupUi(val id: String, val name: String, val charges: OwnChargesUi)

sealed interface OwnMonthlyPaymentsIntent {
    data object Retry : OwnMonthlyPaymentsIntent
    data class OpenGroup(val groupId: String) : OwnMonthlyPaymentsIntent
}

sealed interface OwnMonthlyPaymentsEffect {
    data class OpenGroup(val groupId: String) : OwnMonthlyPaymentsEffect
}
