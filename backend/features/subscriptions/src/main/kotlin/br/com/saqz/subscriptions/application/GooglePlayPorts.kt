package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.GooglePlayPurchase
import br.com.saqz.subscriptions.domain.GooglePlaySubscription
import java.time.Instant
import java.util.UUID

sealed interface GooglePlayLookup {
    data class Found(val purchase: GooglePlayPurchase) : GooglePlayLookup

    /** Token inexistente, expirado há muito tempo ou de outro app. */
    data object NotFound : GooglePlayLookup
}

/**
 * A API do Google não respondeu agora (rede, 5xx, credencial sem permissão). Quem chamou deve
 * falhar de forma que o app e o Pub/Sub tentem de novo, nunca recusar a compra.
 */
class GooglePlayUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Google Play Developer API (androidpublisher v3) para o pacote do app. */
interface GooglePlayPurchasesApi {
    /** `purchases.subscriptionsv2.get`. Lança [GooglePlayUnavailableException]. */
    fun subscription(purchaseToken: String): GooglePlayLookup

    /** `purchases.subscriptions.acknowledge`. Sem isso o Google estorna a compra em 3 dias. */
    fun acknowledge(productId: String, purchaseToken: String)
}

data class GooglePlayOrderRecord(
    val orderId: String,
    val productId: String,
    val recordedAt: Instant,
)

interface GooglePlaySubscriptionRepository {
    fun insertIfAbsent(subscription: GooglePlaySubscription)

    fun findForUpdate(purchaseToken: String): GooglePlaySubscription?

    fun save(subscription: GooglePlaySubscription)

    fun findByOwner(ownerUserId: UUID): List<GooglePlaySubscription>

    fun ownerExists(ownerUserId: UUID): Boolean

    /** A assinatura deste token foi substituída (upgrade ou recompra com `linkedPurchaseToken`). */
    fun supersede(purchaseToken: String, at: Instant)

    fun markAcknowledged(purchaseToken: String)

    /** Grava o pedido atual da assinatura uma vez por order id. */
    fun recordOrder(subscription: GooglePlaySubscription)

    fun listOrdersForOwner(ownerUserId: UUID, limit: Int): List<GooglePlayOrderRecord>
}

fun interface GooglePlayNotificationStore {
    /** Registro de auditoria. @return true quando esta chamada gravou a mensagem (primeira entrega). */
    fun recordIfNew(messageId: String, notificationType: Int?, purchaseToken: String?): Boolean
}
