package br.com.saqz.groups.presentation.gamedetail

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError

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
