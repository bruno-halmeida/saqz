package br.com.saqz.groups.presentation.finance.expenses

sealed interface ExpenseEffect {
    data class Saved(val expenseId: String) : ExpenseEffect

    data class Voided(val expenseId: String) : ExpenseEffect
}
