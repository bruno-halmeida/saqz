package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceDenial
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** PRES-008: o convidado é uma linha do anfitrião, entra pela espera e sai por ele ou pelo organizador. */
class GameGuestsRulesTest {
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val host = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val repository = InMemoryAttendance(group, game, NOW).apply {
        roles[owner] = GroupRole.OWNER
        roles[host] = GroupRole.ATHLETE
        roles[other] = GroupRole.ATHLETE
    }
    private val charges = RecordingCharges()
    private val responses = RespondAttendance(DIRECT_TRANSACTION, repository, charges, { NOW }, UUID::randomUUID)
    private val guests = GameGuests(DIRECT_TRANSACTION, repository, responses) { NOW }

    @Test
    fun `convidado entra na espera, com o nome aparado e o numero seguinte do anfitriao`() {
        repository.record(host, AttendanceStatus.CONFIRMED)
        repository.record(host, AttendanceStatus.DECLINED, guestSeq = 1)

        val result = assertIs<AttendanceCommandResult.Success>(guests.add(host, group, game, "  Rafa Moreira  ", UUID.randomUUID()))

        assertEquals(AttendanceStatus.WAITLISTED, result.attendance.status)
        assertEquals(2, result.attendance.guestSeq)
        assertEquals(host, result.attendance.memberId)
        assertEquals("Rafa Moreira", repository.guestNames[host to 2])
        assertEquals(2, result.event?.guestSeq)
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `nome do convidado vai de 2 a 80 caracteres e nao leva controle nem palavrao`() {
        repository.record(host, AttendanceStatus.CONFIRMED)

        listOf("Jo", "x".repeat(80)).forEach {
            assertIs<AttendanceCommandResult.Success>(guests.add(host, group, game, it, UUID.randomUUID()), "nome de ${it.length}")
        }
        listOf(null, "", "   ", "J", " J ", "x".repeat(81), "Rafa\u0007Moreira", "Fulano fdp").forEach {
            assertEquals(
                AttendanceCommandResult.Denied(AttendanceDenial.REASON_INVALID),
                guests.add(host, group, game, it, UUID.randomUUID()),
                "nome $it",
            )
        }
        assertEquals(2, repository.records.count { it.key.second > 0 })
    }

    @Test
    fun `anfitriao precisa estar confirmado ou na espera para levar convidado`() {
        assertEquals(AttendanceCommandResult.Denied(AttendanceDenial.HOST_NOT_GOING), guests.add(host, group, game, "Rafa Moreira", UUID.randomUUID()))
        repository.record(host, AttendanceStatus.DECLINED)
        assertEquals(AttendanceCommandResult.Denied(AttendanceDenial.HOST_NOT_GOING), guests.add(host, group, game, "Rafa Moreira", UUID.randomUUID()))
        repository.record(host, AttendanceStatus.WAITLISTED, sequence = 1)
        assertIs<AttendanceCommandResult.Success>(guests.add(host, group, game, "Rafa Moreira", UUID.randomUUID()))
    }

    @Test
    fun `quem nao e do grupo nao leva convidado e jogo desconhecido fica oculto`() {
        assertEquals(AttendanceCommandResult.Hidden, guests.add(UUID.randomUUID(), group, game, "Rafa Moreira", UUID.randomUUID()))
        assertEquals(AttendanceCommandResult.Hidden, guests.add(host, group, UUID.randomUUID(), "Rafa Moreira", UUID.randomUUID()))
        assertTrue(repository.records.isEmpty())
    }

    @Test
    fun `mesmo requestId devolve a resposta anterior sem criar outro convidado`() {
        repository.record(host, AttendanceStatus.CONFIRMED)
        val requestId = UUID.randomUUID()
        val first = assertIs<AttendanceCommandResult.Success>(guests.add(host, group, game, "Rafa Moreira", requestId))
        repository.responseReplays[requestId] = AttendanceResponseReplay(first.attendance, first.event!!)

        val again = assertIs<AttendanceCommandResult.Success>(guests.add(host, group, game, "Rafa Moreira", requestId))

        assertEquals(first.attendance, again.attendance)
        assertEquals(first.event, again.event)
        assertEquals(1, repository.records.count { it.key.second > 0 })
        assertEquals(1, repository.events.size)
    }

    @Test
    fun `anfitriao tira o proprio convidado e o organizador tira qualquer um com o motivo padrao`() {
        repository.record(host, AttendanceStatus.CONFIRMED)
        repository.record(host, AttendanceStatus.WAITLISTED, sequence = 1, guestSeq = 1)
        repository.record(host, AttendanceStatus.WAITLISTED, sequence = 2, guestSeq = 2)

        val byHost = assertIs<AttendanceCommandResult.Success>(guests.remove(host, group, game, host, 1))
        val byOwner = assertIs<AttendanceCommandResult.Success>(guests.remove(owner, group, game, host, 2))

        assertEquals(AttendanceStatus.DECLINED, byHost.attendance.status)
        assertEquals(AttendanceStatus.DECLINED, byOwner.attendance.status)
        assertEquals(listOf(AttendanceSource.SELF, AttendanceSource.ORGANIZER), repository.events.map { it.source })
        assertEquals(listOf(null, "Convidado removido pelo gestor"), repository.events.map { it.reason })
        assertEquals(listOf(host, owner), repository.events.map { it.actorId })
    }

    @Test
    fun `outro atleta, quem nao e do grupo e numero invalido nao tiram convidado`() {
        repository.record(host, AttendanceStatus.CONFIRMED)
        repository.record(host, AttendanceStatus.WAITLISTED, sequence = 1, guestSeq = 1)

        assertEquals(AttendanceCommandResult.Hidden, guests.remove(other, group, game, host, 1))
        assertEquals(AttendanceCommandResult.Hidden, guests.remove(UUID.randomUUID(), group, game, host, 1))
        assertEquals(AttendanceCommandResult.Hidden, guests.remove(host, group, game, host, 0))
        assertEquals(AttendanceCommandResult.Hidden, guests.remove(host, group, game, host, -1))
        assertEquals(AttendanceCommandResult.Hidden, guests.remove(host, group, game, host, 7))
        assertEquals(AttendanceCommandResult.Hidden, guests.remove(host, group, UUID.randomUUID(), host, 1))
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(host, 1))
        assertTrue(repository.events.isEmpty())
    }

    @Test
    fun `tirar convidado que ja saiu devolve a linha como esta e nao grava evento`() {
        repository.record(host, AttendanceStatus.CONFIRMED)
        repository.record(host, AttendanceStatus.DECLINED, guestSeq = 1, version = 3)

        val result = assertIs<AttendanceCommandResult.Success>(guests.remove(host, group, game, host, 1))

        assertEquals(AttendanceStatus.DECLINED, result.attendance.status)
        assertEquals(3L, result.attendance.version)
        assertTrue(repository.events.isEmpty())
        assertTrue(charges.calls.isEmpty())
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-10T10:00:00Z")
    }
}
