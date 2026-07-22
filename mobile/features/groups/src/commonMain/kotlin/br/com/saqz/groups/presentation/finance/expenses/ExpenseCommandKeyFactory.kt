package br.com.saqz.groups.presentation.finance.expenses

fun interface ExpenseCommandKeyFactory {
    fun create(): String
}
