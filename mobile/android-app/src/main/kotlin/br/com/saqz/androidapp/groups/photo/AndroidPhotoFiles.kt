package br.com.saqz.androidapp.groups.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoSourceHandle
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

internal data class AndroidPhotoMetadata(val width: Int, val height: Int, val mimeType: String)

internal class AndroidPhotoFiles(private val context: Context) {
    private val directory = File(context.cacheDir, "group-photos")

    fun createSource(): File {
        directory.mkdirs()
        return File(directory, "source-${UUID.randomUUID()}.img").also { it.createNewFile() }
    }

    fun copySelected(uri: Uri): File? = runCatching {
        val original = createSource()
        try {
            context.contentResolver.openInputStream(uri)?.use { source -> copyBounded(source, original) }
                ?: error("selected item unavailable")
            reduce(original)
        } finally {
            original.delete()
        }
    }.getOrNull()

    /**
     * A original vem como o celular salva (HEIC, 50 ou 200 MP, até 50 MB) e nunca é decodificada
     * na resolução cheia: lê com `inSampleSize`, aplica a rotação do EXIF e guarda um JPEG de até
     * [MAX_DIMENSION]. Os limites de [metadata] valem sobre esse resultado. A câmera passa a
     * original por aqui também; o arquivo dela não tem o limite de bytes da saída.
     */
    fun reduce(original: File): File? = runCatching {
        require(original.length() in 1..MAX_SOURCE_BYTES)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(original.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        }
        val sampled = requireNotNull(BitmapFactory.decodeFile(original.path, options))
        val upright = upright(sampled, original)
        val target = createSource()
        try {
            val bytes = requireNotNull(encodeJpegBounded(upright))
            target.writeBytes(bytes)
            target
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        } finally {
            upright.recycle()
        }
    }.getOrNull()

    fun handle(file: File) = GroupPhotoSourceHandle(file.name)

    fun file(handle: GroupPhotoSourceHandle): File? {
        val candidate = File(directory, handle.value)
        return candidate.takeIf { it.parentFile?.canonicalFile == directory.canonicalFile && it.isFile }
    }

    fun previewUri(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.group-photo-files",
        file,
    )

    fun metadata(file: File): AndroidPhotoMetadata? {
        if (!file.isFile || file.length() !in 1..EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        val mime = options.outMimeType ?: return null
        return AndroidPhotoMetadata(options.outWidth, options.outHeight, mime).takeIf {
            it.width in 1..MAX_DIMENSION && it.height in 1..MAX_DIMENSION && mime in ACCEPTED_TYPES
        }
    }

    fun remove(handle: GroupPhotoSourceHandle) {
        file(handle)?.delete()
    }

    fun remove(file: File?) {
        file?.takeIf { it.parentFile?.canonicalFile == directory.canonicalFile }?.delete()
    }

    fun purge() {
        directory.listFiles()?.forEach(File::delete)
    }

    // `attach()` da Activity re-registra os launchers e chamava um purge total.
    // Isso apagava a foto já escolhida (rotação, câmera, recriação) e o PUT
    // falhava porque o arquivo local sumia. Só o stub de 0 bytes da câmera
    // abandonada é lixo — o restante fica até o `cleanup` depois do upload.
    fun purgeOrphans(keep: File? = null) {
        directory.listFiles()
            ?.filter { it != keep && it.length() == 0L }
            ?.forEach(File::delete)
    }

    private fun upright(bitmap: Bitmap, original: File): Bitmap {
        val degrees = runCatching { ExifInterface(original.path).rotationDegrees() }.getOrDefault(0)
        val scale = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (degrees == 0 && scale >= 1f) return bitmap
        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
            if (scale < 1f) postScale(scale, scale)
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun ExifInterface.rotationDegrees(): Int =
        when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }

    private fun copyBounded(source: InputStream, target: File) {
        FileOutputStream(target).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_SOURCE_BYTES)
                output.write(buffer, 0, read)
            }
        }
    }

    internal companion object {
        const val MAX_DIMENSION = 4096
        const val MAX_SOURCE_BYTES = 50L * 1024 * 1024
        val ACCEPTED_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}

/** Menor potência de 2 que deixa o lado maior decodificado em até [target]. */
internal fun sampleSize(width: Int, height: Int, target: Int): Int {
    var sample = 1
    while (maxOf(width, height) / sample > target) sample *= 2
    return sample
}

internal fun encodeJpegBounded(bitmap: Bitmap): ByteArray? {
    for (quality in listOf(92, 85, 75, 65)) {
        val output = java.io.ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) continue
        if (output.size().toLong() <= EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES) return output.toByteArray()
    }
    return null
}
