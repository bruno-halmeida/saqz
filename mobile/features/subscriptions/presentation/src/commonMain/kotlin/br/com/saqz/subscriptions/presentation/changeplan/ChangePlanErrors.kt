package br.com.saqz.subscriptions.presentation.changeplan

import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.presentation.myplan.toUiText
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.changeplan_downgrade_blocked

internal fun SubscriptionError.toChangePlanUiText(): UiText = when (this) {
    SubscriptionError.DowngradeBlocked -> UiText.Res(Res.string.changeplan_downgrade_blocked)
    else -> toUiText()
}
