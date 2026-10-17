package br.com.saqz.groups.data.attendance.share

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.share.*
import br.com.saqz.network.*
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class ResolvedAttendanceLinkDto(
    val groupId: String, val gameId: String, val registrationRequired: Boolean = false,
)

@Serializable
private data class ResolveAttendanceLinkRequestDto(val code: String)

class KtorAttendanceSharingGateway(
    private val network: AuthenticatedNetworkClient,
    private val json: Json = Json { explicitNulls = false },
) : AttendanceSharingGateway {
    override suspend fun resolveLink(code: AttendanceLinkCode) = network.execute(
        HttpMethod.Post,
        "api/attendance-links/resolve",
        ResolvedAttendanceLinkDto.serializer(),
        NetworkRequest(json.encodeToString(ResolveAttendanceLinkRequestDto(code.value))),
    ).toDestinationResult()
}

private fun NetworkResult<ResolvedAttendanceLinkDto>.toDestinationResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toSharingError())
    is NetworkResult.Success -> if (value.groupId.isBlank() || value.gameId.isBlank()) invalidResponse()
    else SaqzResult.Success(AttendanceLinkDestination(GroupId(value.groupId), value.gameId, value.registrationRequired))
}

private fun NetworkError.toSharingError(): AttendanceSharingError = when (this) {
    is NetworkError.ApiProblemError -> when (problem.code) {
        // `reason` é aditivo: sem ele (ou com um valor que este app não conhece) vale a mensagem de sempre.
        "ATTENDANCE_LINK_INVALID_OR_EXPIRED" -> when (problem.reason) {
            "GAME_STARTED" -> AttendanceSharingError.GameClosed(AttendanceLinkClosedReason.GameStarted)
            "GAME_CANCELLED" -> AttendanceSharingError.GameClosed(AttendanceLinkClosedReason.GameCancelled)
            "GAME_UNPUBLISHED" -> AttendanceSharingError.GameClosed(AttendanceLinkClosedReason.GameUnpublished)
            else -> AttendanceSharingError.InvalidOrExpired
        }
        "ATTENDANCE_LINK_ATTEMPT_LIMIT" -> AttendanceSharingError.AttemptLimit(problem.retryAfterSeconds)
        else -> AttendanceSharingError.DataFailure(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> AttendanceSharingError.DataFailure(status.toDataError())
    NetworkError.Timeout -> AttendanceSharingError.DataFailure(DataError.Timeout)
    NetworkError.Connectivity -> AttendanceSharingError.DataFailure(DataError.Connectivity)
    NetworkError.InvalidResponse -> AttendanceSharingError.DataFailure(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> AttendanceSharingError.DataFailure(DataError.PayloadTooLarge)
    NetworkError.Unavailable, NetworkError.Unknown -> AttendanceSharingError.DataFailure(DataError.Unknown)
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

private fun <T> invalidResponse(): SaqzResult<T, AttendanceSharingError> = SaqzResult.Failure(
    AttendanceSharingError.DataFailure(DataError.InvalidResponse),
)
