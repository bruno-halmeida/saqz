package br.com.saqz.subscriptions.presentation.myplan

import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.DataError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.myplan_generic_error
import br.com.saqz.subscriptions.resources.myplan_network_error

/** Tradução genérica para carga, recibos e cancelamento. */
fun SubscriptionError.toUiText(): UiText = when (this) {
    is SubscriptionError.Data -> error.toUiText()
    else -> UiText.Res(Res.string.myplan_generic_error)
}

private fun DataError.toUiText(): UiText = when (this) {
    DataError.Connectivity, DataError.Timeout -> UiText.Res(Res.string.myplan_network_error)
    else -> UiText.Res(Res.string.myplan_generic_error)
}
