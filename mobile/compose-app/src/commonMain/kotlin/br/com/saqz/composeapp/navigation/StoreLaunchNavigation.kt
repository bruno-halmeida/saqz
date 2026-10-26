package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.domain.StoreLaunchPolicy
import br.com.saqz.groups.presentation.navigation.GroupsRoute
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute

/**
 * Applied before composing destinations, including restored stacks and incoming links.
 * O vínculo com o grupo do WhatsApp não passa por aqui: quem decide é o servidor, e as entradas
 * para a tela só aparecem com ele ligado. O recebimento pelo app também não (DEB-142): a constante
 * saiu, e as entradas obedecem a `GET /api/receivables/availability` (`ReceivablesState`).
 */
internal fun NavKey.isAvailableAtLaunch(): Boolean = when (this) {
    SubscriptionsRoute.ChangePlan -> StoreLaunchPolicy.purchases
    is GroupsRoute.Thread -> notices || StoreLaunchPolicy.chat
    else -> true
}
