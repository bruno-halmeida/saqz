package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.settings.UpdateGroupSettings
import br.com.saqz.groups.application.settings.UpdateGroupSettingsField
import br.com.saqz.groups.application.settings.UpdateGroupSettingsResult
import br.com.saqz.groups.application.settings.UpdatedGroupSettings
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

class VersionConflictException : RuntimeException()

@RestController
class AccessGroupSettingsController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val updateGroupSettings: UpdateGroupSettings,
) {
    @PutMapping("/api/groups/{groupId}/settings")
    fun update(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @RequestHeader(name = "If-Match", required = false) ifMatch: String?,
        @RequestBody request: UpdateGroupSettingsRequest,
    ): ResponseEntity<GroupSettingsResponse> {
        val actor = actorResolver.resolve(identity)
        val parsedGroupId = runCatching { UUID.fromString(groupId) }.getOrNull()
            ?: throw GroupNotFoundException()
        val expectedVersion = ifMatch
            ?.let(QUOTED_VERSION::matchEntire)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?: throw InvalidGroupRequestException(
                mapOf("ifMatch" to listOf("must be a quoted positive version")),
            )
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
            is UpdateGroupSettingsResult.Success -> ResponseEntity
                .ok()
                .eTag(result.settings.version.toString())
                .body(result.settings.toResponse())
        }
    }

    private companion object {
        val QUOTED_VERSION = Regex("\"([1-9][0-9]*)\"")
    }
}

private fun UpdatedGroupSettings.toResponse() = GroupSettingsResponse(
    id = id,
    name = name.value,
    timeZone = timeZone.value,
    role = role,
    version = version,
)
