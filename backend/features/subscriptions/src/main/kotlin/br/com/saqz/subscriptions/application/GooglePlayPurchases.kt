package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.GooglePlayProduct
import br.com.saqz.subscriptions.domain.GooglePlayPurchase
import br.com.saqz.subscriptions.domain.GooglePlayState
import br.com.saqz.subscriptions.domain.GooglePlaySubscription
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID

sealed interface SubmitGooglePlayPurchaseResult {
    data object Accepted : SubmitGooglePlayPurchaseResult

    /** Token inexistente, de outro app, produto desconhecido ou compra ainda pendente. */
    data object Invalid : SubmitGooglePlayPurchaseResult

    /** A assinatura foi comprada por (ou já está presa a) outra conta Saqz. */
    data object OwnedByAnotherAccount : SubmitGooglePlayPurchaseResult
}

/**
 * Compra do Play enviada pelo app (compra nova, restauração, reenvio na abertura). O estado vem
 * da API do Google, nunca do app; depois de gravada, a compra é reconhecida aqui — o app não
 * reconhece nada. Idempotente.
 */
class SubmitGooglePlayPurchase(
    private val api: GooglePlayPurchasesApi,
    private val subscriptions: GooglePlaySubscriptionRepository,
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val sync = GooglePlayPurchaseSync(api, subscriptions, clock)

    fun execute(ownerUserId: UUID, productId: String, purchaseToken: String): SubmitGooglePlayPurchaseResult {
        val purchase = when (val lookup = api.subscription(purchaseToken)) {
            GooglePlayLookup.NotFound -> return SubmitGooglePlayPurchaseResult.Invalid
            is GooglePlayLookup.Found -> lookup.purchase
        }
        if (purchase.productId != productId) return SubmitGooglePlayPurchaseResult.Invalid
        val product = sync.recordable(purchase) ?: return SubmitGooglePlayPurchaseResult.Invalid
        val accountId = purchase.obfuscatedAccountId
        if (accountId != null && accountId != ownerUserId.toString()) {
            return SubmitGooglePlayPurchaseResult.OwnedByAnotherAccount
        }
        val owned = transaction.inTransaction { sync.record(purchase, product, ownerUserId) }
        if (owned.ownerUserId != ownerUserId) return SubmitGooglePlayPurchaseResult.OwnedByAnotherAccount
        sync.acknowledgeIfNeeded(purchase)
        return SubmitGooglePlayPurchaseResult.Accepted
    }
}

/** Real-time Developer Notification já tirada do envelope do Pub/Sub. */
data class GooglePlayNotificationCommand(
    val messageId: String,
    val packageName: String?,
    val notificationType: Int?,
    val purchaseToken: String?,
)

sealed interface ProcessGooglePlayNotificationResult {
    data object Accepted : ProcessGooglePlayNotificationResult
    data object Unauthorized : ProcessGooglePlayNotificationResult
}

/**
 * RTDN do Google Play. A notificação só diz qual token mudou; o estado vem da API. Dono
 * desconhecido (token novo sem `obfuscatedAccountId` de uma conta existente) é ignorado: o app
 * envia a compra quando abrir.
 */
class ProcessGooglePlayNotification(
    private val expectedToken: String,
    private val packageName: String,
    private val api: GooglePlayPurchasesApi,
    private val subscriptions: GooglePlaySubscriptionRepository,
    private val notifications: GooglePlayNotificationStore,
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val sync = GooglePlayPurchaseSync(api, subscriptions, clock)

    fun execute(token: String?, command: GooglePlayNotificationCommand): ProcessGooglePlayNotificationResult {
        if (!authorized(token)) return ProcessGooglePlayNotificationResult.Unauthorized
        val purchaseToken = command.purchaseToken
        if (command.packageName != packageName || purchaseToken == null) {
            notifications.recordIfNew(command.messageId, command.notificationType, purchaseToken)
            return ProcessGooglePlayNotificationResult.Accepted
        }
        val purchase = (api.subscription(purchaseToken) as? GooglePlayLookup.Found)?.purchase
        val product = purchase?.let(sync::recordable)
        // Reentrega (inclusive depois de falhar o acknowledge) regrava e reconhece de novo: o
        // estado vem sempre da API, então repetir é inofensivo. A tabela de mensagens é só registro.
        val recorded = transaction.inTransaction {
            notifications.recordIfNew(command.messageId, command.notificationType, purchaseToken)
            if (purchase == null || product == null) return@inTransaction null
            val owner = subscriptions.findForUpdate(purchaseToken)?.ownerUserId
                ?: purchase.obfuscatedAccountId?.let(::uuidOrNull)?.takeIf(subscriptions::ownerExists)
                ?: return@inTransaction null
            sync.record(purchase, product, owner)
        }
        if (recorded != null && purchase != null) sync.acknowledgeIfNeeded(purchase)
        return ProcessGooglePlayNotificationResult.Accepted
    }

    private fun authorized(token: String?): Boolean = expectedToken.isNotBlank() && token != null &&
        MessageDigest.isEqual(token.toByteArray(), expectedToken.toByteArray())

    private fun uuidOrNull(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()
}

/** Gravação comum: cria ou atualiza a assinatura, aposenta a substituída e registra o pedido. */
internal class GooglePlayPurchaseSync(
    private val api: GooglePlayPurchasesApi,
    private val subscriptions: GooglePlaySubscriptionRepository,
    private val clock: Clock,
) {
    /** Produto do Saqz de uma compra que já pode virar assinatura; null para ignorar. */
    fun recordable(purchase: GooglePlayPurchase): GooglePlayProduct? {
        if (purchase.state == GooglePlayState.PENDING || purchase.expiresAt == null) return null
        return GooglePlayProduct.of(purchase.productId, purchase.basePlanId)
    }

    /** Dentro da transação. Devolve a linha gravada — com o dono que ela já tinha, se tinha. */
    fun record(purchase: GooglePlayPurchase, product: GooglePlayProduct, owner: UUID): GooglePlaySubscription {
        subscriptions.insertIfAbsent(GooglePlaySubscription.startedBy(purchase, product, owner))
        val current = checkNotNull(subscriptions.findForUpdate(purchase.purchaseToken))
        if (current.ownerUserId != owner) return current
        val updated = current.refreshedFrom(purchase, product)
        subscriptions.save(updated)
        purchase.linkedPurchaseToken?.let { subscriptions.supersede(it, clock.instant()) }
        if (updated.latestOrderId != null) subscriptions.recordOrder(updated)
        return updated
    }

    fun acknowledgeIfNeeded(purchase: GooglePlayPurchase) {
        if (purchase.acknowledged) return
        api.acknowledge(purchase.productId, purchase.purchaseToken)
        subscriptions.markAcknowledged(purchase.purchaseToken)
    }
}
