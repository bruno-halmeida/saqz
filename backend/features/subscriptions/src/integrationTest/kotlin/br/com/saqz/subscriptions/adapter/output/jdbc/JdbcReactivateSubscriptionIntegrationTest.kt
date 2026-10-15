package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.application.AsaasGateway
import br.com.saqz.subscriptions.application.CreditCardDetails
import br.com.saqz.subscriptions.application.CreditCardHolderInfo
import br.com.saqz.subscriptions.application.ReactivateSubscription
import br.com.saqz.subscriptions.application.ReactivateSubscriptionResult
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcReactivateSubscriptionIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var repository: JdbcSubscriptionRepository
    private val ownerId = UUID.randomUUID()
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val periodEnd = Instant.parse("2026-11-12T12:00:00Z")

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
        jdbc = JdbcClient.create(dataSource)
        repository = JdbcSubscriptionRepository(dataSource)
        jdbc.sql(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES (:id, :subject, true, 'Owner', now(), now())",
        ).param("id", ownerId).param("subject", "subject-$ownerId").update()
    }

    private fun useCase(gateway: Gateway, tokens: JdbcCreditCardTokenStore) = ReactivateSubscription(
        repository, gateway, JdbcSubscriptionsTransactionRunner(dataSource), Clock.fixed(now, ZoneOffset.UTC),
        cardTokens = tokens,
    )

    private fun insertCanceled(billingType: AsaasBillingType) = repository.insert(
        Subscription(
            ownerUserId = ownerId, plan = Plan.TITULAR, cycle = SubscriptionCycle.MONTHLY,
            asaasCustomerId = "cus_1", asaasSubscriptionId = "sub_old", billingType = billingType,
            currentPeriodEnd = periodEnd, status = SubscriptionStatus.CANCELED,
            canceledAt = now.minusSeconds(3_600), firstConfirmedAt = now.minusSeconds(86_400),
        ),
    )

    @Test
    fun `reactivation swaps the asaas id, clears the cancellation and keeps the saved card token`() {
        insertCanceled(AsaasBillingType.CREDIT_CARD)
        val tokens = JdbcCreditCardTokenStore(dataSource)
        tokens.save("sub_old", "tok_123", "1111", "VISA")
        val gateway = Gateway()

        val result = useCase(gateway, tokens).execute(ownerId, "203.0.113.9")

        assertIs<ReactivateSubscriptionResult.Success>(result)
        val saved = repository.findByOwnerUserId(ownerId)!!
        assertEquals("sub_new", saved.asaasSubscriptionId)
        assertEquals(SubscriptionStatus.ACTIVE, saved.status)
        assertNull(saved.canceledAt)
        assertEquals(periodEnd, saved.currentPeriodEnd)
        assertEquals("tok_123", tokens.tokenFor("sub_new"))
        assertNull(tokens.tokenFor("sub_old"))
        assertEquals("tok_123", gateway.token)
        assertEquals(LocalDate.parse("2026-11-12"), gateway.dueDate)
    }

    @Test
    fun `a second call after reactivating is refused and leaves one asaas subscription`() {
        insertCanceled(AsaasBillingType.PIX)
        val tokens = JdbcCreditCardTokenStore(dataSource)
        val gateway = Gateway()
        val reactivate = useCase(gateway, tokens)

        assertIs<ReactivateSubscriptionResult.Success>(reactivate.execute(ownerId, null))
        assertEquals(ReactivateSubscriptionResult.NotCanceled, reactivate.execute(ownerId, null))

        assertEquals(1, gateway.calls)
        assertEquals("sub_new", repository.findByOwnerUserId(ownerId)!!.asaasSubscriptionId)
    }

    private class Gateway : AsaasGateway {
        var calls = 0
        var token: String? = null
        var dueDate: LocalDate? = null
        override fun createSubscriptionStartingAt(
            asaasCustomerId: String, plan: Plan, cycle: SubscriptionCycle, valueCents: Long,
            billingType: AsaasBillingType, nextDueDate: LocalDate, idempotencyKey: String,
            creditCardToken: String?, remoteIp: String?,
        ): String {
            calls++
            token = creditCardToken
            dueDate = nextDueDate
            return "sub_new"
        }
        override fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String) = error("unused")
        override fun createSubscription(
            asaasCustomerId: String, plan: Plan, cycle: SubscriptionCycle, valueCents: Long,
            billingType: AsaasBillingType, idempotencyKey: String, creditCard: CreditCardDetails?,
            creditCardHolderInfo: CreditCardHolderInfo?, remoteIp: String?,
        ) = error("unused")
        override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) = error("unused")
        override fun cancelSubscription(asaasSubscriptionId: String) = error("unused")
        override fun createOneOffCharge(
            asaasCustomerId: String, valueCents: Long, description: String, idempotencyKey: String,
        ) = error("unused")
        override fun regeneratePixPayload(asaasChargeId: String) = error("unused")
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = null
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = null
        override fun findPayment(asaasPaymentId: String) = null
    }
}
