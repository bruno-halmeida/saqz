package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Subscription
import java.util.UUID

interface SubscriptionRepository {
    fun findByAsaasSubscriptionId(asaasSubscriptionId: String): Subscription?

    fun findByOwnerUserId(ownerUserId: UUID): Subscription?

    fun insert(subscription: Subscription)

    fun save(subscription: Subscription)
}
