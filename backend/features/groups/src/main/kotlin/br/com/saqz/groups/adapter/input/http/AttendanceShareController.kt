package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.attendance.share.ResolveAttendanceLink
import br.com.saqz.groups.application.attendance.share.ResolveAttendanceLinkResult
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class ResolveAttendanceLinkRequest @JsonCreator constructor(
    @JsonProperty("code") val code: String?,
)

data class ResolvedAttendanceLinkResponse(
    val groupId: UUID,
    val gameId: UUID,
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
    val registrationRequired: Boolean = false,
)

/** [reason] só existe quando o código é conhecido: código desconhecido segue sem ele. */
class AttendanceLinkInvalidOrExpiredException(val reason: String? = null) : RuntimeException()

class AttendanceLinkAttemptLimitException(val retryAfterSeconds: Int) : RuntimeException()

class AttendanceLinkUnavailableException : RuntimeException()

@RestController
class AttendanceShareController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val resolveAttendanceLink: ResolveAttendanceLink,
) {
    @PostMapping("/api/attendance-links/resolve")
    fun resolve(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestBody request: ResolveAttendanceLinkRequest,
    ): ResolvedAttendanceLinkResponse = when (
        val result = resolveAttendanceLink.execute(actor(identity), required(request.code, "code"))
    ) {
        ResolveAttendanceLinkResult.InvalidOrExpired -> throw AttendanceLinkInvalidOrExpiredException()
        is ResolveAttendanceLinkResult.GameClosed -> throw AttendanceLinkInvalidOrExpiredException(result.reason.name)
        is ResolveAttendanceLinkResult.AttemptLimit -> throw AttendanceLinkAttemptLimitException(result.retryAfterSeconds)
        ResolveAttendanceLinkResult.Unavailable -> throw AttendanceLinkUnavailableException()
        is ResolveAttendanceLinkResult.Success -> ResolvedAttendanceLinkResponse(result.groupId, result.gameId, result.registrationRequired)
    }

    private fun actor(identity: RequestIdentity): UUID = actorResolver.resolve(identity)
    private fun required(value: String?, field: String): String = value?.takeUnless(String::isBlank) ?: invalid(field)
    private fun invalid(field: String): Nothing = throw InvalidGroupRequestException(mapOf(field to listOf("is required or invalid")))
}
