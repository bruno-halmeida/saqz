package br.com.saqz.androidapp.groups.photo

import android.content.Context
import androidx.activity.ComponentActivity
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.domain.photo.GroupPhotoSourceHandle
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import kotlinx.coroutines.CoroutineScope

internal data class AndroidGroupPhotoAdapters(
    val selection: AndroidPhotoSelectionAdapter,
    val encoder: AndroidPhotoEncoder,
    val previews: GroupPhotoPreviewPort,
) {
    fun attach(activity: ComponentActivity) = selection.attach(activity)

    companion object {
        fun create(context: Context, scope: CoroutineScope): AndroidGroupPhotoAdapters {
            val files = AndroidPhotoFiles(context)
            return AndroidGroupPhotoAdapters(
                AndroidPhotoSelectionAdapter(files, scope),
                AndroidPhotoEncoder(files),
                AndroidPhotoPreviewAdapter(files),
            )
        }
    }
}

internal class AndroidPhotoPreviewAdapter(private val files: AndroidPhotoFiles) : GroupPhotoPreviewPort {
    override fun read(preview: String): ByteArray? {
        val file = files.file(GroupPhotoSourceHandle(preview)) ?: return null
        if (file.length() !in 1..EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES) return null
        return runCatching { file.readBytes() }.getOrNull()
    }
}
