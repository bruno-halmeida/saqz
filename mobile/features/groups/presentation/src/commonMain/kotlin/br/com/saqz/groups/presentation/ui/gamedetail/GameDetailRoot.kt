package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.gamedetail.GameDetailEffect
import br.com.saqz.groups.presentation.gamedetail.GameDetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun GameDetailRoot(
    groupId: String,
    gameId: String,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenSettlement: () -> Unit = {},
    onCancel: () -> Unit = onBack,
    viewModel: GameDetailViewModel = koinViewModel(
        key = "game-detail/$groupId/$gameId",
        parameters = { parametersOf(groupId, gameId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            GameDetailEffect.OpenEditor -> onOpenEditor()
            GameDetailEffect.OpenSettlement -> onOpenSettlement()
            GameDetailEffect.Cancelled -> onCancel()
        }
    }
    GameDetailScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
