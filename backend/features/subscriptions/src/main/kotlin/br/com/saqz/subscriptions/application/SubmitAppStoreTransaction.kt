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
 *
 * A assinatura segue o pagamento: se o mesmo Apple ID volta a assinar (a Apple reaproveita a
 * transação original) logado em outra conta, e a assinatura antiga já não dava acesso na data da
 * compra nova, ela passa para a conta que comprou. Ativa, continua presa a quem a comprou.
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
            val owned = when {
                current.ownerUserId == ownerUserId -> current
                token == ownerUserId && !current.isEntitlingAt(verified.purchaseDate) -> current.transferredTo(ownerUserId)
                else -> return@inTransaction SubmitAppStoreTransactionResult.OwnedByAnotherAccount
            }
            subscriptions.save(owned.applying(verified, product))
            subscriptions.recordTransaction(verified, ownerUserId)
            SubmitAppStoreTransactionResult.Accepted
        }
    }
}
