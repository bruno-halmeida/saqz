package br.com.saqz.groups.data.athlete

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.athlete.Athlete
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteFinancialStatus
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.athlete.UpdateAthleteCommand
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
)

@Serializable
private data class RosterEntryDto(
    val userId: String = "",
    val displayName: String = "",
    val phone: String? = null,
    val position: String? = null,
    val membershipType: String = "",
    val active: Boolean = true,
    val financialStatus: String = "",
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
)

@Serializable
private data class OwnProfileDto(
    val userId: String = "",
    val displayName: String = "",
    val phone: String? = null,
    val memberships: List<OwnMembershipDto> = emptyList(),
)

@Serializable
private data class UpdateOwnPositionRequestDto(val position: String?)

@Serializable
private data class UpdateAthleteRequestDto(
    val position: String?,
    val membershipType: String,
    val active: Boolean,
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
    ): SaqzResult<Athlete, AthleteError> = network.execute(
        HttpMethod.Patch,
        "api/groups/${groupId.value}/athletes/me",
        AthleteDto.serializer(),
        NetworkRequest(json.encodeToString(UpdateOwnPositionRequestDto(position?.name))),
    ).mapResult { it.toDomain() ?: return@mapResult null }

    override suspend fun updateAthlete(
        command: UpdateAthleteCommand,
    ): SaqzResult<Athlete, AthleteError> = network.execute(
        HttpMethod.Patch,
        "api/groups/${command.groupId.value}/athletes/${command.userId}",
        AthleteDto.serializer(),
        NetworkRequest(
            json.encodeToString(
                UpdateAthleteRequestDto(command.position?.name, command.membershipType.name, command.active),
            ),
        ),
    ).mapResult { it.toDomain() ?: return@mapResult null }

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

private fun RosterEntryDto.toDomain(): AthleteRosterEntry? {
    if (userId.isBlank() || displayName.isBlank()) return null
    return AthleteRosterEntry(
        userId = userId,
        displayName = displayName,
        phone = phone,
        position = position?.let { runCatching { AthletePosition.valueOf(it) }.getOrNull() ?: return null },
        membershipType = runCatching { AthleteMembershipType.valueOf(membershipType) }.getOrNull() ?: return null,
        active = active,
        financialStatus = runCatching { AthleteFinancialStatus.valueOf(financialStatus) }.getOrNull() ?: return null,
    )
}

private fun AthleteDto.toDomain(): Athlete? {
    if (userId.isBlank() || displayName.isBlank()) return null
    return Athlete(
        userId = userId,
        displayName = displayName,
        role = runCatching { GroupRole.valueOf(role) }.getOrNull() ?: return null,
        position = position?.let { runCatching { AthletePosition.valueOf(it) }.getOrNull() ?: return null },
        membershipType = runCatching { AthleteMembershipType.valueOf(membershipType) }.getOrNull() ?: return null,
        active = active,
    )
}

private fun OwnProfileDto.toDomain(): OwnAthleteProfile? {
    if (userId.isBlank() || displayName.isBlank()) return null
    val mapped = memberships.map { dto ->
        if (dto.groupId.isBlank() || dto.groupName.isBlank()) return null
        OwnAthleteMembership(
            groupId = GroupId(dto.groupId),
            groupName = dto.groupName,
            role = runCatching { GroupRole.valueOf(dto.role) }.getOrNull() ?: return null,
            position = dto.position?.let { runCatching { AthletePosition.valueOf(it) }.getOrNull() ?: return null },
            membershipType = runCatching { AthleteMembershipType.valueOf(dto.membershipType) }.getOrNull()
                ?: return null,
            active = dto.active,
        )
    }
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
