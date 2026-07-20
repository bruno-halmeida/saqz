package br.com.saqz.androidapp.groups.photo

import java.io.File

internal enum class AndroidPhotoRequestKind { CAMERA, LIBRARY }
internal data class AndroidPhotoRequestCompletion(val cameraTarget: File?)

internal class AndroidPhotoRequestTracker {
    private var kind: AndroidPhotoRequestKind? = null
    private var cameraTarget: File? = null

    fun begin(request: AndroidPhotoRequestKind, target: File? = null): Boolean {
        if (kind != null) return false
        require((request == AndroidPhotoRequestKind.CAMERA) == (target != null))
        kind = request
        cameraTarget = target
        return true
    }

    fun complete(request: AndroidPhotoRequestKind): AndroidPhotoRequestCompletion? {
        if (kind != request) return null
        val target = cameraTarget
        kind = null
        cameraTarget = null
        return AndroidPhotoRequestCompletion(target)
    }

    fun cancel(): File? {
        val target = cameraTarget
        kind = null
        cameraTarget = null
        return target
    }

    fun activeCameraTarget(): File? = cameraTarget
}
