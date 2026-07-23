package br.com.saqz.access.presentation.verification

import androidx.compose.runtime.Immutable
import br.com.saqz.access.presentation.AuthUiError

@Immutable
data class VerificationState(
    val email: String = "",
    val isLoading: Boolean = false,
    val error: AuthUiError? = null,
    val verificationSent: Boolean = false,
)

sealed interface VerificationIntent {
    data object Confirm : VerificationIntent

    data object Resend : VerificationIntent
}

/** Verification exposes no one-off effects: session transitions flow through the shared session. */
sealed interface VerificationEffect
