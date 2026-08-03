package br.com.saqz.groups.presentation.gameeditor

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError

@Immutable
data class GameEditorState(
    val isLoading: Boolean,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
)

sealed interface GameEditorIntent {
    data object Retry : GameEditorIntent
}

sealed interface GameEditorEffect {
    data object Saved : GameEditorEffect
}
