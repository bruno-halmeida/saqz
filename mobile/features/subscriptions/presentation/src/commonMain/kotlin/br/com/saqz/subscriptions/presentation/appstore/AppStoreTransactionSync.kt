package br.com.saqz.subscriptions.presentation.appstore

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubmissionError
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubscriptionGateway
import br.com.saqz.subscriptions.domain.port.AppStoreProductsResult
import br.com.saqz.subscriptions.domain.port.AppStorePurchaseResult
import br.com.saqz.subscriptions.domain.port.AppStorePurchasesPort
import br.com.saqz.subscriptions.domain.port.AppStoreSignedTransaction
import br.com.saqz.subscriptions.domain.port.AppStoreTransactionsResult
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.presentation.store.StorePurchaseSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** O que aconteceu com uma transação entregue ao backend. */
sealed interface AppStoreDelivery {
    data class Delivered(val subscription: MySubscription) : AppStoreDelivery
    data object OwnedByAnotherAccount : AppStoreDelivery
    data object Rejected : AppStoreDelivery

    /** Rede ou 5xx: sem `finish()`, o StoreKit devolve a transação e a entrega é refeita. */
    data class Deferred(val error: DataError) : AppStoreDelivery
}

sealed interface AppStoreRestoreOutcome {
    data class Restored(val deliveries: List<AppStoreDelivery>) : AppStoreRestoreOutcome
    data object Failed : AppStoreRestoreOutcome
}

/**
 * Leva as transações do StoreKit ao backend e decide o `finish()` — a política está na
 * tabela de `docs/subscriptions/app-store.md`: conclui no 200, 409 e 422; nunca em falha de
 * rede ou 5xx, para a compra voltar por `Transaction.unfinished` e ninguém pagar sem acesso.
 *
 * Uma instância por processo (Koin `single`). A compra da tela, a restauração e o que chega
 * em segundo plano passam pelo mesmo [deliver], serializado: a mesma transação pode chegar
 * por dois caminhos e o backend é idempotente, mas o `finish()` não precisa correr em paralelo.
 */
class AppStoreTransactionSync(
    private val port: AppStorePurchasesPort,
    private val gateway: AppStoreSubscriptionGateway,
    private val scope: CoroutineScope,
) : StorePurchaseSync {
    private val mutex = Mutex()
    private val deliveredSubscriptions = MutableSharedFlow<MySubscription>(extraBufferCapacity = DELIVERY_BUFFER)
    private var listening = false
    private var authenticated = false

    /** Toda assinatura que o backend devolveu, inclusive a de uma renovação em segundo plano. */
    override val deliveries: SharedFlow<MySubscription> = deliveredSubscriptions.asSharedFlow()

    override fun start() {
        if (listening) return
        listening = true
        port.listenForAppStoreTransactions { transaction ->
            // Deslogado não há conta para prender a compra: ela segue sem `finish()` e sai em
            // `Transaction.unfinished` no próximo login.
            if (authenticated) scope.launch { deliver(transaction) }
        }
    }

    override fun onAuthenticated() {
        if (authenticated) return
        authenticated = true
        scope.launch { drainUnfinished() }
    }

    override fun onSignedOut() {
        authenticated = false
    }

    suspend fun deliver(transaction: AppStoreSignedTransaction): AppStoreDelivery = mutex.withLock {
        when (val result = gateway.submitTransaction(transaction.signedTransaction)) {
            is SaqzResult.Success -> {
                port.finishAppStoreTransaction(transaction.transactionId)
                deliveredSubscriptions.tryEmit(result.value)
                AppStoreDelivery.Delivered(result.value)
            }
            is SaqzResult.Failure -> when (val error = result.error) {
                AppStoreSubmissionError.OwnedByAnotherAccount -> {
                    port.finishAppStoreTransaction(transaction.transactionId)
                    AppStoreDelivery.OwnedByAnotherAccount
                }
                AppStoreSubmissionError.Invalid -> {
                    port.finishAppStoreTransaction(transaction.transactionId)
                    AppStoreDelivery.Rejected
                }
                is AppStoreSubmissionError.Data -> AppStoreDelivery.Deferred(error.error)
            }
        }
    }

    /** Reentrega o que ficou sem `finish()`. Lista vazia quando o StoreKit não respondeu. */
    suspend fun drainUnfinished(): List<AppStoreDelivery> =
        when (val result = port.unfinishedTransactions()) {
            is AppStoreTransactionsResult.Loaded -> result.transactions.map { deliver(it) }
            is AppStoreTransactionsResult.Failed -> emptyList()
        }

    suspend fun restore(): AppStoreRestoreOutcome = when (val result = port.restoredTransactions()) {
        is AppStoreTransactionsResult.Loaded -> AppStoreRestoreOutcome.Restored(result.transactions.map { deliver(it) })
        is AppStoreTransactionsResult.Failed -> AppStoreRestoreOutcome.Failed
    }

    private companion object {
        const val DELIVERY_BUFFER = 16
    }
}

internal suspend fun AppStorePurchasesPort.products(ids: List<String>): AppStoreProductsResult =
    suspendCancellableCoroutine { continuation ->
        loadAppStoreProducts(ids) { if (continuation.isActive) continuation.resume(it) }
    }

internal suspend fun AppStorePurchasesPort.purchase(productId: String, appAccountToken: String): AppStorePurchaseResult =
    suspendCancellableCoroutine { continuation ->
        purchaseAppStoreProduct(productId, appAccountToken) { if (continuation.isActive) continuation.resume(it) }
    }

private suspend fun AppStorePurchasesPort.unfinishedTransactions(): AppStoreTransactionsResult =
    suspendCancellableCoroutine { continuation ->
        readUnfinishedAppStoreTransactions { if (continuation.isActive) continuation.resume(it) }
    }

private suspend fun AppStorePurchasesPort.restoredTransactions(): AppStoreTransactionsResult =
    suspendCancellableCoroutine { continuation ->
        restoreAppStorePurchases { if (continuation.isActive) continuation.resume(it) }
    }
