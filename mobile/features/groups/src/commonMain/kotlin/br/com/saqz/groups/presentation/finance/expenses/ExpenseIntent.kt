package br.com.saqz.groups.presentation.finance.expenses

sealed interface ExpenseIntent {
    data object Refresh : ExpenseIntent

    data object OpenCreate : ExpenseIntent

    data class OpenEdit(val expenseId: String) : ExpenseIntent

    data class UpdateForm(val form: ExpenseForm) : ExpenseIntent

    data object Submit : ExpenseIntent

    data class RequestVoid(val expenseId: String) : ExpenseIntent

    data object DismissVoid : ExpenseIntent

    data object ConfirmVoid : ExpenseIntent

    data object Retry : ExpenseIntent
}
