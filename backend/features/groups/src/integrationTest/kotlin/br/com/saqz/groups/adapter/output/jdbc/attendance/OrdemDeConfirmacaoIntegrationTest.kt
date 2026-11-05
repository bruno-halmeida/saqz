package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGroupCommunicationRepository
import br.com.saqz.groups.adapter.output.jdbc.home.JdbcHomeRepository
import br.com.saqz.groups.application.attendance.AttendanceCommandResult
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Pedido do dono em 05/11/2026: os confirmados saem na ordem de confirmação, não pelo nome, na tela do jogo, na Início e na
 * lista do grupo do WhatsApp. Quem desiste e volta vai para o fim; quem sobe da espera entra quando sobe.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrdemDeConfirmacaoIntegrationTest {
    private lateinit var h: FilaHarness

    @BeforeEach fun reset() { h = FilaHarness(this) }

    @Test fun `confirmados saem na ordem de confirmacao no elenco, na Inicio e na lista do WhatsApp`() {
        h.cenario(capacity = 10)
        listOf("Zé", "Mara", "Ana").map(::atleta).forEach(::vou)

        assertEquals(listOf("Zé", "Mara", "Ana"), listas())
    }

    @Test fun `quem desiste e volta entra no fim`() {
        h.cenario(capacity = 10)
        val (ze, mara, ana) = listOf("Zé", "Mara", "Ana").map(::atleta)
        listOf(ze, mara, ana).forEach(::vou)
        naoVou(mara)
        vou(mara)

        assertEquals(listOf("Zé", "Ana", "Mara"), listas())
    }

    @Test fun `quem sobe da espera entra depois de quem ja estava`() {
        h.cenario(capacity = 2)
        val (ze, mara, bia) = listOf("Zé", "Mara", "Bia").map(::atleta)
        listOf(ze, mara, bia).forEach(::vou)
        assertEquals("WAITLISTED", h.status(bia))
        naoVou(ze)

        assertEquals("CONFIRMED", h.status(bia))
        assertEquals(listOf("Mara", "Bia"), listas())
    }

    @Test fun `avulso que esperou a prioridade dos mensalistas entra quando o prazo o confirma`() {
        h.cenario(capacity = 4, priority = true)
        val ana = atleta("Ana")
        val ze = atleta("Zé", "MENSALISTA")
        vou(ana)
        vou(ze)
        assertEquals("WAITLISTED", h.status(ana))
        h.encerrarPrazo()
        assertEquals(1, h.job().run())

        assertEquals(listOf("Zé", "Ana"), listas())
    }

    /** A mesma lista nos três lugares: elenco do jogo (app), prévia da Início e lista do lembrete (WhatsApp). */
    private fun listas(): List<String> {
        val elenco = h.repo().roster(h.dono, h.group, h.game)!!.confirmed.map { it.displayName }
        val inicio = JdbcHomeRepository(h.dataSource).find(h.dono, Instant.now(), LocalDate.now())
            .member.nextGame?.rosterPreview?.confirmed?.map { it.displayName }
        val whatsapp = JdbcGroupCommunicationRepository(h.dataSource).reminderRoster(h.group, h.game).confirmed
        assertEquals(elenco, inicio, "Início")
        assertEquals(elenco, whatsapp, "WhatsApp")
        return elenco
    }

    private fun atleta(nome: String, vinculo: String = "AVULSO"): UUID =
        h.atleta(vinculo).also { h.execute("UPDATE access_users SET display_name='$nome' WHERE id='$it'") }

    private fun vou(id: UUID) =
        assertIs<AttendanceCommandResult.Success>(h.respostas().execute(id, h.group, h.game, intent = AttendanceIntent.CONFIRM))

    private fun naoVou(id: UUID) =
        assertIs<AttendanceCommandResult.Success>(h.respostas().execute(id, h.group, h.game, intent = AttendanceIntent.DECLINE))
}
