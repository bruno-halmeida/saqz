package br.com.saqz.subscriptions.application

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Asaas falso e repositório do site em memória, com o [CancelUnpaidWebCheckout] montado sobre eles. */
internal class UnpaidCheckoutWorld(val owner: UUID, val now: Instant) {
    val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    val canceledSubscriptions = mutableListOf<String>()
    val canceledPayments = mutableListOf<String>()
    var paymentStatus: String? = "PENDING"
    var failCancelSubscription = false
    var failCancelPayment = false
    var gatewayAvailable = true

    val asaas: AsaasGateway = Proxy.newProxyInstance(
        AsaasGateway::class.java.classLoader, arrayOf(AsaasGateway::class.java),
    ) { _, method, args ->
        when (method.name) {
            "findLatestPaymentIdForSubscription" -> "pay_1"
            "findPayment" -> AsaasPaymentSnapshot("pay_1", paymentStatus, "https://www.asaas.com/i/1")
            "cancelSubscription" -> {
                if (failCancelSubscription) throw IllegalStateException("Asaas fora do ar")
                canceledSubscriptions += args[0] as String
                Unit
            }
            "cancelPayment" -> {
                if (failCancelPayment) throw IllegalStateException("Asaas fora do ar")
                canceledPayments += args[0] as String
                Unit
            }
            else -> error("não usado: ${method.name}")
        }
    } as AsaasGateway

    private val rows = linkedMapOf<UUID, Subscription>()
    val repository = object : SubscriptionRepository {
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) = rows.values.firstOrNull { it.asaasSubscriptionId == asaasSubscriptionId }
        override fun findByOwnerUserId(ownerUserId: UUID) = rows[ownerUserId]
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID) = rows[ownerUserId]
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun findByLastConfirmedPaymentId(paymentId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = save(subscription)
        override fun save(subscription: Subscription) {
            rows[subscription.ownerUserId] = subscription
        }
    }

    val cancel = CancelUnpaidWebCheckout(repository, { asaas.takeIf { gatewayAvailable } }, InlineTransactions, clock)

    fun row() = rows[owner]

    fun siteCheckout(firstConfirmedAt: Instant? = null, canceledAt: Instant? = null, status: SubscriptionStatus = SubscriptionStatus.PAST_DUE) {
        rows[owner] = Subscription(
            ownerUserId = owner, plan = Plan.ORGANIZADOR, cycle = SubscriptionCycle.MONTHLY,
            asaasCustomerId = "cus_1", asaasSubscriptionId = "sub_1", billingType = AsaasBillingType.PIX,
            currentPeriodEnd = now.plusSeconds(30L * 24 * 3600), status = status,
            pastDueSince = now.minusSeconds(3600), firstConfirmedAt = firstConfirmedAt, canceledAt = canceledAt,
        )
    }
}

class CancelUnpaidWebCheckoutTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val world = UnpaidCheckoutWorld(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), now)

    @Test
    fun `checkout nunca pago e cancelado no Asaas junto com a cobranca pendente e a linha fica cancelada`() {
        world.siteCheckout()

        world.cancel.execute(world.owner)

        assertEquals(listOf("sub_1"), world.canceledSubscriptions)
        assertEquals(listOf("pay_1"), world.canceledPayments)
        val row = assertNotNull(world.row())
        assertEquals(SubscriptionStatus.CANCELED, row.status)
        assertEquals(now, row.canceledAt)
        assertNull(row.firstConfirmedAt)
    }

    @Test
    fun `assinatura do site que ja foi paga alguma vez nao e tocada`() {
        world.siteCheckout(firstConfirmedAt = now.minusSeconds(86_400))
        val before = world.row()

        world.cancel.execute(world.owner)

        assertEquals(before, world.row())
        assertTrue(world.canceledSubscriptions.isEmpty() && world.canceledPayments.isEmpty())
    }

    @Test
    fun `sem assinatura do site ou ja cancelada nao faz nada`() {
        world.cancel.execute(world.owner)
        assertTrue(world.canceledSubscriptions.isEmpty())

        world.siteCheckout(canceledAt = now.minusSeconds(60), status = SubscriptionStatus.CANCELED)
        world.cancel.execute(world.owner)
        assertTrue(world.canceledSubscriptions.isEmpty())
    }

    @Test
    fun `repetir nao cancela duas vezes`() {
        world.siteCheckout()

        repeat(3) { world.cancel.execute(world.owner) }

        assertEquals(listOf("sub_1"), world.canceledSubscriptions)
        assertEquals(listOf("pay_1"), world.canceledPayments)
    }

    @Test
    fun `cobranca que o Asaas ja marcou como paga nao e cancelada`() {
        for (status in listOf("RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH")) {
            world.paymentStatus = status
            world.siteCheckout()

            world.cancel.execute(world.owner)

            assertEquals(SubscriptionStatus.PAST_DUE, world.row()?.status, status)
        }
        assertTrue(world.canceledSubscriptions.isEmpty())
    }

    @Test
    fun `falha do Asaas nao propaga, deixa a linha como estava e a proxima validacao tenta de novo`() {
        world.siteCheckout()
        world.failCancelSubscription = true

        world.cancel.execute(world.owner)

        assertEquals(SubscriptionStatus.PAST_DUE, world.row()?.status)
        assertNull(world.row()?.canceledAt)

        world.failCancelSubscription = false
        world.cancel.execute(world.owner)
        assertEquals(SubscriptionStatus.CANCELED, world.row()?.status)
    }

    @Test
    fun `falha ao cancelar so a cobranca pendente nao desfaz o cancelamento da assinatura`() {
        world.siteCheckout()
        world.failCancelPayment = true

        world.cancel.execute(world.owner)

        assertEquals(SubscriptionStatus.CANCELED, world.row()?.status)
        assertEquals(listOf("sub_1"), world.canceledSubscriptions)
    }

    @Test
    fun `sem o Asaas configurado nada muda e nada quebra`() {
        world.siteCheckout()
        world.gatewayAvailable = false

        world.cancel.execute(world.owner)

        assertEquals(SubscriptionStatus.PAST_DUE, world.row()?.status)
    }
}
