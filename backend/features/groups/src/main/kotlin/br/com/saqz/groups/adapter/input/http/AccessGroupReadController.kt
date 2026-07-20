package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.read.GetGroup
import br.com.saqz.groups.application.read.GetGroupResult
import br.com.saqz.groups.application.read.GroupFinanceDefaultsReadModel
import br.com.saqz.groups.application.read.GroupProfileReadModel
import br.com.saqz.groups.application.read.GroupRegularSlotReadModel
import br.com.saqz.groups.application.read.GroupVenueReadModel
import br.com.saqz.groups.application.read.GroupView
import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.group.CourtPlayStyle
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.sharedkernel.RequestIdentity
import java.time.DayOfWeek
import java.time.LocalTime
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class GroupReadResponse(
    val id: UUID,
    val name: String,
    val timeZone: String,
    val role: GroupRole,
    val version: Long,
    val profileStatus: GroupProfileStatus,
    val privacy: String = "PRIVATE",
    val currency: String = "BRL",
    val profile: GroupProfileReadResponse?,
    val financeDefaults: GroupFinanceDefaultsReadResponse?,
)

data class GroupProfileReadResponse(
    val modality: GroupModality?,
    val composition: GroupComposition?,
    val description: String?,
    val city: String?,
    val level: GroupLevel?,
    val customLevel: String?,
    val playStyle: CourtPlayStyle?,
    val customPlayStyle: String?,
    val defaultVenue: GroupVenueReadResponse?,
    val regularSlots: List<GroupRegularSlotReadResponse>,
    val defaultCapacity: Int?,
    val defaultConfirmationLeadMinutes: Int?,
)

data class GroupVenueReadResponse(
    val id: UUID,
    val name: String,
    val address: String,
    val court: String?,
)

data class GroupRegularSlotReadResponse(
    val id: UUID,
    val weekday: DayOfWeek,
    val startTime: LocalTime,
    val durationMinutes: Int,
)

data class GroupFinanceDefaultsReadResponse(
    val defaultGameFeeCents: Long?,
    val monthlyFeeCents: Long?,
    val monthlyDueDay: Int?,
)

class GroupNotFoundException : RuntimeException()

class AccessForbiddenException : RuntimeException()

@RestController
class AccessGroupReadController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val getGroup: GetGroup,
) {
    @GetMapping("/api/groups/{groupId}")
    fun get(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
    ): ResponseEntity<GroupReadResponse> {
        val actor = actorResolver.resolve(identity)
        val parsedGroupId = runCatching { UUID.fromString(groupId) }.getOrNull()
            ?: throw GroupNotFoundException()
        return when (val result = getGroup.execute(actor, parsedGroupId)) {
            GetGroupResult.GroupNotFound -> throw GroupNotFoundException()
            GetGroupResult.AccessForbidden -> throw AccessForbiddenException()
            is GetGroupResult.Success -> ResponseEntity
                .ok()
                .eTag(result.group.version.toString())
                .body(result.group.toResponse())
        }
    }
}

fun GroupView.toResponse() = GroupReadResponse(
    id = id,
    name = name.value,
    timeZone = timeZone.value,
    role = role,
    version = version,
    profileStatus = profileStatus,
    privacy = "PRIVATE",
    currency = "BRL",
    profile = profile?.toResponse(),
    financeDefaults = financeDefaults?.toResponse(),
)

private fun GroupProfileReadModel.toResponse() = GroupProfileReadResponse(
    modality = modality,
    composition = composition,
    description = description,
    city = city,
    level = level,
    customLevel = customLevel,
    playStyle = playStyle,
    customPlayStyle = customPlayStyle,
    defaultVenue = defaultVenue?.toResponse(),
    regularSlots = regularSlots.map { it.toResponse() },
    defaultCapacity = defaultCapacity,
    defaultConfirmationLeadMinutes = defaultConfirmationLeadMinutes,
)

private fun GroupVenueReadModel.toResponse() = GroupVenueReadResponse(id, name, address, court)

private fun GroupRegularSlotReadModel.toResponse() =
    GroupRegularSlotReadResponse(id, weekday, startTime, durationMinutes)

private fun GroupFinanceDefaultsReadModel.toResponse() =
    GroupFinanceDefaultsReadResponse(defaultGameFeeCents, monthlyFeeCents, monthlyDueDay)
