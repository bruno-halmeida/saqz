package br.com.saqz.access.presentation.passwordreset

import androidx.compose.runtime.Immutable

@Immutable
data class PasswordResetState(
    val email: String = "",
    val isLoading: Boolean = false,
    val resetConfirmation: Boolean = false,
    val validationAttempted: Boolean = false,
)

sealed interface PasswordResetIntent {
    data class UpdateEmail(val value: String) : PasswordResetIntent

    data object SubmitPasswordReset : PasswordResetIntent

    data object ShowLogin : PasswordResetIntent
}

/** Password reset exposes no one-off effects: navigation and reset flow through the shared session. */
sealed interface PasswordResetEffect
