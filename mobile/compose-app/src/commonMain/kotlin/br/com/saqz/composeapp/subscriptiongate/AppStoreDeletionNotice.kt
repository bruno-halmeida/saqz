package br.com.saqz.composeapp.subscriptiongate

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.appstore_deletion_manage
import br.com.saqz.composeapp.resources.appstore_deletion_notice
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Immutable
data class AppStoreDeletionNoticeState(val visible: Boolean = false)

sealed interface AppStoreDeletionNoticeIntent {
    data object Refresh : AppStoreDeletionNoticeIntent
}

/**
 * Exigência da Apple na exclusão de conta: quem assina pela App Store precisa saber que a
 * cobrança continua até cancelar na conta Apple — o Saqz não consegue cancelar por ela.
 * Mora no app-shell porque junta o Perfil (exclusão) e Assinaturas, que não se conhecem.
 */
class AppStoreDeletionNoticeViewModel(
    private val gateway: SubscriptionGateway,
) : MviViewModel<AppStoreDeletionNoticeState, AppStoreDeletionNoticeIntent, Nothing>(AppStoreDeletionNoticeState()) {
    private var generation = 0

    init {
        load()
    }

    override fun handleIntent(intent: AppStoreDeletionNoticeIntent) {
        when (intent) {
            AppStoreDeletionNoticeIntent.Refresh -> load()
        }
    }

    private fun load() {
        val current = ++generation
        viewModelScope.launch {
            val subscription = (gateway.mySubscription() as? SaqzResult.Success)?.value
            if (current != generation) return@launch
            val renewing = subscription?.provider == SubscriptionProvider.AppStore && subscription.autoRenew == true
            update { it.copy(visible = renewing) }
        }
    }
}

object AppStoreDeletionNoticeTags {
    const val Notice = "appstore-deletion-notice"
    const val Manage = "appstore-deletion-manage"
}

@Composable
internal fun AppStoreDeletionNotice(
    checkout: AppStoreCheckout = koinInject(),
    viewModel: AppStoreDeletionNoticeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.visible) return
    SaqzCard(tone = SaqzCardTone.Soft, modifier = Modifier.testTag(AppStoreDeletionNoticeTags.Notice)) {
        Text(
            text = stringResource(Res.string.appstore_deletion_notice),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textPrimary,
        )
        if (checkout.canManageSubscriptions) {
            SaqzButton(
                label = stringResource(Res.string.appstore_deletion_manage),
                onClick = { checkout.manageSubscriptions { viewModel.onIntent(AppStoreDeletionNoticeIntent.Refresh) } },
                variant = SaqzButtonVariant.Secondary,
                fullWidth = true,
                modifier = Modifier.testTag(AppStoreDeletionNoticeTags.Manage),
            )
        }
    }
}
