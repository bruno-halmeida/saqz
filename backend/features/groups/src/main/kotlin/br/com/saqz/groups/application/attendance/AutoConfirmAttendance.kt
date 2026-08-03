package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.game.GameSideEffect
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AutoConfirmationPolicy
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.recurrence.ResolvedWeeklyOccurrence
import java.time.Instant
import java.util.UUID

data class AutoConfirmationGame(
    val groupId: UUID,
    val gameId: UUID,
    val ownerId: UUID,
    val status: GameStatus,
    val capacity: Int,
    val confirmedCount: Int,
    val autoConfirmEnabled: Boolean,
)

sealed interface AutoConfirmationOptInUpdate {
    data class Success(val enabled: Boolean) : AutoConfirmationOptInUpdate
    data object GroupNotFound : AutoConfirmationOptInUpdate
    data object NotMensalista : AutoConfirmationOptInUpdate
    data object FeatureDisabled : AutoConfirmationOptInUpdate
}

interface AutoConfirmationRepository {
    fun updateOwnOptIn(groupId: UUID, memberId: UUID, enabled: Boolean): AutoConfirmationOptInUpdate
    fun lockGame(groupId: UUID, gameId: UUID): AutoConfirmationGame?
    fun lockOccurrence(groupId: UUID, seriesId: UUID, localDate: java.time.LocalDate, slotKey: UUID): AutoConfirmationGame?
    fun candidates(gameId: UUID): List<br.com.saqz.groups.domain.attendance.AutoConfirmationCandidate>
    fun nextWaitlistSequence(groupId: UUID, gameId: UUID): Long
    fun save(record: AttendanceRecord)
    fun append(event: AttendanceEvent)
}

fun interface AutoConfirmationMaterializationPort {
    fun apply(occurrences: List<br.com.saqz.groups.application.game.recurrence.MaterializedGameOccurrence>)
}

class AutoConfirmAttendance(
    private val transaction: TransactionRunner,
    private val repository: AutoConfirmationRepository,
    private val now: () -> Instant,
    private val ids: () -> UUID = UUID::randomUUID,
) : br.com.saqz.groups.application.game.GameSideEffectPort {
    fun updateOwnOptIn(groupId: UUID, memberId: UUID, enabled: Boolean): AutoConfirmationOptInUpdate =
        transaction.inTransaction { repository.updateOwnOptIn(groupId, memberId, enabled) }

    override fun apply(game: Game, actorId: UUID, effects: Set<GameSideEffect>) {
        if (GameSideEffect.ATTENDANCE_OPENED in effects) {
            applyGame(game.groupId, game.id, actorId)
        }
    }

    fun applyMaterialized(occurrences: List<br.com.saqz.groups.application.game.recurrence.MaterializedGameOccurrence>) {
        occurrences.forEach { occurrence ->
            applyOccurrence(occurrence.occurrence)
        }
    }

    private fun applyGame(groupId: UUID, gameId: UUID, actorId: UUID): Int =
        transaction.inTransaction {
            val game = repository.lockGame(groupId, gameId) ?: return@inTransaction 0
            if (!game.autoConfirmEnabled || game.status != GameStatus.PUBLISHED) return@inTransaction 0
            confirm(game, actorId)
        }

    private fun applyOccurrence(occurrence: ResolvedWeeklyOccurrence): Int = transaction.inTransaction {
        val game = repository.lockOccurrence(
            occurrence.groupId,
            occurrence.seriesId,
            occurrence.localDate,
            occurrence.slot.slotKey,
        ) ?: return@inTransaction 0
        if (!game.autoConfirmEnabled) return@inTransaction 0
        confirm(game, game.ownerId)
    }

    private fun confirm(game: AutoConfirmationGame, actorId: UUID): Int {
        val assignments = AutoConfirmationPolicy.decide(
            repository.candidates(game.gameId),
            game.capacity,
            game.confirmedCount,
        )
        if (assignments.isEmpty()) return 0
        val timestamp = now()
        assignments.forEach { assignment ->
            val waitlistSequence = if (assignment.status == AttendanceStatus.WAITLISTED) {
                repository.nextWaitlistSequence(game.groupId, game.gameId)
            } else null
            repository.save(
                AttendanceRecord(
                    game.gameId,
                    game.groupId,
                    assignment.memberId,
                    assignment.status,
                    waitlistSequence,
                    timestamp,
                    timestamp,
                    1,
                ),
            )
            repository.append(
                AttendanceEvent(
                    ids(),
                    game.gameId,
                    game.groupId,
                    assignment.memberId,
                    actorId,
                    AttendanceSource.SYSTEM,
                    null,
                    assignment.status,
                    null,
                    timestamp,
                ),
            )
        }
        return assignments.size
    }
}
