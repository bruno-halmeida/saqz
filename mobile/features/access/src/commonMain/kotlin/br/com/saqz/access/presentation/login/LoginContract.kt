package br.com.saqz.access.presentation.login

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.text.UiText

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

    data object ShowRegistration : LoginIntent

    data object ShowPasswordReset : LoginIntent
}

/** Login exposes no one-off effects: screen changes and sign-in flow through the shared session. */
sealed interface LoginEffect
