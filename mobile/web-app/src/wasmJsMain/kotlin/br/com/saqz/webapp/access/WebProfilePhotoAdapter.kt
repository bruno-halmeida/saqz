package br.com.saqz.webapp.access

import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeProfilePhotoPort
import br.com.saqz.access.domain.port.ProfilePhotoCallback
import br.com.saqz.access.domain.port.ProfilePhotoResult
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.profile.domain.ProfilePhotoSelectionCallback
import br.com.saqz.profile.domain.ProfilePhotoSelectionCancelable
import br.com.saqz.profile.domain.ProfilePhotoSelectionPort
import br.com.saqz.profile.domain.ProfilePhotoSelectionResult
import br.com.saqz.webapp.groups.WebGroupPhotoAdapters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A foto de perfil não tem pilha própria: quem escolhe e quem recorta é o `WebGroupPhotoAdapters`
 * (a mesma divisão do `AndroidProfilePhotoAdapter`). Aqui só se traduz `suspend` em callback e se
 * solta o arquivo de origem, que o acesso não usa: o envio é por bytes.
 *
 * Sem etapa de permissão: no navegador quem pede acesso à câmera é o próprio sistema, ao abrir o
 * seletor, e uma recusa chega como seleção cancelada.
 */
internal class WebProfilePhotoAdapter(
    private val photos: WebGroupPhotoAdapters,
    private val scope: CoroutineScope,
) : NativeProfilePhotoPort, ProfilePhotoSelectionPort {
    override fun chooseCamera(done: ProfilePhotoCallback): Cancelable =
        launching { done.complete(prepared(photos.selection::chooseCamera).forAccess()) }

    override fun chooseLibrary(done: ProfilePhotoCallback): Cancelable =
        launching { done.complete(prepared(photos.selection::chooseLibrary).forAccess()) }

    override fun chooseCamera(done: ProfilePhotoSelectionCallback): ProfilePhotoSelectionCancelable =
        launching { done.complete(prepared(photos.selection::chooseCamera)) }

    override fun chooseLibrary(done: ProfilePhotoSelectionCallback): ProfilePhotoSelectionCancelable =
        launching { done.complete(prepared(photos.selection::chooseLibrary)) }

    private fun launching(block: suspend () -> Unit): JobCancelable = JobCancelable(scope.launch { block() })

    private suspend fun prepared(open: suspend () -> GroupPhotoSelectionResult): ProfilePhotoSelectionResult =
        when (val selected = open()) {
            is GroupPhotoSelectionResult.Selected -> encoded(selected.value.source.value)
            GroupPhotoSelectionResult.Cancelled -> ProfilePhotoSelectionResult.Cancelled
            GroupPhotoSelectionResult.CameraPermissionDenied -> ProfilePhotoSelectionResult.CameraPermissionDenied
            GroupPhotoSelectionResult.LibraryPermissionDenied -> ProfilePhotoSelectionResult.LibraryPermissionDenied
            GroupPhotoSelectionResult.Failed -> ProfilePhotoSelectionResult.Failed
        }

    // O `finally` também cobre a desistência da tela: cancelar durante a codificação ainda solta a origem.
    private suspend fun encoded(source: String): ProfilePhotoSelectionResult = try {
        when (val result = photos.encoder.encode(source, CENTERED)) {
            is GroupPhotoEncodingResult.Encoded -> selected(result.value)
            GroupPhotoEncodingResult.Failed -> ProfilePhotoSelectionResult.Failed
        }
    } finally {
        photos.selection.cleanup(source)
    }

    private fun selected(photo: EncodedGroupPhoto): ProfilePhotoSelectionResult {
        val bytes = photo.source.read()
        return if (bytes.isEmpty()) {
            ProfilePhotoSelectionResult.Failed
        } else {
            ProfilePhotoSelectionResult.Selected(bytes, photo.mediaType.value)
        }
    }

    // O acesso não distingue as recusas de permissão: no Android elas também viram Failed.
    private fun ProfilePhotoSelectionResult.forAccess(): ProfilePhotoResult = when (this) {
        is ProfilePhotoSelectionResult.Selected -> ProfilePhotoResult.Selected(bytes, mediaType)
        ProfilePhotoSelectionResult.Cancelled -> ProfilePhotoResult.Cancelled
        ProfilePhotoSelectionResult.CameraPermissionDenied,
        ProfilePhotoSelectionResult.LibraryPermissionDenied,
        ProfilePhotoSelectionResult.Failed,
        -> ProfilePhotoResult.Failed
    }

    // As duas portas têm um `cancel()` com a mesma assinatura: uma implementação serve às duas.
    private class JobCancelable(private val job: Job) : Cancelable, ProfilePhotoSelectionCancelable {
        override fun cancel() = job.cancel()
    }

    private companion object {
        // Recorte central sem zoom, como o Android (`CENTERED`) e o iOS usam para o avatar.
        val CENTERED = GroupPhotoCrop()
    }
}
