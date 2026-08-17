package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import br.com.saqz.composeapp.subscriptiongate.SubscriptionGateEffect
import br.com.saqz.composeapp.subscriptiongate.SubscriptionGateIntent
import br.com.saqz.composeapp.subscriptiongate.SubscriptionGateScreen
import br.com.saqz.composeapp.subscriptiongate.SubscriptionGateViewModel
import br.com.saqz.designsystem.ObserveAsEvents
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

/** App-owned entry point for the shared subscription authorization gate. */
@Serializable
data object SubscriptionRequired : NavKey

/**
 * The gate is composed inside its Navigation3 entry so the ViewModel store belongs to this
 * route and is cleared when the entry is removed. The screen remains feature UI owned by the
 * parallel gate slice; only lifecycle and navigation wiring live here.
 */
@Composable
internal fun SubscriptionRequiredDestination(
    onBack: () -> Unit,
    onAuthorizationSuccess: () -> Unit,
    viewModel: SubscriptionGateViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        // The ViewModel defaults to foreground=true; establish the lifecycle truth before
        // Opened, otherwise background composition could start an authorization check.
        viewModel.onIntent(SubscriptionGateIntent.ForegroundChanged(false))
        viewModel.onIntent(SubscriptionGateIntent.Opened)
        onDispose {
            viewModel.onIntent(SubscriptionGateIntent.ForegroundChanged(false))
            viewModel.onIntent(SubscriptionGateIntent.Closed)
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(SubscriptionGateIntent.ForegroundChanged(true))
        onPauseOrDispose {
            viewModel.onIntent(SubscriptionGateIntent.ForegroundChanged(false))
        }
    }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            SubscriptionGateEffect.AuthorizationGranted -> onAuthorizationSuccess()
        }
    }

    SubscriptionGateScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}
