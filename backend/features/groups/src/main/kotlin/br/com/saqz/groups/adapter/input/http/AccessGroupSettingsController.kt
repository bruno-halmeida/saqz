package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.settings.UpdateGroupSettings
import br.com.saqz.groups.application.settings.UpdateGroupSettingsField
import br.com.saqz.groups.application.settings.UpdateGroupSettingsResult
import br.com.saqz.groups.application.settings.UpdatedGroupSettings
import br.com.saqz.groups.application.settings.UpdateGroupProfileInput
import br.com.saqz.groups.application.read.GetGroup
import br.com.saqz.groups.application.read.GetGroupResult
import br.com.saqz.groups.domain.group.CourtPlayStyle
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.GroupProfileDefaultsInput
import br.com.saqz.groups.domain.group.GroupVenueInput
import br.com.saqz.groups.domain.group.RegularSlotInput
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.time.DayOfWeek
import java.time.LocalTime

data class UpdateGroupSettingsRequest @JsonCreator constructor(
    @JsonProperty("name") val name: String,
    @JsonProperty("timeZone") val timeZone: String,
)

data class GroupSettingsResponse(
    val id: UUID,
    val name: String,
    val timeZone: String,
    val role: GroupRole,
    val version: Long,
)

data class UpdateGroupProfileRequest @JsonCreator constructor(
    @JsonProperty("name") val name: String?,
    @JsonProperty("modality") val modality: GroupModality?,
    @JsonProperty("composition") val composition: GroupComposition?,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("city") val city: String? = null,
    @JsonProperty("level") val level: GroupLevel? = null,
    @JsonProperty("customLevel") val customLevel: String? = null,
    @JsonProperty("playStyle") val playStyle: CourtPlayStyle? = null,
    @JsonProperty("customPlayStyle") val customPlayStyle: String? = null,
    @JsonProperty("defaultVenue") val defaultVenue: UpdateGroupVenueRequest? = null,
    @JsonProperty("regularSlots") val regularSlots: List<UpdateRegularSlotRequest>? = null,
    @JsonProperty("defaultCapacity") val defaultCapacity: Int? = null,
    @JsonProperty("defaultConfirmationLeadMinutes") val defaultConfirmationLeadMinutes: Int? = null,
    @JsonProperty("defaultGameFeeCents") val defaultGameFeeCents: Long? = null,
    @JsonProperty("monthlyFeeCents") val monthlyFeeCents: Long? = null,
    @JsonProperty("monthlyDueDay") val monthlyDueDay: Int? = null,
)

data class UpdateGroupVenueRequest @JsonCreator constructor(
    @JsonProperty("id") val id: UUID? = null,
    @JsonProperty("name") val name: String?,
    @JsonProperty("address") val address: String?,
    @JsonProperty("court") val court: String? = null,
)

data class UpdateRegularSlotRequest @JsonCreator constructor(
    @JsonProperty("id") val id: UUID? = null,
    @JsonProperty("weekday") val weekday: DayOfWeek?,
    @JsonProperty("startTime") val startTime: LocalTime?,
    @JsonProperty("durationMinutes") val durationMinutes: Int?,
)

class VersionConflictException : RuntimeException()

class PreconditionRequiredException : RuntimeException()

@RestController
class AccessGroupSettingsController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val updateGroupSettings: UpdateGroupSettings,
    private val getGroup: GetGroup,
) {
    @PutMapping("/api/groups/{groupId}")
    fun updateProfile(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @RequestHeader(name = "If-Match", required = false) ifMatch: String?,
        @RequestBody request: UpdateGroupProfileRequest,
    ): ResponseEntity<GroupReadResponse> {
        val actor = actorResolver.resolve(identity)
        val parsedGroupId = parseGroupId(groupId)
        val expectedVersion = parseRequiredIfMatch(ifMatch)
        val result = updateGroupSettings.execute(actor, parsedGroupId, expectedVersion, request.toInput())
        return when (result) {
            UpdateGroupSettingsResult.GroupNotFound -> throw GroupNotFoundException()
            UpdateGroupSettingsResult.AccessForbidden -> throw AccessForbiddenException()
            UpdateGroupSettingsResult.VersionConflict -> throw VersionConflictException()
            is UpdateGroupSettingsResult.Invalid -> error("profile update returned legacy validation result")
            is UpdateGroupSettingsResult.InvalidProfile -> throw InvalidGroupRequestException(
                result.errors.groupBy({ it.field }, { it.message }),
            )
            is UpdateGroupSettingsResult.Success -> ResponseEntity
                .ok()
                .eTag(result.settings.version.toString())
                .body(loadUpdatedGroup(actor, parsedGroupId))
        }
    }

    @PutMapping("/api/groups/{groupId}/settings")
    fun update(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @RequestHeader(name = "If-Match", required = false) ifMatch: String?,
        @RequestBody request: UpdateGroupSettingsRequest,
    ): ResponseEntity<GroupSettingsResponse> {
        val actor = actorResolver.resolve(identity)
        val parsedGroupId = parseGroupId(groupId)
        val expectedVersion = parseLegacyIfMatch(ifMatch)
        return when (
            val result = updateGroupSettings.execute(
                actor,
                parsedGroupId,
                expectedVersion,
                request.name,
                request.timeZone,
            )
        ) {
            UpdateGroupSettingsResult.GroupNotFound -> throw GroupNotFoundException()
            UpdateGroupSettingsResult.AccessForbidden -> throw AccessForbiddenException()
            UpdateGroupSettingsResult.VersionConflict -> throw VersionConflictException()
            is UpdateGroupSettingsResult.Invalid -> throw InvalidGroupRequestException(
                result.fields.associate { field ->
                    when (field) {
                        UpdateGroupSettingsField.NAME ->
                            "name" to listOf("must be between 2 and 80 characters without controls")
                        UpdateGroupSettingsField.TIME_ZONE ->
                            "timeZone" to listOf("must be a valid IANA identifier")
                    }
                },
            )
            is UpdateGroupSettingsResult.InvalidProfile -> throw InvalidGroupRequestException(
                result.errors.groupBy({ it.field }, { it.message }),
            )
            is UpdateGroupSettingsResult.Success -> ResponseEntity
                .ok()
                .eTag(result.settings.version.toString())
                .body(result.settings.toResponse())
        }
    }

    private fun parseGroupId(groupId: String): UUID =
        runCatching { UUID.fromString(groupId) }.getOrNull() ?: throw GroupNotFoundException()

    private fun parseRequiredIfMatch(ifMatch: String?): Long {
        if (ifMatch == null) throw PreconditionRequiredException()
        return parseQuotedVersion(ifMatch)
    }

    private fun parseLegacyIfMatch(ifMatch: String?): Long =
        ifMatch?.let(::parseQuotedVersion)
            ?: throw InvalidGroupRequestException(
                mapOf("ifMatch" to listOf("must be a quoted positive version")),
            )

    private fun parseQuotedVersion(ifMatch: String): Long =
        QUOTED_VERSION.matchEntire(ifMatch)?.groupValues?.get(1)?.toLongOrNull()
            ?: throw InvalidGroupRequestException(
                mapOf("ifMatch" to listOf("must be a quoted positive version")),
            )

    private fun loadUpdatedGroup(actor: UUID, groupId: UUID): GroupReadResponse =
        when (val result = getGroup.execute(actor, groupId)) {
            is GetGroupResult.Success -> result.group.toResponse()
            GetGroupResult.GroupNotFound,
            GetGroupResult.AccessForbidden,
            -> error("updated group could not be read")
        }

    private companion object {
        val QUOTED_VERSION = Regex("\"([1-9][0-9]*)\"")
    }
}

private fun UpdateGroupProfileRequest.toInput() = UpdateGroupProfileInput(
    profile = GroupProfileDefaultsInput(
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
    ),
    defaultVenueId = defaultVenue?.id,
    regularSlotIds = regularSlots.orEmpty().map { it.id },
)

private fun UpdatedGroupSettings.toResponse() = GroupSettingsResponse(
    id = id,
    name = name.value,
    timeZone = timeZone.value,
    role = role,
    version = version,
)
