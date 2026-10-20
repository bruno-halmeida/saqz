package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.communication.ReminderGame
import br.com.saqz.groups.application.communication.gameSchedule
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.PositionLoad
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.sharedkernel.job.JobLog
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import java.time.Instant
import java.util.UUID

/** Jogo publicado cujo prazo de confirmação venceu com gente na fila e vaga sobrando. */
data class DeadlineWaitlistCandidate(
    val gameId: UUID,
    val groupId: UUID,
    val ownerId: UUID,
    val game: ReminderGame,
    val capacity: Int,
    val confirmed: Int,
    val waitlisted: Int,
)

fun interface DeadlineWaitlistRepository {
    /** Candidatos em [now]; em escolha manual, só quem ainda não recebeu o aviso. */
    fun expired(now: Instant): List<DeadlineWaitlistCandidate>
}

/** Aviso ao dono e admins quando o grupo escolhe quem entra (canal WAITLIST_DEADLINE, uma vez por jogo). */
fun interface WaitlistDeadlineNotice {
    fun publish(candidate: DeadlineWaitlistCandidate, body: String)
}

/**
 * Prazo de confirmação vencido: a prioridade de mensalista acaba e a fila entra nas vagas que sobraram.
 * Em ORDEM DE CHEGADA o sistema promove (origem SYSTEM, o dono assina) enquanto houver vaga; em ESCOLHA
 * MANUAL só avisa dono e admins uma vez e deixa a escolha com eles — menos em grupo congelado (DEB-074), onde
 * o aviso pede uma ação que o grupo recusa; a ordem de chegada segue promovendo. Idempotente pelo estado: sem fila
 * ou sem vaga não há candidato, e o aviso manual é único por jogo (índice em group_messages).
 * Decisão de 09/10/2026 (tests/multi-aparelhos/rodada-2026-10-09.md, achado 1).
 */
class SettleWaitlistAtDeadline(
    private val transaction: TransactionRunner,
    private val candidates: DeadlineWaitlistRepository,
    private val repository: AttendanceCommandRepository,
    private val charges: AttendanceChargePort,
    private val notice: WaitlistDeadlineNotice,
    private val now: () -> Instant,
    private val writeAccess: GroupWriteAccess = GroupWriteAccess.Unrestricted,
    private val ids: () -> UUID = UUID::randomUUID,
    private val onFailure: (String, Throwable) -> Unit = { _, _ -> },
) {
    /** Devolve quantas pessoas subiram mais quantos avisos saíram. */
    fun run(): Int = candidates.expired(now()).sumOf { candidate ->
        try {
            val settled = settle(candidate)
            // Um `job_item` por promovido (ou pelo aviso da escolha manual), depois do commit; só ids.
            settled.promotedMemberIds.forEach {
                JobLog.item("waitlist_deadline", "waitlist_promoted", "groupId" to candidate.groupId, "gameId" to candidate.gameId, "memberId" to it)
            }
            if (settled.noticed) JobLog.item("waitlist_deadline", "manual_choice_notified", "groupId" to candidate.groupId, "gameId" to candidate.gameId)
            settled.promotedMemberIds.size + if (settled.noticed) 1 else 0
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            onFailure("game ${candidate.gameId}", failure)
            JobLog.itemFailed("waitlist_deadline", "settle_failed", failure, "groupId" to candidate.groupId, "gameId" to candidate.gameId)
            0
        }
    }

    private class Settled(val promotedMemberIds: List<UUID> = emptyList(), val noticed: Boolean = false)

    private fun settle(candidate: DeadlineWaitlistCandidate): Settled = transaction.inTransaction {
        val aggregate = repository.lockCapacity(candidate.groupId, candidate.gameId, candidate.ownerId)
            ?: return@inTransaction Settled()
        val timestamp = now()
        if (aggregate.gameStatus != GameStatus.PUBLISHED || aggregate.confirmationDeadline > timestamp) {
            return@inTransaction Settled()
        }
        // Jogo com posições: vaga aberta é a de cada posição que tem fila; nada sobe de uma posição para outra.
        val loads = repository.positionLoads(candidate.gameId)
        if (loads.isNotEmpty()) return@inTransaction settlePositions(candidate, aggregate, loads, timestamp)
        val spots = aggregate.capacity - aggregate.confirmedCount
        if (spots <= 0) return@inTransaction Settled()
        when (aggregate.promotionMode) {
            PromotionMode.FIFO -> Settled(promotedMemberIds = promote(aggregate, spots, timestamp))
            PromotionMode.MANUAL -> {
                if (!writeAccess.canWrite(candidate.groupId)) return@inTransaction Settled()
                notice.publish(candidate, waitlistDeadlineBody(candidate.game, candidate.waitlisted, spots))
                Settled(noticed = true)
            }
        }
    }

    private fun settlePositions(
        candidate: DeadlineWaitlistCandidate,
        aggregate: CapacityAggregate,
        loads: List<PositionLoad>,
        timestamp: Instant,
    ): Settled {
        val open = loads.filter { it.waitlisted > 0 && it.confirmed < it.slots }
        if (open.isEmpty()) return Settled()
        return when (aggregate.promotionMode) {
            PromotionMode.FIFO -> Settled(
                promotedMemberIds = open.flatMap { load ->
                    promotePosition(aggregate.forMember(aggregate.placeholder()), load.position, repository, charges, timestamp, ids)
                        .map(AttendanceRecord::memberId)
                },
            )
            PromotionMode.MANUAL -> {
                if (!writeAccess.canWrite(candidate.groupId)) return Settled()
                notice.publish(candidate, waitlistDeadlineBody(candidate.game, open.sumOf { it.waitlisted }, open.sumOf { it.slots - it.confirmed }))
                Settled(noticed = true)
            }
        }
    }

    // Só o esqueleto do agregado: promotePosition troca membro, linha e vagas a cada promoção.
    private fun CapacityAggregate.placeholder() = AttendanceRecord(
        gameId, groupId, actorId, br.com.saqz.groups.domain.attendance.AttendanceStatus.WAITLISTED, null, startsAt, startsAt, 0,
    )

    private fun promote(aggregate: CapacityAggregate, spots: Int, timestamp: Instant): List<UUID> {
        var confirmedCount = aggregate.confirmedCount
        val promoted = mutableListOf<UUID>()
        repeat(spots) {
            val waiting = repository.earliestWaitlisted(aggregate.groupId, aggregate.gameId, timestamp) ?: return promoted
            val result = promoteAttendance(
                aggregate.forMember(waiting).copy(confirmedCount = confirmedCount),
                AttendanceSource.SYSTEM,
                reason = null,
                repository = repository,
                charges = charges,
                timestamp = timestamp,
                ids = ids,
            )
            if (result !is AttendancePromotionResult.Success) return promoted
            promoted += waiting.memberId
            confirmedCount++
        }
        return promoted
    }

    // Como na promoção por capacidade: depois do prazo a fila é só ordem de chegada; o vínculo só alimenta a cobrança.
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

/** Corpo do aviso: o que venceu, quantos esperam, quantas vagas, e o que o gestor precisa fazer. */
fun waitlistDeadlineBody(game: ReminderGame, waitlisted: Int, spots: Int): String =
    "Prazo de confirmação encerrado: ${gameSchedule(game.localDate, game.localTime)}\nLocal: ${game.venue}\n" +
        "$waitlisted na lista de espera · $spots ${if (spots == 1) "vaga aberta" else "vagas abertas"}\n\n" +
        "O grupo escolhe quem entra: abra o jogo e libere as vagas."
