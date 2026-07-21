package br.com.saqz.groups.application.attendance.share

import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.GameStatus
import java.time.Instant
import java.util.UUID

data class AttendanceLinkRotatableTarget(
    val groupId: UUID,
    val gameId: UUID,
    val actorRole: GroupRole,
    val status: GameStatus,
    val confirmationDeadline: Instant,
)

data class RotateAttendanceLinkCommand(
    val groupId: UUID,
    val gameId: UUID,
    val digest: AttendanceLinkTokenDigest,
    val createdByUserId: UUID,
)

data class AttendanceLinkAttemptWindow(
    val windowStartedAt: Instant,
    val invalidCount: Int,
) {
    init {
        require(invalidCount in 0..10) { "Invalid attendance-link count must be between zero and ten" }
    }
}

data class AttendanceLinkResolvableTarget(
    val groupId: UUID,
    val gameId: UUID,
    val status: GameStatus,
    val confirmationDeadline: Instant,
)

data class RecordInvalidAttendanceLinkAttempt(
    val userId: UUID,
    val windowStartedAt: Instant,
    val invalidCount: Int,
)

interface AttendanceLinkRepository {
    fun lockRotatableTarget(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceLinkRotatableTarget?

    fun rotate(command: RotateAttendanceLinkCommand)

    fun lockAttemptWindow(userId: UUID, initializedAt: Instant): AttendanceLinkAttemptWindow

    fun findResolvableTarget(actorId: UUID, digest: AttendanceLinkTokenDigest): AttendanceLinkResolvableTarget?

    fun recordInvalidAttempt(command: RecordInvalidAttendanceLinkAttempt)
}
