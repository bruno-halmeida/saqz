package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.details.GroupDetailsEffect
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A tela não navega: o efeito sobe para quem registrou o destino no `NavDisplay`
 * (AGENTS.md §6). [onEffect] é o callback único; quem o liga é o ticket de navegação.
 */
@Composable
fun GroupDetailsRoot(
    groupId: String,
    onBack: () -> Unit,
    onEffect: (GroupDetailsEffect) -> Unit,
    viewModel: GroupDetailsViewModel = koinViewModel(parameters = { parametersOf(groupId) }),
    refreshVersion: Int = 0,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(refreshVersion) {
        if (refreshVersion > 0) viewModel.onIntent(GroupDetailsIntent.Retry)
    }
    ObserveAsEvents(viewModel.effects, onEvent = onEffect)
    GroupDetailsScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
