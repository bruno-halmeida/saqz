package br.com.saqz.groups.presentation.finance.expenses

import br.com.saqz.groups.data.finance.ExpenseCategoryDto
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseForm(
    val description: String = "",
    val amountBrl: String = "",
    val expenseDate: String = "",
    val category: ExpenseCategoryDto? = null,
    val customCategory: String = "",
    val notes: String = "",
)
