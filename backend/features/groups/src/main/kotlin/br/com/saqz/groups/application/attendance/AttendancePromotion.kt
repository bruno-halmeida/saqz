package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceDecision
import br.com.saqz.groups.domain.attendance.AttendanceDecisionContext
import br.com.saqz.groups.domain.attendance.AttendanceDenial
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceTransitionPolicy
import br.com.saqz.groups.domain.group.PromotionMode
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
            promotionMode = aggregate.promotionMode,
            startsAt = aggregate.startsAt,
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

/**
 * Próximo da espera a subir numa vaga liberada. Antes do prazo, em ordem de chegada e com a prioridade de
 * mensalista ligada, o mensalista mais antigo da espera sobe primeiro; sem mensalista na espera, sobe o avulso
 * mais antigo. Depois do prazo, ou sem prioridade, é FIFO puro. Quem decide é a ordenação da query, pelo instante
 * [timestamp]. O job [SettleWaitlistAtDeadline] continua preenchendo no prazo o que sobrar.
 */
internal fun AttendanceCommandRepository.nextToPromote(
    groupId: UUID,
    gameId: UUID,
    timestamp: Instant,
): AttendanceRecord? = earliestWaitlisted(groupId, gameId, timestamp)

/** Promove até [freed] pessoas da fila; [aggregate].confirmedCount é a contagem ANTES das saídas. */
internal fun promoteFreed(
    aggregate: AttendanceAggregate,
    freed: Int,
    repository: AttendanceCommandRepository,
    charges: AttendanceChargePort,
    timestamp: Instant,
    ids: () -> UUID,
    startConfirmed: Int = (aggregate.confirmedCount - freed).coerceAtLeast(0),
): List<AttendanceRecord> {
    var confirmed = startConfirmed
    return buildList {
        repeat(freed) {
            val waiting = repository.nextToPromote(aggregate.groupId, aggregate.gameId, timestamp) ?: return@buildList
            val target = aggregate.copy(memberId = waiting.memberId, guestSeq = waiting.guestSeq, guestName = null, current = waiting, confirmedCount = confirmed)
            val result = promoteAttendance(target, AttendanceSource.SYSTEM, reason = null, repository = repository, charges = charges, timestamp = timestamp, ids = ids)
            if (result !is AttendancePromotionResult.Success) return@buildList
            add(result.attendance); confirmed++
        }
    }
}

/**
 * Jogo com posições (DEB-102): sobe da espera de [position], e só dela, enquanto a posição tiver vaga livre. Conta de
 * novo a cada promoção (vem do banco, já com as saídas gravadas); posição com confirmados acima das vagas
 * (entrada forçada) não promove ninguém até voltar abaixo delas. Mensalista e prioridade valem dentro da posição
 * (a ordenação da query só olha a fila daquela posição).
 */
internal fun promotePosition(
    aggregate: AttendanceAggregate,
    position: AthletePosition,
    repository: AttendanceCommandRepository,
    charges: AttendanceChargePort,
    timestamp: Instant,
    ids: () -> UUID,
): List<AttendanceRecord> = buildList {
    while (true) {
        val load = repository.positionLoads(aggregate.gameId).firstOrNull { it.position == position } ?: break
        if (load.confirmed >= load.slots) break
        val waiting = repository.earliestWaitlistedIn(aggregate.groupId, aggregate.gameId, timestamp, position) ?: break
        val target = aggregate.copy(
            memberId = waiting.memberId, guestSeq = waiting.guestSeq, guestName = null, current = waiting,
            capacity = load.slots, confirmedCount = load.confirmed, position = position,
        )
        val result = promoteAttendance(target, AttendanceSource.SYSTEM, reason = null, repository = repository, charges = charges, timestamp = timestamp, ids = ids)
        if (result !is AttendancePromotionResult.Success) break
        add(result.attendance)
    }
}
