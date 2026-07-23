package br.com.saqz.androidapp.groups.photo

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.domain.photo.GroupPhotoSelection
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.groups.domain.photo.GroupPhotoSourceHandle
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal class AndroidPhotoSelectionAdapter(
    private val files: AndroidPhotoFiles,
    private val scope: CoroutineScope,
) : GroupPhotoSelectionPort {
    private val tracker = AndroidPhotoRequestTracker()
    private var continuation: CancellableContinuation<GroupPhotoSelectionResult>? = null
    private var camera: ActivityResultLauncher<android.net.Uri>? = null
    private var library: ActivityResultLauncher<PickVisualMediaRequest>? = null

    fun attach(activity: ComponentActivity) {
        camera?.unregister()
        library?.unregister()
        camera = activity.activityResultRegistry.register(CAMERA_KEY, activity, TakePicture(), ::cameraResult)
        library = activity.activityResultRegistry.register(LIBRARY_KEY, activity, PickVisualMedia(), ::libraryResult)
        files.purgeOrphans(tracker.activeCameraTarget())
    }

    override suspend fun chooseCamera(): GroupPhotoSelectionResult = withContext(Dispatchers.Main.immediate) {
        val target = runCatching(files::createSource).getOrNull() ?: return@withContext GroupPhotoSelectionResult.Failed
        await(AndroidPhotoRequestKind.CAMERA, target) {
            val uri = files.previewUri(target)
            camera?.launch(uri) ?: error("camera launcher unavailable")
        }
    }

    override suspend fun chooseLibrary(): GroupPhotoSelectionResult = withContext(Dispatchers.Main.immediate) {
        await(AndroidPhotoRequestKind.LIBRARY) {
            library?.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                ?: error("photo picker launcher unavailable")
        }
    }

    override fun cleanup(source: String) = files.remove(GroupPhotoSourceHandle(source))

    private suspend fun await(kind: AndroidPhotoRequestKind, target: java.io.File? = null, launch: () -> Unit) =
        suspendCancellableCoroutine { next ->
            if (!tracker.begin(kind, target) || continuation != null) {
                files.remove(target)
                next.resume(GroupPhotoSelectionResult.Failed)
                return@suspendCancellableCoroutine
            }
            continuation = next
            next.invokeOnCancellation {
                files.remove(tracker.cancel())
                continuation = null
            }
            runCatching(launch).onFailure {
                files.remove(tracker.cancel())
                continuation = null
                next.resume(GroupPhotoSelectionResult.Failed)
            }
        }

    private fun cameraResult(success: Boolean) {
        val target = tracker.complete(AndroidPhotoRequestKind.CAMERA)?.cameraTarget ?: return
        if (!success) {
            files.remove(target)
            finish(GroupPhotoSelectionResult.Cancelled)
        } else inspect(target)
    }

    private fun libraryResult(uri: Uri?) {
        if (tracker.complete(AndroidPhotoRequestKind.LIBRARY) == null || continuation == null) return
        if (uri == null) finish(GroupPhotoSelectionResult.Cancelled)
        else scope.launch(Dispatchers.IO) {
            val copied = files.copySelected(uri)
            if (copied == null) finish(GroupPhotoSelectionResult.Failed) else inspectNow(copied)
        }
    }

    private fun inspect(file: java.io.File) {
        scope.launch(Dispatchers.IO) { inspectNow(file) }
    }

    private fun inspectNow(file: java.io.File) {
        val metadata = files.metadata(file)
        if (metadata == null) {
            files.remove(file)
            finish(GroupPhotoSelectionResult.Failed)
            return
        }
        val delivered = finish(
            GroupPhotoSelectionResult.Selected(
                GroupPhotoSelection(
                    files.handle(file),
                    GroupPhotoPreviewHandle(file.name),
                    metadata.width,
                    metadata.height,
                ),
            ),
        )
        if (!delivered) files.remove(file)
    }

    private fun finish(result: GroupPhotoSelectionResult): Boolean {
        val next = continuation ?: return false
        continuation = null
        if (!next.isActive) return false
        next.resume(result)
        return true
    }

    private companion object {
        const val CAMERA_KEY = "saqz-group-photo-camera"
        const val LIBRARY_KEY = "saqz-group-photo-library"
    }
}
