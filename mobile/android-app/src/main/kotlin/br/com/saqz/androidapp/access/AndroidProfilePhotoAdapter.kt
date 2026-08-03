package br.com.saqz.androidapp.access

import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeProfilePhotoPort
import br.com.saqz.access.domain.port.ProfilePhotoCallback
import br.com.saqz.access.domain.port.ProfilePhotoResult
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.profile.domain.ProfilePhotoSelectionCallback
import br.com.saqz.profile.domain.ProfilePhotoSelectionCancelable
import br.com.saqz.profile.domain.ProfilePhotoSelectionPort
import br.com.saqz.profile.domain.ProfilePhotoSelectionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A foto de perfil não tem pilha própria: quem escolhe é o `AndroidPhotoSelectionAdapter`
 * (com o tracker que sobrevive à morte do processo) e quem recorta e recodifica é o
 * `AndroidPhotoEncoder`. Aqui só se traduz `suspend` em callback — a fronteira nativa não
 * aceita `suspend` — e se apaga o arquivo de origem, que o acesso não usa: o envio é por bytes.
 */
internal class AndroidProfilePhotoAdapter(
    private val selection: GroupPhotoSelectionPort,
    private val encoder: GroupPhotoEncoderPort,
    private val scope: CoroutineScope,
    private val permissions: AndroidProfilePhotoPermissions = AndroidProfilePhotoPermissions.Allowed,
) : NativeProfilePhotoPort, ProfilePhotoSelectionPort {
    override fun chooseCamera(done: ProfilePhotoCallback): Cancelable = choose(done, selection::chooseCamera)

    override fun chooseLibrary(done: ProfilePhotoCallback): Cancelable = choose(done, selection::chooseLibrary)

    override fun chooseCamera(done: ProfilePhotoSelectionCallback): ProfilePhotoSelectionCancelable =
        chooseProfile(
            done,
            selection::chooseCamera,
            { permissions.cameraGranted() },
            ProfilePhotoSelectionResult.CameraPermissionDenied,
        )

    override fun chooseLibrary(done: ProfilePhotoSelectionCallback): ProfilePhotoSelectionCancelable =
        chooseProfile(
            done,
            selection::chooseLibrary,
            { permissions.libraryGranted() },
            ProfilePhotoSelectionResult.LibraryPermissionDenied,
        )

    private fun choose(done: ProfilePhotoCallback, open: suspend () -> GroupPhotoSelectionResult): Cancelable =
        JobCancelable(scope.launch { done.complete(chosen(open())) })

    private suspend fun chosen(selected: GroupPhotoSelectionResult): ProfilePhotoResult = when (selected) {
        is GroupPhotoSelectionResult.Selected -> when (val result = encoded(selected.value.source.value)) {
            is ProfilePhotoSelectionResult.Selected ->
                ProfilePhotoResult.Selected(result.bytes, result.mediaType)
            ProfilePhotoSelectionResult.Cancelled -> ProfilePhotoResult.Cancelled
            ProfilePhotoSelectionResult.CameraPermissionDenied,
            ProfilePhotoSelectionResult.LibraryPermissionDenied,
            ProfilePhotoSelectionResult.Failed,
            -> ProfilePhotoResult.Failed
        }
        GroupPhotoSelectionResult.Cancelled -> ProfilePhotoResult.Cancelled
        GroupPhotoSelectionResult.CameraPermissionDenied -> ProfilePhotoResult.Failed
        GroupPhotoSelectionResult.LibraryPermissionDenied -> ProfilePhotoResult.Failed
        GroupPhotoSelectionResult.Failed -> ProfilePhotoResult.Failed
    }

    private fun chooseProfile(
        done: ProfilePhotoSelectionCallback,
        open: suspend () -> GroupPhotoSelectionResult,
        permissionGranted: () -> Boolean,
        denied: ProfilePhotoSelectionResult,
    ): ProfilePhotoSelectionCancelable {
        if (!permissionGranted()) {
            done.complete(denied)
            return ProfileRequestCancelable(Job())
        }
        val job = scope.launch { done.complete(chosenProfile(open())) }
        return ProfileRequestCancelable(job)
    }

    private suspend fun chosenProfile(selected: GroupPhotoSelectionResult): ProfilePhotoSelectionResult = when (selected) {
        is GroupPhotoSelectionResult.Selected -> encoded(selected.value.source.value)
        GroupPhotoSelectionResult.Cancelled -> ProfilePhotoSelectionResult.Cancelled
        GroupPhotoSelectionResult.CameraPermissionDenied -> ProfilePhotoSelectionResult.CameraPermissionDenied
        GroupPhotoSelectionResult.LibraryPermissionDenied -> ProfilePhotoSelectionResult.LibraryPermissionDenied
        GroupPhotoSelectionResult.Failed -> ProfilePhotoSelectionResult.Failed
    }

    // O `finally` também cobre a desistência da tela: cancelar durante a codificação ainda
    // apaga a origem.
    private suspend fun encoded(source: String): ProfilePhotoSelectionResult = try {
        when (val result = encoder.encode(source, CENTERED)) {
            is GroupPhotoEncodingResult.Encoded -> selected(result.value)
            GroupPhotoEncodingResult.Failed -> ProfilePhotoSelectionResult.Failed
        }
    } finally {
        selection.cleanup(source)
    }

    private fun selected(photo: EncodedGroupPhoto): ProfilePhotoSelectionResult {
        val bytes = photo.source.read()
        return if (bytes.isEmpty()) ProfilePhotoSelectionResult.Failed
        else ProfilePhotoSelectionResult.Selected(bytes, photo.mediaType.value)
    }

    private class JobCancelable(private val job: Job) : Cancelable {
        override fun cancel() = job.cancel()
    }

    private class ProfileRequestCancelable(private val job: Job) : ProfilePhotoSelectionCancelable {
        override fun cancel() = job.cancel()
    }

    private companion object {
        val CENTERED = GroupPhotoCrop()
    }
}

internal interface AndroidProfilePhotoPermissions {
    fun cameraGranted(): Boolean
    fun libraryGranted(): Boolean

    data object Allowed : AndroidProfilePhotoPermissions {
        override fun cameraGranted() = true
        override fun libraryGranted() = true
    }
}
