package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.attendance.*
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

data class AttendanceSelfRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID?,
    @JsonProperty("intent") val intent: String?,
)
data class AttendanceOverrideRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID?,
    @JsonProperty("memberId") val memberId: UUID?,
    @JsonProperty("intent") val intent: String?,
    @JsonProperty("reason") val reason: String?,
)
data class CapacityRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID?,
    @JsonProperty("capacity") val capacity: Int?,
)
data class AttendanceEntryResponse(
    val memberId: UUID,
    val status: String,
    val waitlistPosition: Long?,
    val version: Long,
)
data class AttendanceAuditResponse(
    val actorId: UUID,
    val source: String,
    val oldStatus: String?,
    val newStatus: String,
    val reason: String?,
    val occurredAt: Instant,
)
data class AttendanceDetailResponse(
    val ownAttendance: AttendanceEntryResponse?,
    val confirmedCount: Int,
    val availableSpots: Int,
    val waitlistCount: Int,
    val capacity: Int,
)
data class AttendanceMutationResponse(
    val attendance: AttendanceEntryResponse,
    val audit: AttendanceAuditResponse?,
    val promotedCount: Int,
    val detail: AttendanceDetailResponse,
)
data class CapacityResponse(
    val capacity: Int,
    val version: Long,
    val promotedCount: Int,
    val detail: AttendanceDetailResponse,
)
class AttendanceDeadlinePassedException : RuntimeException()
class AttendanceFrozenException : RuntimeException()

@RestController
class AttendanceController(
    private val actors: VerifiedGroupActorResolver,
    private val responses: RespondAttendance,
    private val capacities: AdjustGameCapacity,
    private val details: AttendanceDetailQuery,
) {
    @GetMapping("/api/groups/{groupId}/games/{gameId}/attendance")
    fun read(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @PathVariable gameId: String,
    ): AttendanceDetailResponse = detail(actors.resolve(identity), uuid(groupId), uuid(gameId))

    @PutMapping("/api/groups/{groupId}/games/{gameId}/attendance")
    fun respond(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @PathVariable gameId: String,
        @RequestBody request: AttendanceSelfRequest,
    ): ResponseEntity<AttendanceMutationResponse> {
        required(request.requestId, "requestId")
        val actor = actors.resolve(identity)
        return mutation(
            responses.execute(actor, uuid(groupId), uuid(gameId), intent = intent(request.intent)),
            actor, uuid(groupId), uuid(gameId),
        )
    }

    @PostMapping("/api/groups/{groupId}/games/{gameId}/attendance/override")
    fun override(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @PathVariable gameId: String,
        @RequestBody request: AttendanceOverrideRequest,
    ): ResponseEntity<AttendanceMutationResponse> {
        required(request.requestId, "requestId")
        val member = required(request.memberId, "memberId")
        val actor = actors.resolve(identity)
        return mutation(
            responses.execute(actor, uuid(groupId), uuid(gameId), member, intent(request.intent), AttendanceSource.ORGANIZER, request.reason),
            actor, uuid(groupId), uuid(gameId),
        )
    }

    @PutMapping("/api/groups/{groupId}/games/{gameId}/capacity")
    fun capacity(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @PathVariable gameId: String,
        @RequestHeader("If-Match", required = false) ifMatch: String?,
        @RequestBody request: CapacityRequest,
    ): ResponseEntity<CapacityResponse> {
        required(request.requestId, "requestId")
        val requested = required(request.capacity, "capacity")
        val actor = actors.resolve(identity)
        val group = uuid(groupId); val game = uuid(gameId)
        return when (val result = capacities.execute(actor, group, game, version(ifMatch), requested)) {
            is CapacityCommandResult.Success -> ResponseEntity.ok().eTag(result.version.toString())
                .body(CapacityResponse(result.capacity, result.version, result.promoted.size, detail(actor, group, game)))
            CapacityCommandResult.Hidden -> throw GameNotFoundException()
            CapacityCommandResult.Forbidden -> throw AccessForbiddenException()
            CapacityCommandResult.Conflict -> throw VersionConflictException()
            CapacityCommandResult.Frozen -> throw AttendanceFrozenException()
            CapacityCommandResult.InvalidCapacity -> invalid("capacity")
        }
    }

    private fun mutation(
        result: AttendanceCommandResult,
        actor: UUID,
        group: UUID,
        game: UUID,
    ): ResponseEntity<AttendanceMutationResponse> = when (result) {
        is AttendanceCommandResult.Success -> ResponseEntity.ok().eTag(result.attendance.version.toString()).body(
            AttendanceMutationResponse(result.attendance.response(), result.event?.response(), result.promoted.size, detail(actor, group, game)),
        )
        is AttendanceCommandResult.Denied -> when (result.reason) {
            AttendanceDenial.REASON_REQUIRED, AttendanceDenial.REASON_INVALID -> invalid("reason")
            AttendanceDenial.DEADLINE_PASSED -> throw AttendanceDeadlinePassedException()
            AttendanceDenial.NOT_PUBLISHED, AttendanceDenial.FROZEN -> throw AttendanceFrozenException()
        }
        AttendanceCommandResult.Hidden -> throw GameNotFoundException()
        AttendanceCommandResult.Forbidden -> throw AccessForbiddenException()
    }

    private fun detail(actor: UUID, group: UUID, game: UUID): AttendanceDetailResponse =
        details.find(actor, group, game)?.response() ?: throw GameNotFoundException()
    private fun intent(value: String?): AttendanceIntent = value?.let { runCatching { AttendanceIntent.valueOf(it) }.getOrNull() } ?: invalid("intent")
    private fun uuid(value: String): UUID = runCatching { UUID.fromString(value) }.getOrElse { throw GameNotFoundException() }
    private fun version(value: String?): Long { if (value == null) throw PreconditionRequiredException(); return Regex("\"([1-9][0-9]*)\"").matchEntire(value)?.groupValues?.get(1)?.toLong() ?: invalid("ifMatch") }
    private fun <T : Any> required(value: T?, field: String): T = value ?: invalid(field)
    private fun invalid(field: String): Nothing = throw InvalidGroupRequestException(mapOf(field to listOf("is required or invalid")))
}

private fun AttendanceRecord.response() = AttendanceEntryResponse(memberId, status.name, waitlistSequence, version)
private fun AttendanceEvent.response() = AttendanceAuditResponse(actorId, source.name, oldStatus?.name, newStatus.name, reason, occurredAt)
private fun AttendanceDetail.response() = AttendanceDetailResponse(own?.response(), confirmedCount, availableSpots, waitlistCount, capacity)
