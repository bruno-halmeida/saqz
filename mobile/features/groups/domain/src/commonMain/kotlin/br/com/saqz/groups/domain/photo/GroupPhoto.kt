package br.com.saqz.groups.domain.photo

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import kotlin.jvm.JvmInline

// SPEC_DEVIATION: source/preview handles are data classes, not @JvmInline value classes.
// Reason: GroupPhotoSelection (below) is constructed by the native Swift photo adapter and
// returned into Kotlin. Kotlin/Native's Objective-C export erases value-class members to an
// opaque `id`, so Swift cannot construct a model whose fields are value classes. Data classes
// export as real Objective-C types with initializers Swift can call. Value-class semantics are
// not needed for these opaque provider handles.
data class GroupPhotoSourceHandle(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

data class GroupPhotoPreviewHandle(val value: String) {
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

// These ports are implemented by native (Swift/Android) adapters and exchange raw String
// identifiers at the boundary; Kotlin domain code wraps them in GroupPhotoSourceHandle /
// GroupPhotoPreviewHandle. Keeping the FFI signature primitive avoids leaking domain value
// objects across the native boundary and keeps the exported Objective-C surface unambiguous.
interface GroupPhotoSelectionPort {
    suspend fun chooseCamera(): GroupPhotoSelectionResult
    suspend fun chooseLibrary(): GroupPhotoSelectionResult
    fun cleanup(source: String)
}

interface GroupPhotoEncoderPort {
    suspend fun encode(
        source: String,
        crop: GroupPhotoCrop,
    ): GroupPhotoEncodingResult

    fun cancel(source: String)
}

fun interface GroupPhotoPreviewPort {
    fun read(preview: String): ByteArray?
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
