package br.com.saqz.groups.data.attendance.share

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.share.*
import br.com.saqz.network.*
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class AttendanceLinkUrlDto(val url: String)

@Serializable
internal data class ResolvedAttendanceLinkDto(val groupId: String, val gameId: String)

@Serializable
internal data class AttendanceShareSnapshotPersonDto(
    val displayName: String,
    val waitlistPosition: Long? = null,
)

@Serializable
internal data class AttendanceShareSnapshotDto(
    val title: String,
    val startsAt: String,
    val timeZone: String,
    val venue: String,
    val capacity: Int,
    val confirmed: List<AttendanceShareSnapshotPersonDto>,
    val waitlisted: List<AttendanceShareSnapshotPersonDto>,
    val declined: List<AttendanceShareSnapshotPersonDto>,
)

@Serializable
private data class ResolveAttendanceLinkRequestDto(val code: String)

class KtorAttendanceSharingGateway(
    private val network: AuthenticatedNetworkClient,
    private val json: Json = Json { explicitNulls = false },
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : AttendanceSharingGateway {
    override suspend fun rotateLink(groupId: GroupId, gameId: String) = network.execute(
        HttpMethod.Post,
        "api/groups/${groupId.value}/games/$gameId/attendance-link",
        AttendanceLinkUrlDto.serializer(),
    ).toLinkResult()

    override suspend fun resolveLink(code: AttendanceLinkCode) = network.execute(
        HttpMethod.Post,
        "api/attendance-links/resolve",
        ResolvedAttendanceLinkDto.serializer(),
        NetworkRequest(json.encodeToString(ResolveAttendanceLinkRequestDto(code.value))),
    ).toDestinationResult()

    override suspend fun readSnapshot(groupId: GroupId, gameId: String) =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "api/groups/${groupId.value}/games/$gameId/attendance-share",
                AttendanceShareSnapshotDto.serializer(),
            )
        }.toSnapshotResult()
}

private fun NetworkResult<AttendanceLinkUrlDto>.toLinkResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toSharingError())
    is NetworkResult.Success -> value.url.takeIf(String::isNotBlank)
        ?.let(::AttendanceLinkUrl)?.let { SaqzResult.Success(it) } ?: invalidResponse()
}

private fun NetworkResult<ResolvedAttendanceLinkDto>.toDestinationResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toSharingError())
    is NetworkResult.Success -> if (value.groupId.isBlank() || value.gameId.isBlank()) invalidResponse()
    else SaqzResult.Success(AttendanceLinkDestination(GroupId(value.groupId), value.gameId))
}

private fun NetworkResult<AttendanceShareSnapshotDto>.toSnapshotResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toSharingError())
    is NetworkResult.Success -> value.toDomain()?.let { SaqzResult.Success(it) } ?: invalidResponse()
}

private fun AttendanceShareSnapshotDto.toDomain(): AttendanceShareSnapshot? {
    if (title.isBlank() || startsAt.isBlank() || timeZone.isBlank() || venue.isBlank() || capacity < 0) return null
    fun List<AttendanceShareSnapshotPersonDto>.people(): List<AttendanceSharePerson>? =
        takeIf { list -> list.all { it.displayName.isNotBlank() && (it.waitlistPosition == null || it.waitlistPosition > 0) } }
            ?.map { AttendanceSharePerson(it.displayName, it.waitlistPosition) }
    return AttendanceShareSnapshot(title, startsAt, timeZone, venue, capacity, confirmed.people() ?: return null,
        waitlisted.people() ?: return null, declined.people() ?: return null)
}

private fun NetworkError.toSharingError(): AttendanceSharingError = when (this) {
    is NetworkError.ApiProblemError -> when (problem.code) {
        "ATTENDANCE_LINK_INVALID_OR_EXPIRED" -> AttendanceSharingError.InvalidOrExpired
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
