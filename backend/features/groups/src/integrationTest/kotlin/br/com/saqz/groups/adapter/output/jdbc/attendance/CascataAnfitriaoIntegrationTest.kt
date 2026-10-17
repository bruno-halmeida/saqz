package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.application.attendance.AttendanceCommandResult
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * DEB-003 (PRES-004, PROM-002, PRES-013): anfitrião confirmado que desiste leva os convidados junto, ANTES de a fila andar;
 * a fila sobe na ordem de chegada, só quem subiu é avisado e as cobranças seguem a regra.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CascataAnfitriaoIntegrationTest {
    private lateinit var h: FilaHarness

    @BeforeEach fun reset() { h = FilaHarness(this) }

    private class Cena(val anfitriao: UUID, val outro: UUID, val fila: List<UUID>)

    /** Jogo de 4 vagas lotado: anfitrião, [convidadosConfirmados] convidados dele e outros até lotar; a fila vem na ordem de [fila]. */
    private fun cena(convidadosConfirmados: Int, fila: Int, convidadoNaEspera: Boolean = false): Cena {
        h.cenario(capacity = 4)
        val anfitriao = h.atleta()
        assertIs<AttendanceCommandResult.Success>(h.respostas().execute(anfitriao, h.group, h.game, intent = AttendanceIntent.CONFIRM))
        repeat(convidadosConfirmados) { h.convidadoConfirmado(anfitriao, "Convidado ${it + 1}") }
        val outro = h.atleta(); h.presenca(outro, "CONFIRMED")
        repeat(3 - convidadosConfirmados - 1) { h.presenca(h.atleta(), "CONFIRMED") }
        if (convidadoNaEspera) {
            h.convidados().add(anfitriao, h.group, h.game, "Convidado da espera", UUID.randomUUID())
            assertEquals("WAITLISTED", h.status(anfitriao, guest = convidadosConfirmados + 1))
        }
        val esperando = (1..fila).map { h.atleta() }
        esperando.forEach { assertIs<AttendanceCommandResult.Success>(h.respostas().execute(it, h.group, h.game, intent = AttendanceIntent.CONFIRM)) }
        esperando.forEach { assertEquals("WAITLISTED", h.status(it)) }
        return Cena(anfitriao, outro, esperando)
    }

    @Test fun `anfitriao que desiste leva os convidados e a fila sobe nas vagas dos tres, na ordem de chegada`() {
        val c = cena(convidadosConfirmados = 2, fila = 3, convidadoNaEspera = true)
        assertIs<AttendanceCommandResult.Success>(h.respostas().execute(c.anfitriao, h.group, h.game, intent = AttendanceIntent.DECLINE))

        assertEquals("DECLINED", h.status(c.anfitriao))
        listOf(1, 2, 3).forEach { assertEquals("DECLINED", h.status(c.anfitriao, guest = it), "convidado $it") }
        c.fila.forEach { assertEquals("CONFIRMED", h.status(it)) }
        assertEquals("CONFIRMED", h.status(c.outro))
        assertEquals(4, h.int("SELECT count(*) FROM game_attendance WHERE game_id='${h.game}' AND status='CONFIRMED'"))
        // O convidado que esperava na frente da fila caiu junto e nunca foi promovido.
        assertEquals(0, h.eventos(c.anfitriao, "SYSTEM", "CONFIRMED"))
        c.fila.forEach { assertEquals(1, h.eventos(it, "SYSTEM", "CONFIRMED")) }
    }

    @Test fun `com so duas vagas liberadas sobem os dois primeiros da fila e o terceiro continua esperando`() {
        val c = cena(convidadosConfirmados = 1, fila = 3)
        val (a, b, d) = c.fila

        h.respostas().execute(c.anfitriao, h.group, h.game, intent = AttendanceIntent.DECLINE)

        assertEquals("CONFIRMED", h.status(a)); assertEquals("CONFIRMED", h.status(b))
        assertEquals("WAITLISTED", h.status(d))
    }

    @Test fun `so quem subiu recebe aviso e anfitriao, convidados e quem ficou na espera nao`() {
        val c = cena(convidadosConfirmados = 1, fila = 3)
        val (a, b, d) = c.fila
        // Colocar o convidado no jogo (setup) já avisou o anfitrião uma vez; a cascata não pode somar nenhum.
        val avisosDoAnfitriao = h.avisos(c.anfitriao)

        h.respostas().execute(c.anfitriao, h.group, h.game, intent = AttendanceIntent.DECLINE)

        assertEquals(1, h.avisos(a)); assertEquals(1, h.avisos(b))
        assertEquals(0, h.avisos(d))
        assertEquals(avisosDoAnfitriao, h.avisos(c.anfitriao))
        assertEquals(0, h.avisos(c.outro))
    }

    @Test fun `cobrancas - diaria do anfitriao continua pendente, a do convidado e cancelada e quem subiu ganha a sua`() {
        val c = cena(convidadosConfirmados = 2, fila = 3)
        assertEquals("PENDING", h.statusDaDiaria(c.anfitriao)); assertEquals("PENDING", h.statusDaDiaria(c.anfitriao, guest = 1))

        h.respostas().execute(c.anfitriao, h.group, h.game, intent = AttendanceIntent.DECLINE)

        assertEquals("PENDING", h.statusDaDiaria(c.anfitriao))
        assertEquals("CANCELLED", h.statusDaDiaria(c.anfitriao, guest = 1))
        assertEquals("CANCELLED", h.statusDaDiaria(c.anfitriao, guest = 2))
        c.fila.forEach { assertEquals(1, h.diarias(it)) }
    }
}
