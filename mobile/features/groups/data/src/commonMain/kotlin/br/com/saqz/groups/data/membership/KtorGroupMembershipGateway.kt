package br.com.saqz.groups.data.membership

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.GroupInviteUrl
import br.com.saqz.groups.domain.membership.GroupInviteMetadata
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private enum class GroupRoleDto {
    OWNER,
    ADMIN,
    ATHLETE,
}

@Serializable
private enum class AssignableGroupRoleDto {
    ADMIN,
    ATHLETE,
}

@Serializable
private data class MembershipDto(
    val userId: String = "",
    val displayName: String = "",
    val role: GroupRoleDto? = null,
)

@Serializable
private data class InviteUrlDto(val inviteUrl: String = "")

@Serializable
private data class InviteMetadataDto(
    val active: Boolean = false,
    val expiresAt: String? = null,
    val createdAt: String? = null,
    val createdByName: String? = null,
)

@Serializable
private data class ChangeRoleRequestDto(val role: AssignableGroupRoleDto)

class KtorGroupMembershipGateway(
    private val network: AuthenticatedNetworkClient,
    private val json: Json = Json { explicitNulls = false },
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : GroupMembershipGateway {
    override suspend fun listMemberships(
        groupId: GroupId,
    ): SaqzResult<List<GroupMembership>, GroupMembershipError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "api/groups/${groupId.value}/memberships",
                ListSerializer(MembershipDto.serializer()),
            )
        }.toMembershipsResult()

    override suspend fun changeRole(
        command: ChangeMembershipRoleCommand,
    ): SaqzResult<GroupMembership, GroupMembershipError> = network.execute(
        HttpMethod.Put,
        "api/groups/${command.groupId.value}/memberships/${command.userId}/role",
        MembershipDto.serializer(),
        NetworkRequest(
            json.encodeToString(ChangeRoleRequestDto(AssignableGroupRoleDto.valueOf(command.role.name))),
        ),
    ).toMembershipResult()

    override suspend fun rotateInvite(
        groupId: GroupId,
    ): SaqzResult<GroupInviteUrl, GroupMembershipError> = network.execute(
        HttpMethod.Post,
        "api/groups/${groupId.value}/invite",
        InviteUrlDto.serializer(),
    ).toInviteUrlResult()

    override suspend fun readInviteMetadata(
        groupId: GroupId,
    ): SaqzResult<GroupInviteMetadata, GroupMembershipError> = retryTransport(
        RetrySafety.Read,
        delayMillis = retryDelay,
    ) {
        network.execute(
            HttpMethod.Get,
            "api/groups/${groupId.value}/invite",
            InviteMetadataDto.serializer(),
        )
    }.toInviteMetadataResult()

    override suspend fun expireInvite(
        groupId: GroupId,
    ) = network.executeNoContent(
        HttpMethod.Delete,
        "api/groups/${groupId.value}/invite",
    ).toEmptyResult()

}

private fun NetworkResult<List<MembershipDto>>.toMembershipsResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomainError())
    is NetworkResult.Success -> value.map { it.toDomain() }
        .takeIf { memberships -> memberships.none { it == null } }
        ?.filterNotNull()
        ?.let { SaqzResult.Success(it) }
        ?: invalidResponse()
}

private fun NetworkResult<MembershipDto>.toMembershipResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomainError())
    is NetworkResult.Success -> value.toDomain()?.let { SaqzResult.Success(it) } ?: invalidResponse()
}

private fun NetworkResult<InviteUrlDto>.toInviteUrlResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomainError())
    is NetworkResult.Success -> value.inviteUrl
        .takeIf(String::isNotBlank)
        ?.let(::GroupInviteUrl)
        ?.let { SaqzResult.Success(it) }
        ?: invalidResponse()
}

private fun NetworkResult<InviteMetadataDto>.toInviteMetadataResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomainError())
    is NetworkResult.Success -> value.toDomain()?.let { SaqzResult.Success(it) } ?: invalidResponse()
}

private fun NetworkResult<Unit>.toEmptyResult() = when (this) {
    is NetworkResult.Success -> SaqzResult.Success(Unit)
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomainError())
}

private fun MembershipDto.toDomain(): GroupMembership? {
    val domainRole = role ?: return null
    if (userId.isBlank() || displayName.isBlank()) return null
    return GroupMembership(userId, displayName, GroupRole.valueOf(domainRole.name))
}

private fun InviteMetadataDto.toDomain(): GroupInviteMetadata? {
    if (active && expiresAt.isNullOrBlank()) return null
    if (!active && listOf(createdAt, createdByName).any { it != null && it.isBlank() }) return null
    return GroupInviteMetadata(active, expiresAt, createdAt, createdByName)
}

private fun NetworkError.toDomainError(): GroupMembershipError = when (this) {
    is NetworkError.ApiProblemError -> when {
        problem.code == "INVITE_INVALID_OR_EXPIRED" -> GroupMembershipError.InvalidOrExpired
        problem.code == "INVITE_ATTEMPT_LIMIT" -> GroupMembershipError.AttemptLimit(problem.retryAfterSeconds)
        problem.code == "VALIDATION_FAILED" || problem.status == 400 -> GroupMembershipError.Validation(
            ValidationDetails(emptyList(), problem.fieldErrors.orEmpty()),
        )
        else -> GroupMembershipError.DataFailure(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> GroupMembershipError.DataFailure(status.toDataError())
    NetworkError.Timeout -> GroupMembershipError.DataFailure(DataError.Timeout)
    NetworkError.Connectivity -> GroupMembershipError.DataFailure(DataError.Connectivity)
    NetworkError.InvalidResponse -> GroupMembershipError.DataFailure(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> GroupMembershipError.DataFailure(DataError.PayloadTooLarge)
    NetworkError.Unavailable, NetworkError.Unknown -> GroupMembershipError.DataFailure(DataError.Unknown)
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

private fun invalidResponse() = SaqzResult.Failure(
    GroupMembershipError.DataFailure(DataError.InvalidResponse),
)
