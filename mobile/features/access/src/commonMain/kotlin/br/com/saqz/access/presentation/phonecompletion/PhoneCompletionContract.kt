package br.com.saqz.access.presentation.phonecompletion

import androidx.compose.runtime.Immutable
import br.com.saqz.access.presentation.AuthUiError

@Immutable
data class PhoneCompletionState(
    val phone: String = "",
    val isLoading: Boolean = false,
    val error: AuthUiError? = null,
    val invalidPhone: Boolean = false,
)

sealed interface PhoneCompletionIntent {
    data class UpdatePhone(val value: String) : PhoneCompletionIntent

    data object Complete : PhoneCompletionIntent
}

/** Phone completion exposes no one-off effects: session transitions flow through the shared session. */
sealed interface PhoneCompletionEffect
