package br.com.saqz.androidapp.groups.photo

import android.content.Context
import androidx.activity.ComponentActivity
import br.com.saqz.groups.port.GroupPhotoCachePort
import kotlinx.coroutines.CoroutineScope
import java.io.File

internal data class AndroidGroupPhotoAdapters(
    val selection: AndroidPhotoSelectionAdapter,
    val encoder: AndroidPhotoEncoder,
    val cache: GroupPhotoCachePort,
) {
    fun attach(activity: ComponentActivity) = selection.attach(activity)

    companion object {
        fun create(context: Context, scope: CoroutineScope): AndroidGroupPhotoAdapters {
            val files = AndroidPhotoFiles(context)
            return AndroidGroupPhotoAdapters(
                AndroidPhotoSelectionAdapter(files, scope),
                AndroidPhotoEncoder(files),
                AndroidPhotoCache(context),
            )
        }
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
