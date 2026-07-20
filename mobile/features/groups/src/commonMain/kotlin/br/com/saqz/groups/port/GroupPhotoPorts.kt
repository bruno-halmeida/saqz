package br.com.saqz.groups.port

import io.ktor.utils.io.ByteReadChannel

data class GroupPhotoSourceHandle(val value: String) {
    init { require(value.isNotBlank()) }
}

data class GroupPhotoPreviewHandle(val value: String) {
    init { require(value.isNotBlank()) }
}

enum class GroupPhotoMediaType(val value: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
}

data class GroupPhotoSelection(
    val source: GroupPhotoSourceHandle,
    val preview: GroupPhotoPreviewHandle,
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0)
    }
}

data class GroupPhotoCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val size: Float = 1f,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && size > 0f && size <= 1f)
        require(left + size <= 1.0001f && top + size <= 1.0001f)
    }
}

fun interface GroupPhotoByteSource {
    fun open(): ByteReadChannel
}

data class EncodedGroupPhoto(
    val mediaType: GroupPhotoMediaType,
    val contentLength: Long,
    val source: GroupPhotoByteSource,
) {
    init {
        require(contentLength in 1..MAX_GROUP_PHOTO_BYTES)
    }

    companion object {
        const val MAX_GROUP_PHOTO_BYTES: Long = 5L * 1024L * 1024L
    }
}

sealed interface GroupPhotoSelectionResult {
    data class Selected(val value: GroupPhotoSelection) : GroupPhotoSelectionResult
    data object Cancelled : GroupPhotoSelectionResult
    data object Failed : GroupPhotoSelectionResult
}

sealed interface GroupPhotoEncodingResult {
    data class Encoded(val value: EncodedGroupPhoto) : GroupPhotoEncodingResult
    data object Failed : GroupPhotoEncodingResult
}

interface GroupPhotoSelectionPort {
    suspend fun chooseCamera(): GroupPhotoSelectionResult
    suspend fun chooseLibrary(): GroupPhotoSelectionResult
    fun cleanup(source: GroupPhotoSourceHandle)
}

interface GroupPhotoEncoderPort {
    suspend fun encode(source: GroupPhotoSourceHandle, crop: GroupPhotoCrop): GroupPhotoEncodingResult
    fun cancel(source: GroupPhotoSourceHandle)
}

interface GroupPhotoCachePort {
    fun evict(groupId: String)
    fun clearAll()
}
