package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.TrialEndingNotice
import br.com.saqz.sharedkernel.subscription.TrialUsage
import br.com.saqz.sharedkernel.subscription.TrialUsageLookup
import br.com.saqz.subscriptions.domain.OrganizerTrial
import br.com.saqz.subscriptions.domain.Plan
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrialEndingRemindersTest {
    private val owner = UUID.randomUUID()
    private val started = Instant.parse("2026-10-01T15:00:00Z") // 12h em São Paulo
    private val ends = started.plus(Duration.ofDays(14))        // 15/10 12h em São Paulo
    private val usage = TrialUsage(games = 3, confirmedAthletes = 12, cashCents = 45_000)

    private val store = FakeStore(OrganizerTrial(owner, started, ends))
    private val published = mutableListOf<String>()
    private val pushed = mutableListOf<String>()
    private val whatsApp = mutableListOf<String>()
    private var paid = false
    private var hasGroup = true
    private var clockNow = Instant.parse("2026-10-12T13:00:00Z") // 10h em São Paulo, 3 dias antes
    private var currentUsage = usage

    private fun job() = TrialEndingReminders(
        store,
        { if (paid) EntitlingSubscription(Plan.ORGANIZADOR) else null },
        TrialUsageLookup { _, since, until -> assertEquals(started, since); assertEquals(clockNow, until); currentUsage },
        TrialEndingNotice { _, body, push -> hasGroup.also { if (it) { published += body; pushed += push } } },
        { _, body -> whatsApp += body },
        Clock.fixed(clockNow, ZoneOffset.UTC),
    )

    private fun runAt(instant: String): Int {
        clockNow = Instant.parse(instant)
        return job().run()
    }

    @Test
    fun `avisa a 3 dias, a 1 dia e no dia do fim, uma vez por marco, com a copy aprovada`() {
        assertEquals(1, runAt("2026-10-12T13:00:00Z"))
        assertEquals(0, runAt("2026-10-12T13:00:00Z"))
        assertEquals(0, runAt("2026-10-13T13:00:00Z"))
        assertEquals(1, runAt("2026-10-14T13:00:00Z"))
        assertEquals(0, runAt("2026-10-14T13:00:00Z"))
        assertEquals(1, runAt("2026-10-15T13:00:00Z"))
        assertEquals(
            listOf(
                "Faltam 3 dias do seu teste do Saqz. Até aqui, 3 jogos organizados, 12 atletas confirmados e R\$ 450,00 no caixa. Para a galera continuar confirmando presença, assine até 15/10, a partir de R\$ 39,90 por mês. Assinar agora: https://saqz.app/assinar",
                "Seu teste termina amanhã, às 12h. Depois disso você não marca jogos e a galera não confirma presença. Seus grupos e o histórico ficam guardados, e voltam na hora quando você assina. Assinar agora: https://saqz.app/assinar",
                "Seu teste termina hoje, às 12h. Assine agora para a galera continuar confirmando presença sem parar. Tudo o que você montou fica guardado. Assinar agora: https://saqz.app/assinar",
            ),
            published,
        )
        assertEquals(
            listOf(
                "Faltam 3 dias do seu teste. Veja como seguir com a galera.",
                "Seu teste termina amanhã. Assine para a galera não ficar sem lista.",
                "Seu teste termina hoje às 12h. Toque para assinar.",
            ),
            pushed,
        )
        assertEquals(published, whatsApp)
    }

    @Test
    fun `sem jogo marcado o primeiro aviso troca o resumo pelo convite para criar o primeiro`() {
        currentUsage = TrialUsage(0, 0, 0)
        runAt("2026-10-12T13:00:00Z")
        assertEquals(
            "Faltam 3 dias do seu teste do Saqz. Você ainda não marcou nenhum jogo. Crie o primeiro em 1 minuto e veja a lista da galera se fechando. Para a galera continuar confirmando presença, assine até 15/10, a partir de R\$ 39,90 por mês. Assinar agora: https://saqz.app/assinar",
            published.single(),
        )
    }

    @Test
    fun `o horario vem do fim do teste no fuso de Sao Paulo, com minutos quando houver`() {
        val message = trialEndingMessage(0, 0, Instant.parse("2026-10-15T21:30:00Z"), usage)
        assertEquals("Seu teste termina hoje, às 18h30. Assine agora para a galera continuar confirmando presença sem parar. Tudo o que você montou fica guardado. Assinar agora: https://saqz.app/assinar", message.body)
        assertEquals("Seu teste termina hoje às 18h30. Toque para assinar.", message.push)
    }

    @Test
    fun `nenhum texto traz o nome Organizador`() {
        val all = listOf(3, 1, 0, -3, -14).map { trialEndingMessage(it, it, ends, usage) }
        assertTrue(all.none { "Organizador" in it.body || "Organizador" in it.push })
    }

    @Test
    fun `tres dias depois do fim avisa que os grupos estao congelados e catorze dias depois e o ultimo`() {
        assertEquals(0, runAt("2026-10-17T13:00:00Z")) // 2 dias depois
        assertEquals(1, runAt("2026-10-18T13:00:00Z")) // 3 dias depois
        assertEquals(0, runAt("2026-10-18T14:00:00Z"))
        assertEquals(0, runAt("2026-10-25T13:00:00Z"))
        assertEquals(1, runAt("2026-10-29T13:00:00Z")) // 14 dias depois
        assertEquals(0, runAt("2026-10-29T14:00:00Z"))
        assertEquals(0, runAt("2026-10-30T13:00:00Z"))
        assertEquals(0, runAt("2026-11-20T13:00:00Z")) // mais nenhuma
        assertEquals(
            listOf(
                "Sua galera está esperando: seus grupos estão congelados. Assine para voltar de onde parou. Assinar agora: https://saqz.app/assinar",
                "Seus grupos e o histórico seguem guardados. Quando quiser voltar, é só assinar. Assinar agora: https://saqz.app/assinar",
            ),
            published,
        )
        assertEquals(published, whatsApp)
        assertTrue(published.none { "pausad" in it })
    }

    @Test
    fun `depois do fim quem assinou nao recebe aviso`() {
        paid = true
        assertEquals(0, runAt("2026-10-18T13:00:00Z"))
        assertTrue(published.isEmpty() && whatsApp.isEmpty())
    }

    @Test
    fun `teste que acabou ha mais de duas semanas nao recebe o aviso de uma vez ao ligar o job`() {
        assertEquals(0, runAt("2026-11-05T13:00:00Z"))
        assertTrue(published.isEmpty())
    }

    @Test
    fun `rodada perdida depois do fim avisa so o marco mais apertado`() {
        assertEquals(1, runAt("2026-10-20T13:00:00Z")) // 5 dias depois
        assertEquals(0, runAt("2026-10-20T14:00:00Z"))
        assertTrue(published.single().startsWith("Sua galera está esperando"))
    }

    @Test
    fun `fora dos ultimos 3 dias nao avisa`() {
        assertEquals(0, runAt("2026-10-11T13:00:00Z"))
        assertTrue(published.isEmpty() && whatsApp.isEmpty())
    }

    @Test
    fun `quem ja assinou nao recebe aviso`() {
        paid = true
        assertEquals(0, runAt("2026-10-12T13:00:00Z"))
        assertTrue(published.isEmpty() && whatsApp.isEmpty())
    }

    @Test
    fun `no dia do fim, depois da hora do fim, so vale o aviso de depois do fim`() {
        assertEquals(0, runAt("2026-10-15T16:00:00Z"))
        assertTrue(published.isEmpty())
    }

    @Test
    fun `rodada perdida avisa so o marco mais apertado e nao repete os anteriores`() {
        assertEquals(1, runAt("2026-10-14T13:00:00Z"))
        assertEquals(0, runAt("2026-10-14T14:00:00Z"))
        assertEquals(1, published.size)
        assertTrue(published.single().startsWith("Seu teste termina amanhã"))
    }

    @Test
    fun `sem grupo para guardar o aviso a reserva e desfeita e a proxima rodada tenta de novo`() {
        hasGroup = false
        assertEquals(0, runAt("2026-10-12T13:00:00Z"))
        assertTrue(whatsApp.isEmpty())
        hasGroup = true
        assertEquals(1, runAt("2026-10-12T14:00:00Z"))
    }

    @Test
    fun `falha do WhatsApp nao desfaz o aviso da central`() {
        val failing = TrialEndingReminders(
            store, { null }, { _, _, _ -> usage }, { _, body, _ -> published += body; true },
            { _, _ -> error("uazapi fora do ar") }, Clock.fixed(clockNow, ZoneOffset.UTC),
        )
        assertEquals(1, failing.run())
        assertEquals(0, failing.run())
    }

    @Test
    fun `singular e valores em reais`() {
        assertEquals("1 jogo organizado, 1 atleta confirmado e R\$ 1.250,50 no caixa", trialUsageSummary(TrialUsage(1, 1, 125_050)))
    }

    @Test
    fun `rodada perdida do primeiro aviso diz quantos dias faltam de verdade`() {
        assertEquals(1, runAt("2026-10-13T13:00:00Z"))
        assertTrue(published.single().startsWith("Faltam 2 dias do seu teste do Saqz."))
        assertEquals("Faltam 2 dias do seu teste. Veja como seguir com a galera.", pushed.single())
    }

    @Test
    fun `dias restantes contam dias de calendario em Sao Paulo`() {
        // 02h UTC de 16/10 ainda é 23h de 15/10 em São Paulo.
        assertEquals(0, trialDaysLeft(Instant.parse("2026-10-15T09:00:00Z"), Instant.parse("2026-10-16T02:00:00Z")))
        assertEquals(1, trialDaysLeft(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-16T02:00:00Z")))
    }

    private class FakeStore(private val trial: OrganizerTrial) : TrialEndingRepository {
        private val claimed = mutableSetOf<Int>()

        override fun around(now: Instant, ahead: Duration, behind: Duration) =
            listOf(trial).filter { it.endsAt > now.minus(behind) && it.endsAt <= now.plus(ahead) }

        override fun claim(ownerUserId: UUID, due: List<Int>): Boolean {
            val fresh = due.filter { claimed.add(it) }
            return due.min() in fresh
        }

        override fun release(ownerUserId: UUID, milestone: Int) {
            claimed.remove(milestone)
        }
    }
}
