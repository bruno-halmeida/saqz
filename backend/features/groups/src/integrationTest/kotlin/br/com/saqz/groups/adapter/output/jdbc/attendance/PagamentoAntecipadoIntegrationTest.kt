package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeManagementRepository
import br.com.saqz.groups.application.attendance.AttendanceCommandResult
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.finance.charge.PaidMethod
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DEB-150 (PROM-010): a diária nasce no "Vou" da espera, o gestor vê o pagamento e coloca com ou sem baixa. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PagamentoAntecipadoIntegrationTest {
    private lateinit var h: FilaHarness

    @BeforeEach fun reset() {
        h = FilaHarness(this); h.cenario(capacity = 8)
        h.execute("UPDATE access_groups SET promotion_mode='MANUAL', advance_payment_required=true WHERE id='${h.group}'")
    }

    private fun vou(member: UUID) =
        assertIs<AttendanceCommandResult.Success>(h.respostas().execute(member, h.group, h.game, intent = AttendanceIntent.CONFIRM, requestId = UUID.randomUUID()))

    private fun colocar(member: UUID, markPaid: Boolean = false, method: PaidMethod? = null, guest: Int = 0, request: UUID = UUID.randomUUID()) =
        h.respostas().promote(h.dono, h.group, h.game, member, request, "Colocado no jogo", guestSeq = guest, markPaid = markPaid, paidMethod = method)

    private fun dueDate(member: UUID, guest: Int = 0) =
        h.string("SELECT (due_date = g.local_date)::text FROM group_charges c JOIN games g ON g.id=c.game_id WHERE c.member_user_id='$member' AND c.guest_seq=$guest")

    @Test fun `avulso que da Vou vai para a espera com a diaria pendente vencendo na data do jogo`() {
        val avulso = h.atleta(); vou(avulso)
        assertEquals("WAITLISTED", h.status(avulso))
        assertEquals("PENDING", h.statusDaDiaria(avulso)); assertEquals("true", dueDate(avulso))
        assertEquals(2500, h.int("SELECT amount_cents FROM group_charges WHERE member_user_id='$avulso'"))
    }

    @Test fun `mensalista nao paga, convidado do mensalista paga no nome do anfitriao`() {
        val mensalista = h.atleta("MENSALISTA"); vou(mensalista)
        assertEquals(0, h.diarias(mensalista))
        assertIs<AttendanceCommandResult.Success>(h.convidados().add(mensalista, h.group, h.game, "Bia Souza", UUID.randomUUID()))
        assertEquals("WAITLISTED", h.status(mensalista, 1)); assertEquals("PENDING", h.statusDaDiaria(mensalista, 1))
    }

    @Test fun `jogo sem valor e opcao desligada nao criam diaria na espera`() {
        h.execute("UPDATE games SET game_fee_cents=NULL, no_game_fee=true WHERE id='${h.game}'")
        val a = h.atleta(); vou(a); assertEquals(0, h.diarias(a))
        h.execute("UPDATE games SET game_fee_cents=2500, no_game_fee=false WHERE id='${h.game}'")
        h.execute("UPDATE access_groups SET advance_payment_required=false WHERE id='${h.group}'")
        val b = h.atleta(); vou(b); assertEquals("WAITLISTED", h.status(b)); assertEquals(0, h.diarias(b))
    }

    @Test fun `o roster mostra a diaria da espera so ao gestor`() {
        val a = h.atleta(); vou(a)
        val semDiaria = h.atleta(); h.presenca(semDiaria, "WAITLISTED", h.repo().nextWaitlistSequence(h.group, h.game))
        val gestor = checkNotNull(h.repo().roster(h.dono, h.group, h.game)).waitlisted.associateBy { it.memberId }
        val charge = assertNotNull(gestor.getValue(a).charge)
        assertEquals("PENDING", charge.status); assertEquals(2500, charge.amountCents); assertEquals(1, charge.version)
        assertNull(gestor.getValue(semDiaria).charge)
        val atleta = checkNotNull(h.repo().roster(a, h.group, h.game)).waitlisted
        assertTrue(atleta.all { it.charge == null }, "o atleta nunca recebe a diária de ninguém")
    }

    @Test fun `Colocar ja pagou da baixa na pendente, cria e baixa quem nao tinha e so promove quem ja pagou`() {
        val pendente = h.atleta(); vou(pendente)
        assertIs<AttendanceCommandResult.Success>(colocar(pendente, markPaid = true, method = PaidMethod.CASH))
        assertEquals("CONFIRMED", h.status(pendente)); assertEquals("PAID", h.statusDaDiaria(pendente))
        assertEquals("CASH", h.string("SELECT paid_method::text FROM group_charges WHERE member_user_id='$pendente'"))

        // Esperava antes de a opção ser ligada: a diária nasce no Colocar e já sai recebida (PIX por padrão).
        val antigo = h.atleta(); h.presenca(antigo, "WAITLISTED", h.repo().nextWaitlistSequence(h.group, h.game))
        assertIs<AttendanceCommandResult.Success>(colocar(antigo, markPaid = true))
        assertEquals("PAID", h.statusDaDiaria(antigo)); assertEquals("PIX", h.string("SELECT paid_method::text FROM group_charges WHERE member_user_id='$antigo'"))

        val pagou = h.atleta(); vou(pagou)
        h.execute("UPDATE group_charges SET status='PAID', paid_method='OTHER' WHERE member_user_id='$pagou'")
        assertIs<AttendanceCommandResult.Success>(colocar(pagou, markPaid = true, method = PaidMethod.CASH))
        assertEquals("OTHER", h.string("SELECT paid_method::text FROM group_charges WHERE member_user_id='$pagou'"))
        listOf(pendente, antigo, pagou).forEach { assertEquals(1, h.diarias(it)) }
    }

    @Test fun `Colocar e cobrar depois mantem a pendente sem segunda diaria, e o mesmo requestId nao repete`() {
        val a = h.atleta(); vou(a)
        val request = UUID.randomUUID()
        assertIs<AttendanceCommandResult.Success>(colocar(a, request = request))
        assertIs<AttendanceCommandResult.Success>(colocar(a, markPaid = true, request = request))
        assertEquals("CONFIRMED", h.status(a)); assertEquals("PENDING", h.statusDaDiaria(a)); assertEquals(1, h.diarias(a))
    }

    @Test fun `a lista do gestor marca a diaria de quem esta na espera`() {
        val espera = h.atleta(); vou(espera)
        val colocado = h.atleta(); vou(colocado); colocar(colocado)
        val charges = JdbcChargeManagementRepository(h.dataSource).list(h.group).associateBy { it.charge.memberId }
        assertTrue(charges.getValue(espera).charge.waitlisted)
        assertTrue(!charges.getValue(colocado).charge.waitlisted)
    }

    @Test fun `Nao vou depois de pagar mantem a diaria recebida`() {
        val a = h.atleta(); vou(a)
        h.execute("UPDATE group_charges SET status='PAID', paid_method='PIX' WHERE member_user_id='$a'")
        assertIs<AttendanceCommandResult.Success>(h.respostas().execute(a, h.group, h.game, intent = AttendanceIntent.DECLINE, requestId = UUID.randomUUID()))
        assertEquals("DECLINED", h.status(a)); assertEquals("PAID", h.statusDaDiaria(a))
        // O acerto mostra "Desistiu" também para quem pagou; dispensada e cancelada não ganham a etiqueta.
        val dispensada = h.atleta(); vou(dispensada); val cancelada = h.atleta(); vou(cancelada)
        listOf(dispensada, cancelada).forEach { h.respostas().execute(it, h.group, h.game, intent = AttendanceIntent.DECLINE, requestId = UUID.randomUUID()) }
        h.execute("UPDATE group_charges SET status='WAIVED' WHERE member_user_id='$dispensada'")
        h.execute("UPDATE group_charges SET status='CANCELLED' WHERE member_user_id='$cancelada'")
        val charges = JdbcChargeManagementRepository(h.dataSource).list(h.group).associateBy { it.charge.memberId }
        assertTrue(charges.getValue(a).charge.withdrew)
        assertTrue(!charges.getValue(dispensada).charge.withdrew); assertTrue(!charges.getValue(cancelada).charge.withdrew)
    }

    @Test fun `o segundo gestor que coloca com ja pagou da a baixa mesmo com a pessoa ja confirmada`() {
        val a = h.atleta(); vou(a)
        assertIs<AttendanceCommandResult.Success>(colocar(a))
        assertEquals("PENDING", h.statusDaDiaria(a))
        assertIs<AttendanceCommandResult.Success>(colocar(a, markPaid = true, method = PaidMethod.CASH))
        assertEquals("CONFIRMED", h.status(a)); assertEquals("PAID", h.statusDaDiaria(a)); assertEquals(1, h.diarias(a))
    }
}
