package br.com.saqz.profile.presentation.photo

import androidx.compose.runtime.Immutable

@Immutable
internal data class ProfilePhotoState(
    val photoUrl: String? = null,
    val isLoading: Boolean = false,
    val error: ProfilePhotoError? = null,
)

internal sealed interface ProfilePhotoIntent {
    data object ChooseCamera : ProfilePhotoIntent
    data object ChooseLibrary : ProfilePhotoIntent
    data object Remove : ProfilePhotoIntent
    data object ClearError : ProfilePhotoIntent
}

/** Erros que a UI consegue traduzir sem depender de [br.com.saqz.domain.DataError]. */
sealed interface ProfilePhotoError {
    data object CameraPermissionDenied : ProfilePhotoError
    data object LibraryPermissionDenied : ProfilePhotoError
    data object SelectionFailed : ProfilePhotoError
    data object UploadFailed : ProfilePhotoError
    data object RemovalFailed : ProfilePhotoError
}
