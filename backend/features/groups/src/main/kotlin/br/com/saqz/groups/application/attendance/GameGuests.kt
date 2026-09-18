package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceDecision
import br.com.saqz.groups.domain.attendance.AttendanceDecisionContext
import br.com.saqz.groups.domain.attendance.AttendanceDenial
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceTransitionPolicy
import java.time.Instant
import java.util.UUID

/** Convidado de jogo (VUL-239): "+1" pelo nome, linha de presença do ANFITRIÃO com `guestSeq > 0`. */
class GameGuests(
    private val transaction: TransactionRunner,
    private val repository: AttendanceCommandRepository,
    private val responses: RespondAttendance,
    private val now: () -> Instant,
) {
    fun add(actorId: UUID, groupId: UUID, gameId: UUID, rawName: String?, requestId: UUID): AttendanceCommandResult =
        transaction.inTransaction {
            val name = guestName(rawName) ?: return@inTransaction AttendanceCommandResult.Denied(AttendanceDenial.REASON_INVALID)
            repository.findResponseReplay(groupId, gameId, actorId, requestId)?.let {
                return@inTransaction AttendanceCommandResult.Success(it.attendance, event = it.event)
            }
            val host = repository.lock(groupId, gameId, actorId, actorId)
                ?: return@inTransaction AttendanceCommandResult.Hidden
            if (host.actorRole == null) return@inTransaction AttendanceCommandResult.Hidden
            if (host.current?.status !in setOf(AttendanceStatus.CONFIRMED, AttendanceStatus.WAITLISTED)) {
                return@inTransaction AttendanceCommandResult.Denied(AttendanceDenial.HOST_NOT_GOING)
            }
            val guest = host.copy(current = null, guestSeq = repository.nextGuestSeq(gameId, actorId), guestName = name)
            decide(guest, AttendanceIntent.CONFIRM, AttendanceSource.SELF, null, requestId)
        }

    fun remove(actorId: UUID, groupId: UUID, gameId: UUID, hostId: UUID, guestSeq: Int): AttendanceCommandResult =
        transaction.inTransaction {
            if (guestSeq <= 0) return@inTransaction AttendanceCommandResult.Hidden
            val guest = repository.lock(groupId, gameId, hostId, actorId, guestSeq)
                ?: return@inTransaction AttendanceCommandResult.Hidden
            val current = guest.current ?: return@inTransaction AttendanceCommandResult.Hidden
            val organizer = guest.actorRole == GroupRole.OWNER || guest.actorRole == GroupRole.ADMIN
            val source = when {
                actorId == hostId -> AttendanceSource.SELF
                organizer -> AttendanceSource.ORGANIZER
                else -> return@inTransaction AttendanceCommandResult.Hidden
            }
            if (current.status == AttendanceStatus.DECLINED) return@inTransaction AttendanceCommandResult.Success(current)
            decide(guest, AttendanceIntent.DECLINE, source, ORGANIZER_REASON.takeIf { source == AttendanceSource.ORGANIZER }, null)
        }

    private fun decide(
        aggregate: AttendanceAggregate,
        intent: AttendanceIntent,
        source: AttendanceSource,
        reason: String?,
        requestId: UUID?,
    ): AttendanceCommandResult = when (val decision = AttendanceTransitionPolicy.decide(
        AttendanceDecisionContext(
            aggregate.gameStatus, aggregate.confirmationDeadline, now(), aggregate.capacity, aggregate.confirmedCount,
            aggregate.current?.status, source, reason, aggregate.membershipType, aggregate.mensalistaPriority, guest = true,
        ),
        intent,
    )) {
        is AttendanceDecision.Denied -> AttendanceCommandResult.Denied(decision.reason)
        is AttendanceDecision.Transition -> responses.apply(aggregate, decision, requestId)
    }

    private fun guestName(raw: String?): String? {
        val name = raw?.trim()?.takeUnless(String::isBlank) ?: return null
        if (name.codePointCount(0, name.length) !in 2..80) return null
        if (name.codePoints().anyMatch(Character::isISOControl)) return null
        return name
    }

    private companion object {
        const val ORGANIZER_REASON = "Convidado removido pelo gestor"
    }
}
