package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.application.AppStoreNotification
import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.domain.AppStoreEnvironment
import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.AppStoreRenewalInfo
import br.com.saqz.subscriptions.domain.AppStoreSubscription
import br.com.saqz.subscriptions.domain.AppStoreTransaction
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
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAppStoreSubscriptionRepositoryIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var repository: JdbcAppStoreSubscriptionRepository
    private val ownerId = UUID.randomUUID()

    // Postgres guarda microssegundos; Instant.now() no macOS tem nanos.
    private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
        jdbc = JdbcClient.create(dataSource)
        repository = JdbcAppStoreSubscriptionRepository(dataSource)
        jdbc.sql(
            """
            INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at)
            VALUES (:id, :subject, true, 'Owner', now(), now())
            """.trimIndent(),
        ).param("id", ownerId).param("subject", "subject-$ownerId").update()
    }

    @Test
    fun `a subscription round trips with its renewal state`() {
        val stored = subscription().applying(
            AppStoreRenewalInfo(
                originalTransactionId = "1000",
                autoRenew = true,
                autoRenewProductId = AppStoreProduct.TITULAR_ANUAL.productId,
                inBillingRetry = true,
                gracePeriodExpiresAt = now.plus(Duration.ofDays(3)),
                signedAt = now,
            ),
        )
        repository.insertIfAbsent(subscription())
        repository.save(stored)

        assertEquals(stored, repository.findForUpdate("1000"))
        assertEquals(listOf(stored), repository.findByOwner(ownerId))
    }

    @Test
    fun `inserting an existing original transaction keeps the stored row`() {
        repository.insertIfAbsent(subscription())
        repository.insertIfAbsent(subscription(product = AppStoreProduct.ILIMITADO_ANUAL))

        assertEquals(AppStoreProduct.ORGANIZADOR_MENSAL, repository.findForUpdate("1000")?.product)
    }

    @Test
    fun `receipts list paid transactions newest first and drop refunds`() {
        repository.insertIfAbsent(subscription())
        repository.recordTransaction(transaction("1000", now.minus(Duration.ofDays(30))), ownerId)
        repository.recordTransaction(transaction("1001", now), ownerId)
        repository.recordTransaction(transaction("1001", now, revocationDate = now), ownerId)
        repository.recordTransaction(transaction("1000", now.minus(Duration.ofDays(30))), ownerId)

        val receipts = repository.listTransactionsForOwner(ownerId, limit = 10)

        assertEquals(listOf("1000"), receipts.map { it.transactionId })
        assertEquals(59_900L, receipts.single().priceMillis)
    }

    @Test
    fun `owner existence follows access users`() {
        assertTrue(repository.ownerExists(ownerId))
        assertFalse(repository.ownerExists(UUID.randomUUID()))
    }

    @Test
    fun `a notification is recorded once`() {
        val store = JdbcAppStoreNotificationStore(dataSource)
        val notification = AppStoreNotification(
            notificationUuid = UUID.randomUUID(),
            type = "TEST",
            subtype = null,
            environment = AppStoreEnvironment.SANDBOX,
            signedAt = now,
            transaction = null,
            renewalInfo = null,
        )

        assertTrue(store.recordIfNew(notification))
        assertFalse(store.recordIfNew(notification))
    }

    @Test
    fun `an App Store subscription grants the plan and wins over a smaller web plan`() {
        val lookup = JdbcSubscriptionPlanLookup(dataSource)
        JdbcSubscriptionRepository(dataSource).insert(webSubscription(Plan.TITULAR))
        repository.insertIfAbsent(subscription())

        assertEquals(Plan.ORGANIZADOR, lookup.findEntitlingPlan(ownerId)?.plan)
    }

    @Test
    fun `expired or refunded App Store subscriptions grant nothing, billing grace still does`() {
        val lookup = JdbcSubscriptionPlanLookup(dataSource)
        repository.insertIfAbsent(subscription(expiresAt = now.minusSeconds(60)))
        assertNull(lookup.findEntitlingPlan(ownerId))

        repository.save(
            subscription(expiresAt = now.minusSeconds(60)).copy(gracePeriodExpiresAt = now.plus(Duration.ofDays(2))),
        )
        assertEquals(Plan.ORGANIZADOR, lookup.findEntitlingPlan(ownerId)?.plan)

        repository.save(subscription().copy(revokedAt = now.minusSeconds(1)))
        assertNull(lookup.findEntitlingPlan(ownerId))
    }

    @Test
    fun `a downgrade scheduled in the App Store is the pending plan`() {
        repository.insertIfAbsent(subscription())
        repository.save(subscription().copy(autoRenew = true, autoRenewProductId = AppStoreProduct.TITULAR_MENSAL.productId))

        val entitling = JdbcSubscriptionPlanLookup(dataSource).findEntitlingPlan(ownerId)

        assertEquals(Plan.TITULAR, entitling?.pendingPlan)
    }

    @Test
    fun `any App Store charge counts as paid history for the trial`() {
        val history = JdbcPaidSubscriptionHistory(dataSource)
        assertFalse(history.hasPaidHistory(ownerId))

        repository.insertIfAbsent(subscription())
        repository.recordTransaction(transaction("1000", now), ownerId)

        assertTrue(history.hasPaidHistory(ownerId))
    }

    private fun subscription(
        product: AppStoreProduct = AppStoreProduct.ORGANIZADOR_MENSAL,
        expiresAt: Instant = now.plus(Duration.ofDays(30)),
    ) = AppStoreSubscription.startedBy(
        transaction("1000", expiresAt.minus(Duration.ofDays(30)), expiresAt = expiresAt, product = product),
        product,
        ownerId,
    )

    private fun transaction(
        transactionId: String,
        purchaseDate: Instant,
        expiresAt: Instant = purchaseDate.plus(Duration.ofDays(30)),
        revocationDate: Instant? = null,
        product: AppStoreProduct = AppStoreProduct.ORGANIZADOR_MENSAL,
    ) = AppStoreTransaction(
        transactionId = transactionId,
        originalTransactionId = "1000",
        productId = product.productId,
        purchaseDate = purchaseDate,
        expiresDate = expiresAt,
        revocationDate = revocationDate,
        appAccountToken = ownerId,
        environment = AppStoreEnvironment.SANDBOX,
        priceMillis = 59_900,
        currency = "BRL",
        signedAt = purchaseDate,
    )

    private fun webSubscription(plan: Plan) = Subscription(
        ownerUserId = ownerId,
        plan = plan,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_1",
        asaasSubscriptionId = "sub_1",
        billingType = AsaasBillingType.PIX,
        currentPeriodEnd = now.plus(Duration.ofDays(10)),
        status = SubscriptionStatus.ACTIVE,
        firstConfirmedAt = now.minus(Duration.ofDays(20)),
    )
}
