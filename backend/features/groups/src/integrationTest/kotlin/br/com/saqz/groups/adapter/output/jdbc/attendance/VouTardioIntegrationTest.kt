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
 * DEB-004 (PRES-005, PROM-002): "Vou" depois do prazo vai para a espera; em ordem de chegada a fila ocupa na hora as vagas livres,
 * com quem já esperava na frente. O tardio só sobe se ainda sobrar vaga (caminho `lateJoin` de `RespondAttendance`).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VouTardioIntegrationTest {
    private lateinit var h: FilaHarness

    @BeforeEach fun reset() { h = FilaHarness(this) }

    private class Cena(val antigos: List<UUID>, val tardio: UUID)

    /** Prazo vencido, jogo de 4 vagas com [confirmados] confirmados e [antigos] pessoas que já esperavam, na ordem de chegada. */
    private fun cena(confirmados: Int, antigos: Int, vinculoDoTardio: String = "AVULSO", priority: Boolean = false): Cena {
        h.cenario(capacity = 4, priority = priority, deadline = "now() - interval '1 minute'")
        repeat(confirmados) { h.presenca(h.atleta(), "CONFIRMED") }
        val fila = (1..antigos).map { h.atleta() }
        fila.forEachIndexed { i, id -> h.presenca(id, "WAITLISTED", i + 1L) }
        return Cena(fila, h.atleta(vinculoDoTardio))
    }

    private fun vou(id: UUID) = assertIs<AttendanceCommandResult.Success>(h.respostas().execute(id, h.group, h.game, intent = AttendanceIntent.CONFIRM))

    @Test fun `duas vagas livres e fila antiga de duas pessoas - a fila sobe na hora e o tardio espera`() {
        val c = cena(confirmados = 2, antigos = 2)
        vou(c.tardio)
        c.antigos.forEach { assertEquals("CONFIRMED", h.status(it)) }
        assertEquals("WAITLISTED", h.status(c.tardio))
        c.antigos.forEach { assertEquals(1, h.avisos(it)); assertEquals(1, h.diarias(it)) }
        assertEquals(0, h.avisos(c.tardio)); assertEquals(0, h.diarias(c.tardio))
    }

    @Test fun `uma vaga livre e fila antiga de duas pessoas - sobe so o mais antigo e o tardio fica no fim`() {
        val c = cena(confirmados = 3, antigos = 2)
        vou(c.tardio)
        assertEquals("CONFIRMED", h.status(c.antigos[0]))
        assertEquals("WAITLISTED", h.status(c.antigos[1]))
        assertEquals("WAITLISTED", h.status(c.tardio))
        val seqs = c.antigos.map { h.int("SELECT waitlist_sequence FROM game_attendance WHERE game_id='${h.game}' AND member_user_id='$it'") }
        assertEquals(true, seqs[1] < h.int("SELECT waitlist_sequence FROM game_attendance WHERE game_id='${h.game}' AND member_user_id='${c.tardio}'"))
    }

    @Test fun `vagas sobrando depois da fila antiga - o tardio sobe por ultimo e ganha a diaria`() {
        val c = cena(confirmados = 1, antigos = 2)
        vou(c.tardio)
        (c.antigos + c.tardio).forEach { assertEquals("CONFIRMED", h.status(it)) }
        assertEquals(1, h.diarias(c.tardio))
        // Quem chegou na hora e subiu na mesma chamada não recebe aviso (PRES-013); a fila antiga recebe.
        assertEquals(0, h.avisos(c.tardio))
        c.antigos.forEach { assertEquals(1, h.avisos(it)) }
    }

    @Test fun `mensalista tardio com prioridade ligada nao passa na frente de avulso que ja esperava`() {
        val c = cena(confirmados = 3, antigos = 1, vinculoDoTardio = "MENSALISTA", priority = true)
        vou(c.tardio)
        assertEquals("CONFIRMED", h.status(c.antigos[0]))
        assertEquals("WAITLISTED", h.status(c.tardio))
    }

    @Test fun `na escolha manual o tardio fica na espera e a fila antiga nao anda sozinha`() {
        val c = cena(confirmados = 2, antigos = 2)
        h.execute("UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='${h.group}'")
        vou(c.tardio)
        (c.antigos + c.tardio).forEach { assertEquals("WAITLISTED", h.status(it)) }
    }
}
