package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.application.attendance.AttendanceCommandResult
import br.com.saqz.groups.application.attendance.CapacityCommandResult
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceDenial
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DEB-102 (regras 1 a 12): vaga por posição. Jogo de 5 vagas: PONTA 2, CENTRAL 2, LEVANTADOR 1.
 * O pedido sem posição (apps 1.0.6 a 1.0.9) cai na regra de compatibilidade; sem posições configuradas nada muda.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VagaPorPosicaoIntegrationTest {
    private lateinit var h: FilaHarness

    @BeforeEach fun reset() { h = FilaHarness(this) }

    private val posicoes = mapOf("PONTA" to 2, "CENTRAL" to 2, "LEVANTADOR" to 1)

    private fun jogoComPosicoes(priority: Boolean = false, deadline: String = "now() + interval '1 day'") =
        h.cenario(capacity = 99, priority = priority, deadline = deadline, posicoes = posicoes)

    private fun vou(id: UUID, posicao: AthletePosition? = null) = assertIs<AttendanceCommandResult.Success>(
        h.respostas().execute(id, h.group, h.game, intent = AttendanceIntent.CONFIRM, position = posicao),
    )

    private fun naoVou(id: UUID) = assertIs<AttendanceCommandResult.Success>(
        h.respostas().execute(id, h.group, h.game, intent = AttendanceIntent.DECLINE),
    )

    private fun confirmados(posicao: String) = h.int("SELECT count(*) FROM game_attendance WHERE game_id='${h.game}' AND position='$posicao' AND status='CONFIRMED'")

    @Test fun `regra 8 - o jogo nasce com a soma das posicoes e copia as vagas do grupo`() {
        jogoComPosicoes()
        assertEquals(5, h.int("SELECT capacity FROM games WHERE id='${h.game}'"))
        assertEquals(3, h.int("SELECT count(*) FROM game_position_slots WHERE game_id='${h.game}'"))
        assertEquals(2, h.int("SELECT slots FROM game_position_slots WHERE game_id='${h.game}' AND position='PONTA'"))
    }

    @Test fun `regra 3 - posicao cheia vai para a espera mesmo com vaga no jogo`() {
        jogoComPosicoes()
        val a = h.atleta(); val b = h.atleta(); val c = h.atleta()
        vou(a, AthletePosition.PONTA); vou(b, AthletePosition.PONTA)
        val terceiro = vou(c, AthletePosition.PONTA)
        assertEquals("WAITLISTED", h.status(c))
        assertEquals(1L, terceiro.attendance.waitlistPosition ?: terceiro.attendance.waitlistSequence)
        assertEquals(2, h.int("SELECT count(*) FROM game_attendance WHERE game_id='${h.game}' AND status='CONFIRMED'"))
        // Nas outras posições ainda há vaga.
        val d = h.atleta(); vou(d, AthletePosition.CENTRAL)
        assertEquals("CONFIRMED", h.status(d))
    }

    @Test fun `regras 4 e 11 - a vaga que abre sobe so quem espera na mesma posicao`() {
        jogoComPosicoes()
        val central = List(2) { h.atleta() }; central.forEach { vou(it, AthletePosition.CENTRAL) }
        val esperaCentral = h.atleta(); vou(esperaCentral, AthletePosition.CENTRAL) // chegou primeiro na espera
        val ponta = List(2) { h.atleta() }; ponta.forEach { vou(it, AthletePosition.PONTA) }
        val esperaPonta = h.atleta(); vou(esperaPonta, AthletePosition.PONTA)
        naoVou(ponta[0])
        assertEquals("CONFIRMED", h.status(esperaPonta))
        assertEquals("WAITLISTED", h.status(esperaCentral))
        naoVou(central[0])
        assertEquals("CONFIRMED", h.status(esperaCentral))
    }

    @Test fun `regra 10 - mensalista tem prioridade dentro da posicao e nao entre posicoes`() {
        jogoComPosicoes(priority = true)
        val ponta = List(2) { h.atleta() }
        ponta.forEach { h.presenca(it, "CONFIRMED", posicao = "PONTA") }
        val avulso = h.atleta("AVULSO"); h.presenca(avulso, "WAITLISTED", 1, "PONTA")
        val mensalista = h.atleta("MENSALISTA"); h.presenca(mensalista, "WAITLISTED", 2, "PONTA")
        val mensalistaCentral = h.atleta("MENSALISTA"); h.presenca(mensalistaCentral, "WAITLISTED", 3, "CENTRAL")
        h.execute("UPDATE game_attendance SET waitlist_sequence=waitlist_sequence WHERE false")
        naoVou(ponta[0])
        assertEquals("CONFIRMED", h.status(mensalista))
        assertEquals("WAITLISTED", h.status(avulso))
        // Vaga na CENTRAL estava livre antes, mas o mensalista esperava lá por escolha dele: segue na espera até o prazo/ação.
        assertEquals("WAITLISTED", h.status(mensalistaCentral))
    }

    @Test fun `regra 5 - organizador forca a entrada e a posicao fica acima das vagas ate voltar ao limite`() {
        jogoComPosicoes()
        val ponta = List(2) { h.atleta() }; ponta.forEach { vou(it, AthletePosition.PONTA) }
        val espera = h.atleta(); vou(espera, AthletePosition.PONTA)
        val forcado = h.atleta()
        // Sem force o override segue como hoje: posição cheia = espera.
        val semForce = assertIs<AttendanceCommandResult.Success>(h.respostas().execute(h.dono, h.group, h.game, forcado, AttendanceIntent.CONFIRM, AttendanceSource.ORGANIZER, "Chegou agora", position = AthletePosition.PONTA))
        assertEquals("WAITLISTED", semForce.attendance.status.name)
        val comForce = assertIs<AttendanceCommandResult.Success>(h.respostas().execute(h.dono, h.group, h.game, forcado, AttendanceIntent.CONFIRM, AttendanceSource.ORGANIZER, "Chegou agora", position = AthletePosition.PONTA, force = true))
        assertEquals("CONFIRMED", comForce.attendance.status.name)
        assertTrue(comForce.attendance.forced)
        assertEquals(3, confirmados("PONTA"))
        naoVou(ponta[0]) // 3 -> 2 confirmados: ainda no limite, ninguém sobe
        assertEquals("WAITLISTED", h.status(espera))
        naoVou(ponta[1]) // 2 -> 1: abriu vaga de verdade
        assertEquals("CONFIRMED", h.status(espera))
    }

    @Test fun `regra 5 - forcar numa posicao com vaga livre nao marca forcado`() {
        jogoComPosicoes()
        val a = h.atleta()
        val r = assertIs<AttendanceCommandResult.Success>(h.respostas().execute(h.dono, h.group, h.game, a, AttendanceIntent.CONFIRM, AttendanceSource.ORGANIZER, "Chegou agora", position = AthletePosition.LEVANTADOR, force = true))
        assertEquals("CONFIRMED", r.attendance.status.name)
        assertEquals(false, r.attendance.forced)
    }

    @Test fun `compat - pedido sem posicao usa a principal do perfil, depois a secundaria, depois a mais livre`() {
        jogoComPosicoes()
        val principal = h.atleta(principal = "CENTRAL"); vou(principal)
        assertEquals("CENTRAL", h.posicaoDe(principal))
        // Principal sem vaga configurada (LIBERO) cai na secundária.
        val secundaria = h.atleta(principal = "LIBERO", secundaria = "LEVANTADOR"); vou(secundaria)
        assertEquals("LEVANTADOR", h.posicaoDe(secundaria))
        // Sem perfil útil: mais vagas livres; PONTA (2 livres) empata com CENTRAL (1 livre agora) e vence.
        val semPerfil = h.atleta(); vou(semPerfil)
        assertEquals("PONTA", h.posicaoDe(semPerfil))
        val outro = h.atleta(); vou(outro)
        // PONTA 1 livre e CENTRAL 1 livre: empate pela ordem do enum (PONTA antes de CENTRAL).
        assertEquals("PONTA", h.posicaoDe(outro))
        val maisUm = h.atleta(); vou(maisUm)
        assertEquals("CENTRAL", h.posicaoDe(maisUm))
        // Principal cheia (LEVANTADOR já ocupado pela secundária acima): o app antigo cai na espera da principal.
        val esperando = h.atleta(principal = "LEVANTADOR"); vou(esperando)
        assertEquals("WAITLISTED", h.status(esperando))
        assertEquals("LEVANTADOR", h.posicaoDe(esperando))
    }

    @Test fun `posicao sem vaga configurada no jogo e negada`() {
        jogoComPosicoes()
        val a = h.atleta()
        val r = h.respostas().execute(a, h.group, h.game, intent = AttendanceIntent.CONFIRM, position = AthletePosition.LIBERO)
        assertEquals(AttendanceCommandResult.Denied(AttendanceDenial.INVALID_POSITION), r)
    }

    @Test fun `compat - grupo sem posicoes ignora a posicao enviada e funciona como sempre`() {
        h.cenario(capacity = 2)
        val a = h.atleta(principal = "PONTA"); val b = h.atleta(); val c = h.atleta()
        vou(a, AthletePosition.CENTRAL); vou(b); vou(c)
        assertNull(h.posicaoDe(a)); assertNull(h.posicaoDe(b))
        assertEquals("CONFIRMED", h.status(a)); assertEquals("CONFIRMED", h.status(b)); assertEquals("WAITLISTED", h.status(c))
        naoVou(a)
        assertEquals("CONFIRMED", h.status(c))
        assertNull(h.repo().find(a, h.group, h.game)?.positions?.takeIf { it.isNotEmpty() })
    }

    @Test fun `regra 9 - quem convida escolhe a posicao do convidado e sem escolha vai para a mais livre`() {
        jogoComPosicoes()
        val anfitriao = h.atleta(); vou(anfitriao, AthletePosition.PONTA)
        h.convidados().add(anfitriao, h.group, h.game, "Convidado Um", UUID.randomUUID(), AthletePosition.LEVANTADOR)
        assertEquals("LEVANTADOR", h.posicaoDe(anfitriao, 1))
        h.convidados().add(anfitriao, h.group, h.game, "Convidado Dois", UUID.randomUUID()) // app antigo
        // PONTA 1 livre, CENTRAL 2 livres, LEVANTADOR 1 livre -> CENTRAL.
        assertEquals("CENTRAL", h.posicaoDe(anfitriao, 2))
        val negado = h.convidados().add(anfitriao, h.group, h.game, "Convidado Tres", UUID.randomUUID(), AthletePosition.LIBERO)
        assertEquals(AttendanceCommandResult.Denied(AttendanceDenial.INVALID_POSITION), negado)
    }

    @Test fun `anfitriao que sai libera as vagas dos convidados nas posicoes deles`() {
        jogoComPosicoes()
        val anfitriao = h.atleta(); vou(anfitriao, AthletePosition.PONTA)
        val seq = h.convidadoConfirmadoEm(anfitriao, "Convidado", AthletePosition.LEVANTADOR)
        val esperaLev = h.atleta(); vou(esperaLev, AthletePosition.LEVANTADOR)
        val central = List(2) { h.atleta() }; central.forEach { vou(it, AthletePosition.CENTRAL) }
        val esperaCentral = h.atleta(); vou(esperaCentral, AthletePosition.CENTRAL)
        assertEquals("CONFIRMED", h.status(anfitriao, seq))
        naoVou(anfitriao)
        assertEquals("DECLINED", h.status(anfitriao, seq))
        assertEquals("CONFIRMED", h.status(esperaLev))
        assertEquals("WAITLISTED", h.status(esperaCentral)) // a vaga aberta era de PONTA e LEVANTADOR, não da CENTRAL
    }

    @Test fun `saida do grupo libera a vaga para a mesma posicao`() {
        jogoComPosicoes()
        val ponta = List(2) { h.atleta() }; ponta.forEach { vou(it, AthletePosition.PONTA) }
        val espera = h.atleta(); vou(espera, AthletePosition.PONTA)
        val central = List(2) { h.atleta() }; central.forEach { vou(it, AthletePosition.CENTRAL) }
        val esperaCentral = h.atleta(); vou(esperaCentral, AthletePosition.CENTRAL)
        br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner(h.dataSource).inTransaction { h.liberar().release(h.group, ponta[0], h.dono) }
        assertEquals("CONFIRMED", h.status(espera))
        assertEquals("WAITLISTED", h.status(esperaCentral))
    }

    @Test fun `prazo vencido - o job promove cada posicao com a propria fila e nao cruza posicoes`() {
        jogoComPosicoes(deadline = "now() + interval '1 hour'")
        val ponta = List(2) { h.atleta() }; ponta.forEach { h.presenca(it, "CONFIRMED", posicao = "PONTA") }
        val central = List(2) { h.atleta() }; central.forEach { h.presenca(it, "CONFIRMED", posicao = "CENTRAL") }
        val levantador = h.atleta(); h.presenca(levantador, "CONFIRMED", posicao = "LEVANTADOR")
        val esperaPonta = h.atleta(); h.presenca(esperaPonta, "WAITLISTED", 1, "PONTA")
        val esperaCentral = h.atleta(); h.presenca(esperaCentral, "WAITLISTED", 2, "CENTRAL")
        h.execute("UPDATE game_attendance SET status='DECLINED', waitlist_sequence=NULL WHERE member_user_id='${central[0]}'")
        h.execute("UPDATE games SET confirmation_deadline = now() - interval '1 minute' WHERE id='${h.game}'")
        assertEquals(1, h.job().run())
        assertEquals("CONFIRMED", h.status(esperaCentral))
        assertEquals("WAITLISTED", h.status(esperaPonta)) // PONTA está cheia
    }

    @Test fun `concorrencia - duas pessoas na ultima vaga da posicao, so uma confirma`() {
        jogoComPosicoes()
        vou(h.atleta(), AthletePosition.PONTA)
        val a = h.atleta(); val b = h.atleta()
        val pool = Executors.newFixedThreadPool(2)
        val largada = CountDownLatch(1)
        val tarefas = listOf(a, b).map { id ->
            pool.submit<AttendanceCommandResult> { largada.await(); h.respostas().execute(id, h.group, h.game, intent = AttendanceIntent.CONFIRM, position = AthletePosition.PONTA) }
        }
        largada.countDown()
        tarefas.forEach { assertIs<AttendanceCommandResult.Success>(it.get()) }
        pool.shutdown()
        assertEquals(2, confirmados("PONTA"))
        assertEquals(1, h.int("SELECT count(*) FROM game_attendance WHERE game_id='${h.game}' AND position='PONTA' AND status='WAITLISTED'"))
    }

    @Test fun `leituras - resumo por posicao, vez na fila da propria posicao e total do jogo intacto`() {
        jogoComPosicoes()
        val ponta = List(2) { h.atleta() }; ponta.forEach { vou(it, AthletePosition.PONTA) }
        val p1 = h.atleta(); vou(p1, AthletePosition.PONTA)
        val central = List(2) { h.atleta() }; central.forEach { vou(it, AthletePosition.CENTRAL) }
        val c1 = h.atleta(); vou(c1, AthletePosition.CENTRAL)
        val p2 = h.atleta(); vou(p2, AthletePosition.PONTA)
        val detail = requireNotNull(h.repo().find(p2, h.group, h.game))
        assertEquals(5, detail.capacity)
        assertEquals(4, detail.confirmedCount)
        assertEquals(1, detail.availableSpots)
        assertEquals(3, detail.waitlistCount)
        val ponta0 = detail.positions.first { it.position == AthletePosition.PONTA }
        assertEquals(listOf(2, 2, 0, 2), listOf(ponta0.slots, ponta0.confirmed, ponta0.missing, ponta0.waitlisted))
        assertEquals(1, detail.positions.first { it.position == AthletePosition.LEVANTADOR }.missing)
        // p2 é o 2º da fila da PONTA (p1 está na frente), mesmo com um da CENTRAL esperando entre os dois.
        assertEquals(2L, detail.own?.waitlistPosition)
        assertEquals(AthletePosition.PONTA, detail.own?.position)
        val roster = requireNotNull(h.repo().roster(p2, h.group, h.game))
        assertEquals(listOf(1L, 1L, 2L).sorted(), roster.waitlisted.map { it.waitlistPosition!! }.sorted())
        assertEquals(3, roster.positions.size)
    }

    @Test fun `ajustar o total de um jogo com posicoes e negado, repetir o mesmo total nao`() {
        jogoComPosicoes()
        val version = h.int("SELECT version FROM games WHERE id='${h.game}'").toLong()
        val adjust = br.com.saqz.groups.application.attendance.AdjustGameCapacity(br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner(h.dataSource), h.repo(), h.cobrancas(), { java.time.Instant.now() })
        assertEquals(CapacityCommandResult.InvalidCapacity, adjust.execute(h.dono, h.group, h.game, version, 8))
    }
}
