package br.com.saqz.subscriptions.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Fluxo 8 · Assinaturas (8e "Meu plano"). Não existe módulo de perfil neste
 * repo ainda — a apresentação autenticada foi zerada num reset e está sendo reconstruída fluxo
 * a fluxo (só grupos e assinaturas existem hoje). Por isso 8e entra aqui; quando o módulo de
 * perfil existir de verdade, o ponto de entrada de lá só precisa navegar para esta rota.
 *
 * Depende só de `navigation3-runtime` pelo contrato [NavKey], nunca de `navigation3-ui`.
 */
@Serializable
sealed interface SubscriptionsRoute : NavKey {

    /** 8e: plano atual, uso, recibos e o menu Gerenciar (VUL-112). Sem argumento — a
     * tela busca tudo via `SubscriptionGateway.mySubscription()`. */
    @Serializable
    data object MyPlan : SubscriptionsRoute
}
