package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.PromotionMode
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
        val promoted = promote(aggregate, (capacity - aggregate.confirmedCount).coerceAtLeast(0), capacity)
        CapacityCommandResult.Success(capacity, expectedVersion + 1, promoted)
    }

    private fun promote(aggregate: CapacityAggregate, spots: Int, targetCapacity: Int): List<AttendanceRecord> {
        if (spots == 0 || aggregate.promotionMode != PromotionMode.FIFO) {
            return emptyList()
        }
        val timestamp = now()
        var confirmedCount = aggregate.confirmedCount
        return buildList {
            repeat(spots) {
                val waiting = repository.earliestWaitlisted(aggregate.groupId, aggregate.gameId) ?: return@buildList
                when (val result = promoteAttendance(
                    aggregate.forMember(waiting).copy(capacity = targetCapacity, confirmedCount = confirmedCount),
                    AttendanceSource.SYSTEM,
                    reason = null,
                    repository = repository,
                    charges = charges,
                    timestamp = timestamp,
                    ids = ids,
                )) {
                    is AttendancePromotionResult.Denied -> return@buildList
                    is AttendancePromotionResult.Success -> {
                        add(result.attendance)
                        confirmedCount++
                    }
                }
            }
        }
    }

    // A promoção por capacidade é FIFO e independe do vínculo; o agregado aqui só alimenta a cobrança.
    private fun CapacityAggregate.forMember(record: AttendanceRecord) = AttendanceAggregate(
        groupId, gameId, record.memberId, actorId, actorRole, gameStatus,
        confirmationDeadline, capacity, confirmedCount, record, gameFeeCents, gameDate,
        AthleteMembershipType.MENSALISTA,
        mensalistaPriority,
        promotionMode,
    )
}
