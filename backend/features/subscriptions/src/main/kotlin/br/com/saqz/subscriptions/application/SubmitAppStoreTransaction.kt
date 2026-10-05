package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.AppStoreSubscription
import java.util.UUID

sealed interface SubmitAppStoreTransactionResult {
    data object Accepted : SubmitAppStoreTransactionResult

    /** JWS inválido, outro app, produto que não é assinatura do Saqz ou ambiente não aceito. */
    data object Invalid : SubmitAppStoreTransactionResult

    /** A assinatura foi comprada por (ou já está presa a) outra conta Saqz. */
    data object OwnedByAnotherAccount : SubmitAppStoreTransactionResult
}

/**
 * Transação que o app recebeu do StoreKit (compra, renovação vista no aparelho, restauração).
 * Idempotente: a mesma transação reenviada não muda nada.
 *
 * Sem `appAccountToken` (compra feita fora do app, como código de oferta resgatado na App
 * Store), a assinatura fica com quem a enviou primeiro.
 */
class SubmitAppStoreTransaction(
    private val verifier: AppStoreSignedDataVerifier,
    private val subscriptions: AppStoreSubscriptionRepository,
    private val transaction: SubscriptionsTransactionRunner,
) {
    fun execute(ownerUserId: UUID, signedTransaction: String): SubmitAppStoreTransactionResult {
        val verified = verifier.verifyTransaction(signedTransaction)
            ?: return SubmitAppStoreTransactionResult.Invalid
        val product = AppStoreProduct.fromProductId(verified.productId)
            ?: return SubmitAppStoreTransactionResult.Invalid
        if (verified.expiresDate == null) return SubmitAppStoreTransactionResult.Invalid
        val token = verified.appAccountToken
        if (token != null && token != ownerUserId) return SubmitAppStoreTransactionResult.OwnedByAnotherAccount

        return transaction.inTransaction {
            subscriptions.insertIfAbsent(AppStoreSubscription.startedBy(verified, product, ownerUserId))
            val current = checkNotNull(subscriptions.findForUpdate(verified.originalTransactionId))
            if (current.ownerUserId != ownerUserId) {
                SubmitAppStoreTransactionResult.OwnedByAnotherAccount
            } else {
                subscriptions.save(current.applying(verified, product))
                subscriptions.recordTransaction(verified, ownerUserId)
                SubmitAppStoreTransactionResult.Accepted
            }
        }
    }
}
