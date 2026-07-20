package br.com.saqz.groups.application.photo

import java.io.InputStream
import java.util.UUID

enum class GroupPhotoMediaType(val value: String) {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp"),
}

data class ValidatedGroupPhoto(
    val bytes: ByteArray,
    val mediaType: GroupPhotoMediaType,
    val width: Int,
    val height: Int,
    val sha256Digest: ByteArray,
) {
    val byteSize: Long get() = bytes.size.toLong()
}

enum class GroupPhotoRejection {
    EMPTY,
    TOO_LARGE,
    UNSUPPORTED_TYPE,
    DECLARED_TYPE_MISMATCH,
    ANIMATED,
    INVALID_IMAGE,
    DIMENSIONS_TOO_LARGE,
}

sealed interface GroupPhotoValidationResult {
    data class Valid(val photo: ValidatedGroupPhoto) : GroupPhotoValidationResult
    data class Rejected(val reason: GroupPhotoRejection) : GroupPhotoValidationResult
}

fun interface GroupPhotoValidationPort {
    fun validate(declaredContentType: String, input: InputStream): GroupPhotoValidationResult
}

data class ReplaceGroupPhotoCommand(
    val groupId: UUID,
    val expectedGroupVersion: Long,
    val actorId: UUID,
    val photo: ValidatedGroupPhoto,
)

data class StoredGroupPhoto(
    val groupId: UUID,
    val bytes: ByteArray,
    val mediaType: GroupPhotoMediaType,
    val byteSize: Long,
    val width: Int,
    val height: Int,
    val sha256Digest: ByteArray,
    val version: Long,
    val updatedBy: UUID,
)

data class GroupPhotoMetadata(
    val groupId: UUID,
    val mediaType: GroupPhotoMediaType,
    val byteSize: Long,
    val width: Int,
    val height: Int,
    val version: Long,
)

sealed interface GroupPhotoWriteResult {
    data class Replaced(val photo: StoredGroupPhoto, val groupVersion: Long) : GroupPhotoWriteResult
    data class Removed(val groupVersion: Long) : GroupPhotoWriteResult
    data class AlreadyAbsent(val groupVersion: Long) : GroupPhotoWriteResult
    data object VersionConflict : GroupPhotoWriteResult
}

interface GroupPhotoRepository {
    fun replace(command: ReplaceGroupPhotoCommand): GroupPhotoWriteResult
    fun remove(groupId: UUID, expectedGroupVersion: Long): GroupPhotoWriteResult
    fun read(groupId: UUID): StoredGroupPhoto?
    fun readMetadata(groupId: UUID): GroupPhotoMetadata?
}
