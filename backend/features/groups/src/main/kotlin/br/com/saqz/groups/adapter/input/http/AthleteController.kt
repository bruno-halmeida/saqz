package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.athlete.AthleteMembership
import br.com.saqz.groups.application.athlete.AthleteRosterEntry
import br.com.saqz.groups.application.athlete.AthleteRosterFilter
import br.com.saqz.groups.application.athlete.AthleteStats
import br.com.saqz.groups.application.athlete.GetAthleteStats
import br.com.saqz.groups.application.athlete.GetAthleteStatsResult
import br.com.saqz.groups.application.athlete.FinancialStatus
import br.com.saqz.groups.application.athlete.GetOwnAthleteProfile
import br.com.saqz.groups.application.athlete.GetOwnAthleteProfileResult
import br.com.saqz.groups.application.athlete.ListAthletes
import br.com.saqz.groups.application.athlete.ListAthletesResult
import br.com.saqz.groups.application.athlete.ListAthletesSuccess
import br.com.saqz.groups.application.athlete.RemoveAthlete
import br.com.saqz.groups.application.athlete.RemoveAthleteResult
import br.com.saqz.groups.application.athlete.UpdateAthlete
import br.com.saqz.groups.application.athlete.UpdateAthleteCommand
import br.com.saqz.groups.application.athlete.UpdateAthleteResult
import br.com.saqz.groups.application.athlete.UpdateOwnAthleteProfile
import br.com.saqz.groups.application.athlete.UpdateOwnAthleteProfileCommand
import br.com.saqz.groups.application.athlete.UpdateOwnAthleteProfileResult
import br.com.saqz.groups.domain.AthleteLevel
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.AthletePreferredSide
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
import java.time.Instant
import java.util.UUID

data class UpdateOwnAthleteProfileRequest @JsonCreator constructor(
    @JsonProperty("nickname") val nickname: String?,
    @JsonProperty("position") val position: String?,
    @JsonProperty("secondaryPosition") val secondaryPosition: String?,
    @JsonProperty("level") val level: String?,
    @JsonProperty("preferredSide") val preferredSide: String?,
    @JsonProperty("heightCm") val heightCm: Int?,
)

data class UpdateAthleteRequest @JsonCreator constructor(
    @JsonProperty("nickname") val nickname: String?,
    @JsonProperty("position") val position: String?,
    @JsonProperty("secondaryPosition") val secondaryPosition: String?,
    @JsonProperty("level") val level: String?,
    @JsonProperty("preferredSide") val preferredSide: String?,
    @JsonProperty("heightCm") val heightCm: Int?,
    @JsonProperty("membershipType") val membershipType: String,
    @JsonProperty("active") val active: Boolean,
    @JsonProperty("monthlyFeeCents") val monthlyFeeCents: Long?,
    @JsonProperty("monthlyDueDay") val monthlyDueDay: Int?,
)

data class AthleteResponse(
    val userId: UUID,
    val displayName: String,
    val role: GroupRole,
    val position: String?,
    val membershipType: String,
    val active: Boolean,
    val nickname: String?,
    val secondaryPosition: String?,
    val level: String?,
    val preferredSide: String?,
    val heightCm: Int?,
    val monthlyFeeCents: Long?,
    val monthlyDueDay: Int?,
)

data class AthleteRosterEntryResponse(
    val userId: UUID,
    val displayName: String,
    val role: GroupRole,
    val phone: String?,
    val position: String?,
    val membershipType: String,
    val active: Boolean,
    val financialStatus: String,
    val nickname: String?,
    val secondaryPosition: String?,
    val level: String?,
    val preferredSide: String?,
    val heightCm: Int?,
    val monthlyFeeCents: Long?,
    val monthlyDueDay: Int?,
    val joinedAt: Instant,
)

data class AthleteRosterResponse(val athletes: List<AthleteRosterEntryResponse>)

data class OwnAthleteMembershipResponse(
    val groupId: UUID,
    val groupName: String,
    val role: GroupRole,
    val position: String?,
    val membershipType: String,
    val active: Boolean,
    val nickname: String?,
    val secondaryPosition: String?,
    val level: String?,
    val preferredSide: String?,
    val heightCm: Int?,
    val monthlyFeeCents: Long?,
    val monthlyDueDay: Int?,
    val joinedAt: Instant,
)

data class OwnAthleteProfileResponse(
    val userId: UUID,
    val displayName: String,
    val phone: String?,
    val memberships: List<OwnAthleteMembershipResponse>,
)

data class AthleteStatsResponse(
    val games: Int,
    val attendanceRate: Int?,
    val absences: Int,
)

@RestController
class AthleteController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val listAthletes: ListAthletes,
    private val updateOwnAthleteProfile: UpdateOwnAthleteProfile,
    private val updateAthlete: UpdateAthlete,
    private val removeAthlete: RemoveAthlete,
    private val getOwnAthleteProfile: GetOwnAthleteProfile,
    private val getAthleteStats: GetAthleteStats,
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
            is ListAthletesSuccess -> AthleteRosterResponse(
                result.athletes.map { it.toResponse(result.role) },
            )
            is ListAthletesResult.Success -> AthleteRosterResponse(
                result.athletes.map { it.toResponse(null) },
            )
        }
    }

    @PatchMapping("/api/groups/{groupId}/athletes/me")
    fun updateOwn(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @RequestBody request: UpdateOwnAthleteProfileRequest,
    ): AthleteResponse {
        val command = UpdateOwnAthleteProfileCommand(
            groupId = parseId(groupId),
            userId = actor(identity),
            nickname = request.nickname,
            position = request.position?.let { parseEnum<AthletePosition>(it, "position") },
            secondaryPosition = request.secondaryPosition?.let { parseEnum<AthletePosition>(it, "secondaryPosition") },
            level = request.level?.let { parseEnum<AthleteLevel>(it, "level") },
            preferredSide = request.preferredSide?.let { parseEnum<AthletePreferredSide>(it, "preferredSide") },
            heightCm = request.heightCm,
        )
        return when (val result = updateOwnAthleteProfile.execute(actor(identity), parseId(groupId), command)) {
            UpdateOwnAthleteProfileResult.GroupNotFound -> throw GroupNotFoundException()
            is UpdateOwnAthleteProfileResult.Invalid -> throw InvalidGroupRequestException(result.fieldErrors, status = 422)
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
            nickname = request.nickname,
            secondaryPosition = request.secondaryPosition?.let { parseEnum<AthletePosition>(it, "secondaryPosition") },
            level = request.level?.let { parseEnum<AthleteLevel>(it, "level") },
            preferredSide = request.preferredSide?.let { parseEnum<AthletePreferredSide>(it, "preferredSide") },
            heightCm = request.heightCm,
            monthlyFeeCents = request.monthlyFeeCents,
            monthlyDueDay = request.monthlyDueDay,
        )
        return when (val result = updateAthlete.execute(actor(identity), command)) {
            UpdateAthleteResult.GroupNotFound -> throw GroupNotFoundException()
            UpdateAthleteResult.AccessForbidden -> throw AccessForbiddenException()
            is UpdateAthleteResult.Invalid -> throw InvalidGroupRequestException(result.fieldErrors, status = 422)
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
                        nickname = it.nickname,
                        secondaryPosition = it.secondaryPosition?.name,
                        level = it.level?.name,
                        preferredSide = it.preferredSide?.name,
                        heightCm = it.heightCm,
                        monthlyFeeCents = it.monthlyFeeCents,
                        monthlyDueDay = it.monthlyDueDay,
                        joinedAt = it.joinedAt,
                    )
                },
            )
        }

    @GetMapping("/api/groups/{groupId}/athletes/{userId}/stats")
    fun stats(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @PathVariable("userId") userId: String,
    ): AthleteStatsResponse = when (
        val result = getAthleteStats.execute(actor(identity), parseId(groupId), parseId(userId))
    ) {
        GetAthleteStatsResult.GroupNotFound -> throw GroupNotFoundException()
        GetAthleteStatsResult.AccessForbidden -> throw AccessForbiddenException()
        is GetAthleteStatsResult.Success -> result.stats.toResponse()
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
    nickname = nickname,
    secondaryPosition = secondaryPosition?.name,
    level = level?.name,
    preferredSide = preferredSide?.name,
    heightCm = heightCm,
    monthlyFeeCents = monthlyFeeCents,
    monthlyDueDay = monthlyDueDay,
)

private fun AthleteRosterEntry.toResponse(viewerRole: GroupRole?): AthleteRosterEntryResponse {
    val canReadFinancial = viewerRole == GroupRole.OWNER || viewerRole == GroupRole.ADMIN
    return AthleteRosterEntryResponse(
        userId = userId,
        displayName = displayName.value,
        role = role,
        phone = phone,
        position = position?.name,
        membershipType = membershipType.name,
        active = active,
        nickname = nickname,
        secondaryPosition = secondaryPosition?.name,
        level = level?.name,
        preferredSide = preferredSide?.name,
        heightCm = heightCm,
        monthlyFeeCents = monthlyFeeCents.takeIf { canReadFinancial },
        monthlyDueDay = monthlyDueDay.takeIf { canReadFinancial },
        joinedAt = joinedAt,
        financialStatus = when (viewerRole) {
            GroupRole.OWNER, GroupRole.ADMIN -> financialStatus.name
            GroupRole.ATHLETE, null -> FinancialStatus.DESCONHECIDO.name
        },
    )
}

private fun AthleteStats.toResponse() = AthleteStatsResponse(games, attendanceRate, absences)
