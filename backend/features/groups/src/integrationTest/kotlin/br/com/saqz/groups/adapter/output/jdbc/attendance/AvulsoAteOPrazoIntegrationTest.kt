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
 * DEB-005 (PRES-006, PROM-005, PRES-013): com a prioridade de mensalista ligada, o "Vou" do avulso dentro do prazo vai para a espera
 * mesmo com vaga; no prazo o job (`SettleWaitlistAtDeadline`) o confirma, com a diária do avulso e o aviso direto.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AvulsoAteOPrazoIntegrationTest {
    private lateinit var h: FilaHarness

    @BeforeEach fun reset() { h = FilaHarness(this); h.cenario(capacity = 4, priority = true) }

    private fun vou(id: UUID) = assertIs<AttendanceCommandResult.Success>(h.respostas().execute(id, h.group, h.game, intent = AttendanceIntent.CONFIRM))

    @Test fun `Vou do avulso vai para a espera mesmo com vaga e o job do prazo o confirma com diaria e aviso`() {
        val avulso = h.atleta()
        vou(avulso)
        assertEquals("WAITLISTED", h.status(avulso))
        assertEquals(0, h.diarias(avulso)); assertEquals(0, h.avisos(avulso))
        assertEquals(0, h.job().run(), "antes do prazo o job não mexe em nada")

        h.encerrarPrazo()
        assertEquals(1, h.job().run())

        assertEquals("CONFIRMED", h.status(avulso))
        assertEquals(1, h.diarias(avulso)); assertEquals("PENDING", h.statusDaDiaria(avulso))
        assertEquals(1, h.avisos(avulso))
        assertEquals(1, h.eventos(avulso, "SYSTEM", "CONFIRMED"))
        assertEquals(0, h.job().run(), "segunda rodada não repete")
        assertEquals(1, h.diarias(avulso)); assertEquals(1, h.avisos(avulso))
    }

    @Test fun `mensalista confirma na hora no mesmo jogo e nao gera diaria`() {
        val avulso = h.atleta(); val mensalista = h.atleta("MENSALISTA")
        vou(avulso); vou(mensalista)
        assertEquals("WAITLISTED", h.status(avulso))
        assertEquals("CONFIRMED", h.status(mensalista))
        assertEquals(0, h.diarias(mensalista))
        h.encerrarPrazo(); h.job().run()
        assertEquals("CONFIRMED", h.status(avulso))
        assertEquals(0, h.diarias(mensalista))
    }

    @Test fun `com uma vaga so no prazo sobe o avulso que chegou primeiro`() {
        repeat(3) { h.presenca(h.atleta(), "CONFIRMED") }
        val primeiro = h.atleta(); val segundo = h.atleta()
        vou(primeiro); vou(segundo)
        h.encerrarPrazo()
        assertEquals(1, h.job().run())
        assertEquals("CONFIRMED", h.status(primeiro)); assertEquals("WAITLISTED", h.status(segundo))
        assertEquals(1, h.diarias(primeiro)); assertEquals(0, h.diarias(segundo))
        assertEquals(1, h.avisos(primeiro)); assertEquals(0, h.avisos(segundo))
    }

    @Test fun `com a prioridade desligada o avulso confirma na hora e o job nao tem o que fazer`() {
        h.execute("UPDATE access_groups SET mensalista_priority=false WHERE id='${h.group}'")
        val avulso = h.atleta()
        vou(avulso)
        assertEquals("CONFIRMED", h.status(avulso)); assertEquals(1, h.diarias(avulso))
        h.encerrarPrazo()
        assertEquals(0, h.job().run())
    }
}
