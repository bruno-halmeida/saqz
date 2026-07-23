package br.com.saqz.access.presentation.namecompletion

import androidx.compose.runtime.Immutable
import br.com.saqz.access.presentation.AuthUiError

@Immutable
data class NameCompletionState(
    val name: String = "",
    val isLoading: Boolean = false,
    val error: AuthUiError? = null,
    val invalidName: Boolean = false,
)

sealed interface NameCompletionIntent {
    data class UpdateName(val value: String) : NameCompletionIntent

    data object Complete : NameCompletionIntent
}

/** Name completion exposes no one-off effects: session transitions flow through the shared session. */
sealed interface NameCompletionEffect
