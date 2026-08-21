package br.com.saqz.groups.data.athlete

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.athlete.Athlete
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteFinancialStatus
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteLevel
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthletePreferredSide
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.athlete.AthleteStats
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.athlete.UpdateAthleteCommand
import br.com.saqz.groups.domain.athlete.UpdateOwnAthleteProfileCommand
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class AthleteDto(
    val userId: String = "",
    val displayName: String = "",
    val role: String = "",
    val position: String? = null,
    val membershipType: String = "",
    val active: Boolean = true,
    val nickname: String? = null,
    val secondaryPosition: String? = null,
    val level: String? = null,
    val preferredSide: String? = null,
    val heightCm: Int? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
)

@Serializable
private data class RosterEntryDto(
    val userId: String = "",
    val displayName: String = "",
    val role: String = "ATHLETE",
    val phone: String? = null,
    val position: String? = null,
    val membershipType: String = "",
    val active: Boolean = true,
    val financialStatus: String = "DESCONHECIDO",
    val nickname: String? = null,
    val secondaryPosition: String? = null,
    val level: String? = null,
    val preferredSide: String? = null,
    val heightCm: Int? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
    val joinedAt: String = "",
)

@Serializable
private data class RosterDto(val athletes: List<RosterEntryDto> = emptyList())

@Serializable
private data class OwnMembershipDto(
    val groupId: String = "",
    val groupName: String = "",
    val role: String = "",
    val position: String? = null,
    val membershipType: String = "",
    val active: Boolean = true,
    val nickname: String? = null,
    val secondaryPosition: String? = null,
    val level: String? = null,
    val preferredSide: String? = null,
    val heightCm: Int? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
    val joinedAt: String = "",
)

@Serializable
private data class OwnProfileDto(
    val userId: String = "",
    val displayName: String = "",
    val phone: String? = null,
    val memberships: List<OwnMembershipDto> = emptyList(),
)

@Serializable
private data class UpdateOwnProfileRequestDto(
    val nickname: String?,
    val position: String?,
    val secondaryPosition: String?,
    val level: String?,
    val preferredSide: String?,
    val heightCm: Int?,
)

@Serializable
private data class UpdateAthleteRequestDto(
    val nickname: String?,
    val position: String?,
    val secondaryPosition: String?,
    val level: String?,
    val preferredSide: String?,
    val heightCm: Int?,
    val membershipType: String,
    val active: Boolean,
    val monthlyFeeCents: Long?,
    val monthlyDueDay: Int?,
)

@Serializable
private data class AthleteStatsDto(
    val games: Int = 0,
    val attendanceRate: Int? = null,
    val absences: Int = 0,
)

class KtorAthleteGateway(
    private val network: AuthenticatedNetworkClient,
    private val json: Json = Json { explicitNulls = false },
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : AthleteGateway {
    override suspend fun roster(
        groupId: GroupId,
        filter: AthleteRosterFilter,
    ): SaqzResult<List<AthleteRosterEntry>, AthleteError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "api/groups/${groupId.value}/athletes${filter.toQuery()}",
                RosterDto.serializer(),
            )
        }.mapResult { dto -> dto.athletes.mapNotNullOrInvalid(RosterEntryDto::toDomain) }

    override suspend fun updateOwnPosition(
        groupId: GroupId,
        position: AthletePosition?,
    ): SaqzResult<Athlete, AthleteError> = updateOwnProfile(
        UpdateOwnAthleteProfileCommand(
            groupId = groupId,
            nickname = null,
            position = position,
            secondaryPosition = null,
            level = null,
            preferredSide = null,
            heightCm = null,
        ),
    )

    override suspend fun updateOwnProfile(
        command: UpdateOwnAthleteProfileCommand,
    ): SaqzResult<Athlete, AthleteError> = network.execute(
        HttpMethod.Patch,
        "api/groups/${command.groupId.value}/athletes/me",
        AthleteDto.serializer(),
        NetworkRequest(
            json.encodeToString(
                UpdateOwnProfileRequestDto(
                    nickname = command.nickname,
                    position = command.position?.name,
                    secondaryPosition = command.secondaryPosition?.name,
                    level = command.level?.name,
                    preferredSide = command.preferredSide?.name,
                    heightCm = command.heightCm,
                ),
            ),
        ),
    ).mapResult { it.toDomain() ?: return@mapResult null }

    override suspend fun updateAthlete(
        command: UpdateAthleteCommand,
    ): SaqzResult<Athlete, AthleteError> = network.execute(
        HttpMethod.Patch,
        "api/groups/${command.groupId.value}/athletes/${command.userId}",
        AthleteDto.serializer(),
        NetworkRequest(
            json.encodeToString(
                UpdateAthleteRequestDto(
                    nickname = command.nickname,
                    position = command.position?.name,
                    secondaryPosition = command.secondaryPosition?.name,
                    level = command.level?.name,
                    preferredSide = command.preferredSide?.name,
                    heightCm = command.heightCm,
                    membershipType = command.membershipType.name,
                    active = command.active,
                    monthlyFeeCents = command.monthlyFeeCents,
                    monthlyDueDay = command.monthlyDueDay,
                ),
            ),
        ),
    ).mapResult { it.toDomain() ?: return@mapResult null }

    override suspend fun stats(
        groupId: GroupId,
        userId: String,
    ): SaqzResult<AthleteStats, AthleteError> = retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
        network.execute(
            HttpMethod.Get,
            "api/groups/${groupId.value}/athletes/$userId/stats",
            AthleteStatsDto.serializer(),
        )
    }.mapResult { AthleteStats(it.games, it.attendanceRate, it.absences) }

    override suspend fun removeAthlete(
        groupId: GroupId,
        userId: String,
    ): SaqzResult<Unit, AthleteError> = network.executeNoContent(
        HttpMethod.Delete,
        "api/groups/${groupId.value}/athletes/$userId",
    ).mapResult { }

    override suspend fun ownProfile(): SaqzResult<OwnAthleteProfile, AthleteError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(HttpMethod.Get, "api/athletes/me", OwnProfileDto.serializer())
        }.mapResult { it.toDomain() ?: return@mapResult null }
}

private fun AthleteRosterFilter.toQuery(): String {
    val params = buildList {
        search?.takeIf(String::isNotBlank)?.let { add("search=${it.encodeURLParameter()}") }
        membershipType?.let { add("type=${it.name}") }
        position?.let { add("position=${it.name}") }
        financialStatus?.let { add("financialStatus=${it.name}") }
        if (includeInactive) add("includeInactive=true")
    }
    return if (params.isEmpty()) "" else "?${params.joinToString("&")}"
}

private inline fun <T, R> NetworkResult<T>.mapResult(
    transform: (T) -> R?,
): SaqzResult<R, AthleteError> = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toAthleteError())
    is NetworkResult.Success -> transform(value)
        ?.let { SaqzResult.Success(it) }
        ?: SaqzResult.Failure(AthleteError.DataFailure(DataError.InvalidResponse))
}

private inline fun <T, R : Any> List<T>.mapNotNullOrInvalid(transform: (T) -> R?): List<R>? {
    val mapped = map(transform)
    if (mapped.any { it == null }) return null
    return mapped.filterNotNull()
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    runCatching { enumValueOf<T>(name) }.getOrNull()

/** Campo opcional só é válido ausente, ou presente e reconhecido. Presente e não parseado é lixo. */
private fun unparsed(raw: String?, parsed: Any?) = raw != null && parsed == null

private fun RosterEntryDto.toDomain(): AthleteRosterEntry? {
    if (userId.isBlank() || displayName.isBlank()) return null
    val parsedRole = enumOrNull<GroupRole>(role)
    val parsedPosition = position?.let { enumOrNull<AthletePosition>(it) }
    val parsedMembership = enumOrNull<AthleteMembershipType>(membershipType)
    val parsedFinancial = enumOrNull<AthleteFinancialStatus>(financialStatus)
    val parsedSecondaryPosition = secondaryPosition?.let { enumOrNull<AthletePosition>(it) }
    val parsedLevel = level?.let { enumOrNull<AthleteLevel>(it) }
    val parsedPreferredSide = preferredSide?.let { enumOrNull<AthletePreferredSide>(it) }
    if (parsedRole == null || unparsed(position, parsedPosition) || parsedMembership == null || parsedFinancial == null) return null
    if (
        unparsed(secondaryPosition, parsedSecondaryPosition) ||
        unparsed(level, parsedLevel) ||
        unparsed(preferredSide, parsedPreferredSide)
    ) return null
    return AthleteRosterEntry(
        userId = userId,
        displayName = displayName,
        phone = phone,
        position = parsedPosition,
        membershipType = parsedMembership,
        active = active,
        financialStatus = parsedFinancial,
        nickname = nickname,
        secondaryPosition = parsedSecondaryPosition,
        level = parsedLevel,
        preferredSide = parsedPreferredSide,
        heightCm = heightCm,
        monthlyFeeCents = monthlyFeeCents,
        monthlyDueDay = monthlyDueDay,
        joinedAt = joinedAt,
        role = parsedRole,
    )
}

private fun AthleteDto.toDomain(): Athlete? {
    if (userId.isBlank() || displayName.isBlank()) return null
    val parsedRole = enumOrNull<GroupRole>(role)
    val parsedPosition = position?.let { enumOrNull<AthletePosition>(it) }
    val parsedMembership = enumOrNull<AthleteMembershipType>(membershipType)
    val parsedSecondaryPosition = secondaryPosition?.let { enumOrNull<AthletePosition>(it) }
    val parsedLevel = level?.let { enumOrNull<AthleteLevel>(it) }
    val parsedPreferredSide = preferredSide?.let { enumOrNull<AthletePreferredSide>(it) }
    if (parsedRole == null || unparsed(position, parsedPosition) || parsedMembership == null) return null
    if (
        unparsed(secondaryPosition, parsedSecondaryPosition) ||
        unparsed(level, parsedLevel) ||
        unparsed(preferredSide, parsedPreferredSide)
    ) return null
    return Athlete(
        userId = userId,
        displayName = displayName,
        role = parsedRole,
        position = parsedPosition,
        membershipType = parsedMembership,
        active = active,
        nickname = nickname,
        secondaryPosition = parsedSecondaryPosition,
        level = parsedLevel,
        preferredSide = parsedPreferredSide,
        heightCm = heightCm,
        monthlyFeeCents = monthlyFeeCents,
        monthlyDueDay = monthlyDueDay,
    )
}

private fun OwnMembershipDto.toDomain(): OwnAthleteMembership? {
    if (groupId.isBlank() || groupName.isBlank()) return null
    val parsedRole = enumOrNull<GroupRole>(role)
    val parsedPosition = position?.let { enumOrNull<AthletePosition>(it) }
    val parsedMembership = enumOrNull<AthleteMembershipType>(membershipType)
    val parsedSecondaryPosition = secondaryPosition?.let { enumOrNull<AthletePosition>(it) }
    val parsedLevel = level?.let { enumOrNull<AthleteLevel>(it) }
    val parsedPreferredSide = preferredSide?.let { enumOrNull<AthletePreferredSide>(it) }
    if (parsedRole == null || unparsed(position, parsedPosition) || parsedMembership == null) return null
    if (
        unparsed(secondaryPosition, parsedSecondaryPosition) ||
        unparsed(level, parsedLevel) ||
        unparsed(preferredSide, parsedPreferredSide)
    ) return null
    return OwnAthleteMembership(
        groupId = GroupId(groupId),
        groupName = groupName,
        role = parsedRole,
        position = parsedPosition,
        membershipType = parsedMembership,
        active = active,
        nickname = nickname,
        secondaryPosition = parsedSecondaryPosition,
        level = parsedLevel,
        preferredSide = parsedPreferredSide,
        heightCm = heightCm,
        monthlyFeeCents = monthlyFeeCents,
        monthlyDueDay = monthlyDueDay,
        joinedAt = joinedAt,
    )
}

private fun OwnProfileDto.toDomain(): OwnAthleteProfile? {
    if (userId.isBlank() || displayName.isBlank()) return null
    val mapped = memberships.mapNotNullOrInvalid { it.toDomain() } ?: return null
    return OwnAthleteProfile(userId, displayName, phone, mapped)
}

private fun NetworkError.toAthleteError(): AthleteError = when (this) {
    is NetworkError.ApiProblemError -> when {
        problem.code == "VALIDATION_FAILED" || problem.status == 400 -> AthleteError.Validation(
            ValidationDetails(emptyList(), problem.fieldErrors.orEmpty()),
        )
        else -> AthleteError.DataFailure(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> AthleteError.DataFailure(status.toDataError())
    NetworkError.Timeout -> AthleteError.DataFailure(DataError.Timeout)
    NetworkError.Connectivity -> AthleteError.DataFailure(DataError.Connectivity)
    NetworkError.InvalidResponse -> AthleteError.DataFailure(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> AthleteError.DataFailure(DataError.PayloadTooLarge)
    NetworkError.Unavailable, NetworkError.Unknown -> AthleteError.DataFailure(DataError.Unknown)
}

private fun Int.toDataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}
