package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.photo.USER_PHOTO_MEDIA_TYPE
import br.com.saqz.access.application.photo.UploadUserPhotoResult
import br.com.saqz.access.application.photo.UserPhotoRejection
import br.com.saqz.access.application.photo.UserPhotoService
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

const val USER_PHOTO_PATH: String = "/api/session/photo"

class InvalidUserPhotoException : RuntimeException()

class UserPhotoTooLargeException : RuntimeException()

class UserPhotoNotFoundException : RuntimeException()

@RestController
class UserPhotoController(
    private val bootstrapSession: BootstrapSession,
    private val service: UserPhotoService,
) {
    @PutMapping(USER_PHOTO_PATH, consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<Void> {
        val userId = resolveUserId(identity)
        val declaredContentType = file.contentType ?: throw InvalidUserPhotoException()
        return when (val result = service.upload(userId, declaredContentType, file.inputStream)) {
            is UploadUserPhotoResult.Success -> ResponseEntity.noContent().eTag(photoTag(result.digest)).build()
            is UploadUserPhotoResult.Rejected -> when (result.reason) {
                UserPhotoRejection.TOO_LARGE -> throw UserPhotoTooLargeException()
                else -> throw InvalidUserPhotoException()
            }
        }
    }

    @GetMapping(USER_PHOTO_PATH)
    fun read(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
    ): ResponseEntity<ByteArray> {
        val photo = service.read(resolveUserId(identity)) ?: throw UserPhotoNotFoundException()
        val etag = photoTag(photo.digest)
        val builder = ResponseEntity.status(if (ifNoneMatch == etag) HttpStatus.NOT_MODIFIED else HttpStatus.OK)
            .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
            .header(HttpHeaders.ETAG, etag)
        return if (ifNoneMatch == etag) {
            builder.build()
        } else {
            builder.contentType(MediaType.parseMediaType(USER_PHOTO_MEDIA_TYPE))
                .contentLength(photo.byteSize)
                .body(photo.bytes)
        }
    }

    @DeleteMapping(USER_PHOTO_PATH)
    fun remove(@AuthenticationPrincipal identity: RequestIdentity): ResponseEntity<Void> {
        service.remove(resolveUserId(identity))
        return ResponseEntity.noContent().build()
    }

    private fun resolveUserId(identity: RequestIdentity): UUID =
        when (val result = bootstrapSession.execute(identity)) {
            BootstrapSessionResult.InvalidDisplayName -> throw InvalidDisplayNameException()
            BootstrapSessionResult.Suspended -> throw AccountSuspendedException()
            is BootstrapSessionResult.Success -> result.session.user.id
        }

    // Validador forte pelo conteudo: bytes iguais dao a mesma ETag e bytes
    // diferentes dao ETag diferente, mesmo entre contas e depois de uma remocao.
    // Um contador por linha reiniciaria em 1 e faria o cache privado do navegador
    // receber 304 para a foto de outra conta nesta mesma URL.
    private fun photoTag(digest: String) = "\"$digest\""
}
