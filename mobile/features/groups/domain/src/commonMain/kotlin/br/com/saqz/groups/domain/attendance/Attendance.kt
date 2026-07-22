package br.com.saqz.groups.domain.attendance

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import kotlin.jvm.JvmInline

enum class AttendanceStatus {
    Confirmed,
    Declined,
    Waitlisted,
}

enum class AttendanceIntent {
    Confirm,
    Decline,
}

@JvmInline
value class AttendanceVersionToken(val value: String)

data class AttendanceEntry(
    val memberId: String,
    val status: AttendanceStatus,
    val waitlistPosition: Long? = null,
    val version: Long,
)

data class AttendanceAudit(
    val actorId: String,
    val source: String,
    val oldStatus: AttendanceStatus? = null,
    val newStatus: AttendanceStatus,
    val reason: String? = null,
    val occurredAt: String,
)

data class AttendanceDetail(
    val ownAttendance: AttendanceEntry? = null,
    val confirmedCount: Int,
    val availableSpots: Int,
    val waitlistCount: Int,
    val capacity: Int,
)

data class AttendanceMutation(
    val attendance: AttendanceEntry,
    val audit: AttendanceAudit? = null,
    val promotedCount: Int,
    val detail: AttendanceDetail,
)

data class VersionedAttendanceMutation(
    val value: AttendanceMutation,
    val version: AttendanceVersionToken,
)

data class AttendanceCapacity(
    val capacity: Int,
    val version: Long,
    val promotedCount: Int,
    val detail: AttendanceDetail,
)

data class VersionedAttendanceCapacity(
    val value: AttendanceCapacity,
    val version: AttendanceVersionToken,
)

data class SelfAttendanceCommand(
    val requestId: String,
    val intent: AttendanceIntent,
)

data class OverrideAttendanceCommand(
    val requestId: String,
    val memberId: String,
    val intent: AttendanceIntent,
    val reason: String,
)

data class AttendanceCapacityCommand(
    val requestId: String,
    val capacity: Int,
)

sealed interface AttendanceError : SaqzError {
    data class Validation(val error: DataError.Validation) : AttendanceError
    data object HiddenResource : AttendanceError
    data object DeadlinePassed : AttendanceError
    data object Frozen : AttendanceError
    data object Conflict : AttendanceError
    data object Authentication : AttendanceError
    data class Data(val error: DataError) : AttendanceError
}

interface AttendanceGateway {
    suspend fun read(
        groupId: GroupId,
        gameId: String,
    ): SaqzResult<AttendanceDetail, AttendanceError>

    suspend fun respond(
        groupId: GroupId,
        gameId: String,
        command: SelfAttendanceCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError>

    suspend fun override(
        groupId: GroupId,
        gameId: String,
        command: OverrideAttendanceCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError>

    suspend fun capacity(
        groupId: GroupId,
        gameId: String,
        version: AttendanceVersionToken,
        command: AttendanceCapacityCommand,
    ): SaqzResult<VersionedAttendanceCapacity, AttendanceError>
}
