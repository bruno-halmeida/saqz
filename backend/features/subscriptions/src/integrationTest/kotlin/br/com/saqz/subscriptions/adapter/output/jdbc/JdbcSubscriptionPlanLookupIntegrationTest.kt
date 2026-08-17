package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSubscriptionPlanLookupIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var subscriptions: JdbcSubscriptionRepository
    private lateinit var lookup: JdbcSubscriptionPlanLookup
    private val ownerId = UUID.randomUUID()

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
        jdbc = JdbcClient.create(dataSource)
        subscriptions = JdbcSubscriptionRepository(dataSource)
        lookup = JdbcSubscriptionPlanLookup(dataSource)
        jdbc.sql(
            """
            INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at)
            VALUES (:id, :subject, true, 'Owner', now(), now())
            """.trimIndent(),
        )
            .param("id", ownerId)
            .param("subject", "subject-$ownerId")
            .update()
    }

    @Test
    fun `an active subscription is entitling`() {
        subscriptions.insert(subscription(status = SubscriptionStatus.ACTIVE, firstConfirmedAt = Instant.now()))

        assertEquals(Plan.TITULAR, lookup.findEntitlingPlan(ownerId)?.plan)
    }

    @Test
    fun `a canceled subscription that was never paid is not entitling even within the paid period`() {
        subscriptions.insert(
            subscription(
                status = SubscriptionStatus.CANCELED,
                firstConfirmedAt = null,
                currentPeriodEnd = Instant.now().plusSeconds(86_400),
            ),
        )

        assertNull(lookup.findEntitlingPlan(ownerId))
    }

    @Test
    fun `a canceled subscription that was paid stays entitling until the period ends`() {
        subscriptions.insert(
            subscription(
                status = SubscriptionStatus.CANCELED,
                firstConfirmedAt = Instant.now().minusSeconds(3_600),
                currentPeriodEnd = Instant.now().plusSeconds(86_400),
            ),
        )

        assertEquals(Plan.TITULAR, lookup.findEntitlingPlan(ownerId)?.plan)
    }

    @Test
    fun `a canceled subscription past its period end is not entitling`() {
        subscriptions.insert(
            subscription(
                status = SubscriptionStatus.CANCELED,
                firstConfirmedAt = Instant.now().minusSeconds(86_400),
                currentPeriodEnd = Instant.now().minusSeconds(3_600),
            ),
        )

        assertNull(lookup.findEntitlingPlan(ownerId))
    }

    @Test
    fun `a past due subscription that was never paid is not entitling`() {
        subscriptions.insert(subscription(status = SubscriptionStatus.PAST_DUE, firstConfirmedAt = null))

        assertNull(lookup.findEntitlingPlan(ownerId))
    }

    @Test
    fun `repository round-trips native enums including null billing and pending plans`() {
        val stored = subscription(
            status = SubscriptionStatus.ACTIVE,
            firstConfirmedAt = Instant.now(),
            pendingPlan = Plan.ORGANIZADOR,
            billingType = null,
        )
        subscriptions.insert(stored)

        val found = checkNotNull(subscriptions.findByOwnerUserId(ownerId))
        assertEquals(Plan.TITULAR, found.plan)
        assertEquals(SubscriptionCycle.MONTHLY, found.cycle)
        assertEquals(SubscriptionStatus.ACTIVE, found.status)
        assertNull(found.billingType)
        assertEquals(Plan.ORGANIZADOR, found.pendingPlan)

        val updated = found.copy(
            plan = Plan.ILIMITADO,
            cycle = SubscriptionCycle.ANNUAL,
            status = SubscriptionStatus.PAST_DUE,
            billingType = AsaasBillingType.CREDIT_CARD,
            pendingPlan = null,
            pendingUpgradePlan = Plan.TITULAR,
        )
        subscriptions.save(updated)

        val reloaded = checkNotNull(subscriptions.findByOwnerUserId(ownerId))
        assertEquals(Plan.ILIMITADO, reloaded.plan)
        assertEquals(SubscriptionCycle.ANNUAL, reloaded.cycle)
        assertEquals(SubscriptionStatus.PAST_DUE, reloaded.status)
        assertEquals(AsaasBillingType.CREDIT_CARD, reloaded.billingType)
        assertNull(reloaded.pendingPlan)
        assertEquals(Plan.TITULAR, reloaded.pendingUpgradePlan)
    }

    private fun subscription(
        status: SubscriptionStatus,
        firstConfirmedAt: Instant?,
        currentPeriodEnd: Instant = Instant.now().plusSeconds(86_400),
        pendingPlan: Plan? = null,
        billingType: AsaasBillingType? = AsaasBillingType.PIX,
    ) = Subscription(
        ownerUserId = ownerId,
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_1",
        asaasSubscriptionId = "sub_${UUID.randomUUID()}",
        billingType = billingType,
        currentPeriodEnd = currentPeriodEnd,
        status = status,
        pendingPlan = pendingPlan,
        firstConfirmedAt = firstConfirmedAt,
    )
}
