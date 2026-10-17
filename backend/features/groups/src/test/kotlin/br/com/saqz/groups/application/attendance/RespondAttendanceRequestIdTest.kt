package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.PromotionMode
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** N-18: o link do WhatsApp repete o requestId entre toques diferentes; só vale como repetição se for a última resposta. */
class RespondAttendanceRequestIdTest {
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val athlete = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val going = UUID.randomUUID()
    private val notGoing = UUID.randomUUID()
    private val repository = FakeRepository()
    private val responses = RespondAttendance(
        object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
        repository,
        AttendanceChargePort { _, _ -> },
        { NOW },
    )

    private fun tap(intent: AttendanceIntent, requestId: UUID) =
        assertIs<AttendanceCommandResult.Success>(
            responses.execute(athlete, group, game, intent = intent, requestId = requestId),
        )

    @Test
    fun `retry imediato com o mesmo id e repeticao e nao grava evento novo`() {
        val first = tap(AttendanceIntent.CONFIRM, going)
        val retry = tap(AttendanceIntent.CONFIRM, going)
        assertEquals(1, repository.events.size)
        assertEquals(first.event?.id, retry.event?.id)
        assertEquals(AttendanceStatus.CONFIRMED, retry.attendance.status)
    }

    @Test
    fun `vou, nao vou, vou pelo mesmo link termina confirmado com 3 eventos`() {
        tap(AttendanceIntent.CONFIRM, going)
        tap(AttendanceIntent.DECLINE, notGoing)
        val last = tap(AttendanceIntent.CONFIRM, going)
        assertEquals(AttendanceStatus.CONFIRMED, last.attendance.status)
        assertEquals(
            listOf(AttendanceStatus.CONFIRMED, AttendanceStatus.DECLINED, AttendanceStatus.CONFIRMED),
            repository.events.map { it.newStatus },
        )
    }

    @Test
    fun `nao vou, vou, nao vou pelo mesmo link termina recusado com 3 eventos`() {
        tap(AttendanceIntent.DECLINE, notGoing)
        tap(AttendanceIntent.CONFIRM, going)
        val last = tap(AttendanceIntent.DECLINE, notGoing)
        assertEquals(AttendanceStatus.DECLINED, last.attendance.status)
        assertEquals(
            listOf(AttendanceStatus.DECLINED, AttendanceStatus.CONFIRMED, AttendanceStatus.DECLINED),
            repository.events.map { it.newStatus },
        )
    }

    @Test
    fun `retry depois da resposta nova repete so a ultima, sem duplicar`() {
        tap(AttendanceIntent.CONFIRM, going)
        tap(AttendanceIntent.DECLINE, notGoing)
        tap(AttendanceIntent.CONFIRM, going)
        val retry = tap(AttendanceIntent.CONFIRM, going)
        assertEquals(3, repository.events.size)
        assertEquals(AttendanceStatus.CONFIRMED, retry.attendance.status)
    }

    @Test
    fun `jogo cheio no terceiro toque vai para a espera como hoje`() {
        repository.record(other, AttendanceStatus.CONFIRMED)
        repository.capacity = 1
        tap(AttendanceIntent.DECLINE, notGoing)
        // vaga ocupada por outra pessoa: o "Vou" do segundo e do terceiro toque cai na espera
        val second = tap(AttendanceIntent.CONFIRM, going)
        assertEquals(AttendanceStatus.WAITLISTED, second.attendance.status)
        tap(AttendanceIntent.DECLINE, notGoing)
        val third = tap(AttendanceIntent.CONFIRM, going)
        assertEquals(AttendanceStatus.WAITLISTED, third.attendance.status)
        assertEquals(4, repository.events.size)
    }

    private inner class FakeRepository : AttendanceCommandRepository {
        val records = mutableMapOf<Pair<UUID, Int>, AttendanceRecord>()
        val events = mutableListOf<AttendanceEvent>()
        var capacity = 12
        private var allocator = 0L

        fun record(memberId: UUID, status: AttendanceStatus) {
            records[memberId to 0] = AttendanceRecord(game, group, memberId, status, null, NOW, NOW, 1, 0)
        }

        override fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID, guestSeq: Int): AttendanceAggregate? =
            AttendanceAggregate(
                group, game, memberId, actorId, GroupRole.ATHLETE, GameStatus.PUBLISHED, NOW.plusSeconds(60), capacity,
                records.values.count { it.status == AttendanceStatus.CONFIRMED },
                records[memberId to guestSeq], 2500L, LocalDate.of(2026, 8, 11),
                AthleteMembershipType.MENSALISTA, true, PromotionMode.FIFO, guestSeq,
                startsAt = NOW.plusSeconds(3600),
            )

        // Mesma regra do SQL: só é repetição se for o último evento da pessoa naquele jogo.
        override fun findResponseReplay(groupId: UUID, gameId: UUID, actorId: UUID, requestId: UUID): AttendanceResponseReplay? {
            val event = events.lastOrNull { it.memberId == actorId && it.guestSeq == 0 } ?: return null
            if (event.requestId != requestId || event.source != AttendanceSource.SELF) return null
            return AttendanceResponseReplay(records.getValue(event.memberId to 0), event)
        }

        override fun lockCapacity(groupId: UUID, gameId: UUID, actorId: UUID): CapacityAggregate? = null
        override fun nextWaitlistSequence(groupId: UUID, gameId: UUID) = ++allocator
        override fun earliestWaitlisted(groupId: UUID, gameId: UUID, now: java.time.Instant) =
            records.values.filter { it.status == AttendanceStatus.WAITLISTED }.minByOrNull { it.waitlistSequence!! }
        override fun save(record: AttendanceRecord) { records[record.memberId to record.guestSeq] = record }
        override fun saveGuest(record: AttendanceRecord, guestName: String) = save(record)
        override fun append(event: AttendanceEvent) { events += event }
        override fun updateCapacity(gameId: UUID, expectedVersion: Long, capacity: Int) = false
        override fun nextGuestSeq(gameId: UUID, hostId: UUID) = 1
        override fun activeGuests(groupId: UUID, gameId: UUID, hostId: UUID) = emptyList<AttendanceRecord>()
    }

    private companion object { val NOW: Instant = Instant.parse("2026-08-11T10:00:00Z") }
}
