package br.com.saqz.webapp.groups

import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import kotlinx.coroutines.CoroutineScope

// STUB: substituir por <input type="file" accept="image/*"> (capture="environment" para a câmera),
// decodificação com orientação EXIF aplicada (createImageBitmap imageOrientation: "from-image"),
// recorte/encode em canvas e previews em memória. Referência: AndroidPhotoSelectionAdapter.kt,
// AndroidPhotoEncoder.kt e SaqzIOS/GroupsPhoto/IOSGroupPhotoAdapters.swift.
internal class WebGroupPhotoAdapters(private val scope: CoroutineScope) {
    val selection: GroupPhotoSelectionPort = object : GroupPhotoSelectionPort {
        override suspend fun chooseCamera(): GroupPhotoSelectionResult = GroupPhotoSelectionResult.Failed
        override suspend fun chooseLibrary(): GroupPhotoSelectionResult = GroupPhotoSelectionResult.Failed
        override fun cleanup(source: String) = Unit
    }

    val encoder: GroupPhotoEncoderPort = object : GroupPhotoEncoderPort {
        override suspend fun encode(source: String, crop: GroupPhotoCrop): GroupPhotoEncodingResult =
            GroupPhotoEncodingResult.Failed

        override fun cancel(source: String) = Unit
    }

    val previews: GroupPhotoPreviewPort = GroupPhotoPreviewPort { null }
}
