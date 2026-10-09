package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.AppStoreSubscription
import br.com.saqz.subscriptions.domain.AppStoreTransaction

sealed interface ProcessAppStoreNotificationResult {
    /** Processada, repetida ou sem nada para o Saqz (TEST, produto desconhecido, dono desconhecido). */
    data object Accepted : ProcessAppStoreNotificationResult

    /** Não veio assinada pela Apple para este app e ambiente. */
    data object Invalid : ProcessAppStoreNotificationResult
}

/**
 * App Store Server Notifications V2. Toda notificação de assinatura traz a transação mais recente
 * e o renewal info; o estado sai deles, não do tipo da notificação — assim SUBSCRIBED, DID_RENEW,
 * DID_CHANGE_RENEWAL_STATUS, DID_FAIL_TO_RENEW, EXPIRED, REFUND e REVOKE seguem a mesma regra.
 *
 * Dono desconhecido (transação original nova sem `appAccountToken` de uma conta existente) é
 * aceito e ignorado: o app envia a transação quando o usuário abrir e a vincula.
 *
 * Mesma regra de [SubmitAppStoreTransaction] para o Apple ID que volta a assinar logado em outra
 * conta: se a assinatura antiga já não dava acesso na data da compra, ela passa para a conta do
 * `appAccountToken` novo.
 */
class ProcessAppStoreNotification(
    private val verifier: AppStoreSignedDataVerifier,
    private val subscriptions: AppStoreSubscriptionRepository,
    private val notifications: AppStoreNotificationStore,
    private val transaction: SubscriptionsTransactionRunner,
) {
    fun execute(signedPayload: String): ProcessAppStoreNotificationResult {
        val notification = verifier.verifyNotification(signedPayload)
            ?: return ProcessAppStoreNotificationResult.Invalid
        transaction.inTransaction {
            if (notifications.recordIfNew(notification)) apply(notification)
        }
        return ProcessAppStoreNotificationResult.Accepted
    }

    private fun apply(notification: AppStoreNotification) {
        val verified = notification.transaction ?: return
        val product = AppStoreProduct.fromProductId(verified.productId) ?: return
        if (verified.expiresDate == null) return
        val existing = subscriptions.findForUpdate(verified.originalTransactionId)?.let { claimed(it, verified) }
        val current = existing ?: run {
            val owner = verified.appAccountToken?.takeIf(subscriptions::ownerExists) ?: return
            subscriptions.insertIfAbsent(AppStoreSubscription.startedBy(verified, product, owner))
            checkNotNull(subscriptions.findForUpdate(verified.originalTransactionId))
        }
        val renewal = notification.renewalInfo?.takeIf { it.originalTransactionId == current.originalTransactionId }
        val updated = current.applying(verified, product).let { if (renewal != null) it.applying(renewal) else it }
        subscriptions.save(updated)
        subscriptions.recordTransaction(verified, current.ownerUserId)
    }

    private fun claimed(existing: AppStoreSubscription, verified: AppStoreTransaction): AppStoreSubscription {
        val buyer = verified.appAccountToken ?: return existing
        if (buyer == existing.ownerUserId || existing.isEntitlingAt(verified.purchaseDate)) return existing
        if (!subscriptions.ownerExists(buyer)) return existing
        return existing.transferredTo(buyer)
    }
}
