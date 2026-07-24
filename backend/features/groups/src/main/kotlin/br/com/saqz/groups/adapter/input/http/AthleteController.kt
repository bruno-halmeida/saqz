package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.athlete.AthleteMembership
import br.com.saqz.groups.application.athlete.AthleteRosterEntry
import br.com.saqz.groups.application.athlete.AthleteRosterFilter
import br.com.saqz.groups.application.athlete.FinancialStatus
import br.com.saqz.groups.application.athlete.GetOwnAthleteProfile
import br.com.saqz.groups.application.athlete.GetOwnAthleteProfileResult
import br.com.saqz.groups.application.athlete.ListAthletes
import br.com.saqz.groups.application.athlete.ListAthletesResult
import br.com.saqz.groups.application.athlete.RemoveAthlete
import br.com.saqz.groups.application.athlete.RemoveAthleteResult
import br.com.saqz.groups.application.athlete.UpdateAthlete
import br.com.saqz.groups.application.athlete.UpdateAthleteCommand
import br.com.saqz.groups.application.athlete.UpdateAthleteResult
import br.com.saqz.groups.application.athlete.UpdateOwnAthleteProfile
import br.com.saqz.groups.application.athlete.UpdateOwnAthleteProfileResult
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class UpdateOwnAthleteProfileRequest @JsonCreator constructor(
    @JsonProperty("position") val position: String?,
)

data class UpdateAthleteRequest @JsonCreator constructor(
    @JsonProperty("position") val position: String?,
    @JsonProperty("membershipType") val membershipType: String,
    @JsonProperty("active") val active: Boolean,
)

data class AthleteResponse(
    val userId: UUID,
    val displayName: String,
    val role: GroupRole,
    val position: String?,
    val membershipType: String,
    val active: Boolean,
)

data class AthleteRosterEntryResponse(
    val userId: UUID,
    val displayName: String,
    val phone: String?,
    val position: String?,
    val membershipType: String,
    val active: Boolean,
    val financialStatus: String,
)

data class AthleteRosterResponse(val athletes: List<AthleteRosterEntryResponse>)

data class OwnAthleteMembershipResponse(
    val groupId: UUID,
    val groupName: String,
    val role: GroupRole,
    val position: String?,
    val membershipType: String,
    val active: Boolean,
)

data class OwnAthleteProfileResponse(
    val userId: UUID,
    val displayName: String,
    val phone: String?,
    val memberships: List<OwnAthleteMembershipResponse>,
)

@RestController
class AthleteController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val listAthletes: ListAthletes,
    private val updateOwnAthleteProfile: UpdateOwnAthleteProfile,
    private val updateAthlete: UpdateAthlete,
    private val removeAthlete: RemoveAthlete,
    private val getOwnAthleteProfile: GetOwnAthleteProfile,
) {
    @GetMapping("/api/groups/{groupId}/athletes")
    fun roster(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @RequestParam("search", required = false) search: String?,
        @RequestParam("type", required = false) type: String?,
        @RequestParam("position", required = false) position: String?,
        @RequestParam("financialStatus", required = false) financialStatus: String?,
        @RequestParam("includeInactive", required = false, defaultValue = "false") includeInactive: Boolean,
    ): AthleteRosterResponse {
        val filter = AthleteRosterFilter(
            search = search,
            membershipType = type?.let { parseEnum<AthleteMembershipType>(it, "type") },
            position = position?.let { parseEnum<AthletePosition>(it, "position") },
            financialStatus = financialStatus?.let { parseEnum<FinancialStatus>(it, "financialStatus") },
            includeInactive = includeInactive,
        )
        return when (val result = listAthletes.execute(actor(identity), parseId(groupId), filter)) {
            ListAthletesResult.GroupNotFound -> throw GroupNotFoundException()
            ListAthletesResult.AccessForbidden -> throw AccessForbiddenException()
            is ListAthletesResult.Success -> AthleteRosterResponse(result.athletes.map(AthleteRosterEntry::toResponse))
        }
    }

    @PatchMapping("/api/groups/{groupId}/athletes/me")
    fun updateOwn(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @RequestBody request: UpdateOwnAthleteProfileRequest,
    ): AthleteResponse {
        val position = request.position?.let { parseEnum<AthletePosition>(it, "position") }
        return when (val result = updateOwnAthleteProfile.execute(actor(identity), parseId(groupId), position)) {
            UpdateOwnAthleteProfileResult.GroupNotFound -> throw GroupNotFoundException()
            is UpdateOwnAthleteProfileResult.Success -> result.athlete.toResponse()
        }
    }

    @PatchMapping("/api/groups/{groupId}/athletes/{userId}")
    fun update(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @PathVariable("userId") userId: String,
        @RequestBody request: UpdateAthleteRequest,
    ): AthleteResponse {
        val command = UpdateAthleteCommand(
            groupId = parseId(groupId),
            userId = parseId(userId),
            position = request.position?.let { parseEnum<AthletePosition>(it, "position") },
            membershipType = parseEnum<AthleteMembershipType>(request.membershipType, "membershipType"),
            active = request.active,
        )
        return when (val result = updateAthlete.execute(actor(identity), command)) {
            UpdateAthleteResult.GroupNotFound -> throw GroupNotFoundException()
            UpdateAthleteResult.AccessForbidden -> throw AccessForbiddenException()
            is UpdateAthleteResult.Success -> result.athlete.toResponse()
        }
    }

    @DeleteMapping("/api/groups/{groupId}/athletes/{userId}")
    fun remove(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @PathVariable("userId") userId: String,
    ) {
        when (removeAthlete.execute(actor(identity), parseId(groupId), parseId(userId))) {
            RemoveAthleteResult.GroupNotFound -> throw GroupNotFoundException()
            RemoveAthleteResult.AccessForbidden,
            RemoveAthleteResult.OwnerImmutable,
            -> throw AccessForbiddenException()
            RemoveAthleteResult.Success -> Unit
        }
    }

    @GetMapping("/api/athletes/me")
    fun ownProfile(@AuthenticationPrincipal identity: RequestIdentity): OwnAthleteProfileResponse =
        when (val result = getOwnAthleteProfile.execute(actor(identity))) {
            GetOwnAthleteProfileResult.NotFound -> throw GroupNotFoundException()
            is GetOwnAthleteProfileResult.Success -> OwnAthleteProfileResponse(
                userId = result.profile.userId,
                displayName = result.profile.displayName.value,
                phone = result.profile.phone,
                memberships = result.profile.memberships.map {
                    OwnAthleteMembershipResponse(
                        groupId = it.groupId,
                        groupName = it.groupName.value,
                        role = it.role,
                        position = it.position?.name,
                        membershipType = it.membershipType.name,
                        active = it.active,
                    )
                },
            )
        }

    private fun actor(identity: RequestIdentity): UUID = actorResolver.resolve(identity)

    private fun parseId(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull()
        ?: throw GroupNotFoundException()

    private inline fun <reified T : Enum<T>> parseEnum(raw: String, field: String): T =
        runCatching { enumValueOf<T>(raw) }.getOrNull()
            ?: throw InvalidGroupRequestException(mapOf(field to listOf("is invalid")))
}

private fun AthleteMembership.toResponse() = AthleteResponse(
    userId = userId,
    displayName = displayName.value,
    role = role,
    position = position?.name,
    membershipType = membershipType.name,
    active = active,
)

private fun AthleteRosterEntry.toResponse() = AthleteRosterEntryResponse(
    userId = userId,
    displayName = displayName.value,
    phone = phone,
    position = position?.name,
    membershipType = membershipType.name,
    active = active,
    financialStatus = financialStatus.name,
)
