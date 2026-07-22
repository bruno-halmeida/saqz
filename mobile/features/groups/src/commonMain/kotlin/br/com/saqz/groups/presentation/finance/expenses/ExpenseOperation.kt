package br.com.saqz.groups.presentation.finance.expenses

internal sealed interface ExpenseOperation {
    data class Save(val draft: ExpenseDraft) : ExpenseOperation
    data class Void(val expenseId: String, val etag: String) : ExpenseOperation
}
