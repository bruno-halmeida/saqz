package br.com.saqz.access.presentation.registration

import androidx.compose.runtime.Immutable
import br.com.saqz.access.presentation.AuthUiError

@Immutable
data class RegistrationState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: AuthUiError? = null,
    val validationAttempted: Boolean = false,
)

sealed interface RegistrationIntent {
    data class UpdateName(val value: String) : RegistrationIntent

    data class UpdateEmail(val value: String) : RegistrationIntent

    data class UpdatePassword(val value: String) : RegistrationIntent

    data object SubmitRegistration : RegistrationIntent

    data object SubmitGoogleLogin : RegistrationIntent

    data object ShowLogin : RegistrationIntent
}

/** Registration exposes no one-off effects: navigation and sign-up flow through the shared session. */
sealed interface RegistrationEffect
