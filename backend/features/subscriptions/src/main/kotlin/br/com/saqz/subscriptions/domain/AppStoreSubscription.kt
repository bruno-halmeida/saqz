package br.com.saqz.subscriptions.domain

import java.time.Instant
import java.util.UUID

/** Ambiente que assinou a transação. XCODE é o teste local do Xcode: não tem assinatura da Apple. */
enum class AppStoreEnvironment { PRODUCTION, SANDBOX, XCODE }

/** Transação de assinatura já verificada (assinatura, app e ambiente). */
data class AppStoreTransaction(
    val transactionId: String,
    val originalTransactionId: String,
    val productId: String,
    val purchaseDate: Instant,
    val expiresDate: Instant?,
    val revocationDate: Instant?,
    val appAccountToken: UUID?,
    val environment: AppStoreEnvironment,
    /** Milésimos da moeda (59900 = R$ 59,90). */
    val priceMillis: Long?,
    val currency: String?,
    val signedAt: Instant,
)

/** Estado da renovação automática, já verificado. */
data class AppStoreRenewalInfo(
    val originalTransactionId: String,
    val autoRenew: Boolean,
    val autoRenewProductId: String?,
    val inBillingRetry: Boolean,
    val gracePeriodExpiresAt: Instant?,
    val signedAt: Instant,
)

/**
 * Uma assinatura da App Store, identificada pela transação original. A Apple manda transações e
 * renewal info fora de ordem (notificação reenviada, app reenviando o histórico), então cada
 * parte só substitui o que tem se for mais nova do que o que já está gravado.
 */
data class AppStoreSubscription(
    val originalTransactionId: String,
    val ownerUserId: UUID,
    val environment: AppStoreEnvironment,
    val product: AppStoreProduct,
    val latestTransactionId: String,
    val latestPurchaseDate: Instant,
    val latestSignedAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val autoRenew: Boolean? = null,
    val autoRenewProductId: String? = null,
    val autoRenewChangedAt: Instant? = null,
    val inBillingRetry: Boolean = false,
    val gracePeriodExpiresAt: Instant? = null,
    val renewalSignedAt: Instant? = null,
) {
    val autoRenewProduct: AppStoreProduct? get() = AppStoreProduct.fromProductId(autoRenewProductId)

    /** Downgrade (ou troca de ciclo para outro plano) agendado para a próxima renovação. */
    val pendingPlan: Plan?
        get() = autoRenewProduct?.plan?.takeIf { autoRenew != false && it != product.plan }

    /**
     * Espelho do trecho App Store de `JdbcSubscriptionPlanLookup.findEntitlingPlan` — mudou lá,
     * muda aqui. Estorno corta na hora; vencida só vale dentro da carência de cobrança da Apple.
     */
    fun isEntitlingAt(now: Instant): Boolean =
        revokedAt == null && (expiresAt.isAfter(now) || gracePeriodExpiresAt?.isAfter(now) == true)

    /**
     * Passa a assinatura para outra conta do Saqz: o mesmo Apple ID voltou a assinar depois de
     * ela vencer, agora logado em outra conta. O renewal info antigo é descartado — o da compra
     * nova chega pela notificação.
     */
    fun transferredTo(newOwnerUserId: UUID): AppStoreSubscription = copy(
        ownerUserId = newOwnerUserId,
        autoRenew = null,
        autoRenewProductId = null,
        autoRenewChangedAt = null,
        inBillingRetry = false,
        gracePeriodExpiresAt = null,
        renewalSignedAt = null,
    )

    fun applying(transaction: AppStoreTransaction, product: AppStoreProduct): AppStoreSubscription {
        val expires = transaction.expiresDate ?: return this
        return when {
            transaction.transactionId == latestTransactionId ->
                // Mesma cobrança: só muda o estorno (REFUND / REFUND_REVERSED), e só se for mais nova.
                if (transaction.signedAt.isBefore(latestSignedAt)) this
                else copy(revokedAt = transaction.revocationDate, latestSignedAt = transaction.signedAt)
            transaction.purchaseDate.isAfter(latestPurchaseDate) -> copy(
                product = product,
                latestTransactionId = transaction.transactionId,
                latestPurchaseDate = transaction.purchaseDate,
                latestSignedAt = transaction.signedAt,
                expiresAt = expires,
                revokedAt = transaction.revocationDate,
            )
            // Cobrança anterior (renovação antiga, plano de antes de um upgrade): só vira recibo.
            else -> this
        }
    }

    fun applying(renewal: AppStoreRenewalInfo): AppStoreSubscription {
        val stored = renewalSignedAt
        if (stored != null && renewal.signedAt.isBefore(stored)) return this
        val changedAt = if (autoRenew == renewal.autoRenew) autoRenewChangedAt else renewal.signedAt
        return copy(
            autoRenew = renewal.autoRenew,
            autoRenewProductId = renewal.autoRenewProductId,
            autoRenewChangedAt = changedAt,
            inBillingRetry = renewal.inBillingRetry,
            gracePeriodExpiresAt = renewal.gracePeriodExpiresAt,
            renewalSignedAt = renewal.signedAt,
        )
    }

    companion object {
        fun startedBy(
            transaction: AppStoreTransaction,
            product: AppStoreProduct,
            ownerUserId: UUID,
        ): AppStoreSubscription = AppStoreSubscription(
            originalTransactionId = transaction.originalTransactionId,
            ownerUserId = ownerUserId,
            environment = transaction.environment,
            product = product,
            latestTransactionId = transaction.transactionId,
            latestPurchaseDate = transaction.purchaseDate,
            latestSignedAt = transaction.signedAt,
            expiresAt = requireNotNull(transaction.expiresDate) { "assinatura sem data de expiração" },
            revokedAt = transaction.revocationDate,
        )
    }
}
