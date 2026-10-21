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

/** Valor de um pedido lido da Orders API. Centavos da [currency]. */
data class GooglePlayOrderAmount(
    val orderId: String,
    /** `Order.total`: o que o comprador pagou, com descontos e impostos. */
    val grossCents: Long,
    /** `Order.tax`: o imposto contido no total; null quando a API não informa. */
    val taxCents: Long?,
    val currency: String,
    /** `Order.state` (PROCESSED, REFUNDED...). */
    val state: String?,
)

/**
 * A Orders API recusou o lote (4xx): basta um id desconhecido ou de outro pacote para o
 * `orders.batchGet` inteiro falhar. Quem chamou tenta os ids um a um.
 */
class GooglePlayOrdersRejectedException(message: String) : RuntimeException(message)

/** Orders API do Android Publisher. Precisa da permissão "Ver dados financeiros" no Play Console. */
fun interface GooglePlayOrdersApi {
    /**
     * `orders.batchGet` com 1 a 1000 ids distintos. Lança [GooglePlayUnavailableException] (rede, 5xx, 401/403)
     * ou [GooglePlayOrdersRejectedException] (algum id recusado).
     */
    fun orders(orderIds: List<String>): List<GooglePlayOrderAmount>
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

/** Valor dos pedidos em `google_play_orders` (colunas da V183). */
interface GooglePlayOrderAmountStore {
    /** O pedido existe e ainda não tem valor. */
    fun orderAmountMissing(orderId: String): Boolean

    /**
     * Pedidos reais sem valor cuja última tentativa foi antes de [checkedBefore] (ou nunca houve),
     * os nunca tentados primeiro.
     */
    fun ordersWithoutAmount(checkedBefore: Instant, limit: Int): List<String>

    fun saveOrderAmount(amount: GooglePlayOrderAmount, at: Instant)

    /** Registra a tentativa sem valor, para o job só voltar depois do intervalo. */
    fun markOrderAmountChecked(orderIds: List<String>, at: Instant)
}

fun interface GooglePlayNotificationStore {
    /** Registro de auditoria. @return true quando esta chamada gravou a mensagem (primeira entrega). */
    fun recordIfNew(messageId: String, notificationType: Int?, purchaseToken: String?): Boolean
}
