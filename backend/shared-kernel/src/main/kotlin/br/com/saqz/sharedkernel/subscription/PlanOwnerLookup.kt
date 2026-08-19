package br.com.saqz.sharedkernel.subscription

import java.util.UUID

/**
 * Quem paga um plano entitulador é owner da conta — mesmo com zero grupos.
 * A fonte é o mesmo predicado de [br.com.saqz.subscriptions.domain.Subscription.isEntitlingAt].
 */
fun interface PlanOwnerLookup {
    fun isPlanOwner(userId: UUID): Boolean
}
