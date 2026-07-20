package br.com.saqz.androidapp.groups.photo

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import br.com.saqz.groups.port.EncodedGroupPhoto
import br.com.saqz.groups.port.GroupPhotoSourceHandle
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
        val target = createSource()
        try {
            context.contentResolver.openInputStream(uri)?.use { source -> copyBounded(source, target) }
                ?: error("selected item unavailable")
            target
        } catch (failure: Throwable) {
            target.delete()
            throw failure
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

    fun purgeOrphans(keep: File? = null) {
        directory.listFiles()?.filter { it != keep }?.forEach(File::delete)
    }

    private fun copyBounded(source: InputStream, target: File) {
        FileOutputStream(target).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                total += read
                require(total <= EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES)
                output.write(buffer, 0, read)
            }
        }
    }

    private companion object {
        const val MAX_DIMENSION = 4096
        val ACCEPTED_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
