package br.com.saqz.groups.presentation.gameeditor

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError

/**
 * 4 · editor de jogo. Scaffold do VUL-151: só carrega e mostra o estado; o formulário
 * real é VUL-153.
 *
 * `gameId` null sinaliza criação; presente, edição. O carregamento só acontece em
 * edição — criar começa vazio.
 */
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
