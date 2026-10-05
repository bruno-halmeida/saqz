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
import br.com.saqz.composeapp.resources.googleplay_deletion_notice
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
data class AppStoreDeletionNoticeState(
    /** A loja que segue cobrando (App Store ou Google Play); nulo esconde o aviso. */
    val store: SubscriptionProvider? = null,
) {
    val visible: Boolean get() = store != null
}

sealed interface AppStoreDeletionNoticeIntent {
    data object Refresh : AppStoreDeletionNoticeIntent
}

/**
 * Exigência das lojas na exclusão de conta: quem assina pela App Store ou pelo Google Play
 * precisa saber que a cobrança continua até cancelar na conta da loja — o Saqz não consegue
 * cancelar por ela. Mora no app-shell porque junta o Perfil (exclusão) e Assinaturas, que
 * não se conhecem.
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
            val renewingAtStore = subscription?.takeIf {
                it.autoRenew == true && it.provider in STORE_PROVIDERS
            }?.provider
            update { it.copy(store = renewingAtStore) }
        }
    }
}

private val STORE_PROVIDERS = setOf(SubscriptionProvider.AppStore, SubscriptionProvider.GooglePlay)

object AppStoreDeletionNoticeTags {
    const val Notice = "appstore-deletion-notice"
    const val Manage = "appstore-deletion-manage"
}

@Composable
internal fun AppStoreDeletionNotice(
    checkout: StoreCheckout = koinInject(),
    viewModel: AppStoreDeletionNoticeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.visible) return
    val notice = when (state.store) {
        SubscriptionProvider.GooglePlay -> Res.string.googleplay_deletion_notice
        else -> Res.string.appstore_deletion_notice
    }
    SaqzCard(tone = SaqzCardTone.Soft, modifier = Modifier.testTag(AppStoreDeletionNoticeTags.Notice)) {
        Text(
            text = stringResource(notice),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textPrimary,
        )
        // Só abre a gestão da loja deste aparelho: assinatura do Play vista no iPhone só avisa.
        if (checkout.canManageSubscriptions && checkout.deviceStore == state.store) {
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
