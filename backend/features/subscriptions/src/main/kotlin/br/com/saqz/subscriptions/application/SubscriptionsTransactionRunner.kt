package br.com.saqz.subscriptions.application

interface SubscriptionsTransactionRunner {
    fun <T> inTransaction(block: () -> T): T
}
