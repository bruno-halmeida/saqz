package br.com.saqz.groups.data.attendance

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.attendance.AttendanceAudit
import br.com.saqz.groups.domain.attendance.AttendanceCapacity
import br.com.saqz.groups.domain.attendance.AttendanceCapacityCommand
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceMutation
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken
import br.com.saqz.groups.domain.attendance.OverrideAttendanceCommand
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.attendance.VersionedAttendanceCapacity
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal enum class AttendanceStatusTransport {
    CONFIRMED,
    DECLINED,
    WAITLISTED,
}

@Serializable
internal enum class AttendanceIntentTransport {
    CONFIRM,
    DECLINE,
}

@Serializable
internal data class AttendanceEntryTransport(
    val memberId: String,
    val status: AttendanceStatusTransport,
    val waitlistPosition: Long? = null,
    val version: Long,
)

@Serializable
internal data class AttendanceAuditTransport(
    val actorId: String,
    val source: String,
    val oldStatus: AttendanceStatusTransport? = null,
    val newStatus: AttendanceStatusTransport,
    val reason: String? = null,
    val occurredAt: String,
)

@Serializable
internal data class AttendanceDetailTransport(
    val ownAttendance: AttendanceEntryTransport? = null,
    val confirmedCount: Int,
    val availableSpots: Int,
    val waitlistCount: Int,
    val capacity: Int,
)

@Serializable
internal data class AttendanceMutationTransport(
    val attendance: AttendanceEntryTransport,
    val audit: AttendanceAuditTransport? = null,
    val promotedCount: Int,
    val detail: AttendanceDetailTransport,
)

@Serializable
internal data class AttendanceCapacityTransport(
    val capacity: Int,
    val version: Long,
    val promotedCount: Int,
    val detail: AttendanceDetailTransport,
)

@Serializable
internal data class SelfAttendanceRequest(
    val requestId: String,
    val intent: AttendanceIntentTransport,
)

@Serializable
internal data class OverrideAttendanceRequest(
    val requestId: String,
    val memberId: String,
    val intent: AttendanceIntentTransport,
    val reason: String,
)

@Serializable
internal data class AttendanceCapacityRequest(
    val requestId: String,
    val capacity: Int,
)

class KtorAttendanceGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : AttendanceGateway {
    private val json = Json { explicitNulls = false }

    override suspend fun read(
        groupId: GroupId,
        gameId: String,
    ): SaqzResult<AttendanceDetail, AttendanceError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                attendanceRoute(groupId, gameId),
                AttendanceDetailTransport.serializer(),
            )
        }.detailResult()

    override suspend fun respond(
        groupId: GroupId,
        gameId: String,
        command: SelfAttendanceCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError> =
        retryTransport(command.requestId.safety(), delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Put,
                attendanceRoute(groupId, gameId),
                AttendanceMutationTransport.serializer(),
                NetworkRequest(json.encodeToString(command.toRequest())),
            )
        }.mutationResult()

    override suspend fun override(
        groupId: GroupId,
        gameId: String,
        command: OverrideAttendanceCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError> =
        retryTransport(command.requestId.safety(), delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "${attendanceRoute(groupId, gameId)}/override",
                AttendanceMutationTransport.serializer(),
                NetworkRequest(json.encodeToString(command.toRequest())),
            )
        }.mutationResult()

    override suspend fun capacity(
        groupId: GroupId,
        gameId: String,
        version: AttendanceVersionToken,
        command: AttendanceCapacityCommand,
    ): SaqzResult<VersionedAttendanceCapacity, AttendanceError> =
        retryTransport(command.requestId.safety(), delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Put,
                "api/groups/${groupId.value}/games/$gameId/capacity",
                AttendanceCapacityTransport.serializer(),
                NetworkRequest(
                    body = json.encodeToString(command.toRequest()),
                    headers = mapOf(HttpHeaders.IfMatch to version.value),
                ),
            )
        }.capacityResult()

    private fun attendanceRoute(groupId: GroupId, gameId: String) =
        "api/groups/${groupId.value}/games/$gameId/attendance"
}

private fun String.safety() =
    if (isBlank()) RetrySafety.Never else RetrySafety.IdempotentWrite

private fun SelfAttendanceCommand.toRequest() = SelfAttendanceRequest(
    requestId = requestId,
    intent = AttendanceIntentTransport.entries[intent.ordinal],
)

private fun OverrideAttendanceCommand.toRequest() = OverrideAttendanceRequest(
    requestId = requestId,
    memberId = memberId,
    intent = AttendanceIntentTransport.entries[intent.ordinal],
    reason = reason,
)

private fun AttendanceCapacityCommand.toRequest() = AttendanceCapacityRequest(
    requestId = requestId,
    capacity = capacity,
)

private fun NetworkResult<AttendanceDetailTransport>.detailResult() = when (this) {
    is NetworkResult.Success -> SaqzResult.Success(value.toDomain())
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomain())
}

private fun NetworkResult<AttendanceMutationTransport>.mutationResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomain())
    is NetworkResult.Success -> versionToken()
        ?.let { VersionedAttendanceMutation(value.toDomain(), it) }
        ?.let { SaqzResult.Success(it) }
        ?: invalidResponse()
}

private fun NetworkResult<AttendanceCapacityTransport>.capacityResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomain())
    is NetworkResult.Success -> versionToken()
        ?.let { VersionedAttendanceCapacity(value.toDomain(), it) }
        ?.let { SaqzResult.Success(it) }
        ?: invalidResponse()
}

private fun NetworkResult.Success<*>.versionToken() = metadata.header(HttpHeaders.ETag)
    ?.takeIf(String::isNotBlank)
    ?.let(::AttendanceVersionToken)

private fun invalidResponse(): SaqzResult.Failure<AttendanceError> =
    SaqzResult.Failure(AttendanceError.Data(DataError.InvalidResponse))

private fun NetworkError.toDomain(): AttendanceError = when (this) {
    is NetworkError.ApiProblemError -> when (problem.code) {
        "VALIDATION_FAILED" -> AttendanceError.Validation(
            DataError.Validation(
                ValidationDetails(
                    globalMessages = emptyList(),
                    fieldMessages = problem.fieldErrors.orEmpty(),
                ),
            ),
        )
        "GAME_NOT_FOUND", "GROUP_NOT_FOUND" -> AttendanceError.HiddenResource
        "ATTENDANCE_DEADLINE_PASSED" -> AttendanceError.DeadlinePassed
        "ATTENDANCE_FROZEN", "INVALID_GAME_TRANSITION" -> AttendanceError.Frozen
        "VERSION_CONFLICT" -> AttendanceError.Conflict
        "AUTHENTICATION_REQUIRED" -> AttendanceError.Authentication
        else -> AttendanceError.Data(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> AttendanceError.Data(status.toDataError())
    NetworkError.Timeout -> AttendanceError.Data(DataError.Timeout)
    NetworkError.Connectivity -> AttendanceError.Data(DataError.Connectivity)
    NetworkError.InvalidResponse -> AttendanceError.Data(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> AttendanceError.Data(DataError.PayloadTooLarge)
    NetworkError.Unavailable,
    NetworkError.Unknown,
    -> AttendanceError.Data(DataError.Unknown)
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

private fun AttendanceEntryTransport.toDomain() = AttendanceEntry(
    memberId = memberId,
    status = AttendanceStatus.entries[status.ordinal],
    waitlistPosition = waitlistPosition,
    version = version,
)

private fun AttendanceAuditTransport.toDomain() = AttendanceAudit(
    actorId = actorId,
    source = source,
    oldStatus = oldStatus?.let { AttendanceStatus.entries[it.ordinal] },
    newStatus = AttendanceStatus.entries[newStatus.ordinal],
    reason = reason,
    occurredAt = occurredAt,
)

private fun AttendanceDetailTransport.toDomain() = AttendanceDetail(
    ownAttendance = ownAttendance?.toDomain(),
    confirmedCount = confirmedCount,
    availableSpots = availableSpots,
    waitlistCount = waitlistCount,
    capacity = capacity,
)

private fun AttendanceMutationTransport.toDomain() = AttendanceMutation(
    attendance = attendance.toDomain(),
    audit = audit?.toDomain(),
    promotedCount = promotedCount,
    detail = detail.toDomain(),
)

private fun AttendanceCapacityTransport.toDomain() = AttendanceCapacity(
    capacity = capacity,
    version = version,
    promotedCount = promotedCount,
    detail = detail.toDomain(),
)
