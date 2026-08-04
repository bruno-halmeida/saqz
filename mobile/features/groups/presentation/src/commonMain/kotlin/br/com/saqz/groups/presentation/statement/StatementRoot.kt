package br.com.saqz.groups.presentation.statement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun StatementRoot(
    groupId: String,
    onBack: () -> Unit,
    onEffect: (StatementEffect) -> Unit,
    viewModel: StatementViewModel = koinViewModel(parameters = { parametersOf(groupId) }),
    refreshVersion: Int = 0,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(StatementIntent.Retry)
    }
    LaunchedEffect(refreshVersion) {
        if (refreshVersion > 0) viewModel.onIntent(StatementIntent.Retry)
    }
    ObserveAsEvents(viewModel.effects, onEvent = onEffect)
    StatementScreen(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
    )
}
