package br.com.saqz.access.presentation.login

import androidx.compose.runtime.Immutable
import br.com.saqz.access.presentation.UiText

@Immutable
data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: UiText? = null,
)

sealed interface LoginIntent {
    data class UpdateEmail(val value: String) : LoginIntent

    data class UpdatePassword(val value: String) : LoginIntent

    data object SubmitPasswordLogin : LoginIntent

    data object SubmitGoogleLogin : LoginIntent
}

/** Login exposes no one-off effects: sign-in flows through the shared session. */
sealed interface LoginEffect
