package br.com.saqz.groups.presentation.photo

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
data class GroupPhotoState(
    val photoUrl: String? = null,
    val preview: ImageBitmap? = null,
    val isLoading: Boolean = false,
    val error: GroupPhotoUiError? = null,
    val changeVersion: Int = 0,
    val hasPending: Boolean = false,
)

sealed interface GroupPhotoIntent {
    data class BindGroup(val groupId: String?) : GroupPhotoIntent

    data object ChooseCamera : GroupPhotoIntent

    data object ChooseLibrary : GroupPhotoIntent

    data object Remove : GroupPhotoIntent

    data object ClearError : GroupPhotoIntent

    /** Envia a foto retida depois do create/save do perfil, com a versão fresca. */
    data object Commit : GroupPhotoIntent
}

sealed interface GroupPhotoUiError {
    data object CameraPermissionDenied : GroupPhotoUiError
    data object LibraryPermissionDenied : GroupPhotoUiError
    data object SelectionFailed : GroupPhotoUiError
    data object EncodingFailed : GroupPhotoUiError
    data object LoadFailed : GroupPhotoUiError
    data object UploadFailed : GroupPhotoUiError
    data object RemovalFailed : GroupPhotoUiError
    data object TargetUnavailable : GroupPhotoUiError
}
