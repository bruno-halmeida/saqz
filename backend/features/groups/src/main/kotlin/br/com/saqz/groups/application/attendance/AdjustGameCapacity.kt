package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.GameStatus
import java.time.Instant
import java.util.UUID

sealed interface CapacityCommandResult {
    data class Success(
        val capacity: Int,
        val version: Long,
        val promoted: List<AttendanceRecord>,
    ) : CapacityCommandResult
    data object Hidden : CapacityCommandResult
    data object Forbidden : CapacityCommandResult
    data object Conflict : CapacityCommandResult
    data object Frozen : CapacityCommandResult
    data object InvalidCapacity : CapacityCommandResult
}

class AdjustGameCapacity(
    private val transaction: TransactionRunner,
    private val repository: AttendanceCommandRepository,
    private val charges: AttendanceChargePort,
    private val now: () -> Instant,
    private val ids: () -> UUID = UUID::randomUUID,
) {
    fun execute(
        actorId: UUID,
        groupId: UUID,
        gameId: UUID,
        expectedVersion: Long,
        capacity: Int,
    ): CapacityCommandResult = transaction.inTransaction {
        if (capacity !in 2..100) return@inTransaction CapacityCommandResult.InvalidCapacity
        val aggregate = repository.lockCapacity(groupId, gameId, actorId)
            ?: return@inTransaction CapacityCommandResult.Hidden
        if (aggregate.actorRole == null) return@inTransaction CapacityCommandResult.Hidden
        if (aggregate.actorRole != GroupRole.OWNER && aggregate.actorRole != GroupRole.ADMIN) {
            return@inTransaction CapacityCommandResult.Forbidden
        }
        if (aggregate.gameStatus != GameStatus.PUBLISHED) return@inTransaction CapacityCommandResult.Frozen
        if (aggregate.version != expectedVersion) return@inTransaction CapacityCommandResult.Conflict
        if (!repository.updateCapacity(gameId, expectedVersion, capacity)) return@inTransaction CapacityCommandResult.Conflict
        val promoted = promote(aggregate, (capacity - aggregate.confirmedCount).coerceAtLeast(0))
        CapacityCommandResult.Success(capacity, expectedVersion + 1, promoted)
    }

    private fun promote(aggregate: CapacityAggregate, spots: Int): List<AttendanceRecord> {
        if (spots == 0) return emptyList()
        val timestamp = now()
        return buildList {
            repeat(spots) {
                val waiting = repository.earliestWaitlisted(aggregate.groupId, aggregate.gameId) ?: return@buildList
                val promoted = waiting.copy(
                    status = AttendanceStatus.CONFIRMED,
                    waitlistSequence = null,
                    updatedAt = timestamp,
                    version = waiting.version + 1,
                )
                repository.save(promoted)
                repository.append(
                    AttendanceEvent(
                        ids(), aggregate.gameId, aggregate.groupId, promoted.memberId,
                        aggregate.actorId, AttendanceSource.SYSTEM, AttendanceStatus.WAITLISTED,
                        AttendanceStatus.CONFIRMED, null, timestamp,
                    ),
                )
                charges.promoted(aggregate.forMember(waiting), aggregate.actorId)
                add(promoted)
            }
        }
    }

    private fun CapacityAggregate.forMember(record: AttendanceRecord) = AttendanceAggregate(
        groupId, gameId, record.memberId, actorId, actorRole, gameStatus,
        confirmationDeadline, capacity, confirmedCount, record, gameFeeCents, gameDate,
    )
}
