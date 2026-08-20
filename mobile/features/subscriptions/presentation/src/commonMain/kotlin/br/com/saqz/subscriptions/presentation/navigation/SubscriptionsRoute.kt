package br.com.saqz.subscriptions.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Fluxo 8 · Assinaturas (8e "Meu plano"). O perfil (7a) navega para cá quando
 * `planOwner` — quem paga o plano, mesmo sem grupo. A tela busca tudo via
 * `SubscriptionGateway.mySubscription()`.
 *
 * Depende só de `navigation3-runtime` pelo contrato [NavKey], nunca de `navigation3-ui`.
 */
@Serializable
sealed interface SubscriptionsRoute : NavKey {

    /** 8e: plano atual, uso, recibos e o menu Gerenciar (VUL-112). Sem argumento — a
     * tela busca tudo via `SubscriptionGateway.mySubscription()`. */
    @Serializable
    data object MyPlan : SubscriptionsRoute

    /** Catálogo de planos com a assinatura vigente marcada, para troca. */
    @Serializable
    data object ChangePlan : SubscriptionsRoute
}
