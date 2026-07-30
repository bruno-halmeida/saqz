package br.com.saqz.subscriptions.adapter.output.jdbc

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
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAsaasIdempotencyStoreIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var store: JdbcAsaasIdempotencyStore

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    @BeforeEach
    fun resetDatabase() {
        flyway().clean()
        flyway().migrate()
        store = JdbcAsaasIdempotencyStore(dataSource)
    }

    @AfterAll
    fun stopDatabase() = postgres.stop()

    @Test
    fun `tryBegin wins once then complete stores resource id`() {
        val now = Instant.parse("2026-07-30T12:00:00Z")
        assertTrue(store.tryBegin("key-1", now))
        assertFalse(store.tryBegin("key-1", now))
        assertNull(store.find("key-1")?.resourceId)
        assertEquals(now, store.find("key-1")?.createdAt)

        store.complete("key-1", "sub_ABC")
        assertEquals("sub_ABC", store.find("key-1")?.resourceId)
        assertFalse(store.tryBegin("key-1", now))
    }

    @Test
    fun `release removes unfinished reservation so retry can begin`() {
        val now = Instant.parse("2026-07-30T12:00:00Z")
        assertTrue(store.tryBegin("key-2", now))
        store.release("key-2")
        assertTrue(store.tryBegin("key-2", now))
        store.complete("key-2", "pay_1")
        store.release("key-2")
        assertEquals("pay_1", store.find("key-2")?.resourceId)
    }

    @Test
    fun `concurrent tryBegin allows only one winner`() {
        val winners = AtomicInteger(0)
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)
        val pool = Executors.newFixedThreadPool(8)
        repeat(8) {
            pool.execute {
                start.await()
                if (store.tryBegin("race-key", Instant.now())) {
                    winners.incrementAndGet()
                }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        pool.shutdown()
        assertEquals(1, winners.get())
    }

    private fun flyway(): Flyway = Flyway.configure()
        .dataSource(dataSource)
        .locations(*allSubscriptionsFeatureMigrationLocations())
        .cleanDisabled(false)
        .load()
}
