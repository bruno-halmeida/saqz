package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.communication.FirstConfirmationNotice
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.AthletePosition
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
    private val firstConfirmation: FirstConfirmationNotice = FirstConfirmationNotice { _, _, _, _ -> },
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
        position: AthletePosition? = null,
        force: Boolean = false,
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
        // Jogo com posições: a decisão vale para a posição do atleta (vagas e confirmados dela). Sem posições, o total.
        val loads = repository.positionLoads(gameId)
        val scoped = if (loads.isEmpty()) aggregate else when (
            val scope = aggregate.scopedTo(loads, position.takeIf { intent == AttendanceIntent.CONFIRM })
        ) {
            PositionScope.Invalid -> return@inTransaction AttendanceCommandResult.Denied(AttendanceDenial.INVALID_POSITION)
            is PositionScope.Scoped -> scope.aggregate
        }
        when (val decision = AttendanceTransitionPolicy.decide(
            AttendanceDecisionContext(
                scoped.gameStatus,
                scoped.confirmationDeadline,
                now(),
                scoped.capacity,
                scoped.confirmedCount,
                scoped.current?.status,
                source,
                reason,
                scoped.membershipType,
                scoped.mensalistaPriority,
                promotionMode = scoped.promotionMode,
                startsAt = scoped.startsAt,
                force = force && loads.isNotEmpty(),
            ),
            intent,
        )) {
            is AttendanceDecision.Denied -> AttendanceCommandResult.Denied(decision.reason)
            is AttendanceDecision.Transition -> apply(scoped, decision, requestId)
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
        // Jogo com posições: "Colocar no jogo" respeita as vagas da posição de quem espera (forçar é o override com force).
        val loads = repository.positionLoads(gameId)
        val scoped = if (loads.isEmpty()) aggregate else when (val scope = aggregate.scopedTo(loads, null)) {
            PositionScope.Invalid -> return@inTransaction AttendanceCommandResult.Denied(AttendanceDenial.INVALID_POSITION)
            is PositionScope.Scoped -> scope.aggregate
        }
        when (val result = promoteAttendance(
            scoped,
            AttendanceSource.ORGANIZER,
            reason,
            requestId = requestId,
            repository = repository,
            charges = charges,
            timestamp = now(),
            ids = ids,
        )) {
            // Dois gestores tocando em "Colocar no jogo" na mesma pessoa: quem chega depois encontra o que pediu e
            // não leva erro. Sem evento nem cobrança novos; o jogo fechado continua negado antes daqui.
            is AttendancePromotionResult.Denied ->
                scoped.current?.takeIf { result.reason == AttendanceDenial.NOT_WAITLISTED && it.status == AttendanceStatus.CONFIRMED }
                    ?.let { AttendanceCommandResult.Success(it) }
                    ?: AttendanceCommandResult.Denied(result.reason)
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
            position = aggregate.position,
            forced = decision.forced,
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
        // Resposta do próprio atleta (não do organizador nem do sistema), sem convidado, de quem não é o dono.
        if (event.source == AttendanceSource.SELF && event.newStatus == AttendanceStatus.CONFIRMED &&
            aggregate.guestSeq == 0 && aggregate.actorRole != GroupRole.OWNER
        ) {
            firstConfirmation.confirmed(aggregate.groupId, aggregate.gameId, aggregate.memberId, event.id)
        }
        if (decision.createGameCharge) charges.confirmed(aggregate, aggregate.actorId)
        val leaving = decision.newStatus == AttendanceStatus.DECLINED
        val wasConfirmed = decision.oldStatus == AttendanceStatus.CONFIRMED
        if (leaving && wasConfirmed && aggregate.guestSeq > 0) charges.guestRemoved(aggregate, aggregate.actorId)
        // Anfitrião saiu: os convidados dele saem ANTES de qualquer promoção, senão a fila
        // promoveria um convidado que cai no passo seguinte.
        val freedGuestPositions = if (leaving && aggregate.guestSeq == 0) dropGuests(aggregate, timestamp) else emptyList()
        val freedByGuests = freedGuestPositions.size
        val freed = freedByGuests + if (leaving && wasConfirmed) 1 else 0
        // "Vou" depois do prazo cai na espera; em ordem de chegada a fila ocupa na hora as vagas livres
        // (a fila vem antes de quem acabou de entrar), sem esperar o job de cada minuto.
        val lateJoin = decision.newStatus == AttendanceStatus.WAITLISTED && timestamp > aggregate.confirmationDeadline
        val promoted = when {
            aggregate.promotionMode != PromotionMode.FIFO -> emptyList()
            // Posições: vaga que abre sobe só quem espera na MESMA posição; "Vou" tardio olha a própria posição.
            aggregate.position != null -> buildList {
                val positions = buildSet<AthletePosition> {
                    if (leaving && wasConfirmed) add(aggregate.position)
                    addAll(freedGuestPositions.filterNotNull())
                    if (lateJoin) add(aggregate.position)
                }
                positions.forEach { addAll(promotePosition(aggregate, it, repository, charges, timestamp, ids)) }
            }
            lateJoin -> promoteFreed(aggregate, (aggregate.capacity - aggregate.confirmedCount).coerceAtLeast(0), repository, charges, timestamp, ids, aggregate.confirmedCount)
            else -> promoteFreed(aggregate, freed, repository, charges, timestamp, ids)
        }
        val own = promoted.firstOrNull { it.memberId == record.memberId && it.guestSeq == record.guestSeq } ?: record
        return AttendanceCommandResult.Success(own, promoted, event)
    }

    /** Derruba os convidados ativos do anfitrião; devolve a posição de cada vaga CONFIRMADA que eles liberaram (nula sem posições). */
    private fun dropGuests(host: AttendanceAggregate, timestamp: Instant): List<AthletePosition?> =
        repository.activeGuests(host.groupId, host.gameId, host.memberId).filter { guest ->
            val dropped = guest.copy(status = AttendanceStatus.DECLINED, waitlistSequence = null, forced = false, updatedAt = maxOf(timestamp, guest.respondedAt), version = guest.version + 1)
            repository.save(dropped)
            repository.append(AttendanceEvent(ids(), host.gameId, host.groupId, host.memberId, host.actorId, AttendanceSource.SYSTEM, guest.status, AttendanceStatus.DECLINED, null, timestamp, guestSeq = guest.guestSeq))
            val confirmed = guest.status == AttendanceStatus.CONFIRMED
            if (confirmed) charges.guestRemoved(host.copy(guestSeq = guest.guestSeq, current = guest), host.actorId)
            confirmed
        }.map { it.position }

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
