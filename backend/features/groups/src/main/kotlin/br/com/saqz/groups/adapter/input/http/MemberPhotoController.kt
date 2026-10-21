package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.photo.MemberPhotoDirectory
import br.com.saqz.groups.application.photo.ReadMemberPhotoResult
import br.com.saqz.sharedkernel.RequestIdentity
import java.util.UUID
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

class MemberPhotoNotFoundException : RuntimeException()

/** Foto de perfil de outro membro do grupo (MEM-006); a própria segue em `/api/session/photo`. */
@RestController
class MemberPhotoController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val photos: MemberPhotoDirectory,
) {
    @GetMapping("/api/groups/{groupId}/members/{userId}/photo")
    fun read(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @PathVariable("userId") userId: String,
        @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
    ): ResponseEntity<ByteArray> {
        val actor = actorResolver.resolve(identity)
        val group = parseUuid(groupId) ?: throw GroupNotFoundException()
        val target = parseUuid(userId) ?: throw MemberPhotoNotFoundException()
        val photo = when (val result = photos.read(actor, group, target)) {
            is ReadMemberPhotoResult.Found -> result.photo
            ReadMemberPhotoResult.Forbidden -> throw AccessForbiddenException()
            ReadMemberPhotoResult.NotFound -> throw MemberPhotoNotFoundException()
        }
        // Mesmo validador forte do `/api/session/photo`: o digest do conteúdo.
        val etag = "\"${photo.digest}\""
        val builder = ResponseEntity.status(if (ifNoneMatch == etag) HttpStatus.NOT_MODIFIED else HttpStatus.OK)
            .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
            .header(HttpHeaders.ETAG, etag)
        return if (ifNoneMatch == etag) {
            builder.build()
        } else {
            builder.contentType(MediaType.IMAGE_JPEG).contentLength(photo.byteSize).body(photo.bytes)
        }
    }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()
}
