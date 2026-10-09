package br.com.saqz.groups.presentation.statement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.RefreshWhenVisible
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun StatementRoot(
    groupId: String,
    onBack: () -> Unit,
    onEffect: (StatementEffect) -> Unit,
    viewModel: StatementViewModel = koinViewModel(
        key = "statement/$groupId",
        parameters = { parametersOf(groupId) },
    ),
    refreshVersion: Int = 0,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Primeira abertura, volta à tela e volta do lançamento (`refreshVersion`): a ViewModel decide
    // entre a carga completa e a recarga por baixo.
    RefreshWhenVisible(refreshVersion) { viewModel.onIntent(StatementIntent.Refresh) }
    ObserveAsEvents(viewModel.effects, onEvent = onEffect)
    StatementScreen(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
    )
}
