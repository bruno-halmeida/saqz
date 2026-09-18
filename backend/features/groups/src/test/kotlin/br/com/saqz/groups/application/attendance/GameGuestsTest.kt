package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceDenial
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.PromotionMode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GameGuestsTest {
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val host = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private lateinit var repository: FakeRepository
    private lateinit var guests: GameGuests

    @BeforeEach
    fun setup() {
        repository = FakeRepository()
        val transaction = object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() }
        val charges = AttendanceChargePort { _, _ -> }
        val responses = RespondAttendance(transaction, repository, charges, { NOW }, UUID::randomUUID)
        guests = GameGuests(transaction, repository, responses, { NOW })
    }

    @Test
    fun guestJoinsTheWaitlistEvenWithRoom() {
        repository.record(host, AttendanceStatus.CONFIRMED)
        val result = assertIs<AttendanceCommandResult.Success>(guests.add(host, group, game, "Rafa Moreira", UUID.randomUUID()))
        assertEquals(AttendanceStatus.WAITLISTED, result.attendance.status)
        assertEquals(1, result.attendance.guestSeq)
    }

    @Test
    fun hostWithoutAnswerCannotBringAGuest() {
        val result = assertIs<AttendanceCommandResult.Denied>(guests.add(host, group, game, "Rafa Moreira", UUID.randomUUID()))
        assertEquals(AttendanceDenial.HOST_NOT_GOING, result.reason)
    }

    @Test
    fun hostDeclinedCannotBringAGuest() {
        repository.record(host, AttendanceStatus.DECLINED)
        val result = assertIs<AttendanceCommandResult.Denied>(guests.add(host, group, game, "Rafa Moreira", UUID.randomUUID()))
        assertEquals(AttendanceDenial.HOST_NOT_GOING, result.reason)
    }

    @Test
    fun afterTheDeadlineTheHostCannotAddOrRemove() {
        repository.deadline = NOW.minusSeconds(1)
        repository.record(host, AttendanceStatus.CONFIRMED)
        val addResult = assertIs<AttendanceCommandResult.Denied>(guests.add(host, group, game, "Rafa Moreira", UUID.randomUUID()))
        assertEquals(AttendanceDenial.DEADLINE_PASSED, addResult.reason)
        repository.record(host, AttendanceStatus.WAITLISTED, guestSeq = 1)
        val removeResult = assertIs<AttendanceCommandResult.Denied>(guests.remove(host, group, game, host, 1))
        assertEquals(AttendanceDenial.DEADLINE_PASSED, removeResult.reason)
    }

    @Test
    fun organizerRemovesAGuestAfterTheDeadline() {
        repository.deadline = NOW.minusSeconds(1)
        repository.record(host, AttendanceStatus.CONFIRMED)
        repository.record(host, AttendanceStatus.WAITLISTED, guestSeq = 1)
        val result = assertIs<AttendanceCommandResult.Success>(guests.remove(owner, group, game, host, 1))
        assertEquals(AttendanceStatus.DECLINED, result.attendance.status)
    }

    @Test
    fun anotherMemberCannotRemove() {
        repository.record(host, AttendanceStatus.CONFIRMED)
        repository.record(host, AttendanceStatus.WAITLISTED, guestSeq = 1)
        assertEquals(AttendanceCommandResult.Hidden, guests.remove(other, group, game, host, 1))
    }

    @Test
    fun invalidNamesAreRejected() {
        repository.record(host, AttendanceStatus.CONFIRMED)
        listOf("A", "a".repeat(81), "   ", "badname").forEach { name ->
            val result = assertIs<AttendanceCommandResult.Denied>(guests.add(host, group, game, name, UUID.randomUUID()))
            assertEquals(AttendanceDenial.REASON_INVALID, result.reason)
        }
    }

    @Test
    fun secondGuestGetsTheNextSeq() {
        repository.record(host, AttendanceStatus.CONFIRMED)
        guests.add(host, group, game, "Primeiro Convidado", UUID.randomUUID())
        val second = assertIs<AttendanceCommandResult.Success>(guests.add(host, group, game, "Segundo Convidado", UUID.randomUUID()))
        assertEquals(2, second.attendance.guestSeq)
    }

    private inner class FakeRepository : AttendanceCommandRepository {
        val records = mutableMapOf<Pair<UUID, Int>, AttendanceRecord>()
        val events = mutableListOf<AttendanceEvent>()
        var deadline = NOW.plusSeconds(60)
        var allocator = 0L
        private val status = GameStatus.PUBLISHED
        private val capacity = 12

        fun record(memberId: UUID, status: AttendanceStatus, sequence: Long? = null, guestSeq: Int = 0) {
            records[memberId to guestSeq] = AttendanceRecord(game, group, memberId, status, sequence, NOW, NOW, 1, guestSeq)
            if (sequence != null) allocator = maxOf(allocator, sequence)
        }

        override fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID, guestSeq: Int): AttendanceAggregate? {
            if (groupId != group || gameId != game) return null
            val actorRole = role(actorId) ?: return null
            return AttendanceAggregate(
                group, game, memberId, actorId, actorRole, status, deadline, capacity, confirmedCount(),
                records[memberId to guestSeq], 2500L, LocalDate.of(2026, 8, 11),
                AthleteMembershipType.MENSALISTA, true, PromotionMode.FIFO, guestSeq,
            )
        }

        override fun lockCapacity(groupId: UUID, gameId: UUID, actorId: UUID): CapacityAggregate? = null
        override fun nextWaitlistSequence(groupId: UUID, gameId: UUID) = ++allocator
        override fun earliestWaitlisted(groupId: UUID, gameId: UUID) =
            records.values.filter { it.status == AttendanceStatus.WAITLISTED }.minByOrNull { it.waitlistSequence!! }
        override fun save(record: AttendanceRecord) { records[record.memberId to record.guestSeq] = record }
        override fun saveGuest(record: AttendanceRecord, guestName: String) = save(record)
        override fun append(event: AttendanceEvent) { events += event }
        override fun updateCapacity(gameId: UUID, expectedVersion: Long, capacity: Int) = false
        override fun nextGuestSeq(gameId: UUID, hostId: UUID) =
            (records.keys.filter { it.first == hostId }.maxOfOrNull { it.second } ?: 0) + 1
        override fun activeGuests(groupId: UUID, gameId: UUID, hostId: UUID) =
            records.filterKeys { it.first == hostId && it.second > 0 }.values
                .filter { it.status == AttendanceStatus.CONFIRMED || it.status == AttendanceStatus.WAITLISTED }

        private fun confirmedCount() = records.values.count { it.status == AttendanceStatus.CONFIRMED }
        private fun role(actorId: UUID) = when (actorId) {
            owner -> GroupRole.OWNER
            host, other -> GroupRole.ATHLETE
            else -> null
        }
    }

    private companion object { val NOW: Instant = Instant.parse("2026-08-11T10:00:00Z") }
}
