package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.TrialEndingNotice
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SubscriptionPastDueRemindersTest {
    private val owner = UUID.randomUUID()
    private val invoice = "https://www.asaas.com/i/abc123"

    // Vencimento em 10/10 (São Paulo): dia 0 = 10/10, +3 = 13/10 e último dia = 17/10.
    private val since = Instant.parse("2026-10-10T03:00:00Z")
    private val asaas = PastDueSubscription(owner, PastDueProvider.ASAAS, since, LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-17"), "sub_1")

    private val subscriptions = mutableListOf(asaas)
    private val published = mutableListOf<Pair<String, String>>()
    private val whatsApp = mutableListOf<String>()
    private val store = FakeStore()
    private var hasGroup = true
    private var payment: AsaasPaymentSnapshot? = AsaasPaymentSnapshot("pay_1", "OVERDUE", invoice)
    private var gatewayAvailable = true
    private var overdue: OverdueInvoice? = OverdueInvoice("pay_1", "https://www.asaas.com/i/from-webhook")
    private var clockNow = Instant.parse("2026-10-10T13:00:00Z") // 10h em São Paulo

    private fun runAt(instant: String): Int {
        clockNow = Instant.parse(instant)
        return SubscriptionPastDueReminders(
            store,
            TrialEndingNotice { _, body, push -> hasGroup.also { if (it) published += body to push } },
            { _, body -> whatsApp += body },
            { if (gatewayAvailable) fakeGateway() else null },
            Clock.fixed(clockNow, ZoneOffset.UTC),
        ).run()
    }

    @Test
    fun `avisa no vencimento, 3 dias depois e no ultimo dia, uma vez por marco, com a fatura do Asaas`() {
        assertEquals(1, runAt("2026-10-10T13:00:00Z"))
        assertEquals(0, runAt("2026-10-10T14:00:00Z"))
        assertEquals(0, runAt("2026-10-12T13:00:00Z"))
        assertEquals(1, runAt("2026-10-13T13:00:00Z"))
        assertEquals(0, runAt("2026-10-16T13:00:00Z"))
        assertEquals(1, runAt("2026-10-17T13:00:00Z"))
        assertEquals(0, runAt("2026-10-17T14:00:00Z"))
        assertEquals(
            listOf(
                "Seu pagamento do Saqz está pendente. Seus grupos serão congelados em breve se não houver pagamento. Pagar agora: $invoice",
                "Seu pagamento do Saqz continua pendente. Seus grupos serão congelados em breve se não houver pagamento. Pagar agora: $invoice",
                "Seus grupos congelam hoje. Pague agora para a galera não ficar sem lista: $invoice",
            ),
            published.map { it.first },
        )
        assertEquals(published.map { it.first }, whatsApp)
    }

    @Test
    fun `o push e curto e nao leva o link`() {
        runAt("2026-10-10T13:00:00Z"); runAt("2026-10-13T13:00:00Z"); runAt("2026-10-17T13:00:00Z")

        assertEquals(
            listOf(
                "Seu pagamento do Saqz está pendente. Toque para regularizar.",
                "Seu pagamento do Saqz continua pendente. Toque para regularizar.",
                "Seus grupos congelam hoje. Toque para regularizar.",
            ),
            published.map { it.second },
        )
        assertTrue(published.none { "http" in it.second })
    }

    @Test
    fun `depois do ultimo dia os grupos ja congelaram e nao sai nada`() {
        assertEquals(0, runAt("2026-10-18T13:00:00Z"))
        assertTrue(published.isEmpty() && whatsApp.isEmpty())
    }

    @Test
    fun `rodada perdida reserva os marcos anteriores e avisa so o mais apertado`() {
        assertEquals(1, runAt("2026-10-14T13:00:00Z")) // dia 4: devidos [0, 3]
        assertEquals(0, runAt("2026-10-14T14:00:00Z"))
        assertEquals(1, published.size)
        assertTrue(published.single().first.startsWith("Seu pagamento do Saqz continua pendente"))
        assertEquals(setOf(0, 3), store.claimed)
    }

    @Test
    fun `o marco de 3 dias sai mesmo com o de vencimento ja reservado`() {
        runAt("2026-10-10T13:00:00Z")
        assertEquals(1, runAt("2026-10-13T13:00:00Z"))
        assertEquals(setOf(0, 3), store.claimed)
    }

    @Test
    fun `atraso novo, com outra ancora, recomeca os marcos`() {
        assertEquals(1, runAt("2026-10-10T13:00:00Z"))
        subscriptions[0] = asaas.copy(since = Instant.parse("2026-11-10T03:00:00Z"), dueDay = LocalDate.parse("2026-11-10"), lastDay = LocalDate.parse("2026-11-17"))
        assertEquals(1, runAt("2026-11-10T13:00:00Z"))
        assertEquals(2, published.size)
    }

    @Test
    fun `sem fatura o aviso sai sem link`() {
        overdue = null
        runAt("2026-10-10T13:00:00Z"); runAt("2026-10-17T13:00:00Z")

        assertEquals(
            listOf(
                "Seu pagamento do Saqz está pendente. Seus grupos serão congelados em breve se não houver pagamento.",
                "Seus grupos congelam hoje. Pague agora para a galera não ficar sem lista.",
            ),
            published.map { it.first },
        )
    }

    @Test
    fun `fatura que o Asaas diz que nao esta vencida nao vira link`() {
        payment = AsaasPaymentSnapshot("pay_1", "PENDING", invoice)
        runAt("2026-10-10T13:00:00Z")

        assertEquals("Seu pagamento do Saqz está pendente. Seus grupos serão congelados em breve se não houver pagamento.", published.single().first)
    }

    @Test
    fun `cobranca ja paga, com o webhook atrasado, nao avisa e a reserva e desfeita`() {
        payment = AsaasPaymentSnapshot("pay_1", "RECEIVED", invoice)

        assertEquals(0, runAt("2026-10-10T13:00:00Z"))

        assertTrue(published.isEmpty() && whatsApp.isEmpty() && store.claimed.isEmpty())
    }

    @Test
    fun `sem o Asaas configurado vale o link do webhook guardado`() {
        gatewayAvailable = false
        runAt("2026-10-10T13:00:00Z")

        assertTrue(published.single().first.endsWith("Pagar agora: https://www.asaas.com/i/from-webhook"))
    }

    @Test
    fun `o link e lido do Asaas, que e a fonte mais nova`() {
        runAt("2026-10-10T13:00:00Z")

        assertNotEquals("https://www.asaas.com/i/from-webhook", invoice)
        assertTrue(published.single().first.endsWith("Pagar agora: $invoice"))
    }

    @Test
    fun `App Store troca o Pagar agora por atualizar a forma de pagamento`() {
        subscriptions[0] = PastDueSubscription(owner, PastDueProvider.APP_STORE, since, LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-17"))
        runAt("2026-10-10T13:00:00Z"); runAt("2026-10-13T13:00:00Z"); runAt("2026-10-17T13:00:00Z")

        assertEquals(
            listOf(
                "Seu pagamento do Saqz está pendente. Seus grupos serão congelados em breve se não houver pagamento. Atualize a forma de pagamento na App Store.",
                "Seu pagamento do Saqz continua pendente. Seus grupos serão congelados em breve se não houver pagamento. Atualize a forma de pagamento na App Store.",
                "Seus grupos congelam hoje. Atualize a forma de pagamento na App Store.",
            ),
            published.map { it.first },
        )
        assertTrue(published.none { "http" in it.first })
    }

    @Test
    fun `Google Play sem data de vencimento avisa so o ultimo dia`() {
        subscriptions[0] = PastDueSubscription(owner, PastDueProvider.GOOGLE_PLAY, since, null, LocalDate.parse("2026-10-17"))

        assertEquals(0, runAt("2026-10-10T13:00:00Z"))
        assertEquals(0, runAt("2026-10-13T13:00:00Z"))
        assertEquals(1, runAt("2026-10-17T13:00:00Z"))

        assertEquals("Seus grupos congelam hoje. Atualize a forma de pagamento no Google Play.", published.single().first)
    }

    @Test
    fun `loja cujo acesso acaba antes das 10h de SP recebe o ultimo aviso na rodada da vespera, uma vez so`() {
        // Fim às 08h de SP de 17/10 (11h UTC): a rodada de 17/10 às 10h já não o encontraria.
        val ends = Instant.parse("2026-10-17T11:00:00Z")
        subscriptions[0] = PastDueSubscription(owner, PastDueProvider.APP_STORE, since, LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-17"), accessEndsAt = ends)

        assertEquals(1, runAt("2026-10-13T13:00:00Z"))
        assertEquals(1, runAt("2026-10-16T13:00:00Z"))
        assertEquals(
            listOf(
                "Seu pagamento do Saqz continua pendente. Seus grupos serão congelados em breve se não houver pagamento. Atualize a forma de pagamento na App Store.",
                "Seus grupos congelam hoje. Atualize a forma de pagamento na App Store.",
            ),
            published.map { it.first },
        )
        assertEquals(0, runAt("2026-10-17T13:00:00Z"))
        assertEquals(2, published.size)
    }

    @Test
    fun `Google Play com fim de madrugada recebe o unico aviso na vespera`() {
        val ends = Instant.parse("2026-10-17T06:00:00Z") // 03h de SP
        subscriptions[0] = PastDueSubscription(owner, PastDueProvider.GOOGLE_PLAY, ends.minusSeconds(3 * 86400), null, LocalDate.parse("2026-10-17"), accessEndsAt = ends)

        assertEquals(0, runAt("2026-10-15T13:00:00Z"))
        assertEquals(1, runAt("2026-10-16T13:00:00Z"))
        assertEquals("Seus grupos congelam hoje. Atualize a forma de pagamento no Google Play.", published.single().first)
        assertEquals(0, runAt("2026-10-17T13:00:00Z"))
    }

    @Test
    fun `fim do acesso depois da proxima rodada nao antecipa o ultimo aviso`() {
        val ends = Instant.parse("2026-10-17T15:00:00Z") // 12h de SP: a rodada de 17/10 às 10h pega
        subscriptions[0] = PastDueSubscription(owner, PastDueProvider.GOOGLE_PLAY, since, null, LocalDate.parse("2026-10-17"), accessEndsAt = ends)

        assertEquals(0, runAt("2026-10-16T13:00:00Z"))
        assertEquals(1, runAt("2026-10-17T13:00:00Z"))
    }

    @Test
    fun `carencia curta da loja, o ultimo dia cai antes dos 7 dias e vence o marco de 3`() {
        subscriptions[0] = PastDueSubscription(owner, PastDueProvider.APP_STORE, since, LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-13"))

        assertEquals(1, runAt("2026-10-13T13:00:00Z"))

        assertEquals("Seus grupos congelam hoje. Atualize a forma de pagamento na App Store.", published.single().first)
    }

    @Test
    fun `sem grupo para guardar o aviso a reserva e desfeita e a proxima rodada tenta de novo`() {
        hasGroup = false
        assertEquals(0, runAt("2026-10-10T13:00:00Z"))
        assertTrue(whatsApp.isEmpty())
        hasGroup = true
        assertEquals(1, runAt("2026-10-10T14:00:00Z"))
    }

    @Test
    fun `falha do WhatsApp nao desfaz o aviso da central`() {
        val failing = SubscriptionPastDueReminders(
            store, { _, body, push -> published += body to push; true },
            { _, _ -> error("uazapi fora do ar") }, { null }, Clock.fixed(Instant.parse("2026-10-10T13:00:00Z"), ZoneOffset.UTC),
        )
        assertEquals(1, failing.run())
        assertEquals(0, failing.run())
    }

    @Test
    fun `dias de calendario em Sao Paulo, nao em UTC`() {
        // 23h de 12/10 em SP (02h UTC de 13/10): ainda não é o dia 3.
        assertEquals(1, runAt("2026-10-10T13:00:00Z"))
        assertEquals(0, runAt("2026-10-13T02:00:00Z"))
        assertEquals(1, runAt("2026-10-13T03:00:00Z"))
    }

    @Test
    fun `marcos devidos`() {
        val today = { date: String -> dueMilestones(asaas, LocalDate.parse(date), Instant.parse("${date}T13:00:00Z")) }
        assertEquals(emptyList(), today("2026-10-09"))
        assertEquals(listOf(0), today("2026-10-10"))
        assertEquals(listOf(0, 3), today("2026-10-13"))
        assertEquals(listOf(0, 3, 7), today("2026-10-17"))
        assertEquals(emptyList(), today("2026-10-18"))
    }

    private inner class FakeStore : SubscriptionPastDueRepository {
        private val reservations = mutableSetOf<Pair<Instant, Int>>()

        /** Marcos reservados, de qualquer atraso. */
        val claimed get() = reservations.map { it.second }.toSet()

        override fun pastDue(now: Instant) = subscriptions.toList()

        override fun overdueInvoice(subscription: PastDueSubscription) = overdue

        override fun claim(subscription: PastDueSubscription, due: List<Int>): Boolean {
            val fresh = due.filter { reservations.add(subscription.since to it) }
            return due.max() in fresh
        }

        override fun release(subscription: PastDueSubscription, milestone: Int) {
            reservations.remove(subscription.since to milestone)
        }
    }

    private fun fakeGateway(): AsaasGateway = Proxy.newProxyInstance(
        AsaasGateway::class.java.classLoader, arrayOf(AsaasGateway::class.java),
    ) { _, method, _ -> if (method.name == "findPayment") payment else error("não usado: ${method.name}") } as AsaasGateway
}
