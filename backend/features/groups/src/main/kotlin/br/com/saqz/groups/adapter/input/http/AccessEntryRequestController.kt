package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.entryrequest.ApproveEntryRequest
import br.com.saqz.groups.application.entryrequest.ApproveEntryRequestResult
import br.com.saqz.groups.application.entryrequest.GroupEntryRequest
import br.com.saqz.groups.application.entryrequest.ListEntryRequests
import br.com.saqz.groups.application.entryrequest.ListEntryRequestsResult
import br.com.saqz.groups.application.entryrequest.RejectEntryRequest
import br.com.saqz.groups.application.entryrequest.RejectEntryRequestResult
import br.com.saqz.groups.application.entryrequest.SaveEntryRequestAthleteProfile
import br.com.saqz.groups.application.entryrequest.SaveEntryRequestAthleteProfileCommand
import br.com.saqz.groups.application.entryrequest.SaveEntryRequestAthleteProfileResult
import br.com.saqz.groups.application.membership.AccessMembership
import br.com.saqz.groups.domain.AthleteLevel
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.AthletePreferredSide
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus
import java.time.Instant
import java.util.UUID

data class EntryRequestResponse(
    val userId: UUID,
    val displayName: String,
    val requestedAt: Instant,
)

data class EntryRequestAthleteProfileRequest @JsonCreator constructor(
    @JsonProperty("nickname") val nickname: String?,
    @JsonProperty("position") val position: String?,
    @JsonProperty("secondaryPosition") val secondaryPosition: String?,
    @JsonProperty("level") val level: String?,
    @JsonProperty("preferredSide") val preferredSide: String?,
    @JsonProperty("heightCm") val heightCm: Int?,
)

class EntryRequestNotFoundException : RuntimeException()

class EntryRequestAthleteLimitExceededException : RuntimeException()

@RestController
class AccessEntryRequestController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val listEntryRequests: ListEntryRequests,
    private val approveEntryRequest: ApproveEntryRequest,
    private val rejectEntryRequest: RejectEntryRequest,
    private val saveEntryRequestAthleteProfile: SaveEntryRequestAthleteProfile,
) {
    @GetMapping("/api/groups/{groupId}/entry-requests")
    fun list(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
    ): List<EntryRequestResponse> = when (
        val result = listEntryRequests.execute(actorResolver.resolve(identity), parseId(groupId))
    ) {
        ListEntryRequestsResult.GroupNotFound -> throw GroupNotFoundException()
        ListEntryRequestsResult.AccessForbidden -> throw AccessForbiddenException()
        is ListEntryRequestsResult.Success -> result.requests.map(GroupEntryRequest::toResponse)
    }

    @PostMapping("/api/groups/{groupId}/entry-requests/{userId}/approve")
    fun approve(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @PathVariable userId: String,
    ): AccessMembershipResponse = when (
        val result = approveEntryRequest.execute(
            actorResolver.resolve(identity),
            parseId(groupId),
            parseUserId(userId),
        )
    ) {
        ApproveEntryRequestResult.GroupNotFound -> throw GroupNotFoundException()
        ApproveEntryRequestResult.AccessForbidden -> throw AccessForbiddenException()
        ApproveEntryRequestResult.RequestNotFound -> throw EntryRequestNotFoundException()
        ApproveEntryRequestResult.AthleteLimitExceeded -> throw EntryRequestAthleteLimitExceededException()
        is ApproveEntryRequestResult.Success -> result.membership.toEntryMembershipResponse()
    }

    @PutMapping("/api/groups/{groupId}/entry-requests/me/athlete-profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun saveOwnAthleteProfile(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @RequestBody request: EntryRequestAthleteProfileRequest,
    ) {
        val actor = actorResolver.resolve(identity)
        val parsedGroupId = parseId(groupId)
        val command = SaveEntryRequestAthleteProfileCommand(
            groupId = parsedGroupId,
            userId = actor,
            nickname = request.nickname,
            position = request.position?.let { parseEnum<AthletePosition>(it, "position") },
            secondaryPosition = request.secondaryPosition?.let { parseEnum<AthletePosition>(it, "secondaryPosition") },
            level = request.level?.let { parseEnum<AthleteLevel>(it, "level") },
            preferredSide = request.preferredSide?.let { parseEnum<AthletePreferredSide>(it, "preferredSide") },
            heightCm = request.heightCm,
        )
        when (val result = saveEntryRequestAthleteProfile.execute(actor, parsedGroupId, command)) {
            SaveEntryRequestAthleteProfileResult.GroupNotFound -> throw GroupNotFoundException()
            SaveEntryRequestAthleteProfileResult.RequestNotFound -> throw EntryRequestNotFoundException()
            is SaveEntryRequestAthleteProfileResult.Invalid ->
                throw InvalidGroupRequestException(result.fieldErrors, status = 422)
            SaveEntryRequestAthleteProfileResult.Success -> Unit
        }
    }

    @DeleteMapping("/api/groups/{groupId}/entry-requests/{userId}")
    fun reject(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @PathVariable userId: String,
    ): ResponseEntity<Void> {
        when (
            rejectEntryRequest.execute(
                actorResolver.resolve(identity),
                parseId(groupId),
                parseUserId(userId),
            )
        ) {
            RejectEntryRequestResult.GroupNotFound -> throw GroupNotFoundException()
            RejectEntryRequestResult.AccessForbidden -> throw AccessForbiddenException()
            RejectEntryRequestResult.Success -> Unit
        }
        return ResponseEntity.noContent().build()
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String, field: String): T =
        runCatching { enumValueOf<T>(raw) }.getOrNull()
            ?: throw InvalidGroupRequestException(mapOf(field to listOf("is invalid")))

    private fun parseId(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull()
        ?: throw GroupNotFoundException()

    private fun parseUserId(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull()
        ?: throw EntryRequestNotFoundException()
}

private fun GroupEntryRequest.toResponse() = EntryRequestResponse(userId, displayName.value, requestedAt)

private fun AccessMembership.toEntryMembershipResponse() = AccessMembershipResponse(userId, displayName.value, role)
