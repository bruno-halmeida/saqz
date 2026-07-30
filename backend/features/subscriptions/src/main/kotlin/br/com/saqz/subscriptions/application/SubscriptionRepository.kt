package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Subscription

interface SubscriptionRepository {
    fun findByAsaasSubscriptionId(asaasSubscriptionId: String): Subscription?

    fun save(subscription: Subscription)
}
