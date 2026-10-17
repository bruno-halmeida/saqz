package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.game.GameCapacityAdjuster
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.game.GameCapacity
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
) : GameCapacityAdjuster {
    /** O editor do jogo usa o mesmo caminho do "Ajustar vagas" (DEB-047). */
    override fun adjust(actor: UUID, groupId: UUID, gameId: UUID, expectedVersion: Long, capacity: Int): CapacityCommandResult =
        execute(actor, groupId, gameId, expectedVersion, capacity)

    fun execute(
        actorId: UUID,
        groupId: UUID,
        gameId: UUID,
        expectedVersion: Long,
        capacity: Int,
    ): CapacityCommandResult = transaction.inTransaction {
        if (capacity !in GameCapacity.MIN..GameCapacity.MAX) return@inTransaction CapacityCommandResult.InvalidCapacity
        val aggregate = repository.lockCapacity(groupId, gameId, actorId)
            ?: return@inTransaction CapacityCommandResult.Hidden
        if (aggregate.actorRole == null) return@inTransaction CapacityCommandResult.Hidden
        if (aggregate.actorRole != GroupRole.OWNER && aggregate.actorRole != GroupRole.ADMIN) {
            return@inTransaction CapacityCommandResult.Forbidden
        }
        // Depois do início do jogo as vagas não mudam mais (DEB-020).
        if (aggregate.gameStatus != GameStatus.PUBLISHED || aggregate.startedAt(now())) return@inTransaction CapacityCommandResult.Frozen
        if (aggregate.version != expectedVersion) return@inTransaction CapacityCommandResult.Conflict
        if (!repository.updateCapacity(gameId, expectedVersion, capacity)) return@inTransaction CapacityCommandResult.Conflict
        val promoted = promote(aggregate, (capacity - aggregate.confirmedCount).coerceAtLeast(0), capacity)
        CapacityCommandResult.Success(capacity, expectedVersion + 1, promoted)
    }

    private fun CapacityAggregate.startedAt(timestamp: Instant) = timestamp >= startsAt

    private fun promote(aggregate: CapacityAggregate, spots: Int, targetCapacity: Int): List<AttendanceRecord> {
        if (spots == 0 || aggregate.promotionMode != PromotionMode.FIFO) {
            return emptyList()
        }
        val timestamp = now()
        var confirmedCount = aggregate.confirmedCount
        return buildList {
            repeat(spots) {
                val waiting = repository.nextToPromote(aggregate.groupId, aggregate.gameId, timestamp) ?: return@buildList
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
        guestSeq = record.guestSeq,
        startsAt = startsAt,
    )
}
