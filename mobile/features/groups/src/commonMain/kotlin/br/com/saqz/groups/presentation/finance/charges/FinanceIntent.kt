package br.com.saqz.groups.presentation.finance.charges

import br.com.saqz.groups.data.finance.ChargeStatusDto

sealed interface FinanceIntent {
    data object Refresh : FinanceIntent

    data class UpdateMonthly(
        val month: String,
        val amountBrl: String,
        val dueDate: String,
        val memberIds: Set<String>,
    ) : FinanceIntent

    data object ReviewMonthly : FinanceIntent

    data object GenerateMonthly : FinanceIntent

    data class UpdateStatus(
        val chargeId: String,
        val status: ChargeStatusDto,
        val note: String? = null,
    ) : FinanceIntent

    data object Retry : FinanceIntent
}
