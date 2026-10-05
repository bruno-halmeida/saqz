package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.domain.GooglePlayProduct
import br.com.saqz.subscriptions.domain.GooglePlayState
import br.com.saqz.subscriptions.domain.GooglePlaySubscription
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcGooglePlaySubscriptionRepositoryIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcGooglePlaySubscriptionRepository
    private val ownerId = UUID.randomUUID()
    private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
        repository = JdbcGooglePlaySubscriptionRepository(dataSource)
        JdbcClient.create(dataSource).sql(
            """
            INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at)
            VALUES (:id, :subject, true, 'Owner', now(), now())
            """.trimIndent(),
        ).param("id", ownerId).param("subject", "subject-$ownerId").update()
    }

    @Test
    fun `a subscription round trips and keeps the first owner on conflicting inserts`() {
        val stored = subscription("tok1")
        repository.insertIfAbsent(stored)
        repository.insertIfAbsent(stored.copy(product = GooglePlayProduct.ILIMITADO_ANUAL))

        assertEquals(stored, repository.findForUpdate("tok1"))
        assertEquals(listOf(stored), repository.findByOwner(ownerId))
    }

    @Test
    fun `saving updates state, acknowledgement and supersession`() {
        repository.insertIfAbsent(subscription("tok1"))
        repository.save(subscription("tok1").copy(state = GooglePlayState.CANCELED, autoRenew = false, canceledAt = now))
        repository.markAcknowledged("tok1")
        repository.supersede("tok1", now)

        val row = assertNotNull(repository.findForUpdate("tok1"))
        assertEquals(GooglePlayState.CANCELED, row.state)
        assertTrue(row.acknowledged)
        assertEquals(now, row.supersededAt)
    }

    @Test
    fun `orders are recorded once and listed newest first`() {
        repository.insertIfAbsent(subscription("tok1", orderId = "GPA.1"))
        repository.recordOrder(subscription("tok1", orderId = "GPA.1"))
        repository.recordOrder(subscription("tok1", orderId = "GPA.1"))
        repository.recordOrder(subscription("tok1", orderId = "GPA.1..0"))

        assertEquals(setOf("GPA.1", "GPA.1..0"), repository.listOrdersForOwner(ownerId, 10).map { it.orderId }.toSet())
    }

    @Test
    fun `entitlement counts active, canceled in period and grace, never on hold or replaced`() {
        val lookup = JdbcSubscriptionPlanLookup(dataSource)
        repository.insertIfAbsent(subscription("tok1", state = GooglePlayState.ON_HOLD))
        assertNull(lookup.findEntitlingPlan(ownerId))

        repository.save(subscription("tok1", state = GooglePlayState.IN_GRACE_PERIOD))
        assertEquals(Plan.ORGANIZADOR, lookup.findEntitlingPlan(ownerId)?.plan)

        repository.save(subscription("tok1", state = GooglePlayState.CANCELED))
        assertEquals(Plan.ORGANIZADOR, lookup.findEntitlingPlan(ownerId)?.plan)

        repository.supersede("tok1", now)
        assertNull(lookup.findEntitlingPlan(ownerId))
    }

    @Test
    fun `an expired subscription grants nothing`() {
        repository.insertIfAbsent(subscription("tok1", expiresAt = now.minusSeconds(60)))

        assertNull(JdbcSubscriptionPlanLookup(dataSource).findEntitlingPlan(ownerId))
    }

    @Test
    fun `a Play order counts as paid history and a notification is recorded once`() {
        val history = JdbcPaidSubscriptionHistory(dataSource)
        assertFalse(history.hasPaidHistory(ownerId))
        repository.insertIfAbsent(subscription("tok1"))
        repository.recordOrder(subscription("tok1"))
        assertTrue(history.hasPaidHistory(ownerId))

        val notifications = JdbcGooglePlayNotificationStore(dataSource)
        assertTrue(notifications.recordIfNew("m1", 4, "tok1"))
        assertFalse(notifications.recordIfNew("m1", 4, "tok1"))
    }

    private fun subscription(
        token: String,
        state: GooglePlayState = GooglePlayState.ACTIVE,
        expiresAt: Instant = now.plus(Duration.ofDays(30)),
        orderId: String = "GPA.1",
    ) = GooglePlaySubscription(
        purchaseToken = token,
        ownerUserId = ownerId,
        product = GooglePlayProduct.ORGANIZADOR_MENSAL,
        state = state,
        expiresAt = expiresAt,
        autoRenew = true,
        canceledAt = null,
        latestOrderId = orderId,
        linkedPurchaseToken = null,
        acknowledged = false,
        testPurchase = true,
    )
}
