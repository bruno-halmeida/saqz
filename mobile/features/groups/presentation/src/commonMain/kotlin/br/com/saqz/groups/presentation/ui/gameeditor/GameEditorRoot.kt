package br.com.saqz.groups.presentation.ui.gameeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.gameeditor.GameEditorEffect
import br.com.saqz.groups.presentation.gameeditor.GameEditorViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun GameEditorRoot(
    groupId: String,
    gameId: String?,
    onBack: () -> Unit,
    onOpenGameDetail: (String) -> Unit,
    viewModel: GameEditorViewModel = koinViewModel(parameters = { parametersOf(groupId, gameId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            GameEditorEffect.Saved -> onBack()
            is GameEditorEffect.OpenGameDetail -> onOpenGameDetail(effect.gameId)
        }
    }
    GameEditorScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
