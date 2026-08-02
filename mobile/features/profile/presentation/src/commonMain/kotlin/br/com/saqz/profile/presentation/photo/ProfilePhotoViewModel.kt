package br.com.saqz.profile.presentation.photo

import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.profile.domain.ProfileGateway
import br.com.saqz.profile.domain.ProfilePhotoSelectionCallback
import br.com.saqz.profile.domain.ProfilePhotoSelectionCancelable
import br.com.saqz.profile.domain.ProfilePhotoSelectionPort
import br.com.saqz.profile.domain.ProfilePhotoSelectionResult
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

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

    private fun choose(
        open: (ProfilePhotoSelectionCallback) -> ProfilePhotoSelectionCancelable,
    ) {
        if (state.value.isLoading) return
        update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val selected = awaitSelection(open)) {
                is ProfilePhotoSelectionResult.Selected -> upload(selected)
                ProfilePhotoSelectionResult.Cancelled -> finish()
                ProfilePhotoSelectionResult.CameraPermissionDenied -> finish(ProfilePhotoError.CameraPermissionDenied)
                ProfilePhotoSelectionResult.LibraryPermissionDenied -> finish(ProfilePhotoError.LibraryPermissionDenied)
                ProfilePhotoSelectionResult.Failed -> finish(ProfilePhotoError.SelectionFailed)
            }
        }
    }

    private suspend fun awaitSelection(
        open: (ProfilePhotoSelectionCallback) -> ProfilePhotoSelectionCancelable,
    ): ProfilePhotoSelectionResult = suspendCancellableCoroutine { continuation ->
        lateinit var request: ProfilePhotoSelectionCancelable
        request = open(
            object : ProfilePhotoSelectionCallback {
                override fun complete(result: ProfilePhotoSelectionResult) {
                    if (continuation.isActive) continuation.resume(result)
                }
            },
        )
        continuation.invokeOnCancellation { request.cancel() }
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
