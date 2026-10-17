package br.com.saqz.groups.application.attendance.share

import br.com.saqz.groups.domain.game.GameStatus
import java.time.Instant
import java.util.UUID

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
    val registrationRequired: Boolean = false,
    /** O link vale até o início do jogo: depois do prazo "Vou" cai na lista de espera e "Não vou" libera a vaga. */
    val startsAt: Instant = Instant.MAX,
)

data class RecordInvalidAttendanceLinkAttempt(
    val userId: UUID,
    val windowStartedAt: Instant,
    val invalidCount: Int,
)

interface AttendanceLinkRepository {
    fun lockAttemptWindow(userId: UUID, initializedAt: Instant): AttendanceLinkAttemptWindow

    fun findResolvableTarget(actorId: UUID, digest: AttendanceLinkTokenDigest): AttendanceLinkResolvableTarget?

    fun recordInvalidAttempt(command: RecordInvalidAttendanceLinkAttempt)
}
