package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.group.PromotionMode
import java.time.Instant
import java.util.UUID

sealed interface AttendanceCommandResult {
    data class Success(
        val attendance: AttendanceRecord,
        val promoted: List<AttendanceRecord> = emptyList(),
        val event: AttendanceEvent? = null,
    ) : AttendanceCommandResult
    data class Denied(val reason: AttendanceDenial) : AttendanceCommandResult
    data object Hidden : AttendanceCommandResult
    data object Forbidden : AttendanceCommandResult
}

class RespondAttendance(
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
        memberId: UUID = actorId,
        intent: AttendanceIntent,
        source: AttendanceSource = AttendanceSource.SELF,
        reason: String? = null,
        requestId: UUID? = null,
    ): AttendanceCommandResult = transaction.inTransaction {
        if (intent == AttendanceIntent.PROMOTE) return@inTransaction AttendanceCommandResult.Forbidden
        if (source == AttendanceSource.SELF && requestId != null) {
            repository.findResponseReplay(groupId, gameId, actorId, requestId)?.let {
                return@inTransaction it.result()
            }
        }
        val aggregate = repository.lock(groupId, gameId, memberId, actorId)
            ?: return@inTransaction AttendanceCommandResult.Hidden
        if (source == AttendanceSource.SELF && requestId != null) {
            repository.findResponseReplay(groupId, gameId, actorId, requestId)?.let {
                return@inTransaction it.result()
            }
        }
        if (!aggregate.authorized(source)) return@inTransaction aggregate.denied(source)
        when (val decision = AttendanceTransitionPolicy.decide(
            AttendanceDecisionContext(
                aggregate.gameStatus,
                aggregate.confirmationDeadline,
                now(),
                aggregate.capacity,
                aggregate.confirmedCount,
                aggregate.current?.status,
                source,
                reason,
                aggregate.membershipType,
                aggregate.mensalistaPriority,
            ),
            intent,
        )) {
            is AttendanceDecision.Denied -> AttendanceCommandResult.Denied(decision.reason)
            is AttendanceDecision.Transition -> apply(aggregate, decision, requestId)
        }
    }

    fun promote(
        actorId: UUID,
        groupId: UUID,
        gameId: UUID,
        memberId: UUID,
        requestId: UUID,
        reason: String?,
        guestSeq: Int = 0,
    ): AttendanceCommandResult = transaction.inTransaction {
        repository.findPromotionReplay(groupId, gameId, actorId, requestId)?.let {
            return@inTransaction it.result()
        }
        val aggregate = repository.lock(groupId, gameId, memberId, actorId, guestSeq)
            ?: return@inTransaction AttendanceCommandResult.Hidden
        repository.findPromotionReplay(groupId, gameId, actorId, requestId)?.let {
            return@inTransaction it.result()
        }
        if (!aggregate.authorized(AttendanceSource.ORGANIZER)) {
            return@inTransaction aggregate.denied(AttendanceSource.ORGANIZER)
        }
        if (aggregate.promotionMode != PromotionMode.MANUAL) {
            return@inTransaction AttendanceCommandResult.Denied(AttendanceDenial.MANUAL_PROMOTION_ONLY)
        }
        when (val result = promoteAttendance(
            aggregate,
            AttendanceSource.ORGANIZER,
            reason,
            requestId = requestId,
            repository = repository,
            charges = charges,
            timestamp = now(),
            ids = ids,
        )) {
            is AttendancePromotionResult.Denied -> AttendanceCommandResult.Denied(result.reason)
            is AttendancePromotionResult.Success -> result.result()
        }
    }

    internal fun apply(
        aggregate: AttendanceAggregate,
        decision: AttendanceDecision.Transition,
        requestId: UUID?,
    ): AttendanceCommandResult {
        if (!decision.changed) return AttendanceCommandResult.Success(requireNotNull(aggregate.current))
        val timestamp = now()
        val respondedAt = aggregate.current?.respondedAt ?: timestamp
        val record = AttendanceRecord(
            aggregate.gameId,
            aggregate.groupId,
            aggregate.memberId,
            decision.newStatus,
            if (decision.allocateWaitlistSequence) {
                repository.nextWaitlistSequence(aggregate.groupId, aggregate.gameId)
            } else null,
            respondedAt,
            maxOf(timestamp, respondedAt),
            (aggregate.current?.version ?: 0) + 1,
            guestSeq = aggregate.guestSeq,
        )
        if (aggregate.guestSeq > 0 && aggregate.current == null) {
            repository.saveGuest(record, requireNotNull(aggregate.guestName))
        } else {
            repository.save(record)
        }
        val event = AttendanceEvent(
            ids(),
            aggregate.gameId,
            aggregate.groupId,
            aggregate.memberId,
            aggregate.actorId,
            decision.source,
            decision.oldStatus,
            decision.newStatus,
            decision.reason,
            timestamp,
            requestId,
            guestSeq = aggregate.guestSeq,
        )
        repository.append(event)
        if (decision.createGameCharge) charges.confirmed(aggregate, aggregate.actorId)
        val leaving = decision.newStatus == AttendanceStatus.DECLINED
        val wasConfirmed = decision.oldStatus == AttendanceStatus.CONFIRMED
        if (leaving && wasConfirmed && aggregate.guestSeq > 0) charges.guestRemoved(aggregate, aggregate.actorId)
        // Anfitrião saiu: os convidados dele saem ANTES de qualquer promoção, senão a fila
        // promoveria um convidado que cai no passo seguinte.
        val freedByGuests = if (leaving && aggregate.guestSeq == 0) dropGuests(aggregate, timestamp) else 0
        val freed = freedByGuests + if (leaving && wasConfirmed) 1 else 0
        val promoted = if (aggregate.promotionMode == PromotionMode.FIFO) promoteFreed(aggregate, freed, timestamp) else emptyList()
        return AttendanceCommandResult.Success(record, promoted, event)
    }

    /** Promove até [freed] pessoas da fila; [aggregate].confirmedCount é a contagem ANTES das saídas. */
    private fun promoteFreed(aggregate: AttendanceAggregate, freed: Int, timestamp: Instant): List<AttendanceRecord> {
        var confirmed = (aggregate.confirmedCount - freed).coerceAtLeast(0)
        return buildList {
            repeat(freed) {
                val waiting = repository.earliestWaitlisted(aggregate.groupId, aggregate.gameId) ?: return@buildList
                val target = aggregate.copy(memberId = waiting.memberId, guestSeq = waiting.guestSeq, guestName = null, current = waiting, confirmedCount = confirmed)
                val result = promoteAttendance(target, AttendanceSource.SYSTEM, reason = null, repository = repository, charges = charges, timestamp = timestamp, ids = ids)
                if (result !is AttendancePromotionResult.Success) return@buildList
                add(result.attendance); confirmed++
            }
        }
    }

    /** Derruba os convidados ativos do anfitrião; devolve quantas vagas CONFIRMADAS eles liberaram. */
    private fun dropGuests(host: AttendanceAggregate, timestamp: Instant): Int =
        repository.activeGuests(host.groupId, host.gameId, host.memberId).count { guest ->
            val dropped = guest.copy(status = AttendanceStatus.DECLINED, waitlistSequence = null, updatedAt = maxOf(timestamp, guest.respondedAt), version = guest.version + 1)
            repository.save(dropped)
            repository.append(AttendanceEvent(ids(), host.gameId, host.groupId, host.memberId, host.actorId, AttendanceSource.SYSTEM, guest.status, AttendanceStatus.DECLINED, null, timestamp, guestSeq = guest.guestSeq))
            val confirmed = guest.status == AttendanceStatus.CONFIRMED
            if (confirmed) charges.guestRemoved(host.copy(guestSeq = guest.guestSeq, current = guest), host.actorId)
            confirmed
        }

    private fun AttendancePromotionResult.Success.result() =
        AttendanceCommandResult.Success(attendance, listOf(attendance), event)

    private fun AttendancePromotionReplay.result() =
        AttendanceCommandResult.Success(attendance, listOf(attendance), event)

    private fun AttendanceResponseReplay.result() =
        AttendanceCommandResult.Success(attendance, event = event)

    internal fun AttendanceAggregate.authorized(source: AttendanceSource): Boolean = when (source) {
        AttendanceSource.SELF -> actorId == memberId && actorRole != null
        AttendanceSource.ORGANIZER -> actorRole == GroupRole.OWNER || actorRole == GroupRole.ADMIN
        AttendanceSource.SYSTEM -> true
    }

    internal fun AttendanceAggregate.denied(source: AttendanceSource): AttendanceCommandResult =
        if (actorRole == null || source == AttendanceSource.SELF) AttendanceCommandResult.Hidden
        else AttendanceCommandResult.Forbidden
}
