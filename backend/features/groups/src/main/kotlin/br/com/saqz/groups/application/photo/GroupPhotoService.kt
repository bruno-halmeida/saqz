package br.com.saqz.groups.application.photo

import br.com.saqz.groups.application.read.GetGroup
import br.com.saqz.groups.application.read.GetGroupResult
import br.com.saqz.groups.domain.GroupRole
import java.io.InputStream
import java.util.UUID

sealed interface UploadGroupPhotoResult {
    data class Success(val photoVersion: Long, val groupVersion: Long) : UploadGroupPhotoResult
    data class Invalid(val reason: GroupPhotoRejection) : UploadGroupPhotoResult
    data object GroupNotFound : UploadGroupPhotoResult
    data object AccessForbidden : UploadGroupPhotoResult
    data object VersionConflict : UploadGroupPhotoResult
}

sealed interface ReadGroupPhotoResult {
    data class Success(val photo: StoredGroupPhoto) : ReadGroupPhotoResult
    data object GroupNotFound : ReadGroupPhotoResult
}

sealed interface RemoveGroupPhotoResult {
    data class Success(val groupVersion: Long) : RemoveGroupPhotoResult
    data object GroupNotFound : RemoveGroupPhotoResult
    data object AccessForbidden : RemoveGroupPhotoResult
    data object VersionConflict : RemoveGroupPhotoResult
}

class GroupPhotoService(
    private val getGroup: GetGroup,
    private val validator: GroupPhotoValidationPort,
    private val repository: GroupPhotoRepository,
) {
    fun upload(
        actorId: UUID,
        groupId: UUID,
        expectedGroupVersion: Long,
        declaredContentType: String,
        input: InputStream,
    ): UploadGroupPhotoResult {
        val group = when (val result = getGroup.execute(actorId, groupId)) {
            is GetGroupResult.Success -> result.group
            GetGroupResult.GroupNotFound,
            GetGroupResult.AccessForbidden,
            -> return UploadGroupPhotoResult.GroupNotFound
        }
        if (group.role != GroupRole.OWNER && group.role != GroupRole.ADMIN) {
            return UploadGroupPhotoResult.AccessForbidden
        }
        val photo = when (val result = validator.validate(declaredContentType, input)) {
            is GroupPhotoValidationResult.Valid -> result.photo
            is GroupPhotoValidationResult.Rejected -> return UploadGroupPhotoResult.Invalid(result.reason)
        }
        return when (
            val result = repository.replace(
                ReplaceGroupPhotoCommand(groupId, expectedGroupVersion, actorId, photo),
            )
        ) {
            is GroupPhotoWriteResult.Replaced -> UploadGroupPhotoResult.Success(result.photo.version, result.groupVersion)
            GroupPhotoWriteResult.VersionConflict -> UploadGroupPhotoResult.VersionConflict
            is GroupPhotoWriteResult.AlreadyAbsent,
            is GroupPhotoWriteResult.Removed,
            -> error("photo replacement returned removal result")
        }
    }

    fun read(actorId: UUID, groupId: UUID): ReadGroupPhotoResult {
        when (getGroup.execute(actorId, groupId)) {
            is GetGroupResult.Success -> Unit
            GetGroupResult.GroupNotFound,
            GetGroupResult.AccessForbidden,
            -> return ReadGroupPhotoResult.GroupNotFound
        }
        return repository.read(groupId)?.let(ReadGroupPhotoResult::Success)
            ?: ReadGroupPhotoResult.GroupNotFound
    }

    fun remove(actorId: UUID, groupId: UUID, expectedGroupVersion: Long): RemoveGroupPhotoResult {
        val group = when (val result = getGroup.execute(actorId, groupId)) {
            is GetGroupResult.Success -> result.group
            GetGroupResult.GroupNotFound,
            GetGroupResult.AccessForbidden,
            -> return RemoveGroupPhotoResult.GroupNotFound
        }
        if (group.role != GroupRole.OWNER && group.role != GroupRole.ADMIN) {
            return RemoveGroupPhotoResult.AccessForbidden
        }
        return when (val result = repository.remove(groupId, expectedGroupVersion)) {
            is GroupPhotoWriteResult.Removed -> RemoveGroupPhotoResult.Success(result.groupVersion)
            is GroupPhotoWriteResult.AlreadyAbsent -> RemoveGroupPhotoResult.Success(result.groupVersion)
            GroupPhotoWriteResult.VersionConflict -> RemoveGroupPhotoResult.VersionConflict
            is GroupPhotoWriteResult.Replaced -> error("photo removal returned replacement result")
        }
    }
}
