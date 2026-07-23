package br.com.saqz.groups.presentation.finance.expenses

import br.com.saqz.groups.domain.finance.FinanceVersionToken

internal sealed interface ExpenseOperation {
    data class Save(val draft: ExpenseDraft) : ExpenseOperation
    data class Void(val expenseId: String, val version: FinanceVersionToken) : ExpenseOperation
}
