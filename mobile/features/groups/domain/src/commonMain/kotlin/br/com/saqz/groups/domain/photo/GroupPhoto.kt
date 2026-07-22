package br.com.saqz.groups.domain.photo

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import kotlin.jvm.JvmInline

@JvmInline
value class GroupPhotoSourceHandle(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class GroupPhotoPreviewHandle(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class GroupPhotoVersionToken(val value: String) {
    init {
        require(value.isNotBlank())
    }
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
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val zoom: Float = 1f,
) {
    init {
        require(centerX in 0f..1f && centerY in 0f..1f)
        require(zoom in 1f..8f)
    }
}

fun interface GroupPhotoByteSource {
    fun read(): ByteArray
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

data class GroupPhotoUploadCommand(
    val groupId: GroupId,
    val groupVersion: GroupPhotoVersionToken,
    val photo: EncodedGroupPhoto,
)

data class GroupPhotoReceipt(val version: GroupPhotoVersionToken)

sealed interface GroupPhotoReadResult {
    data class Available(
        val bytes: ByteArray,
        val contentType: String,
        val version: GroupPhotoVersionToken,
    ) : GroupPhotoReadResult {
        override fun equals(other: Any?): Boolean = other is Available &&
            bytes.contentEquals(other.bytes) &&
            contentType == other.contentType &&
            version == other.version

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    data object NotModified : GroupPhotoReadResult
}

sealed interface GroupPhotoError : SaqzError {
    data object MediaLimit : GroupPhotoError
    data object StaleVersion : GroupPhotoError
    data object NotFound : GroupPhotoError
    data class DataFailure(val error: DataError) : GroupPhotoError
}

interface GroupPhotoGateway {
    suspend fun upload(
        command: GroupPhotoUploadCommand,
    ): SaqzResult<GroupPhotoReceipt, GroupPhotoError>

    suspend fun read(
        groupId: GroupId,
        version: GroupPhotoVersionToken? = null,
    ): SaqzResult<GroupPhotoReadResult, GroupPhotoError>

    suspend fun remove(
        groupId: GroupId,
        groupVersion: GroupPhotoVersionToken,
    ): SaqzResult<GroupPhotoReceipt, GroupPhotoError>
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
    suspend fun encode(
        source: GroupPhotoSourceHandle,
        crop: GroupPhotoCrop,
    ): GroupPhotoEncodingResult

    fun cancel(source: GroupPhotoSourceHandle)
}

fun interface GroupPhotoPreviewPort {
    fun read(preview: GroupPhotoPreviewHandle): ByteArray?
}

data class CachedGroupPhoto(
    val preview: GroupPhotoPreviewHandle,
    val version: GroupPhotoVersionToken,
)

interface GroupPhotoCachePort {
    fun read(groupId: GroupId): CachedGroupPhoto?
    fun write(
        groupId: GroupId,
        bytes: ByteArray,
        version: GroupPhotoVersionToken,
    ): CachedGroupPhoto?

    fun evict(groupId: GroupId)
    fun clearAll()
}
