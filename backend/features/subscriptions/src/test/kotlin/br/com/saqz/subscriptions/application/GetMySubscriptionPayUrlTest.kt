package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.OwnedGroupCounter
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Pagar agora" (payUrl) e "já pagou alguma vez" (paymentConfirmed) do `/subscriptions/me` do site. */
class GetMySubscriptionPayUrlTest {
    private val now = Instant.parse("2026-10-12T13:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val webhookLink = "https://www.asaas.com/i/from-webhook"
    private val currentLink = "https://www.asaas.com/i/current"

    private var overdue: OverdueInvoice? = OverdueInvoice("pay_1", webhookLink)
    private var payment: AsaasPaymentSnapshot? = AsaasPaymentSnapshot("pay_1", "OVERDUE", currentLink)
    private var lookups = 0
    private var repositoryFails = false

    private val pastDueRepository = object : SubscriptionPastDueRepository {
        override fun pastDue(now: Instant) = emptyList<PastDueSubscription>()
        override fun overdueInvoice(subscription: PastDueSubscription): OverdueInvoice? {
            lookups++
            if (repositoryFails) error("banco fora")
            return overdue
        }
        override fun claim(subscription: PastDueSubscription, due: List<Int>) = error("unused")
        override fun release(subscription: PastDueSubscription, milestone: Int) = error("unused")
    }

    private val gateway: AsaasGateway = Proxy.newProxyInstance(
        AsaasGateway::class.java.classLoader, arrayOf(AsaasGateway::class.java),
    ) { _, method, _ -> if (method.name == "findPayment") payment else error("não usado: ${method.name}") } as AsaasGateway

    private fun view(subscription: Subscription, resolver: Boolean = true): MySubscriptionView {
        val useCase = GetMySubscription(
            Memory(subscription), OwnedGroupCounter { 0 }, clock,
            overdueInvoices = if (resolver) OverdueInvoiceResolver(pastDueRepository) { gateway } else null,
        )
        return assertIs<GetMySubscriptionResult.Found>(useCase.execute(owner)).subscription
    }

    private fun pastDue(
        since: String = "2026-10-10T03:00:00Z",
        firstConfirmedAt: Instant? = Instant.parse("2026-09-10T03:00:00Z"),
        canceledAt: Instant? = null,
    ) = Subscription(
        ownerUserId = owner, plan = Plan.ORGANIZADOR, cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_1", asaasSubscriptionId = "sub_1", billingType = AsaasBillingType.PIX,
        currentPeriodEnd = Instant.parse("2026-10-10T03:00:00Z"), status = SubscriptionStatus.PAST_DUE,
        pastDueSince = Instant.parse(since), firstConfirmedAt = firstConfirmedAt, canceledAt = canceledAt,
    )

    @Test
    fun `site em atraso na carencia mostra a fatura vencida atual do Asaas`() {
        val found = view(pastDue())

        assertTrue(found.entitled)
        assertEquals(currentLink, found.payUrl)
        assertTrue(found.paymentConfirmed)
    }

    @Test
    fun `site congelado depois da carencia mantem o Pagar agora`() {
        val found = view(pastDue(since = "2026-09-20T03:00:00Z"))

        assertFalse(found.entitled)
        assertEquals(currentLink, found.payUrl)
    }

    @Test
    fun `sem o Asaas vale o link do webhook guardado`() {
        payment = null

        assertEquals(webhookLink, view(pastDue()).payUrl)
    }

    @Test
    fun `sem fatura vencida nao ha link`() {
        overdue = null
        assertNull(view(pastDue()).payUrl)

        overdue = OverdueInvoice("pay_1", webhookLink)
        payment = AsaasPaymentSnapshot("pay_1", "PENDING", currentLink)
        assertNull(view(pastDue()).payUrl)
    }

    @Test
    fun `cobranca ja paga no Asaas nao oferece pagar de novo`() {
        for (status in listOf("RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH")) {
            payment = AsaasPaymentSnapshot("pay_1", status, currentLink)
            assertNull(view(pastDue()).payUrl, status)
        }
    }

    @Test
    fun `checkout nunca pago nao tem link e diz que o pagamento nao foi confirmado`() {
        val found = view(pastDue(firstConfirmedAt = null))

        assertFalse(found.paymentConfirmed)
        assertNull(found.payUrl)
        assertEquals(0, lookups)
    }

    @Test
    fun `assinatura em dia ou cancelada nao consulta fatura`() {
        val active = pastDue().copy(status = SubscriptionStatus.ACTIVE, pastDueSince = null)
        assertNull(view(active).payUrl)
        val canceled = pastDue(canceledAt = Instant.parse("2026-10-11T03:00:00Z")).copy(status = SubscriptionStatus.CANCELED)
        assertNull(view(canceled).payUrl)
        assertEquals(0, lookups)
    }

    @Test
    fun `falha ao achar a fatura nao derruba o me`() {
        repositoryFails = true

        val found = view(pastDue())

        assertNull(found.payUrl)
        assertEquals(SubscriptionStatus.PAST_DUE, found.status)
    }

    @Test
    fun `sem o resolvedor configurado o campo fica vazio`() {
        assertNull(view(pastDue(), resolver = false).payUrl)
    }

    private class Memory(private val subscription: Subscription) : SubscriptionRepository {
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String): Subscription? = null
        override fun findByOwnerUserId(ownerUserId: UUID): Subscription? = subscription.takeIf { it.ownerUserId == ownerUserId }
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID): Subscription? = findByOwnerUserId(ownerUserId)
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun findByLastConfirmedPaymentId(paymentId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = error("unused")
        override fun save(subscription: Subscription) = error("unused")
    }
}
