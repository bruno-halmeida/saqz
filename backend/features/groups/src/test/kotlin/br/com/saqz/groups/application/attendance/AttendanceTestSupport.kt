package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.PromotionMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal val DIRECT_TRANSACTION = object : TransactionRunner {
    override fun <T> inTransaction(block: () -> T): T = block()
}

/** Chamada à porta de cobrança: quem foi cobrado ou estornado, de qual convidado e por quem. */
internal data class ChargeCall(val kind: String, val memberId: UUID, val guestSeq: Int, val actorId: UUID)

internal class RecordingCharges : AttendanceChargePort {
    val calls = mutableListOf<ChargeCall>()
    override fun confirmed(aggregate: AttendanceAggregate, actorId: UUID) {
        calls += ChargeCall("confirmed", aggregate.memberId, aggregate.guestSeq, actorId)
    }
    override fun promoted(aggregate: AttendanceAggregate, actorId: UUID) {
        calls += ChargeCall("promoted", aggregate.memberId, aggregate.guestSeq, actorId)
    }
    override fun guestRemoved(aggregate: AttendanceAggregate, actorId: UUID) {
        calls += ChargeCall("guestRemoved", aggregate.memberId, aggregate.guestSeq, actorId)
    }
}

/**
 * Jogo único em memória para os testes de presença. A fila sobe por `waitlistSequence` (a regra de mensalista
 * mora na query SQL e não é o que se testa aqui). Cada pessoa/convidado é uma linha em [records].
 */
internal class InMemoryAttendance(
    val group: UUID,
    val game: UUID,
    val now: Instant,
) : AttendanceCommandRepository {
    val records = linkedMapOf<Pair<UUID, Int>, AttendanceRecord>()
    val events = mutableListOf<AttendanceEvent>()
    val roles = mutableMapOf<UUID, GroupRole>()
    val membership = mutableMapOf<UUID, AthleteMembershipType>()
    val guestNames = mutableMapOf<Pair<UUID, Int>, String>()
    var capacity = 4
    var deadline: Instant = now.plusSeconds(3600)
    var startsAt: Instant = now.plusSeconds(3 * 3600)
    var gameStatus = GameStatus.PUBLISHED
    var promotionMode = PromotionMode.FIFO
    var mensalistaPriority = false
    var gameVersion = 1L
    var updatedCapacity: Int? = null
    var rejectCapacityUpdate = false
    private var allocator = 0L

    fun record(memberId: UUID, status: AttendanceStatus, sequence: Long? = null, guestSeq: Int = 0, version: Long = 1) {
        records[memberId to guestSeq] = AttendanceRecord(game, group, memberId, status, sequence, now, now, version, guestSeq)
        if (sequence != null) allocator = maxOf(allocator, sequence)
    }

    fun status(memberId: UUID, guestSeq: Int = 0) = records[memberId to guestSeq]?.status
    fun confirmedCount() = records.values.count { it.status == AttendanceStatus.CONFIRMED }

    override fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID, guestSeq: Int): AttendanceAggregate? {
        if (groupId != group || gameId != game) return null
        return AttendanceAggregate(
            group, game, memberId, actorId, roles[actorId], gameStatus, deadline, capacity, confirmedCount(),
            records[memberId to guestSeq], 2500L, LocalDate.of(2026, 8, 11),
            membership[memberId] ?: AthleteMembershipType.AVULSO, mensalistaPriority, promotionMode, guestSeq,
            guestNames[memberId to guestSeq], startsAt,
        )
    }

    override fun lockCapacity(groupId: UUID, gameId: UUID, actorId: UUID): CapacityAggregate? {
        if (groupId != group || gameId != game) return null
        return CapacityAggregate(
            group, game, actorId, roles[actorId], gameStatus, deadline, capacity, confirmedCount(), gameVersion,
            2500L, LocalDate.of(2026, 8, 11), mensalistaPriority, promotionMode, startsAt,
        )
    }

    override fun nextWaitlistSequence(groupId: UUID, gameId: UUID) = ++allocator

    override fun earliestWaitlisted(groupId: UUID, gameId: UUID, now: Instant) =
        records.values.filter { it.status == AttendanceStatus.WAITLISTED }.minByOrNull { it.waitlistSequence!! }

    override fun save(record: AttendanceRecord) { records[record.memberId to record.guestSeq] = record }
    override fun saveGuest(record: AttendanceRecord, guestName: String) {
        guestNames[record.memberId to record.guestSeq] = guestName
        save(record)
    }
    override fun append(event: AttendanceEvent) { events += event }

    override fun updateCapacity(gameId: UUID, expectedVersion: Long, capacity: Int): Boolean {
        if (rejectCapacityUpdate) return false
        updatedCapacity = capacity
        this.capacity = capacity
        return true
    }

    override fun nextGuestSeq(gameId: UUID, hostId: UUID) =
        (records.keys.filter { it.first == hostId }.maxOfOrNull { it.second } ?: 0) + 1

    override fun activeGuests(groupId: UUID, gameId: UUID, hostId: UUID) =
        records.filterKeys { it.first == hostId && it.second > 0 }.values
            .filter { it.status == AttendanceStatus.CONFIRMED || it.status == AttendanceStatus.WAITLISTED }

    override fun futureGamesWithActiveAttendance(groupId: UUID, memberId: UUID, after: Instant): List<UUID> =
        if (startsAt > after && records.any { (key, row) -> key.first == memberId && row.status != AttendanceStatus.DECLINED }) listOf(game)
        else emptyList()
}
