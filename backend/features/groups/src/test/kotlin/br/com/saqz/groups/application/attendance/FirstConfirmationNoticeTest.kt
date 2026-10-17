package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.communication.FirstConfirmationNotice
import br.com.saqz.groups.application.communication.firstConfirmationBody
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
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Quando RespondAttendance chama o aviso da primeira confirmação (a regra "é a primeira" mora no SQL). */
class FirstConfirmationNoticeTest {
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val athlete = UUID.randomUUID()
    private val repository = FakeRepository()
    private val calls = mutableListOf<Triple<UUID, UUID, UUID>>()
    private val responses = RespondAttendance(
        object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
        repository,
        AttendanceChargePort { _, _ -> },
        { NOW },
        UUID::randomUUID,
        FirstConfirmationNotice { groupId, gameId, memberId, _ -> calls += Triple(groupId, gameId, memberId) },
    )

    @Test
    fun `atleta que confirma por conta propria dispara o aviso uma vez, com o evento gravado`() {
        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))
        assertEquals(AttendanceStatus.CONFIRMED, result.attendance.status)
        assertEquals(listOf(Triple(group, game, athlete)), calls)
        assertEquals(1, repository.events.size)
    }

    @Test
    fun `responder de novo sem mudar nada nao repete o aviso`() {
        responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM)
        responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM)
        assertEquals(1, calls.size)
    }

    @Test
    fun `o dono confirmando a propria presenca nao dispara`() {
        responses.execute(owner, group, game, intent = AttendanceIntent.CONFIRM)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `confirmacao marcada pelo organizador nao dispara`() {
        responses.execute(owner, group, game, memberId = athlete, intent = AttendanceIntent.CONFIRM, source = AttendanceSource.ORGANIZER, reason = "Confirmado na quadra")
        assertEquals(AttendanceSource.ORGANIZER, repository.events.single().source)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `confirmacao do sistema nao dispara`() {
        responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM, source = AttendanceSource.SYSTEM)
        assertEquals(AttendanceSource.SYSTEM, repository.events.single().source)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `recusar ou ficar na lista de espera nao dispara`() {
        responses.execute(athlete, group, game, intent = AttendanceIntent.DECLINE)
        repository.capacity = 0
        val waitlisted = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))
        assertEquals(AttendanceStatus.WAITLISTED, waitlisted.attendance.status)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `convidado do atleta nao dispara`() {
        val guests = GameGuests(
            object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
            repository, responses, { NOW },
        )
        repository.record(athlete, AttendanceStatus.CONFIRMED)
        guests.add(athlete, group, game, "Rafa Moreira", UUID.randomUUID())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `texto do aviso usa o dia e a hora no formato das mensagens de jogo`() {
        assertEquals(
            "Rafa confirmou presença no jogo de ${br.com.saqz.groups.application.communication.gameSchedule(LocalDate.of(2026, 8, 11), LocalTime.of(20, 0))}. " +
                "A lista começou a fechar sozinha.",
            firstConfirmationBody("Rafa", LocalDate.of(2026, 8, 11), LocalTime.of(20, 0)),
        )
    }

    private inner class FakeRepository : AttendanceCommandRepository {
        val records = mutableMapOf<Pair<UUID, Int>, AttendanceRecord>()
        val events = mutableListOf<AttendanceEvent>()
        var capacity = 12
        private var allocator = 0L

        fun record(memberId: UUID, status: AttendanceStatus, guestSeq: Int = 0) {
            records[memberId to guestSeq] = AttendanceRecord(game, group, memberId, status, null, NOW, NOW, 1, guestSeq)
        }

        override fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID, guestSeq: Int): AttendanceAggregate? {
            val role = when (actorId) {
                owner -> GroupRole.OWNER
                athlete -> GroupRole.ATHLETE
                else -> return null
            }
            return AttendanceAggregate(
                group, game, memberId, actorId, role, GameStatus.PUBLISHED, NOW.plusSeconds(60), capacity,
                records.values.count { it.status == AttendanceStatus.CONFIRMED },
                records[memberId to guestSeq], 2500L, LocalDate.of(2026, 8, 11),
                AthleteMembershipType.MENSALISTA, true, PromotionMode.FIFO, guestSeq,
                startsAt = NOW.plusSeconds(3600),
            )
        }

        override fun lockCapacity(groupId: UUID, gameId: UUID, actorId: UUID): CapacityAggregate? = null
        override fun nextWaitlistSequence(groupId: UUID, gameId: UUID) = ++allocator
        override fun earliestWaitlisted(groupId: UUID, gameId: UUID, now: java.time.Instant) =
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
    }

    private companion object { val NOW: Instant = Instant.parse("2026-08-11T10:00:00Z") }
}
