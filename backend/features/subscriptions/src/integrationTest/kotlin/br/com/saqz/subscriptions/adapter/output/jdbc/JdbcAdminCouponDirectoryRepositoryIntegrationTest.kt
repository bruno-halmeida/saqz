package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.AdminCouponCreateResult
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import br.com.saqz.subscriptions.testing.startAndAwaitJdbc
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAdminCouponDirectoryRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var repository: JdbcAdminCouponDirectoryRepository

    private val now: Instant = Instant.parse("2026-08-03T12:00:00Z")

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure()
            .dataSource(dataSource)
            .locations(*allSubscriptionsFeatureMigrationLocations())
            .load()
            .migrate()
        repository = JdbcAdminCouponDirectoryRepository(dataSource)
    }

    @AfterAll
    fun stopDatabase() {
        postgres.stop()
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE subscription_events, coupon_redemptions, subscriptions, coupons, access_users CASCADE")
    }

    @Test
    fun `cria lista com usos e assinaturas ativas e recusa codigo duplicado`() {
        val created = repository.create("GALERA10", 10, durationCycles = 6, validUntil = now.plusSeconds(86_400))
        assertIs<AdminCouponCreateResult.Created>(created)
        assertEquals(AdminCouponCreateResult.DuplicateCode, repository.create("GALERA10", 20, null, null))

        val couponId = created.coupon.id
        val comCupom = insertUser()
        val cupomEsgotado = insertUser()
        insertRedemption(couponId, comCupom)
        insertRedemption(couponId, cupomEsgotado)
        insertSubscription(comCupom, couponId, cyclesRemaining = 2)
        insertSubscription(cupomEsgotado, couponId, cyclesRemaining = 0)

        val list = repository.list()

        assertEquals(1, list.size)
        assertEquals("GALERA10", list.single().code)
        assertEquals(2, list.single().redemptions)
        assertEquals(1, list.single().activeSubscriptions)
    }

    @Test
    fun `desativar expira o cupom agora e preserva expiracao ja passada`() {
        val created = repository.create("SAQUE20", 20, null, validUntil = null) as AdminCouponCreateResult.Created

        assertTrue(repository.deactivate(created.coupon.id, now))
        assertEquals(now, repository.list().single().validUntil)

        // Já expirado antes: desativar de novo não empurra a validade para frente.
        assertTrue(repository.deactivate(created.coupon.id, now.plusSeconds(86_400)))
        assertEquals(now, repository.list().single().validUntil)

        assertFalse(repository.deactivate(UUID.randomUUID(), now))
    }

    private fun insertUser(): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', 'sub-$id', true, 'Nome Valido', now(), now())",
        )
        return id
    }

    private fun insertRedemption(couponId: UUID, userId: UUID) {
        execute("INSERT INTO coupon_redemptions (coupon_id, user_id, redeemed_at) VALUES ('$couponId', '$userId', now())")
    }

    private fun insertSubscription(ownerId: UUID, couponId: UUID, cyclesRemaining: Int) {
        execute(
            "INSERT INTO subscriptions (owner_user_id, plan, cycle, status, asaas_customer_id, " +
                "asaas_subscription_id, current_period_end, coupon_id, coupon_cycles_remaining, created_at, updated_at) " +
                "VALUES ('$ownerId', 'TITULAR', 'MONTHLY', 'ACTIVE', 'cus-$ownerId', 'sub-$ownerId', " +
                "'${now.plusSeconds(2_592_000)}', '$couponId', $cyclesRemaining, now(), now())",
        )
    }

    private fun execute(sql: String) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }
}
