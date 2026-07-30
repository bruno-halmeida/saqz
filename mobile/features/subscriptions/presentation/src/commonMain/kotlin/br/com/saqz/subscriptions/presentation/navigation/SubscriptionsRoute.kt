package br.com.saqz.subscriptions.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Fluxo 8 · Planos (8a/8b escolha de plano+cupom, 8c pagamento, 8d plano ativo). A tela
 * 8e ("Meu plano") não entra aqui: vive no módulo de perfil existente (VUL-112).
 *
 * Depende só de `navigation3-runtime` pelo contrato [NavKey], nunca de `navigation3-ui`.
 */
@Serializable
sealed interface SubscriptionsRoute : NavKey {

    /** 8a/8b: escolha de plano, ciclo e cupom. */
    @Serializable
    data object PlanSelection : SubscriptionsRoute

    /** 8c: plano, ciclo e cupom já escolhidos seguem como argumentos escalares. */
    @Serializable
    data class Payment(
        val planId: String,
        val cycle: String,
        val couponCode: String? = null,
    ) : SubscriptionsRoute

    /** 8d: confirmação após a criação da assinatura. */
    @Serializable
    data object PlanActive : SubscriptionsRoute
}
