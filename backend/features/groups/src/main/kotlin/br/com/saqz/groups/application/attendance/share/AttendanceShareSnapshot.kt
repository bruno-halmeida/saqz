package br.com.saqz.groups.application.attendance.share

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import br.com.saqz.groups.domain.GroupRole
import java.time.Instant
import java.util.UUID

data class AttendanceShareSnapshotPerson(
    val displayName: String,
    val waitlistPosition: Long? = null,
)

data class AttendanceShareSnapshot(
    val title: String,
    val startsAt: Instant,
    val timeZone: String,
    val venue: String,
    val capacity: Int,
    val confirmed: List<AttendanceShareSnapshotPerson>,
    val waitlisted: List<AttendanceShareSnapshotPerson>,
    val declined: List<AttendanceShareSnapshotPerson>,
)

data class AttendanceShareSnapshotAccess(
    val actorRole: GroupRole,
)

interface AttendanceShareSnapshotRepository {
    fun findSnapshotAccess(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceShareSnapshotAccess?

    fun readSnapshot(groupId: UUID, gameId: UUID): AttendanceShareSnapshot
}

sealed interface ReadAttendanceShareSnapshotResult {
    data class Success(val snapshot: AttendanceShareSnapshot) : ReadAttendanceShareSnapshotResult

    data object GameNotFound : ReadAttendanceShareSnapshotResult

    data object AccessForbidden : ReadAttendanceShareSnapshotResult
}

class ReadAttendanceShareSnapshot(
    private val transactionRunner: TransactionRunner,
    private val repository: AttendanceShareSnapshotRepository,
    private val accessPolicy: GroupAccessPolicy,
) {
    fun execute(actorId: UUID, groupId: UUID, gameId: UUID): ReadAttendanceShareSnapshotResult = transactionRunner.inTransaction {
        val access = repository.findSnapshotAccess(actorId, groupId, gameId)
            ?: return@inTransaction ReadAttendanceShareSnapshotResult.GameNotFound
        when (accessPolicy.authorize(access.actorRole, GroupAction.MANAGE_ATTENDANCE_SHARE)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction ReadAttendanceShareSnapshotResult.GameNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction ReadAttendanceShareSnapshotResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }
        ReadAttendanceShareSnapshotResult.Success(repository.readSnapshot(groupId, gameId))
    }
}
