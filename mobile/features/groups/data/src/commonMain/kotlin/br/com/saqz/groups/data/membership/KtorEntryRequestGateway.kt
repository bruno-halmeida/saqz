package br.com.saqz.groups.data.membership

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.EntryRequestError
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.domain.membership.GroupEntryRequestGateway
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
private data class EntryRequestTransport(
    val userId: String = "",
    val displayName: String = "",
    val requestedAt: String = "",
)

@Serializable
private enum class GroupRoleTransport {
    OWNER,
    ADMIN,
    ATHLETE,
}

@Serializable
private data class ApprovedMembershipTransport(
    val userId: String = "",
    val displayName: String = "",
    val role: GroupRoleTransport? = null,
)

class KtorEntryRequestGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : GroupEntryRequestGateway {
    override suspend fun list(groupId: GroupId): SaqzResult<List<GroupEntryRequest>, EntryRequestError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "api/groups/${groupId.value}/entry-requests",
                ListSerializer(EntryRequestTransport.serializer()),
            )
        }.toListResult()

    override suspend fun approve(
        groupId: GroupId,
        userId: String,
    ): SaqzResult<GroupMembership, EntryRequestError> = network.execute(
        HttpMethod.Post,
        "api/groups/${groupId.value}/entry-requests/$userId/approve",
        ApprovedMembershipTransport.serializer(),
    ).toMembershipResult()

    override suspend fun reject(groupId: GroupId, userId: String) = network.executeNoContent(
        HttpMethod.Delete,
        "api/groups/${groupId.value}/entry-requests/$userId",
    ).toEmptyResult()
}

private fun NetworkResult<List<EntryRequestTransport>>.toListResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomainError())
    is NetworkResult.Success -> value.map { it.toDomain() }
        .takeIf { requests -> requests.none { it == null } }
        ?.filterNotNull()
        ?.let { SaqzResult.Success(it) }
        ?: invalidResponse()
}

private fun NetworkResult<ApprovedMembershipTransport>.toMembershipResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomainError())
    is NetworkResult.Success -> value.toDomain()?.let { SaqzResult.Success(it) } ?: invalidResponse()
}

private fun NetworkResult<Unit>.toEmptyResult() = when (this) {
    is NetworkResult.Success -> SaqzResult.Success(Unit)
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomainError())
}

private fun EntryRequestTransport.toDomain(): GroupEntryRequest? =
    if (userId.isBlank() || displayName.isBlank() || requestedAt.isBlank()) {
        null
    } else {
        GroupEntryRequest(userId, displayName, requestedAt)
    }

private fun ApprovedMembershipTransport.toDomain(): GroupMembership? {
    val domainRole = role ?: return null
    return if (userId.isBlank() || displayName.isBlank()) {
        null
    } else {
        GroupMembership(userId, displayName, GroupRole.valueOf(domainRole.name))
    }
}

private fun NetworkError.toDomainError(): EntryRequestError = EntryRequestError.DataFailure(
    when (this) {
        is NetworkError.ApiProblemError -> problem.status.toDataError()
        is NetworkError.HttpStatus -> status.toDataError()
        NetworkError.Timeout -> DataError.Timeout
        NetworkError.Connectivity -> DataError.Connectivity
        NetworkError.InvalidResponse -> DataError.InvalidResponse
        NetworkError.PayloadTooLarge -> DataError.PayloadTooLarge
        NetworkError.Unavailable,
        NetworkError.Unknown,
        -> DataError.Unknown
    },
)

private fun Int.toDataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}

private fun <T> invalidResponse(): SaqzResult<T, EntryRequestError> = SaqzResult.Failure(
    EntryRequestError.DataFailure(DataError.InvalidResponse),
)
