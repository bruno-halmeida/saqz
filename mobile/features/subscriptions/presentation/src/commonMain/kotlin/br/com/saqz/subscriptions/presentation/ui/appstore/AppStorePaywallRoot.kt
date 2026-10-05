package br.com.saqz.subscriptions.presentation.ui.appstore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallEffect
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallPhase
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallViewModel
import org.koin.compose.viewmodel.koinViewModel

const val AppStoreTermsUrl = "https://saqz.app/termos/"
const val AppStorePrivacyUrl = "https://saqz.app/privacidade/"

/**
 * [fallback] é o que aparece quando a App Store não vende nesta conta ou neste aparelho
 * ([AppStorePaywallPhase.Unavailable]) — o portão de assinatura de antes.
 */
@Composable
fun AppStorePaywallRoot(
    onBack: () -> Unit,
    onSubscriptionConfirm: () -> Unit,
    fallback: @Composable () -> Unit,
    viewModel: AppStorePaywallViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            AppStorePaywallEffect.Subscribed -> onSubscriptionConfirm()
        }
    }
    if (state.phase == AppStorePaywallPhase.Unavailable && !state.isSubscribed) {
        fallback()
    } else {
        AppStorePaywallScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onBack = onBack,
            onOpenTerms = { uriHandler.openUri(AppStoreTermsUrl) },
            onOpenPrivacy = { uriHandler.openUri(AppStorePrivacyUrl) },
        )
    }
}
