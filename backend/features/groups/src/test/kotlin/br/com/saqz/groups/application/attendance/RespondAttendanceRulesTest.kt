package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Detalhes do registro que `RespondAttendance` grava (PRES-009, PRES-010, PRES-011, PRES-003, PROM-001, PRES-005):
 * quando respondeu e quando mudou, versão, repetição pelo requestId e quem é ocultado ou barrado.
 */
class RespondAttendanceRulesTest {
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val athlete = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val outsider = UUID.randomUUID()
    private val repository = InMemoryAttendance(group, game, NOW).apply {
        roles[owner] = GroupRole.OWNER
        roles[athlete] = GroupRole.ATHLETE
        roles[other] = GroupRole.ATHLETE
    }
    private val charges = RecordingCharges()
    private val responses = RespondAttendance(DIRECT_TRANSACTION, repository, charges, { NOW }, UUID::randomUUID)

    @Test
    fun `primeira resposta nasce na versao 1 com a hora da resposta igual a da mudanca`() {
        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))

        assertEquals(1L, result.attendance.version)
        assertEquals(NOW, result.attendance.respondedAt)
        assertEquals(NOW, result.attendance.updatedAt)
    }

    @Test
    fun `mudar de resposta mantem a hora da primeira resposta, anda a versao e a hora da mudanca`() {
        val earlier = NOW.minusSeconds(7200)
        repository.records[athlete to 0] = AttendanceRecord(game, group, athlete, AttendanceStatus.CONFIRMED, null, earlier, earlier, 4)

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.DECLINE))

        assertEquals(earlier, result.attendance.respondedAt)
        assertEquals(NOW, result.attendance.updatedAt)
        assertEquals(5L, result.attendance.version)
        assertEquals(result.attendance, repository.records.getValue(athlete to 0))
    }

    @Test
    fun `relogio atrasado em relacao a primeira resposta nao faz a atualizacao andar para tras`() {
        val future = NOW.plusSeconds(60)
        repository.records[athlete to 0] = AttendanceRecord(game, group, athlete, AttendanceStatus.CONFIRMED, null, future, future, 1)

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.DECLINE))

        assertEquals(future, result.attendance.updatedAt)
    }

    @Test
    fun `quem desiste e volta entra no fim da fila com o numero seguinte`() {
        repository.capacity = 1
        repository.record(owner, AttendanceStatus.CONFIRMED)
        repository.record(other, AttendanceStatus.WAITLISTED, sequence = 5)
        repository.record(athlete, AttendanceStatus.DECLINED, version = 2)

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))

        assertEquals(AttendanceStatus.WAITLISTED, result.attendance.status)
        assertEquals(6L, result.attendance.waitlistSequence)
        assertEquals(3L, result.attendance.version)
    }

    @Test
    fun `quem sai da espera perde o numero da fila`() {
        repository.record(athlete, AttendanceStatus.WAITLISTED, sequence = 3)

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.DECLINE))

        assertEquals(null, result.attendance.waitlistSequence)
    }

    @Test
    fun `requestId ja respondido devolve a resposta anterior sem gravar, cobrar ou promover`() {
        val requestId = UUID.randomUUID()
        val first = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM, requestId = requestId))
        repository.responseReplays[requestId] = AttendanceResponseReplay(first.attendance, first.event!!)
        repository.responseReplayLookups = 0
        repository.events.clear()
        charges.calls.clear()

        val again = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.DECLINE, requestId = requestId))

        assertEquals(first.attendance, again.attendance)
        assertEquals(first.event, again.event)
        assertEquals(1, repository.responseReplayLookups)
        assertEquals(AttendanceStatus.CONFIRMED, repository.status(athlete))
        assertTrue(repository.events.isEmpty())
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `repeticao que so aparece depois de travar a linha tambem vale`() {
        val requestId = UUID.randomUUID()
        val concurrent = AttendanceRecord(game, group, athlete, AttendanceStatus.CONFIRMED, null, NOW, NOW, 1)
        val concurrentEvent = AttendanceEvent(UUID.randomUUID(), game, group, athlete, athlete, AttendanceSource.SELF, null, AttendanceStatus.CONFIRMED, null, NOW, requestId)
        repository.whileLocking = { repository.responseReplays[requestId] = AttendanceResponseReplay(concurrent, concurrentEvent) }

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM, requestId = requestId))

        assertEquals(concurrent, result.attendance)
        assertEquals(concurrentEvent, result.event)
        assertEquals(2, repository.responseReplayLookups)
        assertTrue(repository.records.isEmpty())
        assertTrue(repository.events.isEmpty())
    }

    @Test
    fun `sem requestId ou fora da resposta do proprio atleta a repeticao nem e consultada`() {
        responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM)
        assertEquals(0, repository.responseReplayLookups)

        responses.execute(owner, group, game, memberId = athlete, intent = AttendanceIntent.DECLINE, source = AttendanceSource.ORGANIZER, reason = "Avisou que nao vem", requestId = UUID.randomUUID())
        assertEquals(0, repository.responseReplayLookups)
        assertEquals(AttendanceStatus.DECLINED, repository.status(athlete))
    }

    @Test
    fun `organizador ou sistema respondendo por alguem que nao e do grupo fica oculto, atleta barrado`() {
        assertEquals(
            AttendanceCommandResult.Hidden,
            responses.execute(outsider, group, game, memberId = athlete, intent = AttendanceIntent.CONFIRM, source = AttendanceSource.ORGANIZER, reason = "Chegou na quadra"),
        )
        assertEquals(
            AttendanceCommandResult.Forbidden,
            responses.execute(other, group, game, memberId = athlete, intent = AttendanceIntent.CONFIRM, source = AttendanceSource.ORGANIZER, reason = "Chegou na quadra"),
        )
        assertEquals(
            AttendanceCommandResult.Hidden,
            responses.execute(outsider, group, game, memberId = outsider, intent = AttendanceIntent.CONFIRM),
        )
        assertTrue(repository.records.isEmpty())
    }

    @Test
    fun `Vou no instante exato do prazo de quem espera a prioridade do mensalista nao sobe ninguem, um segundo depois sobe`() {
        repository.mensalistaPriority = true
        repository.membership[athlete] = AthleteMembershipTypeAvulso
        repository.capacity = 3
        repository.record(owner, AttendanceStatus.CONFIRMED)

        repository.deadline = NOW
        val onTime = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))
        assertEquals(AttendanceStatus.WAITLISTED, onTime.attendance.status)
        assertTrue(onTime.promoted.isEmpty())

        repository.records.remove(athlete to 0)
        repository.deadline = NOW.minusSeconds(1)
        val late = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))
        assertEquals(AttendanceStatus.CONFIRMED, late.attendance.status)
        assertEquals(listOf(athlete), late.promoted.map { it.memberId })
    }

    @Test
    fun `convidados derrubados com o anfitriao andam a versao e a hora da mudanca`() {
        repository.record(athlete, AttendanceStatus.CONFIRMED)
        repository.record(athlete, AttendanceStatus.CONFIRMED, guestSeq = 1, version = 6)

        responses.execute(athlete, group, game, intent = AttendanceIntent.DECLINE)

        val guest = repository.records.getValue(athlete to 1)
        assertEquals(AttendanceStatus.DECLINED, guest.status)
        assertEquals(7L, guest.version)
        assertEquals(NOW, guest.updatedAt)
        assertEquals(null, guest.waitlistSequence)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-10T10:00:00Z")
        val AthleteMembershipTypeAvulso = AthleteMembershipType.AVULSO
    }
}
