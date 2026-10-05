package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.AppStoreEnvironment
import br.com.saqz.subscriptions.domain.AppStoreRenewalInfo
import br.com.saqz.subscriptions.domain.AppStoreSubscription
import br.com.saqz.subscriptions.domain.AppStoreTransaction
import java.time.Instant
import java.util.UUID

/** App Store Server Notification V2 já verificada, com transação e renewal info verificados. */
data class AppStoreNotification(
    val notificationUuid: UUID,
    val type: String,
    val subtype: String?,
    val environment: AppStoreEnvironment?,
    val signedAt: Instant,
    val transaction: AppStoreTransaction?,
    val renewalInfo: AppStoreRenewalInfo?,
)

/**
 * A verificação não conseguiu concluir agora (OCSP da Apple fora do ar). O dado pode ser válido:
 * quem chamou deve falhar de forma que o app e a Apple tentem de novo, nunca recusar.
 */
class AppStoreVerificationUnavailableException(cause: Throwable) : RuntimeException(cause)

/**
 * Verifica o JWS da Apple: cadeia de certificados até a raiz da Apple, assinatura, bundle id e
 * ambiente aceito. Null quando o dado não passa — nunca devolve conteúdo não verificado. Lança
 * [AppStoreVerificationUnavailableException] quando não deu para verificar agora.
 */
interface AppStoreSignedDataVerifier {
    fun verifyTransaction(signedTransaction: String): AppStoreTransaction?
    fun verifyNotification(signedPayload: String): AppStoreNotification?
}

data class AppStoreTransactionRecord(
    val transactionId: String,
    val purchaseDate: Instant,
    val priceMillis: Long?,
    val currency: String?,
    val revokedAt: Instant?,
    val recordedAt: Instant,
)

interface AppStoreSubscriptionRepository {
    /** Cria a linha se a transação original ainda não existe; não mexe numa que já existe. */
    fun insertIfAbsent(subscription: AppStoreSubscription)

    fun findForUpdate(originalTransactionId: String): AppStoreSubscription?

    fun save(subscription: AppStoreSubscription)

    fun findByOwner(ownerUserId: UUID): List<AppStoreSubscription>

    fun ownerExists(ownerUserId: UUID): Boolean

    /** Grava a cobrança uma vez por transaction ID; reenvio só atualiza o estorno. */
    fun recordTransaction(transaction: AppStoreTransaction, ownerUserId: UUID)

    /** Cobranças do dono, mais recentes primeiro. */
    fun listTransactionsForOwner(ownerUserId: UUID, limit: Int): List<AppStoreTransactionRecord>
}

fun interface AppStoreNotificationStore {
    /** @return true quando esta chamada gravou a notificação (primeira entrega). */
    fun recordIfNew(notification: AppStoreNotification): Boolean
}
