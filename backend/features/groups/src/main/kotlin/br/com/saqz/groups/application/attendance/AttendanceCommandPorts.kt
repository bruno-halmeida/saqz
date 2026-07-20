package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.GameStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AttendanceAggregate(
    val groupId: UUID,
    val gameId: UUID,
    val memberId: UUID,
    val actorId: UUID,
    val actorRole: GroupRole?,
    val gameStatus: GameStatus,
    val confirmationDeadline: Instant,
    val capacity: Int,
    val confirmedCount: Int,
    val current: AttendanceRecord?,
    val gameFeeCents: Long?,
    val gameDate: LocalDate,
)

data class AttendanceRecord(
    val gameId: UUID,
    val groupId: UUID,
    val memberId: UUID,
    val status: AttendanceStatus,
    val waitlistSequence: Long?,
    val respondedAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class AttendanceEvent(
    val id: UUID,
    val gameId: UUID,
    val groupId: UUID,
    val memberId: UUID,
    val actorId: UUID,
    val source: AttendanceSource,
    val oldStatus: AttendanceStatus?,
    val newStatus: AttendanceStatus,
    val reason: String?,
    val occurredAt: Instant,
)

interface AttendanceCommandRepository {
    fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID): AttendanceAggregate?
    fun nextWaitlistSequence(groupId: UUID, gameId: UUID): Long
    fun save(record: AttendanceRecord)
    fun append(event: AttendanceEvent)
}

fun interface AttendanceChargePort {
    fun confirmed(aggregate: AttendanceAggregate, actorId: UUID)
}
