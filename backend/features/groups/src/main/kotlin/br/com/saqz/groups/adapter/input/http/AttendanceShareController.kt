package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.attendance.share.AttendanceShareSnapshotPerson
import br.com.saqz.groups.application.attendance.share.ReadAttendanceShareSnapshot
import br.com.saqz.groups.application.attendance.share.ReadAttendanceShareSnapshotResult
import br.com.saqz.groups.application.attendance.share.ResolveAttendanceLink
import br.com.saqz.groups.application.attendance.share.ResolveAttendanceLinkResult
import br.com.saqz.groups.application.attendance.share.RotateAttendanceLink
import br.com.saqz.groups.application.attendance.share.RotateAttendanceLinkResult
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant
import java.util.UUID

data class AttendanceLinkUrlResponse(val url: URI)

data class ResolveAttendanceLinkRequest @JsonCreator constructor(
    @JsonProperty("code") val code: String?,
)

data class ResolvedAttendanceLinkResponse(
    val groupId: UUID,
    val gameId: UUID,
)

data class AttendanceShareSnapshotPersonResponse(
    val displayName: String,
    val waitlistPosition: Long? = null,
)

data class AttendanceShareSnapshotResponse(
    val title: String,
    val startsAt: Instant,
    val timeZone: String,
    val venue: String,
    val capacity: Int,
    val confirmed: List<AttendanceShareSnapshotPersonResponse>,
    val waitlisted: List<AttendanceShareSnapshotPersonResponse>,
    val declined: List<AttendanceShareSnapshotPersonResponse>,
)

class AttendanceLinkInvalidOrExpiredException : RuntimeException()

class AttendanceLinkAttemptLimitException(val retryAfterSeconds: Int) : RuntimeException()

class AttendanceLinkUnavailableException : RuntimeException()

@RestController
class AttendanceShareController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val rotateAttendanceLink: RotateAttendanceLink,
    private val resolveAttendanceLink: ResolveAttendanceLink,
    private val readAttendanceShareSnapshot: ReadAttendanceShareSnapshot,
) {
    @PostMapping("/api/groups/{groupId}/games/{gameId}/attendance-link")
    fun rotate(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @PathVariable("gameId") gameId: String,
    ): AttendanceLinkUrlResponse = when (
        val result = rotateAttendanceLink.execute(actor(identity), game(groupId), game(gameId))
    ) {
        RotateAttendanceLinkResult.GameNotFound -> throw GameNotFoundException()
        RotateAttendanceLinkResult.AccessForbidden -> throw AccessForbiddenException()
        RotateAttendanceLinkResult.AttendanceFrozen -> throw AttendanceFrozenException()
        RotateAttendanceLinkResult.DeadlinePassed -> throw AttendanceDeadlinePassedException()
        is RotateAttendanceLinkResult.Success -> AttendanceLinkUrlResponse(result.url)
    }

    @PostMapping("/api/attendance-links/resolve")
    fun resolve(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestBody request: ResolveAttendanceLinkRequest,
    ): ResolvedAttendanceLinkResponse = when (
        val result = resolveAttendanceLink.execute(actor(identity), required(request.code, "code"))
    ) {
        ResolveAttendanceLinkResult.InvalidOrExpired -> throw AttendanceLinkInvalidOrExpiredException()
        is ResolveAttendanceLinkResult.AttemptLimit -> throw AttendanceLinkAttemptLimitException(result.retryAfterSeconds)
        ResolveAttendanceLinkResult.Unavailable -> throw AttendanceLinkUnavailableException()
        is ResolveAttendanceLinkResult.Success -> ResolvedAttendanceLinkResponse(result.groupId, result.gameId)
    }

    @GetMapping("/api/groups/{groupId}/games/{gameId}/attendance-share")
    fun snapshot(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @PathVariable("gameId") gameId: String,
    ): AttendanceShareSnapshotResponse = when (
        val result = readAttendanceShareSnapshot.execute(actor(identity), game(groupId), game(gameId))
    ) {
        ReadAttendanceShareSnapshotResult.GameNotFound -> throw GameNotFoundException()
        ReadAttendanceShareSnapshotResult.AccessForbidden -> throw AccessForbiddenException()
        is ReadAttendanceShareSnapshotResult.Success -> result.snapshot.response()
    }

    private fun actor(identity: RequestIdentity): UUID = actorResolver.resolve(identity)
    private fun game(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrElse { throw GameNotFoundException() }
    private fun required(value: String?, field: String): String = value?.takeUnless(String::isBlank) ?: invalid(field)
    private fun invalid(field: String): Nothing = throw InvalidGroupRequestException(mapOf(field to listOf("is required or invalid")))
}

private fun br.com.saqz.groups.application.attendance.share.AttendanceShareSnapshot.response() = AttendanceShareSnapshotResponse(
    title = title,
    startsAt = startsAt,
    timeZone = timeZone,
    venue = venue,
    capacity = capacity,
    confirmed = confirmed.map(AttendanceShareSnapshotPerson::response),
    waitlisted = waitlisted.map(AttendanceShareSnapshotPerson::response),
    declined = declined.map(AttendanceShareSnapshotPerson::response),
)

private fun AttendanceShareSnapshotPerson.response() = AttendanceShareSnapshotPersonResponse(displayName, waitlistPosition)
