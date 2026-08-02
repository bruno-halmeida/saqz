package br.com.saqz.profile.presentation.exit

import androidx.compose.runtime.Immutable

sealed interface ProfileExitSheet {
    data object Exit : ProfileExitSheet

    data object ConfirmDelete : ProfileExitSheet
}

enum class ProfileExitError {
    EmailMismatch,
    DeleteFailed,
}

@Immutable
data class ProfileExitState(
    val email: String,
    val sheet: ProfileExitSheet = ProfileExitSheet.Exit,
    val confirmationEmail: String = "",
    val error: ProfileExitError? = null,
    val isDeleting: Boolean = false,
)

sealed interface ProfileExitIntent {
    data object OpenDeleteConfirmation : ProfileExitIntent

    data class UpdateConfirmationEmail(val value: String) : ProfileExitIntent

    data object ConfirmDelete : ProfileExitIntent
}

sealed interface ProfileExitEffect {
    data object AccountDeleted : ProfileExitEffect
}
