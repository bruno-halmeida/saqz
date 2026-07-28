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
) : NativeProfilePhotoPort {
    override fun chooseCamera(done: ProfilePhotoCallback): Cancelable = choose(done, selection::chooseCamera)

    override fun chooseLibrary(done: ProfilePhotoCallback): Cancelable = choose(done, selection::chooseLibrary)

    private fun choose(done: ProfilePhotoCallback, open: suspend () -> GroupPhotoSelectionResult): Cancelable =
        JobCancelable(scope.launch { done.complete(chosen(open())) })

    private suspend fun chosen(selected: GroupPhotoSelectionResult): ProfilePhotoResult = when (selected) {
        is GroupPhotoSelectionResult.Selected -> encoded(selected.value.source.value)
        GroupPhotoSelectionResult.Cancelled -> ProfilePhotoResult.Cancelled
        GroupPhotoSelectionResult.Failed -> ProfilePhotoResult.Failed
    }

    // O `finally` também cobre a desistência da tela: cancelar durante a codificação ainda
    // apaga a origem.
    private suspend fun encoded(source: String): ProfilePhotoResult = try {
        when (val result = encoder.encode(source, CENTERED)) {
            is GroupPhotoEncodingResult.Encoded -> selected(result.value)
            GroupPhotoEncodingResult.Failed -> ProfilePhotoResult.Failed
        }
    } finally {
        selection.cleanup(source)
    }

    private fun selected(photo: EncodedGroupPhoto): ProfilePhotoResult {
        val bytes = photo.source.read()
        return if (bytes.isEmpty()) ProfilePhotoResult.Failed
        else ProfilePhotoResult.Selected(bytes, photo.mediaType.value)
    }

    private class JobCancelable(private val job: Job) : Cancelable {
        override fun cancel() = job.cancel()
    }

    private companion object {
        val CENTERED = GroupPhotoCrop()
    }
}
