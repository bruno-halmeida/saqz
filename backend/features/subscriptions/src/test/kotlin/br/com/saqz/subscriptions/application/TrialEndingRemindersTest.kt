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
    private val whatsApp = mutableListOf<String>()
    private var paid = false
    private var hasGroup = true
    private var clockNow = Instant.parse("2026-10-12T13:00:00Z") // 10h em São Paulo, 3 dias antes

    private fun job() = TrialEndingReminders(
        store,
        { if (paid) EntitlingSubscription(Plan.ORGANIZADOR) else null },
        TrialUsageLookup { _, since, until -> assertEquals(started, since); assertEquals(clockNow, until); usage },
        TrialEndingNotice { _, body -> hasGroup.also { if (it) published += body } },
        { _, body -> whatsApp += body },
        Clock.fixed(clockNow, ZoneOffset.UTC),
    )

    private fun runAt(instant: String): Int {
        clockNow = Instant.parse(instant)
        return job().run()
    }

    @Test
    fun `avisa a 3 dias, a 1 dia e no dia do fim, uma vez por marco`() {
        assertEquals(1, runAt("2026-10-12T13:00:00Z"))
        assertEquals(0, runAt("2026-10-12T13:00:00Z"))
        assertEquals(0, runAt("2026-10-13T13:00:00Z"))
        assertEquals(1, runAt("2026-10-14T13:00:00Z"))
        assertEquals(0, runAt("2026-10-14T13:00:00Z"))
        assertEquals(1, runAt("2026-10-15T13:00:00Z"))
        assertEquals(
            listOf(
                "Faltam 3 dias do seu teste do Organizador. Até aqui: 3 jogos, 12 atletas confirmados, R\$ 450,00 no caixa. Quando o teste acabar, você não marca jogos e a galera não confirma presença. Assine para continuar.",
                "Falta 1 dia do seu teste do Organizador. Até aqui: 3 jogos, 12 atletas confirmados, R\$ 450,00 no caixa. Quando o teste acabar, você não marca jogos e a galera não confirma presença. Assine para continuar.",
                "Hoje é o último dia do seu teste do Organizador. Até aqui: 3 jogos, 12 atletas confirmados, R\$ 450,00 no caixa. Quando o teste acabar, você não marca jogos e a galera não confirma presença. Assine para continuar.",
            ),
            published,
        )
        assertEquals(published, whatsApp)
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
    fun `teste que ja terminou nao recebe aviso`() {
        assertEquals(0, runAt("2026-10-15T16:00:00Z"))
        assertTrue(published.isEmpty())
    }

    @Test
    fun `rodada perdida avisa so o marco mais apertado e nao repete os anteriores`() {
        assertEquals(1, runAt("2026-10-14T13:00:00Z"))
        assertEquals(0, runAt("2026-10-14T14:00:00Z"))
        assertEquals(1, published.size)
        assertTrue(published.single().startsWith("Falta 1 dia"))
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
            store, { null }, { _, _, _ -> usage }, { _, body -> published += body; true },
            { _, _ -> error("uazapi fora do ar") }, Clock.fixed(clockNow, ZoneOffset.UTC),
        )
        assertEquals(1, failing.run())
        assertEquals(0, failing.run())
    }

    @Test
    fun `sem uso o aviso so traz a contagem`() {
        assertEquals(
            "Faltam 2 dias do seu teste do Organizador. Quando o teste acabar, você não marca jogos e a galera não confirma presença. Assine para continuar.",
            trialEndingBody(2, TrialUsage(0, 0, 0)),
        )
    }

    @Test
    fun `singular e valores em reais`() {
        assertEquals("1 jogo, 1 atleta confirmado, R\$ 1.250,50 no caixa", trialUsageSummary(TrialUsage(1, 1, 125_050)))
    }

    @Test
    fun `dias restantes contam dias de calendario em Sao Paulo`() {
        // 02h UTC de 16/10 ainda é 23h de 15/10 em São Paulo.
        assertEquals(0, trialDaysLeft(Instant.parse("2026-10-15T09:00:00Z"), Instant.parse("2026-10-16T02:00:00Z")))
        assertEquals(1, trialDaysLeft(Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-16T02:00:00Z")))
    }

    private class FakeStore(private val trial: OrganizerTrial) : TrialEndingRepository {
        private val claimed = mutableSetOf<Int>()

        override fun endingSoon(now: Instant, within: Duration) =
            listOf(trial).filter { it.endsAt > now && it.endsAt <= now.plus(within) }

        override fun claim(ownerUserId: UUID, due: List<Int>): Boolean {
            val fresh = due.filter { claimed.add(it) }
            return due.min() in fresh
        }

        override fun release(ownerUserId: UUID, milestone: Int) {
            claimed.remove(milestone)
        }
    }
}
