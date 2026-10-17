package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.communication.ReminderGame
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.PromotionMode
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettleWaitlistAtDeadlineTest {
    private val owner = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val third = UUID.randomUUID()
    private val repository = FakeRepository()
    private val notices = mutableListOf<Pair<DeadlineWaitlistCandidate, String>>()
    private val failures = mutableListOf<String>()
    private var candidates = listOf(candidate())
    private val service = SettleWaitlistAtDeadline(
        object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
        { candidates },
        repository,
        { _, _ -> },
        { candidate, body -> notices += candidate to body },
        { NOW },
        onFailure = { what, _ -> failures += what },
    )

    @Test fun `ordem de chegada sobe a fila enquanto houver vaga e assina como sistema pelo dono`() {
        repository.capacity = 3
        repository.record(UUID.randomUUID(), AttendanceStatus.CONFIRMED)
        repository.record(first, AttendanceStatus.WAITLISTED, 1)
        repository.record(second, AttendanceStatus.WAITLISTED, 2)
        repository.record(third, AttendanceStatus.WAITLISTED, 3)

        assertEquals(2, service.run())

        assertEquals(AttendanceStatus.CONFIRMED, repository.status(first))
        assertEquals(AttendanceStatus.CONFIRMED, repository.status(second))
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(third))
        assertEquals(listOf(first, second), repository.events.map { it.memberId })
        assertTrue(repository.events.all { it.source == AttendanceSource.SYSTEM && it.actorId == owner && it.newStatus == AttendanceStatus.CONFIRMED })
        assertTrue(notices.isEmpty())
    }

    @Test fun `escolha manual avisa o gestor com fila e vagas e não promove ninguém`() {
        repository.promotionMode = PromotionMode.MANUAL
        repository.capacity = 4
        repository.record(UUID.randomUUID(), AttendanceStatus.CONFIRMED)
        repository.record(first, AttendanceStatus.WAITLISTED, 1)
        repository.record(second, AttendanceStatus.WAITLISTED, 2)
        candidates = listOf(candidate(waitlisted = 2, confirmed = 1))

        assertEquals(1, service.run())

        assertEquals(AttendanceStatus.WAITLISTED, repository.status(first))
        assertTrue(repository.events.isEmpty())
        val (candidate, body) = notices.single()
        assertEquals(game, candidate.gameId)
        assertEquals(
            "Prazo de confirmação encerrado: terça, 11/08 às 19:30\nLocal: Quadra QA\n2 na lista de espera · 3 vagas abertas\n\n" +
                "O grupo escolhe quem entra: abra o jogo e libere as vagas.",
            body,
        )
    }

    @Test fun `não mexe em jogo com prazo aberto, lotado ou fora de publicado`() {
        repository.record(first, AttendanceStatus.WAITLISTED, 1)
        repository.deadline = NOW.plusSeconds(60)
        assertEquals(0, service.run())
        repository.deadline = NOW.minusSeconds(60)
        repository.capacity = 0
        assertEquals(0, service.run())
        repository.capacity = 2
        repository.gameStatus = GameStatus.COMPLETED
        assertEquals(0, service.run())
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(first))
        assertTrue(notices.isEmpty())
    }

    @Test fun `depois do inicio do jogo a fila não sobe nem no prazo vencido`() {
        repository.capacity = 3
        repository.record(first, AttendanceStatus.WAITLISTED, 1)
        repository.startsAt = NOW.minusSeconds(1)

        assertEquals(0, service.run())
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(first))
        assertTrue(repository.events.isEmpty())

        repository.startsAt = NOW.plusSeconds(1)
        assertEquals(1, service.run())
        assertEquals(AttendanceStatus.CONFIRMED, repository.status(first))
    }

    @Test fun `um jogo que falha não derruba os outros`() {
        repository.record(first, AttendanceStatus.WAITLISTED, 1)
        val broken = candidate().copy(gameId = UUID.randomUUID())
        repository.failOn = broken.gameId
        candidates = listOf(broken, candidate())

        assertEquals(1, service.run())

        assertEquals(listOf("game ${broken.gameId}"), failures)
        assertEquals(AttendanceStatus.CONFIRMED, repository.status(first))
    }

    private fun candidate(waitlisted: Int = 1, confirmed: Int = 0) = DeadlineWaitlistCandidate(
        gameId = game, groupId = group, ownerId = owner,
        game = ReminderGame(LocalDate.of(2026, 8, 11), LocalTime.of(19, 30), "Quadra QA"),
        capacity = 2, confirmed = confirmed, waitlisted = waitlisted,
    )

    private inner class FakeRepository : AttendanceCommandRepository {
        val records = mutableMapOf<Pair<UUID, Int>, AttendanceRecord>()
        val events = mutableListOf<AttendanceEvent>()
        var deadline: Instant = NOW.minusSeconds(60)
        var capacity = 2
        var promotionMode = PromotionMode.FIFO
        var gameStatus = GameStatus.PUBLISHED
        var failOn: UUID? = null
        var startsAt: Instant = NOW.plusSeconds(6 * 3600)
        private var allocator = 0L

        fun record(memberId: UUID, status: AttendanceStatus, sequence: Long? = null) {
            records[memberId to 0] = AttendanceRecord(game, group, memberId, status, sequence, NOW, NOW, 1)
            if (sequence != null) allocator = maxOf(allocator, sequence)
        }
        fun status(memberId: UUID) = records.getValue(memberId to 0).status

        override fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID, guestSeq: Int): AttendanceAggregate? = null
        override fun lockCapacity(groupId: UUID, gameId: UUID, actorId: UUID): CapacityAggregate? {
            if (gameId == failOn) error("locked by someone else")
            if (groupId != group || gameId != game) return null
            return CapacityAggregate(
                group, game, actorId, GroupRole.OWNER, gameStatus, deadline, capacity,
                records.values.count { it.status == AttendanceStatus.CONFIRMED }, 1, 2500L, LocalDate.of(2026, 8, 11),
                mensalistaPriority = true, promotionMode = promotionMode, startsAt = startsAt,
            )
        }
        override fun nextWaitlistSequence(groupId: UUID, gameId: UUID) = ++allocator
        override fun earliestWaitlisted(groupId: UUID, gameId: UUID, now: java.time.Instant) =
            records.values.filter { it.status == AttendanceStatus.WAITLISTED }.minByOrNull { it.waitlistSequence!! }
        override fun save(record: AttendanceRecord) { records[record.memberId to record.guestSeq] = record }
        override fun saveGuest(record: AttendanceRecord, guestName: String) = save(record)
        override fun append(event: AttendanceEvent) { events += event }
        override fun updateCapacity(gameId: UUID, expectedVersion: Long, capacity: Int) = false
        override fun nextGuestSeq(gameId: UUID, hostId: UUID) = 1
        override fun activeGuests(groupId: UUID, gameId: UUID, hostId: UUID): List<AttendanceRecord> = emptyList()
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-10T10:00:00Z")
        @Suppress("unused") val MEMBERSHIP = AthleteMembershipType.AVULSO
    }
}
