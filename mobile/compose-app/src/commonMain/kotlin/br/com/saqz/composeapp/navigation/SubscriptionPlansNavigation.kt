package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute

/**
 * "Ver planos", o toque no push do teste/atraso e o "Abrir" da central: quem já tem assinatura
 * (em dia, na carência, vencida ou cancelada) vai a Meu plano, onde está o pagamento do site, o
 * gerenciamento da loja e o "Assinar de novo". Só quem nunca assinou (teste ou sem plano) vê a
 * tela de planos, que vende pela loja ou pelo checkout do site. A consulta que falha também vai a
 * Meu plano: ali a falha tem "tentar de novo", e a loja nunca vende por cima de uma assinatura
 * que talvez exista.
 */
internal fun MutableList<NavKey>.openSubscriptionPlans(
    subscription: SaqzResult<MySubscription, SubscriptionError>,
    openWebCheckout: () -> Boolean,
) {
    val neverSubscribed = subscription is SaqzResult.Failure && subscription.error == SubscriptionError.NotFound
    if (neverSubscribed) {
        if (lastOrNull() != SubscribeForAccess && !openWebCheckout()) add(SubscribeForAccess)
    } else if (lastOrNull() != SubscriptionsRoute.MyPlan) {
        add(SubscriptionsRoute.MyPlan)
    }
}
