package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.AppStoreEnvironment
import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.AppStoreSubscription
import br.com.saqz.subscriptions.domain.AppStoreTransaction
import java.time.Instant
import java.util.UUID

internal class InMemoryAppStoreSubscriptions(
    private val owners: Set<UUID> = emptySet(),
) : AppStoreSubscriptionRepository, AppStoreNotificationStore {
    val rows = linkedMapOf<String, AppStoreSubscription>()
    val transactions = linkedMapOf<String, Pair<AppStoreTransaction, UUID>>()
    val notifications = mutableSetOf<UUID>()

    override fun insertIfAbsent(subscription: AppStoreSubscription) {
        rows.putIfAbsent(subscription.originalTransactionId, subscription)
    }

    override fun findForUpdate(originalTransactionId: String) = rows[originalTransactionId]

    override fun save(subscription: AppStoreSubscription) {
        rows[subscription.originalTransactionId] = subscription
    }

    override fun findByOwner(ownerUserId: UUID) = rows.values.filter { it.ownerUserId == ownerUserId }

    override fun ownerExists(ownerUserId: UUID) = ownerUserId in owners

    override fun recordTransaction(transaction: AppStoreTransaction, ownerUserId: UUID) {
        transactions[transaction.transactionId] = transaction to ownerUserId
    }

    override fun listTransactionsForOwner(ownerUserId: UUID, limit: Int) = transactions.values
        .filter { (transaction, owner) -> owner == ownerUserId && transaction.revocationDate == null }
        .map { (transaction, _) ->
            AppStoreTransactionRecord(
                transactionId = transaction.transactionId,
                purchaseDate = transaction.purchaseDate,
                priceMillis = transaction.priceMillis,
                currency = transaction.currency,
                revokedAt = null,
                recordedAt = transaction.purchaseDate,
            )
        }
        .sortedByDescending { it.purchaseDate }
        .take(limit)

    override fun recordIfNew(notification: AppStoreNotification) = notifications.add(notification.notificationUuid)
}

/** Verificador que devolve o que foi registrado para cada JWS; o resto é inválido. */
internal class ScriptedAppStoreVerifier : AppStoreSignedDataVerifier {
    val transactions = mutableMapOf<String, AppStoreTransaction>()
    val notifications = mutableMapOf<String, AppStoreNotification>()

    override fun verifyTransaction(signedTransaction: String) = transactions[signedTransaction]

    override fun verifyNotification(signedPayload: String) = notifications[signedPayload]
}

internal object InlineTransactions : SubscriptionsTransactionRunner {
    override fun <T> inTransaction(block: () -> T): T = block()
}

internal fun appStoreTransaction(
    owner: UUID?,
    transactionId: String = "2000000001",
    originalTransactionId: String = "2000000001",
    product: AppStoreProduct = AppStoreProduct.ORGANIZADOR_MENSAL,
    purchaseDate: Instant = Instant.parse("2026-10-01T12:00:00Z"),
    expiresDate: Instant? = purchaseDate.plusSeconds(30L * 24 * 3600),
    revocationDate: Instant? = null,
    signedAt: Instant = purchaseDate,
) = AppStoreTransaction(
    transactionId = transactionId,
    originalTransactionId = originalTransactionId,
    productId = product.productId,
    purchaseDate = purchaseDate,
    expiresDate = expiresDate,
    revocationDate = revocationDate,
    appAccountToken = owner,
    environment = AppStoreEnvironment.SANDBOX,
    priceMillis = 59_900,
    currency = "BRL",
    signedAt = signedAt,
)
