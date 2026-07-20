package br.com.saqz.androidapp.groups.photo

import android.content.Context
import androidx.activity.ComponentActivity
import br.com.saqz.groups.port.GroupPhotoCachePort
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.port.GroupPhotoPreviewPort
import br.com.saqz.groups.port.GroupPhotoSourceHandle
import br.com.saqz.groups.port.EncodedGroupPhoto
import kotlinx.coroutines.CoroutineScope
import java.io.File

internal data class AndroidGroupPhotoAdapters(
    val selection: AndroidPhotoSelectionAdapter,
    val encoder: AndroidPhotoEncoder,
    val cache: GroupPhotoCachePort,
    val previews: GroupPhotoPreviewPort,
) {
    fun attach(activity: ComponentActivity) = selection.attach(activity)

    companion object {
        fun create(context: Context, scope: CoroutineScope): AndroidGroupPhotoAdapters {
            val files = AndroidPhotoFiles(context)
            return AndroidGroupPhotoAdapters(
                AndroidPhotoSelectionAdapter(files, scope),
                AndroidPhotoEncoder(files),
                AndroidPhotoCache(context),
                AndroidPhotoPreviewAdapter(files),
            )
        }
    }
}

internal class AndroidPhotoPreviewAdapter(private val files: AndroidPhotoFiles) : GroupPhotoPreviewPort {
    override fun read(preview: GroupPhotoPreviewHandle): ByteArray? {
        val file = files.file(GroupPhotoSourceHandle(preview.value)) ?: return null
        if (file.length() !in 1..EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES) return null
        return runCatching { file.readBytes() }.getOrNull()
    }
}

private class AndroidPhotoCache(context: Context) : GroupPhotoCachePort {
    private val directory = File(context.cacheDir, "group-photo-cache")
    override fun evict(groupId: String) {
        directory.listFiles()?.filter { it.name.startsWith("${groupId.hashCode()}-") }?.forEach(File::delete)
    }
    override fun clearAll() {
        directory.listFiles()?.forEach(File::delete)
    }
}
