package br.com.saqz.subscriptions.presentation.googleplay

import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.googleplay.GooglePlaySubmissionError
import br.com.saqz.subscriptions.domain.googleplay.GooglePlaySubscriptionGateway
import br.com.saqz.subscriptions.domain.port.GooglePlayProductsResult
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchase
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchaseResult
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesPort
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesResult
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.presentation.store.StoreDelivery
import br.com.saqz.subscriptions.presentation.store.StorePurchaseSync
import br.com.saqz.subscriptions.presentation.store.StoreRestoreOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * Leva as compras do Play ao backend, que relê a assinatura no Google e a reconhece
 * (`docs/subscriptions/google-play.md`). O app nunca reconhece nem consome: compra que o
 * backend não aceitou continua não reconhecida e é reenviada no próximo login — o Google
 * estorna o que ficar 3 dias sem reconhecimento.
 *
 * Uma instância por processo; a compra da tela, a restauração e o que chega em segundo plano
 * passam pelo mesmo [deliver], serializado.
 */
class GooglePlayPurchaseSync(
    private val port: GooglePlayPurchasesPort,
    private val gateway: GooglePlaySubscriptionGateway,
    private val scope: CoroutineScope,
) : StorePurchaseSync {
    private val mutex = Mutex()
    private val deliveredSubscriptions = MutableSharedFlow<MySubscription>(extraBufferCapacity = DELIVERY_BUFFER)
    private var listening = false
    private var authenticated = false

    override val deliveries: SharedFlow<MySubscription> = deliveredSubscriptions.asSharedFlow()

    override fun start() {
        if (listening) return
        listening = true
        port.listenForGooglePlayPurchases { purchase ->
            // Deslogado não há conta para prender a compra: ela fica sem reconhecimento e sai
            // em `queryPurchasesAsync` no próximo login.
            if (authenticated && !purchase.pending) scope.launch { deliver(purchase) }
        }
    }

    override fun onAuthenticated() {
        if (authenticated) return
        authenticated = true
        scope.launch { drainUnacknowledged() }
    }

    override fun onSignedOut() {
        authenticated = false
    }

    suspend fun deliver(purchase: GooglePlayPurchase): StoreDelivery = mutex.withLock {
        when (val result = gateway.submitPurchase(purchase.productId, purchase.purchaseToken)) {
            is SaqzResult.Success -> {
                deliveredSubscriptions.tryEmit(result.value)
                StoreDelivery.Delivered(result.value)
            }
            is SaqzResult.Failure -> when (val error = result.error) {
                GooglePlaySubmissionError.OwnedByAnotherAccount -> StoreDelivery.OwnedByAnotherAccount
                GooglePlaySubmissionError.Invalid -> StoreDelivery.Rejected
                is GooglePlaySubmissionError.Data -> StoreDelivery.Deferred(error.error)
            }
        }
    }

    /** Reentrega as compras concluídas que o backend ainda não reconheceu. */
    suspend fun drainUnacknowledged(): List<StoreDelivery> = when (val result = port.subscriptionPurchases()) {
        is GooglePlayPurchasesResult.Loaded -> result.purchases.filter { !it.acknowledged && !it.pending }.map { deliver(it) }
        is GooglePlayPurchasesResult.Failed -> emptyList()
    }

    /** Restaurar: toda assinatura ativa da conta Google vai ao backend, reconhecida ou não. */
    suspend fun restore(): StoreRestoreOutcome = when (val result = port.subscriptionPurchases()) {
        is GooglePlayPurchasesResult.Loaded -> StoreRestoreOutcome.Restored(result.purchases.filter { !it.pending }.map { deliver(it) })
        is GooglePlayPurchasesResult.Failed -> StoreRestoreOutcome.Failed
    }

    private companion object {
        const val DELIVERY_BUFFER = 16
    }
}

internal suspend fun GooglePlayPurchasesPort.products(ids: List<String>): GooglePlayProductsResult =
    suspendCancellableCoroutine { continuation ->
        loadGooglePlayProducts(ids) { if (continuation.isActive) continuation.resume(it) }
    }

internal suspend fun GooglePlayPurchasesPort.purchase(
    productId: String,
    offerToken: String,
    obfuscatedAccountId: String,
): GooglePlayPurchaseResult = suspendCancellableCoroutine { continuation ->
    purchaseGooglePlayProduct(productId, offerToken, obfuscatedAccountId) {
        if (continuation.isActive) continuation.resume(it)
    }
}

private suspend fun GooglePlayPurchasesPort.subscriptionPurchases(): GooglePlayPurchasesResult =
    suspendCancellableCoroutine { continuation ->
        readGooglePlaySubscriptionPurchases { if (continuation.isActive) continuation.resume(it) }
    }
