package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Subscription
import java.util.UUID

interface SubscriptionRepository {
    fun findByAsaasSubscriptionId(asaasSubscriptionId: String): Subscription?

    fun findByOwnerUserId(ownerUserId: UUID): Subscription?

    fun findByPendingUpgradeChargeId(chargeId: String): Subscription?

    /** Serializes create/check against the owner row (SELECT … FOR UPDATE on access_users). */
    fun lockOwner(ownerUserId: UUID)

    fun insert(subscription: Subscription)

    fun save(subscription: Subscription)
}
