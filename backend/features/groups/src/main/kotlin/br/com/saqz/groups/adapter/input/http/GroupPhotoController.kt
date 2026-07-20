package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.photo.GroupPhotoRejection
import br.com.saqz.groups.application.photo.GroupPhotoService
import br.com.saqz.groups.application.photo.ReadGroupPhotoResult
import br.com.saqz.groups.application.photo.RemoveGroupPhotoResult
import br.com.saqz.groups.application.photo.UploadGroupPhotoResult
import br.com.saqz.sharedkernel.RequestIdentity
import java.util.UUID
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

class InvalidGroupPhotoException(val rejection: GroupPhotoRejection) : RuntimeException()
class GroupPhotoTooLargeException : RuntimeException()

@RestController
class GroupPhotoController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val service: GroupPhotoService,
) {
    @PutMapping("/api/groups/{groupId}/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<Void> {
        val actor = actorResolver.resolve(identity)
        val parsedGroupId = parseGroupId(groupId)
        val expectedVersion = parseRequiredIfMatch(ifMatch)
        val contentType = file.contentType ?: throw InvalidGroupPhotoException(GroupPhotoRejection.UNSUPPORTED_TYPE)
        return when (
            val result = service.upload(actor, parsedGroupId, expectedVersion, contentType, file.inputStream)
        ) {
            is UploadGroupPhotoResult.Success -> ResponseEntity.noContent().eTag(result.groupVersion.toString()).build()
            is UploadGroupPhotoResult.Invalid -> when (result.reason) {
                GroupPhotoRejection.TOO_LARGE -> throw GroupPhotoTooLargeException()
                else -> throw InvalidGroupPhotoException(result.reason)
            }
            UploadGroupPhotoResult.GroupNotFound -> throw GroupNotFoundException()
            UploadGroupPhotoResult.AccessForbidden -> throw AccessForbiddenException()
            UploadGroupPhotoResult.VersionConflict -> throw VersionConflictException()
        }
    }

    @GetMapping("/api/groups/{groupId}/photo")
    fun read(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
    ): ResponseEntity<ByteArray> {
        val actor = actorResolver.resolve(identity)
        return when (val result = service.read(actor, parseGroupId(groupId))) {
            is ReadGroupPhotoResult.Success -> {
                val etag = "\"photo-${result.photo.version}\""
                val builder = ResponseEntity.status(
                    if (ifNoneMatch == etag) HttpStatus.NOT_MODIFIED else HttpStatus.OK,
                )
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                    .header(HttpHeaders.ETAG, etag)
                if (ifNoneMatch == etag) builder.build() else builder
                    .contentType(MediaType.parseMediaType(result.photo.mediaType.value))
                    .contentLength(result.photo.byteSize)
                    .body(result.photo.bytes)
            }
            ReadGroupPhotoResult.GroupNotFound -> throw GroupNotFoundException()
        }
    }

    @DeleteMapping("/api/groups/{groupId}/photo")
    fun remove(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
    ): ResponseEntity<Void> {
        val actor = actorResolver.resolve(identity)
        return when (
            val result = service.remove(actor, parseGroupId(groupId), parseRequiredIfMatch(ifMatch))
        ) {
            is RemoveGroupPhotoResult.Success -> ResponseEntity.noContent().eTag(result.groupVersion.toString()).build()
            RemoveGroupPhotoResult.GroupNotFound -> throw GroupNotFoundException()
            RemoveGroupPhotoResult.AccessForbidden -> throw AccessForbiddenException()
            RemoveGroupPhotoResult.VersionConflict -> throw VersionConflictException()
        }
    }

    private fun parseGroupId(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull()
        ?: throw GroupNotFoundException()

    private fun parseRequiredIfMatch(raw: String?): Long {
        if (raw == null) throw PreconditionRequiredException()
        return QUOTED_VERSION.matchEntire(raw)?.groupValues?.get(1)?.toLongOrNull()
            ?: throw InvalidGroupRequestException(mapOf("ifMatch" to listOf("must be a quoted positive version")))
    }

    private companion object {
        val QUOTED_VERSION = Regex("\"([1-9][0-9]*)\"")
    }
}
