package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Subscription
import java.util.UUID

interface SubscriptionRepository {
    fun findByAsaasSubscriptionId(asaasSubscriptionId: String): Subscription?

    fun findByOwnerUserId(ownerUserId: UUID): Subscription?

    /** Same as [findByOwnerUserId] with row lock for writers racing the webhook. */
    fun findByOwnerUserIdForUpdate(ownerUserId: UUID): Subscription?

    fun findByPendingUpgradeChargeId(chargeId: String): Subscription?

    /**
     * Resolve pelo pagamento ja aplicado. Serve o evento irmao (PAYMENT_RECEIVED apos
     * PAYMENT_CONFIRMED da mesma cobranca): num upgrade o `pendingUpgradeChargeId` ja foi limpo,
     * e sem este caminho o irmao nao resolveria assinatura nenhuma e ficaria em 503 eterno.
     */
    fun findByLastConfirmedPaymentId(paymentId: String): Subscription?

    /** Serializes create/check against the owner row (SELECT … FOR UPDATE on access_users). */
    fun lockOwner(ownerUserId: UUID)

    fun insert(subscription: Subscription)

    fun save(subscription: Subscription)
}
