package br.com.saqz.profile.presentation.photo

import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.profile.domain.ProfileGateway
import br.com.saqz.profile.domain.ProfilePhotoSelectionPort
import br.com.saqz.profile.domain.ProfilePhotoSelectionResult
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

internal class ProfilePhotoViewModel(
    private val gateway: ProfileGateway,
    private val selection: ProfilePhotoSelectionPort,
    initialPhotoUrl: String? = null,
) : MviViewModel<ProfilePhotoState, ProfilePhotoIntent, Nothing>(
    ProfilePhotoState(photoUrl = initialPhotoUrl),
) {
    override fun onIntent(intent: ProfilePhotoIntent) {
        when (intent) {
            ProfilePhotoIntent.ChooseCamera -> choose(selection::chooseCamera)
            ProfilePhotoIntent.ChooseLibrary -> choose(selection::chooseLibrary)
            ProfilePhotoIntent.Remove -> remove()
            ProfilePhotoIntent.ClearError -> update { it.copy(error = null) }
        }
    }

    private fun choose(open: suspend () -> ProfilePhotoSelectionResult) {
        if (state.value.isLoading) return
        update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val selected = open()) {
                is ProfilePhotoSelectionResult.Selected -> upload(selected)
                ProfilePhotoSelectionResult.Cancelled -> finish()
                ProfilePhotoSelectionResult.CameraPermissionDenied -> finish(ProfilePhotoError.CameraPermissionDenied)
                ProfilePhotoSelectionResult.LibraryPermissionDenied -> finish(ProfilePhotoError.LibraryPermissionDenied)
                ProfilePhotoSelectionResult.Failed -> finish(ProfilePhotoError.SelectionFailed)
            }
        }
    }

    private suspend fun upload(selected: ProfilePhotoSelectionResult.Selected) {
        when (gateway.uploadPhoto(selected.bytes, selected.mediaType)) {
            is SaqzResult.Failure -> finish(ProfilePhotoError.UploadFailed)
            is SaqzResult.Success -> when (val refreshed = gateway.bootstrap()) {
                is SaqzResult.Failure -> finish(ProfilePhotoError.UploadFailed)
                is SaqzResult.Success -> update {
                    it.copy(photoUrl = refreshed.value.user.photoUrl, isLoading = false, error = null)
                }
            }
        }
    }

    private fun remove() {
        if (state.value.isLoading) return
        update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (gateway.deletePhoto()) {
                is SaqzResult.Failure -> finish(ProfilePhotoError.RemovalFailed)
                is SaqzResult.Success -> update { it.copy(photoUrl = null, isLoading = false, error = null) }
            }
        }
    }

    private fun finish(error: ProfilePhotoError? = null) {
        update { it.copy(isLoading = false, error = error) }
    }
}
