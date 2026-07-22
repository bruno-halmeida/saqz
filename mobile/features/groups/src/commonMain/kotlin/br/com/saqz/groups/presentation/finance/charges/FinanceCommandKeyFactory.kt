package br.com.saqz.groups.presentation.finance.charges

fun interface FinanceCommandKeyFactory {
    fun create(): String
}
