package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.*
import java.time.Instant
import java.util.UUID

sealed interface AttendanceCommandResult {
    data class Success(
        val attendance: AttendanceRecord,
        val promoted: List<AttendanceRecord> = emptyList(),
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
    ): AttendanceCommandResult = transaction.inTransaction {
        val aggregate = repository.lock(groupId, gameId, memberId, actorId)
            ?: return@inTransaction AttendanceCommandResult.Hidden
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
            ),
            intent,
        )) {
            is AttendanceDecision.Denied -> AttendanceCommandResult.Denied(decision.reason)
            is AttendanceDecision.Transition -> apply(aggregate, decision)
        }
    }

    private fun apply(
        aggregate: AttendanceAggregate,
        decision: AttendanceDecision.Transition,
    ): AttendanceCommandResult {
        if (!decision.changed) return AttendanceCommandResult.Success(requireNotNull(aggregate.current))
        val timestamp = now()
        val record = AttendanceRecord(
            aggregate.gameId,
            aggregate.groupId,
            aggregate.memberId,
            decision.newStatus,
            if (decision.allocateWaitlistSequence) {
                repository.nextWaitlistSequence(aggregate.groupId, aggregate.gameId)
            } else null,
            aggregate.current?.respondedAt ?: timestamp,
            timestamp,
            (aggregate.current?.version ?: 0) + 1,
        )
        repository.save(record)
        repository.append(
            AttendanceEvent(
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
            ),
        )
        if (decision.createGameCharge) charges.confirmed(aggregate, aggregate.actorId)
        val promoted = if (
            decision.oldStatus == AttendanceStatus.CONFIRMED &&
            decision.newStatus == AttendanceStatus.DECLINED
        ) promoteOne(aggregate, timestamp) else null
        return AttendanceCommandResult.Success(record, listOfNotNull(promoted))
    }

    private fun promoteOne(aggregate: AttendanceAggregate, timestamp: Instant): AttendanceRecord? {
        val waiting = repository.earliestWaitlisted(aggregate.groupId, aggregate.gameId) ?: return null
        val promoted = waiting.copy(
            status = AttendanceStatus.CONFIRMED,
            waitlistSequence = null,
            updatedAt = timestamp,
            version = waiting.version + 1,
        )
        repository.save(promoted)
        repository.append(
            AttendanceEvent(
                ids(),
                aggregate.gameId,
                aggregate.groupId,
                promoted.memberId,
                aggregate.actorId,
                AttendanceSource.SYSTEM,
                AttendanceStatus.WAITLISTED,
                AttendanceStatus.CONFIRMED,
                null,
                timestamp,
            ),
        )
        charges.promoted(aggregate.copy(memberId = promoted.memberId, current = waiting), aggregate.actorId)
        return promoted
    }

    private fun AttendanceAggregate.authorized(source: AttendanceSource): Boolean = when (source) {
        AttendanceSource.SELF -> actorId == memberId && actorRole != null
        AttendanceSource.ORGANIZER -> actorRole == GroupRole.OWNER || actorRole == GroupRole.ADMIN
        AttendanceSource.SYSTEM -> true
    }

    private fun AttendanceAggregate.denied(source: AttendanceSource): AttendanceCommandResult =
        if (actorRole == null || source == AttendanceSource.SELF) AttendanceCommandResult.Hidden
        else AttendanceCommandResult.Forbidden
}
