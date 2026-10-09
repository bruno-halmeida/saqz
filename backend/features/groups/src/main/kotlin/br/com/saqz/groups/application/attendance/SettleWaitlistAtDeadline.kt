package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.communication.ReminderGame
import br.com.saqz.groups.application.communication.gameSchedule
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.PromotionMode
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
 * MANUAL só avisa dono e admins uma vez e deixa a escolha com eles. Idempotente pelo estado: sem fila
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
    private val ids: () -> UUID = UUID::randomUUID,
    private val onFailure: (String, Throwable) -> Unit = { _, _ -> },
) {
    /** Devolve quantas pessoas subiram mais quantos avisos saíram. */
    fun run(): Int = candidates.expired(now()).sumOf { candidate ->
        try {
            settle(candidate)
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            onFailure("game ${candidate.gameId}", failure)
            0
        }
    }

    private fun settle(candidate: DeadlineWaitlistCandidate): Int = transaction.inTransaction {
        val aggregate = repository.lockCapacity(candidate.groupId, candidate.gameId, candidate.ownerId)
            ?: return@inTransaction 0
        val timestamp = now()
        if (aggregate.gameStatus != GameStatus.PUBLISHED || aggregate.confirmationDeadline > timestamp) {
            return@inTransaction 0
        }
        val spots = aggregate.capacity - aggregate.confirmedCount
        if (spots <= 0) return@inTransaction 0
        when (aggregate.promotionMode) {
            PromotionMode.FIFO -> promote(aggregate, spots, timestamp)
            PromotionMode.MANUAL -> {
                notice.publish(candidate, waitlistDeadlineBody(candidate.game, candidate.waitlisted, spots))
                1
            }
        }
    }

    private fun promote(aggregate: CapacityAggregate, spots: Int, timestamp: Instant): Int {
        var confirmedCount = aggregate.confirmedCount
        var promoted = 0
        repeat(spots) {
            val waiting = repository.earliestWaitlisted(aggregate.groupId, aggregate.gameId) ?: return promoted
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
            promoted++
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
    )
}

/** Corpo do aviso: o que venceu, quantos esperam, quantas vagas, e o que o gestor precisa fazer. */
fun waitlistDeadlineBody(game: ReminderGame, waitlisted: Int, spots: Int): String =
    "Prazo de confirmação encerrado: ${gameSchedule(game.localDate, game.localTime)}\nLocal: ${game.venue}\n" +
        "$waitlisted na lista de espera · $spots ${if (spots == 1) "vaga aberta" else "vagas abertas"}\n\n" +
        "O grupo escolhe quem entra: abra o jogo e libere as vagas."
