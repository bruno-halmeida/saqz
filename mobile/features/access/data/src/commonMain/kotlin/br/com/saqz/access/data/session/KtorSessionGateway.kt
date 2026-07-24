package br.com.saqz.access.data.session

import br.com.saqz.access.domain.session.AccessError
import br.com.saqz.access.domain.session.AccessMembership
import br.com.saqz.access.domain.session.AccessMembershipRole
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.network.ApiProblem
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import br.com.saqz.network.NetworkRequest
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class SessionUserDto(
    val id: String,
    val email: String?,
    val displayName: String,
    val phone: String? = null,
    val phoneRequired: Boolean = false,
)

@Serializable
internal data class CompleteProfileRequestDto(
    val phone: String,
    val displayName: String? = null,
)

@Serializable
internal data class SessionMembershipDto(
    val groupId: String,
    val groupName: String,
    val role: String,
)

@Serializable
internal data class SessionDto(
    val user: SessionUserDto,
    val memberships: List<SessionMembershipDto>,
)

class KtorSessionGateway(
    private val network: AuthenticatedNetworkClient,
    private val json: Json = Json { explicitNulls = false },
) : SessionGateway {
    override suspend fun bootstrap(): SaqzResult<AccessSession, AccessError> =
        retryTransport(RetrySafety.Never) {
            network.execute(HttpMethod.Put, "api/session", SessionDto.serializer())
        }.toAccessResult()

    override suspend fun completeProfile(
        phone: String,
        displayName: String?,
    ): SaqzResult<AccessSession, AccessError> = network.execute(
        HttpMethod.Patch,
        "api/session/profile",
        SessionDto.serializer(),
        NetworkRequest(json.encodeToString(CompleteProfileRequestDto(phone, displayName))),
    ).toAccessResult()
}

private fun NetworkResult<SessionDto>.toAccessResult(): SaqzResult<AccessSession, AccessError> = when (this) {
    is NetworkResult.Success -> value.toAccessSession()
    is NetworkResult.Failure -> SaqzResult.Failure(error.toAccessError())
}

private fun SessionDto.toAccessSession(): SaqzResult<AccessSession, AccessError> {
    if (user.id.isBlank() || user.displayName.isBlank()) return invalidResponse()
    if (memberships.any { it.groupId.isBlank() || it.groupName.isBlank() || it.role.isBlank() }) {
        return invalidResponse()
    }
    return SaqzResult.Success(
        AccessSession(
            user = AccessUser(user.id, user.email, user.displayName, user.phone, user.phoneRequired),
            memberships = memberships.map {
                AccessMembership(
                    groupId = GroupId(it.groupId),
                    groupName = it.groupName,
                    role = AccessMembershipRole(it.role),
                )
            },
        ),
    )
}

private fun invalidResponse(): SaqzResult.Failure<AccessError> =
    SaqzResult.Failure(AccessError.DataFailure(DataError.InvalidResponse))

private fun NetworkError.toAccessError(): AccessError = when (this) {
    is NetworkError.ApiProblemError -> problem.toAccessError()
    is NetworkError.HttpStatus -> status.toDataError().asAccessError()
    NetworkError.Timeout -> DataError.Timeout.asAccessError()
    NetworkError.Connectivity -> DataError.Connectivity.asAccessError()
    NetworkError.InvalidResponse -> DataError.InvalidResponse.asAccessError()
    NetworkError.PayloadTooLarge -> DataError.PayloadTooLarge.asAccessError()
    NetworkError.Unavailable,
    NetworkError.Unknown,
    -> DataError.Unknown.asAccessError()
}

private fun ApiProblem.toAccessError(): AccessError = when {
    status == 401 -> AccessError.Unauthenticated
    status == 403 && code == "EMAIL_NOT_VERIFIED" -> AccessError.EmailNotVerified
    status == 403 -> AccessError.Forbidden
    code == "VALIDATION_FAILED" || fieldErrors != null -> AccessError.Validation(
        ValidationDetails(globalMessages = emptyList(), fieldMessages = fieldErrors.orEmpty()),
    )
    else -> status.toDataError().asAccessError()
}

private fun Int.toDataError(): DataError = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}

private fun DataError.asAccessError() = AccessError.DataFailure(this)
