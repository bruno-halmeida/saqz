package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.AthleteMembershipType
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
    val membershipType: AthleteMembershipType,
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
    fun lockCapacity(groupId: UUID, gameId: UUID, actorId: UUID): CapacityAggregate?
    fun nextWaitlistSequence(groupId: UUID, gameId: UUID): Long
    fun earliestWaitlisted(groupId: UUID, gameId: UUID): AttendanceRecord?
    fun save(record: AttendanceRecord)
    fun append(event: AttendanceEvent)
    fun updateCapacity(gameId: UUID, expectedVersion: Long, capacity: Int): Boolean
}

fun interface AttendanceChargePort {
    fun confirmed(aggregate: AttendanceAggregate, actorId: UUID)
    fun promoted(aggregate: AttendanceAggregate, actorId: UUID) = confirmed(aggregate, actorId)
}

data class CapacityAggregate(
    val groupId: UUID,
    val gameId: UUID,
    val actorId: UUID,
    val actorRole: GroupRole?,
    val gameStatus: GameStatus,
    val confirmationDeadline: Instant,
    val capacity: Int,
    val confirmedCount: Int,
    val version: Long,
    val gameFeeCents: Long?,
    val gameDate: LocalDate,
)

data class AttendanceDetail(
    val own: AttendanceRecord?,
    val confirmedCount: Int,
    val availableSpots: Int,
    val waitlistCount: Int,
    val capacity: Int,
    val gameVersion: Long,
)

fun interface AttendanceDetailQuery {
    fun find(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceDetail?
}
