package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.adapter.output.jdbc.attendance.FilaHarness
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.application.finance.uncharged.UnchargedDecision
import br.com.saqz.groups.application.finance.uncharged.UnchargedKind
import br.com.saqz.groups.application.finance.uncharged.UnchargedResult
import br.com.saqz.groups.application.finance.uncharged.UnchargedReview
import br.com.saqz.groups.application.finance.uncharged.UnchargedReviewCommand
import br.com.saqz.groups.application.finance.uncharged.UnchargedReviewItem
import br.com.saqz.groups.domain.finance.charge.PaidMethod
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DEB-147 (FIN-016): quem jogou sem cobrança, a revisão em lote e a idempotência. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiariaRetroativaIntegrationTest {
    private lateinit var h: FilaHarness
    private val review get() = UnchargedReview(
        JdbcTransactionRunner(h.dataSource), JdbcUnchargedRepository(h.dataSource),
        ChargeTransactions(JdbcTransactionRunner(h.dataSource), JdbcChargeTransactionRepository(h.dataSource)) { Instant.now() },
        JdbcChargeManagementRepository(h.dataSource), Instant::now,
    )

    @BeforeEach fun reset() {
        h = FilaHarness(this); h.cenario(capacity = 12)
        // Jogo "não definido" num grupo que depois escolheu R$ 20: quem confirmou ficou sem diária.
        h.execute("UPDATE games SET game_fee_cents=NULL WHERE id='${h.game}'")
        h.execute("UPDATE access_groups SET default_game_fee_cents=2000 WHERE id='${h.group}'")
    }

    private fun pessoas() = assertIs<UnchargedResult.Success<br.com.saqz.groups.application.finance.uncharged.UnchargedGameView>>(
        review.game(h.dono, h.group, h.game),
    ).value

    private fun confirmar(
        amount: Long? = 1000,
        request: UUID = UUID.randomUUID(),
        vararg items: UnchargedReviewItem,
    ) = review.review(h.dono, h.group, h.game, UnchargedReviewCommand(request, amount, items.toList()))

    private fun ok(result: UnchargedResult<br.com.saqz.groups.application.finance.uncharged.UnchargedReviewResult>) =
        assertIs<UnchargedResult.Success<br.com.saqz.groups.application.finance.uncharged.UnchargedReviewResult>>(result).value

    @Test fun `entram avulso e convidado do mensalista, com Faltou e quem saiu marcados, e mensalista, espera e quem desistiu ficam de fora`() {
        val avulso = h.atleta(); h.presenca(avulso, "CONFIRMED")
        val mensalista = h.atleta("MENSALISTA"); h.presenca(mensalista, "CONFIRMED")
        val faltou = h.atleta(); h.presenca(faltou, "CONFIRMED")
        h.execute("UPDATE game_attendance SET no_show_marked_at=now() WHERE member_user_id='$faltou'")
        val saiu = h.atleta(); h.presenca(saiu, "CONFIRMED")
        h.execute("UPDATE group_memberships SET active=false WHERE user_id='$saiu'")
        val espera = h.atleta(); h.presenca(espera, "WAITLISTED", 1)
        val desistiu = h.atleta(); h.presenca(desistiu, "DECLINED")
        val convidado = h.convidadoConfirmado(mensalista, "Júlia Rocha")

        val view = pessoas()
        assertEquals(2000, view.suggestedAmountCents, "sem valor no jogo, o padrão do grupo")
        val byKey = view.people.associateBy { it.memberId to it.guestSeq }
        assertEquals(setOf(avulso to 0, faltou to 0, saiu to 0, mensalista to convidado), byKey.keys)
        assertEquals(UnchargedKind.GUEST, byKey.getValue(mensalista to convidado).kind)
        assertEquals("Júlia Rocha", byKey.getValue(mensalista to convidado).displayName)
        assertEquals("User", byKey.getValue(mensalista to convidado).hostDisplayName)
        assertTrue(byKey.getValue(faltou to 0).noShow); assertTrue(byKey.getValue(saiu to 0).leftGroup)
        assertEquals(UnchargedKind.AVULSO, byKey.getValue(avulso to 0).kind)

        val summary = assertIs<UnchargedResult.Success<List<br.com.saqz.groups.application.finance.uncharged.UnchargedGameSummary>>>(review.summary(h.dono, h.group)).value
        assertEquals(4, summary.single().peopleCount)
    }

    @Test fun `jogo cancelado, rascunho ou Sem diaria nao mostra ninguem, e jogo com valor mostra quem ficou sem diaria`() {
        val avulso = h.atleta(); h.presenca(avulso, "CONFIRMED")
        h.execute("UPDATE games SET status='CANCELLED' WHERE id='${h.game}'")
        assertTrue(pessoas().people.isEmpty())
        h.execute("UPDATE games SET status='PUBLISHED', no_game_fee=true WHERE id='${h.game}'")
        assertTrue(pessoas().people.isEmpty())
        assertTrue(assertIs<UnchargedResult.Success<List<*>>>(review.summary(h.dono, h.group)).value.isEmpty())
        h.execute("UPDATE games SET no_game_fee=false, game_fee_cents=2500 WHERE id='${h.game}'")
        assertEquals(2500, pessoas().suggestedAmountCents)
        assertEquals(listOf(avulso), pessoas().people.map { it.memberId })
    }

    @Test fun `Cobrar vence hoje, Ja pagou entra na data do jogo com o meio, Nao cobrar so marca e pode ser reaberto`() {
        val a = h.atleta(); val b = h.atleta(); val c = h.atleta()
        listOf(a, b, c).forEach { h.presenca(it, "CONFIRMED") }
        val result = ok(
            confirmar(
                1000, UUID.randomUUID(),
                UnchargedReviewItem(a, 0, UnchargedDecision.CHARGE),
                UnchargedReviewItem(b, 0, UnchargedDecision.PAID, PaidMethod.CASH),
                UnchargedReviewItem(c, 0, UnchargedDecision.SKIP),
            ),
        )
        assertEquals(listOf(1, 1, 1, 0), with(result.outcome) { listOf(charged, paid, skipped, ignored) })
        assertEquals(2, result.charges.size)
        val hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString()
        assertEquals("PENDING|1000|$hoje", h.string("SELECT status||'|'||amount_cents||'|'||due_date FROM group_charges WHERE member_user_id='$a'"))
        assertEquals("PAID|CASH", h.string("SELECT status||'|'||paid_method FROM group_charges WHERE member_user_id='$b'"))
        assertEquals(
            1,
            h.int("SELECT count(*) FROM group_charge_events e JOIN group_charges c ON c.id=e.charge_id JOIN games g ON g.id=c.game_id WHERE c.member_user_id='$b' AND e.new_status='PAID' AND e.occurred_at=g.starts_at"),
            "recebida com a data do jogo",
        )
        assertEquals(0, h.diarias(c))
        assertEquals(UnchargedDecision.SKIP.name, pessoas().people.single().let { if (it.skipped) "SKIP" else null })
        assertTrue(assertIs<UnchargedResult.Success<List<*>>>(review.summary(h.dono, h.group)).value.isEmpty(), "decidido não conta no resumo")

        // Reabrir o Não cobrar.
        ok(confirmar(1000, UUID.randomUUID(), UnchargedReviewItem(c, 0, UnchargedDecision.CHARGE)))
        assertEquals(1, h.diarias(c)); assertEquals(0, h.int("SELECT count(*) FROM game_uncharged_skips"))
        assertTrue(pessoas().people.isEmpty())
    }

    @Test fun `quem ja tem diaria, saiu da lista ou virou mensalista conta em ignored, e o mesmo requestId nao repete`() {
        val a = h.atleta(); h.presenca(a, "CONFIRMED")
        val virou = h.atleta(); h.presenca(virou, "CONFIRMED")
        h.execute("UPDATE group_memberships SET membership_type='MENSALISTA' WHERE user_id='$virou'")
        val request = UUID.randomUUID()
        val first = ok(confirmar(1000, request, UnchargedReviewItem(a, 0, UnchargedDecision.CHARGE), UnchargedReviewItem(virou, 0, UnchargedDecision.CHARGE)))
        assertEquals(1, first.outcome.charged); assertEquals(1, first.outcome.ignored)
        val again = ok(confirmar(1000, request, UnchargedReviewItem(a, 0, UnchargedDecision.CHARGE)))
        assertEquals(first.outcome, again.outcome); assertEquals(first.charges.map { it.charge.id }, again.charges.map { it.charge.id })
        assertEquals(1, h.int("SELECT count(*) FROM group_charges WHERE game_id='${h.game}'"))
        val outro = ok(confirmar(1000, UUID.randomUUID(), UnchargedReviewItem(a, 0, UnchargedDecision.PAID)))
        assertEquals(1, outro.outcome.ignored); assertEquals("PENDING", h.statusDaDiaria(a))
    }

    @Test fun `dois gestores ao mesmo tempo criam uma diaria por pessoa`() {
        val a = h.atleta(); h.presenca(a, "CONFIRMED")
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        val futures = List(4) { pool.submit<Any> { start.await(); confirmar(1000, UUID.randomUUID(), UnchargedReviewItem(a, 0, UnchargedDecision.CHARGE)) } }
        start.countDown(); futures.forEach { it.get() }; pool.shutdown()
        assertEquals(1, h.int("SELECT count(*) FROM group_charges WHERE game_id='${h.game}' AND member_user_id='$a'"))
    }

    @Test fun `valor obrigatorio com Cobrar ou Ja pagou, atleta recebe 403, fora do grupo 404 e nada vai ao atleta`() {
        val a = h.atleta(); h.presenca(a, "CONFIRMED")
        assertEquals(UnchargedResult.Invalid("amountCents"), confirmar(null, UUID.randomUUID(), UnchargedReviewItem(a, 0, UnchargedDecision.CHARGE)))
        assertEquals(UnchargedResult.Invalid("amountCents"), confirmar(100_000_000, UUID.randomUUID(), UnchargedReviewItem(a, 0, UnchargedDecision.CHARGE)))
        assertIs<UnchargedResult.Success<*>>(confirmar(null, UUID.randomUUID(), UnchargedReviewItem(a, 0, UnchargedDecision.SKIP)))
        assertEquals(UnchargedResult.Forbidden, review.game(a, h.group, h.game))
        assertEquals(UnchargedResult.Forbidden, review.review(a, h.group, h.game, UnchargedReviewCommand(UUID.randomUUID(), 1000, emptyList())))
        assertEquals(UnchargedResult.Hidden, review.summary(UUID.randomUUID(), h.group))
        assertEquals(UnchargedResult.Hidden, review.game(h.dono, h.group, UUID.randomUUID()))

        val antes = h.int("SELECT count(*) FROM group_messages")
        ok(confirmar(1000, UUID.randomUUID(), UnchargedReviewItem(a, 0, UnchargedDecision.CHARGE)))
        assertEquals(antes, h.int("SELECT count(*) FROM group_messages"), "nem central, nem push, nem WhatsApp")
        assertEquals(0, h.int("SELECT count(*) FROM group_notifications WHERE recipient_id='$a'"))
        assertNull(pessoas().people.firstOrNull())
    }
}
