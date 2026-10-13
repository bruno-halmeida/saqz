package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.group.PromotionMode
import java.time.Instant
import java.util.UUID

/**
 * Libera a presença de quem deixou o grupo (saiu, foi removido ou excluiu a conta).
 * Roda na transação de quem chama e ANTES de a filiação ser apagada.
 */
fun interface AttendanceRelease {
    fun release(groupId: UUID, memberId: UUID, actorId: UUID)
}

/** Nos jogos futuros, a pessoa e os convidados dela viram DECLINED (evento SYSTEM) e a fila anda como num "Não vou". */
class ReleaseMemberAttendance(
    private val repository: AttendanceCommandRepository,
    private val charges: AttendanceChargePort,
    private val now: () -> Instant,
    private val ids: () -> UUID = UUID::randomUUID,
) : AttendanceRelease {
    override fun release(groupId: UUID, memberId: UUID, actorId: UUID) {
        val timestamp = now()
        repository.futureGamesWithActiveAttendance(groupId, memberId, timestamp).forEach { gameId ->
            val aggregate = repository.lock(groupId, gameId, memberId, actorId) ?: return@forEach
            val active = buildList {
                aggregate.current?.takeIf { it.status != AttendanceStatus.DECLINED }?.let(::add)
                addAll(repository.activeGuests(groupId, gameId, memberId))
            }
            var freed = 0
            active.forEach { row ->
                repository.save(row.copy(status = AttendanceStatus.DECLINED, waitlistSequence = null, updatedAt = maxOf(timestamp, row.respondedAt), version = row.version + 1))
                repository.append(AttendanceEvent(ids(), gameId, groupId, memberId, actorId, AttendanceSource.SYSTEM, row.status, AttendanceStatus.DECLINED, null, timestamp, guestSeq = row.guestSeq))
                if (row.status == AttendanceStatus.CONFIRMED) {
                    freed++
                    if (row.guestSeq > 0) charges.guestRemoved(aggregate.copy(guestSeq = row.guestSeq, current = row), actorId)
                }
            }
            if (freed > 0 && aggregate.promotionMode == PromotionMode.FIFO) {
                promoteFreed(aggregate, freed, repository, charges, timestamp, ids)
            }
        }
    }
}
