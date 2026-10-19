package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.application.AsaasGateway
import br.com.saqz.subscriptions.application.AsaasPaymentSnapshot
import br.com.saqz.subscriptions.application.CancelUnpaidWebCheckout
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** O cancelamento do checkout nunca pago contra o Postgres de verdade (gateway do Asaas falso). */
class JdbcCancelUnpaidWebCheckoutIntegrationTest {
    private val source = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val subscriptions = JdbcSubscriptionRepository(source)
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val canceled = mutableListOf<String>()
    private var failing = false
    private val gateway: AsaasGateway = Proxy.newProxyInstance(
        AsaasGateway::class.java.classLoader, arrayOf(AsaasGateway::class.java),
    ) { _, method, args ->
        when (method.name) {
            "findLatestPaymentIdForSubscription" -> "pay_1"
            "findPayment" -> AsaasPaymentSnapshot("pay_1", "PENDING", null)
            "cancelSubscription" -> {
                if (failing) error("Asaas fora do ar")
                canceled += args[0] as String
                Unit
            }
            "cancelPayment" -> Unit
            else -> error("não usado: ${method.name}")
        }
    } as AsaasGateway
    private val cancel = CancelUnpaidWebCheckout(
        subscriptions, { gateway }, JdbcSubscriptionsTransactionRunner(source), Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `cancela so o checkout nunca pago, grava a linha cancelada e repetir nao cancela de novo`() {
        val unpaid = site(firstConfirmedAt = null)
        val paid = site(firstConfirmedAt = now.minus(Duration.ofDays(30)))

        repeat(2) { cancel.execute(unpaid) }
        cancel.execute(paid)

        val row = assertNotNull(subscriptions.findByOwnerUserId(unpaid))
        assertEquals(SubscriptionStatus.CANCELED, row.status)
        assertEquals(now, row.canceledAt)
        assertEquals(listOf("sub_$unpaid"), canceled)
        val untouched = assertNotNull(subscriptions.findByOwnerUserId(paid))
        assertEquals(SubscriptionStatus.PAST_DUE, untouched.status)
        assertNull(untouched.canceledAt)
    }

    @Test
    fun `falha do Asaas deixa a linha como estava no banco`() {
        val unpaid = site(firstConfirmedAt = null)
        failing = true

        cancel.execute(unpaid)

        val row = assertNotNull(subscriptions.findByOwnerUserId(unpaid))
        assertEquals(SubscriptionStatus.PAST_DUE, row.status)
        assertNull(row.canceledAt)
    }

    private fun site(firstConfirmedAt: Instant?): UUID = UUID.randomUUID().also { owner ->
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, 'Owner', now(), now())")
            .param("id", owner).param("subject", owner.toString()).update()
        subscriptions.insert(
            Subscription(
                ownerUserId = owner, plan = Plan.ORGANIZADOR, cycle = SubscriptionCycle.MONTHLY, asaasCustomerId = "cus_1",
                asaasSubscriptionId = "sub_$owner", billingType = AsaasBillingType.PIX, currentPeriodEnd = now.plus(Duration.ofDays(20)),
                status = SubscriptionStatus.PAST_DUE, pastDueSince = now.minusSeconds(3600), firstConfirmedAt = firstConfirmedAt,
            ),
        )
    }
}
