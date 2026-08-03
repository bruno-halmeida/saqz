package br.com.saqz.groups.presentation.gamedetail

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError

/**
 * 4 · detalhe do jogo. Scaffold do VUL-151: carrega e mostra o estado. O conteúdo
 * real é VUL-154.
 */
@Immutable
data class GameDetailState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
)

sealed interface GameDetailIntent {
    data object Retry : GameDetailIntent
}

sealed interface GameDetailEffect {
    data object OpenEditor : GameDetailEffect
}
