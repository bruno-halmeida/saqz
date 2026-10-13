package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.attendance.AttendanceDecision
import br.com.saqz.groups.domain.attendance.AttendanceDecisionContext
import br.com.saqz.groups.domain.attendance.AttendanceDenial
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceTransitionPolicy
import java.time.Instant
import java.util.UUID

internal sealed interface AttendancePromotionResult {
    data class Success(
        val attendance: AttendanceRecord,
        val event: AttendanceEvent,
    ) : AttendancePromotionResult

    data class Denied(val reason: AttendanceDenial) : AttendancePromotionResult
}

internal fun promoteAttendance(
    aggregate: AttendanceAggregate,
    source: AttendanceSource,
    reason: String?,
    confirmedCount: Int = aggregate.confirmedCount,
    requestId: UUID? = null,
    repository: AttendanceCommandRepository,
    charges: AttendanceChargePort,
    timestamp: Instant,
    ids: () -> UUID,
): AttendancePromotionResult {
    val decision = AttendanceTransitionPolicy.decide(
        AttendanceDecisionContext(
            aggregate.gameStatus,
            aggregate.confirmationDeadline,
            timestamp,
            aggregate.capacity,
            confirmedCount,
            aggregate.current?.status,
            source,
            reason,
            aggregate.membershipType,
            aggregate.mensalistaPriority,
        ),
        AttendanceIntent.PROMOTE,
    )
    if (decision is AttendanceDecision.Denied) return AttendancePromotionResult.Denied(decision.reason)

    val transition = decision as AttendanceDecision.Transition
    val current = requireNotNull(aggregate.current)
    val promoted = current.copy(
        status = transition.newStatus,
        waitlistSequence = null,
        updatedAt = maxOf(timestamp, current.respondedAt),
        version = current.version + 1,
    )
    repository.save(promoted)
    val event = AttendanceEvent(
        ids(),
        aggregate.gameId,
        aggregate.groupId,
        promoted.memberId,
        aggregate.actorId,
        transition.source,
        transition.oldStatus,
        transition.newStatus,
        transition.reason,
        timestamp,
        requestId,
        guestSeq = promoted.guestSeq,
    )
    repository.append(event)
    if (transition.createGameCharge) charges.promoted(aggregate, aggregate.actorId)
    return AttendancePromotionResult.Success(promoted, event)
}

/** Promove até [freed] pessoas da fila; [aggregate].confirmedCount é a contagem ANTES das saídas. */
internal fun promoteFreed(
    aggregate: AttendanceAggregate,
    freed: Int,
    repository: AttendanceCommandRepository,
    charges: AttendanceChargePort,
    timestamp: Instant,
    ids: () -> UUID,
): List<AttendanceRecord> {
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
