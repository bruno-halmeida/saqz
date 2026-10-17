package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcAttendanceChangeNotice
import br.com.saqz.groups.application.attendance.AttendanceCommandResult
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * DEB-045 (JOGO-002): jogo com prazo no início (o banco não aceita prazo depois do início, ck_games_deadline) não tem janela de prioridade de mensalista, mesmo com a
 * prioridade ligada no grupo: entra quem chegou primeiro, e a posição na fila segue a mesma ordem.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrazoNoInicioIntegrationTest {
    private lateinit var h: FilaHarness

    @BeforeEach fun reset() { h = FilaHarness(this) }

    private fun vou(id: UUID) = assertIs<AttendanceCommandResult.Success>(h.respostas().execute(id, h.group, h.game, intent = AttendanceIntent.CONFIRM))
    private fun repo() = JdbcAttendanceCommandRepository(h.dataSource, JdbcAttendanceChangeNotice(h.dataSource))
    private fun prazoNoInicio() = h.execute("UPDATE games SET confirmation_deadline = starts_at WHERE id='${h.game}'")
    private fun posicao(id: UUID) = repo().find(id, h.group, h.game)!!.own!!.waitlistPosition

    @Test fun `prazo no inicio - avulso confirma na hora por ordem de chegada, com diaria`() {
        h.cenario(capacity = 4, priority = true); prazoNoInicio()
        val avulso = h.atleta()
        vou(avulso)
        assertEquals("CONFIRMED", h.status(avulso)); assertEquals(1, h.diarias(avulso))
    }

    @Test fun `prazo no inicio - avulso e mensalista entram por chegada ate acabar a vaga`() {
        h.cenario(capacity = 4, priority = true); prazoNoInicio()
        repeat(2) { h.presenca(h.atleta(), "CONFIRMED") }
        val primeiro = h.atleta(); val mensalista = h.atleta("MENSALISTA"); val ultimo = h.atleta()
        vou(primeiro); vou(mensalista); vou(ultimo)
        assertEquals("CONFIRMED", h.status(primeiro)); assertEquals("CONFIRMED", h.status(mensalista))
        assertEquals("WAITLISTED", h.status(ultimo))
        assertEquals(0, h.diarias(mensalista))
    }

    @Test fun `prazo no inicio - vaga liberada sobe quem chegou primeiro, nao o mensalista, e a posicao concorda`() {
        h.cenario(capacity = 4, priority = true); prazoNoInicio()
        repeat(4) { h.presenca(h.atleta(), "CONFIRMED") }
        val avulso = h.atleta(); val mensalista = h.atleta("MENSALISTA")
        h.presenca(avulso, "WAITLISTED", 1); h.presenca(mensalista, "WAITLISTED", 2)

        assertEquals(1L, posicao(avulso)); assertEquals(2L, posicao(mensalista))
        assertEquals(avulso, repo().earliestWaitlisted(h.group, h.game, Instant.now())!!.memberId)
    }

    @Test fun `prazo antes do inicio - mensalista continua na frente da fila como antes`() {
        h.cenario(capacity = 4, priority = true)
        repeat(4) { h.presenca(h.atleta(), "CONFIRMED") }
        val avulso = h.atleta(); val mensalista = h.atleta("MENSALISTA")
        h.presenca(avulso, "WAITLISTED", 1); h.presenca(mensalista, "WAITLISTED", 2)

        assertEquals(2L, posicao(avulso)); assertEquals(1L, posicao(mensalista))
        assertEquals(mensalista, repo().earliestWaitlisted(h.group, h.game, Instant.now())!!.memberId)
    }
}
