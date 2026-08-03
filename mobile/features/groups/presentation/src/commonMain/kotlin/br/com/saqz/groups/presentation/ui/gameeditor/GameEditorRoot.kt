package br.com.saqz.groups.presentation.ui.gameeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.gameeditor.GameEditorEffect
import br.com.saqz.groups.presentation.gameeditor.GameEditorViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 4 · editor de jogo. [groupId] vem da rota; [gameId] null cria, presente edita.
 * O efeito `Saved` volta à tela anterior — o formulário real é VUL-153.
 */
@Composable
fun GameEditorRoot(
    groupId: String,
    gameId: String?,
    onBack: () -> Unit,
    viewModel: GameEditorViewModel = koinViewModel(parameters = { parametersOf(groupId, gameId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            GameEditorEffect.Saved -> onBack()
        }
    }
    GameEditorScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
