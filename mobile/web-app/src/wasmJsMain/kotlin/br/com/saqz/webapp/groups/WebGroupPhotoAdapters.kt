package br.com.saqz.webapp.groups

import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoByteSource
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoMediaType
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelection
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.groups.domain.photo.GroupPhotoSourceHandle
import br.com.saqz.webapp.interop.PickedImage
import br.com.saqz.webapp.interop.encodeImageJpeg
import br.com.saqz.webapp.interop.pickImage
import br.com.saqz.webapp.interop.releaseImage
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Escolha, recorte e preview de foto no navegador: o espelho de `AndroidGroupPhotoAdapters` e de
 * `IOSGroupPhotoAdapters`, com `<input type="file">` no lugar da câmera/galeria nativas e canvas
 * no lugar do Bitmap/ImageIO. O arquivo escolhido fica do lado JS (ver `interop/ImageFiles.kt`);
 * aqui ficam só as dimensões e os bytes do preview, que `GroupPhotoPreviewPort.read` devolve de
 * forma síncrona.
 *
 * O handle de origem e o de preview são o mesmo id, como no Android (nome do arquivo no cache).
 */
internal class WebGroupPhotoAdapters {
    private val store = WebPhotoStore()

    val selection: GroupPhotoSelectionPort = WebPhotoSelectionPort(store)
    val encoder: GroupPhotoEncoderPort = WebPhotoEncoder(store)
    val previews: GroupPhotoPreviewPort = GroupPhotoPreviewPort { handle -> store.preview(handle) }
}

private class WebPickedPhoto(val width: Int, val height: Int, val preview: ByteArray)

private class WebPhotoStore {
    private val photos = mutableMapOf<String, WebPickedPhoto>()

    fun put(id: String, photo: WebPickedPhoto) {
        photos[id] = photo
    }

    fun get(id: String): WebPickedPhoto? = photos[id]

    fun preview(handle: String): ByteArray? = photos[handle]?.preview?.takeIf { it.isNotEmpty() }

    fun remove(id: String) {
        photos.remove(id)
        releaseImage(id)
    }
}

private class WebPhotoSelectionPort(private val store: WebPhotoStore) : GroupPhotoSelectionPort {
    // Um pedido por vez, como o AndroidPhotoRequestTracker: o segundo falha em vez de abrir outro seletor.
    private var picking = false

    override suspend fun chooseCamera(): GroupPhotoSelectionResult = choose(camera = true)

    override suspend fun chooseLibrary(): GroupPhotoSelectionResult = choose(camera = false)

    override fun cleanup(source: String) = store.remove(source)

    // O navegador não informa permissão negada da câmera (o `capture` só pede ao sistema para abrir
    // a câmera): quem nega ou fecha o seletor cai em Cancelled, nunca em CameraPermissionDenied.
    private suspend fun choose(camera: Boolean): GroupPhotoSelectionResult {
        if (picking) return GroupPhotoSelectionResult.Failed
        picking = true
        try {
            return when (val picked = pickImage(camera)) {
                PickedImage.Cancelled -> GroupPhotoSelectionResult.Cancelled
                PickedImage.Failed -> GroupPhotoSelectionResult.Failed
                is PickedImage.Selected -> selected(picked)
            }
        } finally {
            picking = false
        }
    }

    private fun selected(picked: PickedImage.Selected): GroupPhotoSelectionResult {
        store.put(picked.id, WebPickedPhoto(picked.width, picked.height, picked.preview))
        return GroupPhotoSelectionResult.Selected(
            GroupPhotoSelection(
                GroupPhotoSourceHandle(picked.id),
                GroupPhotoPreviewHandle(picked.id),
                picked.width,
                picked.height,
            ),
        )
    }
}

private class WebPhotoEncoder(private val store: WebPhotoStore) : GroupPhotoEncoderPort {
    override suspend fun encode(source: String, crop: GroupPhotoCrop): GroupPhotoEncodingResult {
        val photo = store.get(source) ?: return GroupPhotoEncodingResult.Failed
        val region = WebSquareCrop.calculate(photo.width, photo.height, crop)
        val bytes = encodeImageJpeg(
            source,
            region.left,
            region.top,
            region.side,
            JPEG_QUALITIES,
            EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES,
        )
        if (bytes == null || bytes.size.toLong() > EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES) {
            return GroupPhotoEncodingResult.Failed
        }
        return GroupPhotoEncodingResult.Encoded(
            EncodedGroupPhoto(GroupPhotoMediaType.JPEG, bytes.size.toLong(), GroupPhotoByteSource { bytes }),
        )
    }

    // Android e iOS também deixam isto vazio: cancelar a coroutine do encode já aborta o trabalho
    // do lado JS, e quem desiste do envio chama `cleanup`, que solta o arquivo.
    override fun cancel(source: String) = Unit

    private companion object {
        // Mesma escada do Android e do iOS (92/85/75/65) até caber em MAX_GROUP_PHOTO_BYTES.
        val JPEG_QUALITIES = listOf(92, 85, 75, 65)
    }
}

internal data class WebPixelCrop(val left: Int, val top: Int, val side: Int)

/** Mesma conta do `AndroidSquareCrop`: o lado é o menor eixo dividido pelo zoom, e o quadrado nunca sai da imagem. */
internal object WebSquareCrop {
    fun calculate(width: Int, height: Int, crop: GroupPhotoCrop): WebPixelCrop {
        require(width > 0 && height > 0)
        val shortest = min(width, height)
        val side = (shortest / crop.zoom).roundToInt().coerceIn(1, shortest)
        val left = (crop.centerX * width - side / 2f).roundToInt().coerceIn(0, width - side)
        val top = (crop.centerY * height - side / 2f).roundToInt().coerceIn(0, height - side)
        return WebPixelCrop(left, top, side)
    }
}
