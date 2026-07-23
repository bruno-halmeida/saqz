package br.com.saqz.groups.presentation.finance.expenses

import br.com.saqz.groups.domain.finance.ExpenseCategory

data class ExpenseForm(
    val description: String = "",
    val amountBrl: String = "",
    val expenseDate: String = "",
    val category: ExpenseCategory? = null,
    val customCategory: String = "",
    val notes: String = "",
)
