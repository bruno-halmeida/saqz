package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.create.CreateGroup
import br.com.saqz.groups.application.create.CreateGroupResult
import br.com.saqz.groups.application.create.CreatedGroup
import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.group.CourtPlayStyle
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.GroupProfileDefaultsInput
import br.com.saqz.groups.domain.group.GroupVenueInput
import br.com.saqz.groups.domain.group.RegularSlotInput
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

data class CreateGroupRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("name") val name: String?,
    @JsonProperty("modality") val modality: GroupModality?,
    @JsonProperty("composition") val composition: GroupComposition?,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("city") val city: String? = null,
    @JsonProperty("level") val level: GroupLevel? = null,
    @JsonProperty("customLevel") val customLevel: String? = null,
    @JsonProperty("playStyle") val playStyle: CourtPlayStyle? = null,
    @JsonProperty("customPlayStyle") val customPlayStyle: String? = null,
    @JsonProperty("defaultVenue") val defaultVenue: CreateGroupVenueRequest?,
    @JsonProperty("regularSlots") val regularSlots: List<CreateRegularSlotRequest>?,
    @JsonProperty("defaultCapacity") val defaultCapacity: Int? = null,
    @JsonProperty("defaultConfirmationLeadMinutes") val defaultConfirmationLeadMinutes: Int? = null,
    @JsonProperty("defaultGameFeeCents") val defaultGameFeeCents: Long? = null,
    @JsonProperty("monthlyFeeCents") val monthlyFeeCents: Long? = null,
    @JsonProperty("monthlyDueDay") val monthlyDueDay: Int? = null,
    @JsonProperty("timeZone") val timeZone: String,
)

data class CreateGroupVenueRequest @JsonCreator constructor(
    @JsonProperty("name") val name: String?,
    @JsonProperty("address") val address: String?,
    @JsonProperty("court") val court: String?,
)

data class CreateRegularSlotRequest @JsonCreator constructor(
    @JsonProperty("weekday") val weekday: DayOfWeek?,
    @JsonProperty("startTime") val startTime: LocalTime?,
    @JsonProperty("durationMinutes") val durationMinutes: Int?,
)

data class CreateGroupResponse(
    val id: UUID,
    val name: String,
    val timeZone: String,
    val version: Long,
    val role: GroupRole,
    val profileStatus: GroupProfileStatus,
)

class InvalidGroupRequestException(
    val fieldErrors: Map<String, List<String>>,
) : RuntimeException()

@RestController
class AccessGroupController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val createGroup: CreateGroup,
) {
    @PostMapping("/api/groups")
    fun create(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestBody request: CreateGroupRequest,
    ): ResponseEntity<CreateGroupResponse> {
        val actor = actorResolver.resolve(identity)
        val requestId = runCatching { UUID.fromString(request.requestId) }.getOrNull()
            ?: throw InvalidGroupRequestException(
                mapOf("requestId" to listOf("must be a UUID")),
            )
        return when (val result = createGroup.execute(actor, requestId, request.profileInput(), request.timeZone)) {
            is CreateGroupResult.Success -> ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result.group.toResponse())
            is CreateGroupResult.Invalid -> throw InvalidGroupRequestException(
                result.errors.groupBy({ it.field }, { it.message }),
            )
        }
    }
}

private fun CreateGroupRequest.profileInput() = GroupProfileDefaultsInput(
    name = name,
    modality = modality,
    composition = composition,
    description = description,
    city = city,
    level = level,
    customLevel = customLevel,
    playStyle = playStyle,
    customPlayStyle = customPlayStyle,
    defaultVenue = defaultVenue?.let { GroupVenueInput(it.name, it.address, it.court) },
    regularSlots = regularSlots.orEmpty().map { RegularSlotInput(it.weekday, it.startTime, it.durationMinutes) },
    defaultCapacity = defaultCapacity,
    defaultConfirmationLeadMinutes = defaultConfirmationLeadMinutes,
    defaultGameFeeCents = defaultGameFeeCents,
    monthlyFeeCents = monthlyFeeCents,
    monthlyDueDay = monthlyDueDay,
)

private fun CreatedGroup.toResponse() = CreateGroupResponse(id, name, timeZone, version, role, profileStatus)
