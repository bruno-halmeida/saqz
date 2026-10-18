package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.communication.ReminderGame
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.group.PromotionMode
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PROM-005: o job do prazo sobe a fila nas vagas que sobraram (e só nelas), ou avisa o organizador uma vez. */
class SettleWaitlistAtDeadlineRulesTest {
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val waiting = List(5) { UUID.randomUUID() }
    private val repository = InMemoryAttendance(group, game, NOW).apply {
        roles[owner] = GroupRole.OWNER
        deadline = NOW.minusSeconds(60)
    }
    private val charges = RecordingCharges()
    private val notices = mutableListOf<Pair<DeadlineWaitlistCandidate, String>>()
    private var candidates = listOf(candidate())
    private val job = SettleWaitlistAtDeadline(
        DIRECT_TRANSACTION,
        { candidates },
        repository,
        charges,
        { candidate, body -> notices += candidate to body },
        { NOW },
    )

    @Test
    fun `sobe exatamente as vagas que sobraram, na ordem da fila, cobrando cada um assinado pelo dono`() {
        repository.capacity = 5
        repeat(2) { repository.record(UUID.randomUUID(), AttendanceStatus.CONFIRMED) }
        waiting.forEachIndexed { i, id -> repository.record(id, AttendanceStatus.WAITLISTED, sequence = i + 1L) }

        assertEquals(3, job.run())

        assertEquals(waiting.take(3), repository.events.map { it.memberId })
        assertEquals(waiting.take(3).map { ChargeCall("promoted", it, 0, owner) }, charges.calls)
        assertEquals(List(3) { AttendanceStatus.CONFIRMED }, waiting.take(3).map { repository.status(it) })
        assertEquals(List(2) { AttendanceStatus.WAITLISTED }, waiting.drop(3).map { repository.status(it) })
        assertEquals(5, repository.confirmedCount())
        assertTrue(repository.events.all { it.oldStatus == AttendanceStatus.WAITLISTED && it.newStatus == AttendanceStatus.CONFIRMED && it.occurredAt == NOW })
    }

    @Test
    fun `fila menor que as vagas sobe inteira e o job conta so quem subiu`() {
        repository.capacity = 5
        repository.record(UUID.randomUUID(), AttendanceStatus.CONFIRMED)
        waiting.take(2).forEachIndexed { i, id -> repository.record(id, AttendanceStatus.WAITLISTED, sequence = i + 1L) }

        assertEquals(2, job.run())

        assertEquals(3, repository.confirmedCount())
    }

    @Test
    fun `com uma unica vaga sobe uma unica pessoa`() {
        repository.capacity = 3
        repeat(2) { repository.record(UUID.randomUUID(), AttendanceStatus.CONFIRMED) }
        waiting.take(2).forEachIndexed { i, id -> repository.record(id, AttendanceStatus.WAITLISTED, sequence = i + 1L) }

        assertEquals(1, job.run())

        assertEquals(AttendanceStatus.CONFIRMED, repository.status(waiting[0]))
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(waiting[1]))
    }

    @Test
    fun `no instante exato do prazo o job ja age, um segundo antes nao`() {
        repository.capacity = 3
        repository.record(waiting[0], AttendanceStatus.WAITLISTED, sequence = 1)

        repository.deadline = NOW.plusSeconds(1)
        assertEquals(0, job.run())
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(waiting[0]))

        repository.deadline = NOW
        assertEquals(1, job.run())
        assertEquals(AttendanceStatus.CONFIRMED, repository.status(waiting[0]))
    }

    @Test
    fun `jogo lotado por uma vaga nao deixa ninguem subir`() {
        repository.capacity = 2
        repeat(2) { repository.record(UUID.randomUUID(), AttendanceStatus.CONFIRMED) }
        repository.record(waiting[0], AttendanceStatus.WAITLISTED, sequence = 1)

        assertEquals(0, job.run())

        assertEquals(AttendanceStatus.WAITLISTED, repository.status(waiting[0]))
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `jogo que nao e do grupo do candidato e ignorado`() {
        repository.capacity = 3
        repository.record(waiting[0], AttendanceStatus.WAITLISTED, sequence = 1)
        candidates = listOf(candidate().copy(groupId = UUID.randomUUID()))

        assertEquals(0, job.run())

        assertEquals(AttendanceStatus.WAITLISTED, repository.status(waiting[0]))
    }

    @Test
    fun `em escolha manual avisa uma vez com o numero exato de vagas, no singular quando e uma`() {
        repository.promotionMode = PromotionMode.MANUAL
        repository.capacity = 3
        repeat(2) { repository.record(UUID.randomUUID(), AttendanceStatus.CONFIRMED) }
        repository.record(waiting[0], AttendanceStatus.WAITLISTED, sequence = 1)
        candidates = listOf(candidate(waitlisted = 1))

        assertEquals(1, job.run())

        val (_, body) = notices.single()
        assertTrue("1 na lista de espera · 1 vaga aberta\n" in body, body)
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(waiting[0]))
        assertTrue(repository.events.isEmpty())
    }

    @Test
    fun `corpo do aviso conta vagas no plural e no singular`() {
        val reminder = ReminderGame(LocalDate.of(2026, 8, 11), LocalTime.of(19, 30), "Quadra QA")

        assertTrue("3 na lista de espera · 2 vagas abertas\n" in waitlistDeadlineBody(reminder, 3, 2))
        assertTrue("3 na lista de espera · 1 vaga aberta\n" in waitlistDeadlineBody(reminder, 3, 1))
    }

    @Test
    fun `quem sobe pelo job assina como sistema em nome do dono`() {
        repository.capacity = 2
        repository.record(waiting[0], AttendanceStatus.WAITLISTED, sequence = 1)

        job.run()

        val event = repository.events.single()
        assertEquals(AttendanceSource.SYSTEM, event.source)
        assertEquals(owner, event.actorId)
        assertEquals(waiting[0], event.memberId)
    }

    private fun candidate(waitlisted: Int = 1) = DeadlineWaitlistCandidate(
        gameId = game, groupId = group, ownerId = owner,
        game = ReminderGame(LocalDate.of(2026, 8, 11), LocalTime.of(19, 30), "Quadra QA"),
        capacity = 2, confirmed = 0, waitlisted = waitlisted,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-10T10:00:00Z")
    }
}
