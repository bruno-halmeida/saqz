package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute

/** De onde veio o toque: a criação de grupo compra e segue direto para a criação; os outros voltam para onde estavam. */
internal enum class PlansEntry { Default, GroupCreation }

/**
 * "Ver planos", o toque no push do teste/atraso e o "Abrir" da central: quem já tem assinatura
 * (em dia, na carência, vencida ou cancelada) vai a Meu plano, onde está o pagamento do site, o
 * gerenciamento da loja e o "Assinar de novo". Só quem nunca assinou (teste ou sem plano) vê a
 * tela de planos, que vende pela loja. O checkout do site que nunca foi pago
 * (linha em atraso sem pagamento confirmado) conta como quem nunca assinou. A consulta que falha também vai a
 * Meu plano: ali a falha tem "tentar de novo", e a loja nunca vende por cima de uma assinatura
 * que talvez exista.
 *
 * Quem nunca assinou e veio do "+" de Grupos ou do "Ver planos" de Criar grupo ([PlansEntry.GroupCreation]) compra no paywall
 * `SubscriptionRequired`, que depois da compra leva direto à criação do grupo; nas demais entradas é a tela de planos que volta.
 */
internal fun MutableList<NavKey>.openSubscriptionPlans(
    subscription: SaqzResult<MySubscription, SubscriptionError>,
    entry: PlansEntry = PlansEntry.Default,
) {
    val neverSubscribed = when (subscription) {
        is SaqzResult.Failure -> subscription.error == SubscriptionError.NotFound
        is SaqzResult.Success -> subscription.value.provider == SubscriptionProvider.Asaas && !subscription.value.paymentConfirmed
    }
    if (neverSubscribed) {
        val plans: NavKey = if (entry == PlansEntry.GroupCreation) SubscriptionRequired else SubscribeForAccess
        if (lastOrNull() != plans) add(plans)
    } else if (lastOrNull() != SubscriptionsRoute.MyPlan) {
        add(SubscriptionsRoute.MyPlan)
    }
}
