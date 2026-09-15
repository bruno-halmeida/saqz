package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.settings.*
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class ScheduleSlotRequest @JsonCreator constructor(
    @JsonProperty("weekday") val weekday: java.time.DayOfWeek,
    @JsonProperty("startTime") val startTime: java.time.LocalTime,
)
data class GroupScheduleRequest @JsonCreator constructor(
    @JsonProperty("recurring") val recurring: Boolean,
    @JsonProperty("slots") val slots: List<ScheduleSlotRequest>,
    @JsonProperty("durationMinutes") val durationMinutes: Int,
    @JsonProperty("confirmationLeadMinutes") val confirmationLeadMinutes: Int,
    @JsonProperty("paused") val paused: Boolean,
)
@RestController
class GroupScheduleController(private val actors: VerifiedGroupActorResolver, private val service: GroupScheduleService) {
    @GetMapping("/api/groups/{groupId}/schedule")
    fun read(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable("groupId") groupId: String): ResponseEntity<GroupSchedule> =
        service.read(actors.resolve(identity), groupId.uuid()).response()

    @PutMapping("/api/groups/{groupId}/schedule")
    fun update(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @RequestHeader("If-Match", required = false) ifMatch: String?,
        @RequestBody request: GroupScheduleRequest,
    ): ResponseEntity<GroupSchedule> = service.update(
        actors.resolve(identity), groupId.uuid(), QuotedResourceVersion.parseRequired(ifMatch),
        GroupSchedule(request.recurring, request.slots.map { ScheduleSlot(it.weekday, it.startTime) },
            request.durationMinutes, request.confirmationLeadMinutes, request.paused),
    ).response()
}
private fun String.uuid(): UUID = runCatching { UUID.fromString(this) }.getOrElse { throw GroupNotFoundException() }
private fun GroupScheduleResult.response(): ResponseEntity<GroupSchedule> = when (this) {
    is GroupScheduleResult.Success -> ResponseEntity.ok().eTag(value.version.toString()).body(value.schedule)
    GroupScheduleResult.NotFound -> throw GroupNotFoundException()
    GroupScheduleResult.Forbidden -> throw AccessForbiddenException()
    GroupScheduleResult.Conflict -> throw VersionConflictException()
    GroupScheduleResult.Invalid -> throw InvalidGroupRequestException(mapOf("schedule" to listOf("Invalid schedule settings")))
}
