package br.com.saqz.groups.presentation.ui.gameeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.gameeditor.GameEditorEffect
import br.com.saqz.groups.presentation.gameeditor.GameEditorViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.random.Random

@Composable
fun GameEditorRoot(
    groupId: String,
    gameId: String?,
    onBack: () -> Unit,
    onOpenGameDetail: (String) -> Unit,
    onSave: () -> Unit = {},
    viewModel: GameEditorViewModel? = null,
) {
    val instanceKey = rememberSaveable(groupId, gameId) {
        "game-editor-$groupId-${gameId ?: "create"}-${Random.nextLong()}"
    }
    val resolvedViewModel = viewModel ?: koinViewModel(
        key = instanceKey,
        parameters = { parametersOf(groupId, gameId) },
    )
    val state by resolvedViewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(resolvedViewModel.effects) { effect ->
        when (effect) {
            GameEditorEffect.Saved -> {
                onSave()
                onBack()
            }
            is GameEditorEffect.OpenGameDetail -> onOpenGameDetail(effect.gameId)
        }
    }
    GameEditorScreen(state = state, onBack = onBack, onIntent = resolvedViewModel::onIntent)
}
