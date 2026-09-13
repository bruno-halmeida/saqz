package br.com.saqz.receivables.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MemberPaymentHistoryRoot(onBack: () -> Unit, onOpen: (String) -> Unit,
    viewModel: MemberPaymentHistoryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) { viewModel.onIntent(MemberPaymentHistoryIntent.Refresh); onPauseOrDispose { } }
    ObserveAsEvents(viewModel.effects) { if (viewModel.validEffect(it)) onOpen(it.orderId) }
    MemberPaymentHistoryScreen(state, viewModel::onIntent, onBack)
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MemberPaymentRoot(orderId: String, onBack: () -> Unit,
    viewModel: MemberPaymentViewModel = koinViewModel(key = "payment/$orderId", parameters = { parametersOf(orderId) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    BackHandler(enabled = state.pending) { /* Keep the unresolved command available for recovery. */ }
    LifecycleResumeEffect(viewModel) { viewModel.onIntent(MemberPaymentIntent.Refresh); onPauseOrDispose { } }
    ObserveAsEvents(viewModel.effects) { effect ->
        if (viewModel.validEffect(effect)) when (effect) {
            is MemberPaymentEffect.Copy -> {
                clipboard.setText(AnnotatedString(effect.payload))
                viewModel.onIntent(MemberPaymentIntent.Copied)
            }
            is MemberPaymentEffect.Open -> runCatching { uri.openUri(effect.url) }
                .onFailure { viewModel.onIntent(MemberPaymentIntent.OpenFailed) }
        }
    }
    MemberPaymentScreen(state, viewModel::onIntent, onBack)
}
