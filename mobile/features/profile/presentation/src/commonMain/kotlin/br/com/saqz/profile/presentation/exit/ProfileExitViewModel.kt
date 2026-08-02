package br.com.saqz.profile.presentation.exit

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.profile.domain.ProfileGateway
import kotlinx.coroutines.launch

class ProfileExitViewModel(
    private val gateway: ProfileGateway,
    email: String,
) : MviViewModel<ProfileExitState, ProfileExitIntent, ProfileExitEffect>(
    initialState = ProfileExitState(email = email),
) {
    override fun onIntent(intent: ProfileExitIntent) {
        when (intent) {
            ProfileExitIntent.OpenDeleteConfirmation -> openDeleteConfirmation()
            is ProfileExitIntent.UpdateConfirmationEmail -> updateConfirmationEmail(intent.value)
            ProfileExitIntent.ConfirmDelete -> confirmDelete()
        }
    }

    private fun openDeleteConfirmation() {
        if (state.value.sheet != ProfileExitSheet.Exit || state.value.isDeleting) return
        update {
            it.copy(
                sheet = ProfileExitSheet.ConfirmDelete,
                error = null,
            )
        }
    }

    private fun updateConfirmationEmail(value: String) {
        if (state.value.sheet != ProfileExitSheet.ConfirmDelete || state.value.isDeleting) return
        update { it.copy(confirmationEmail = value, error = null) }
    }

    private fun confirmDelete() {
        val current = state.value
        if (current.sheet != ProfileExitSheet.ConfirmDelete || current.isDeleting) return

        if (!deletionEmailMatches(current.email, current.confirmationEmail)) {
            update { it.copy(error = ProfileExitError.EmailMismatch) }
            return
        }

        update { it.copy(error = null, isDeleting = true) }
        viewModelScope.launch {
            when (gateway.deleteSession()) {
                is SaqzResult.Success -> {
                    update { it.copy(isDeleting = false) }
                    emit(ProfileExitEffect.AccountDeleted)
                }

                is SaqzResult.Failure -> update {
                    it.copy(
                        error = ProfileExitError.DeleteFailed,
                        isDeleting = false,
                    )
                }
            }
        }
    }
}
