package br.com.saqz.receivables.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RecurrenceRoot(accountId: String, groupId: String, onBack: () -> Unit,
    viewModel: RecurrenceViewModel = koinViewModel(key = "recurrence/$accountId/$groupId",
        parameters = { parametersOf(accountId, groupId) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    LifecycleResumeEffect(viewModel) { viewModel.onIntent(RecurrenceIntent.Refresh); onPauseOrDispose { } }
    ObserveAsEvents(viewModel.effects) { effect ->
        if (viewModel.validEffect(effect)) when (effect) {
            is RecurrenceEffect.Open -> runCatching { uri.openUri(effect.url) }
                .onFailure { viewModel.onIntent(RecurrenceIntent.OpenFailed) }
        }
    }
    RecurrenceScreen(state, viewModel::onIntent, onBack)
}
