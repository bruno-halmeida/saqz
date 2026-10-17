package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.GameStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** DEB-020: depois do início do jogo as vagas não mudam; antes, mudam e a fila sobe. */
class AdjustGameCapacityTest {
    private val owner = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val waiting = UUID.randomUUID()
    private val repository = FakeRepository()
    private val service = AdjustGameCapacity(
        object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
        repository,
        { _, _ -> },
        { NOW },
    )

    @Test fun `antes do início aceita e promove a fila`() {
        val result = service.execute(owner, group, game, 1, 5)

        assertEquals(5, assertIs<CapacityCommandResult.Success>(result).capacity)
        assertEquals(5, repository.capacity)
        assertEquals(AttendanceStatus.CONFIRMED, repository.waiting.status)
    }

    @Test fun `no instante do início e depois recusa como encerrado sem tocar em nada`() {
        for (starts in listOf(NOW, NOW.minusSeconds(600))) {
            repository.startsAt = starts
            assertIs<CapacityCommandResult.Frozen>(service.execute(owner, group, game, 1, 5))
        }
        assertEquals(4, repository.capacity)
        assertEquals(AttendanceStatus.WAITLISTED, repository.waiting.status)
        assertTrue(repository.events.isEmpty())
    }

    private inner class FakeRepository : AttendanceCommandRepository {
        var capacity = 4
        var startsAt: Instant = NOW.plusSeconds(3600)
        var waiting = AttendanceRecord(game, group, this@AdjustGameCapacityTest.waiting, AttendanceStatus.WAITLISTED, 1, NOW, NOW, 1)
        val events = mutableListOf<AttendanceEvent>()

        override fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID, guestSeq: Int): AttendanceAggregate? = null
        override fun lockCapacity(groupId: UUID, gameId: UUID, actorId: UUID) = CapacityAggregate(
            group, game, actorId, GroupRole.OWNER, GameStatus.PUBLISHED, NOW.minusSeconds(60), capacity,
            4, 1, 2500L, LocalDate.of(2026, 8, 11), startsAt = startsAt,
        )
        override fun nextWaitlistSequence(groupId: UUID, gameId: UUID) = 2L
        override fun earliestWaitlisted(groupId: UUID, gameId: UUID, now: java.time.Instant) = waiting.takeIf { it.status == AttendanceStatus.WAITLISTED }
        override fun save(record: AttendanceRecord) { waiting = record }
        override fun saveGuest(record: AttendanceRecord, guestName: String) = save(record)
        override fun append(event: AttendanceEvent) { events += event }
        override fun updateCapacity(gameId: UUID, expectedVersion: Long, capacity: Int): Boolean { this.capacity = capacity; return true }
        override fun nextGuestSeq(gameId: UUID, hostId: UUID) = 1
        override fun activeGuests(groupId: UUID, gameId: UUID, hostId: UUID): List<AttendanceRecord> = emptyList()
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-10T10:00:00Z")
    }
}
