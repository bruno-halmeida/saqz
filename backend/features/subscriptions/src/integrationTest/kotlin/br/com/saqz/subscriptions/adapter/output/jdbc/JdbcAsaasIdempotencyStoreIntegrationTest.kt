package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
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
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var store: JdbcAsaasIdempotencyStore

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
        store = JdbcAsaasIdempotencyStore(dataSource)
    }

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
        assertTrue(store.release("key-2", now))
        assertTrue(store.tryBegin("key-2", now))
        store.complete("key-2", "pay_1")
        assertFalse(store.release("key-2", now))
        assertEquals("pay_1", store.find("key-2")?.resourceId)
    }

    @Test
    fun `release is a compare-and-delete that no-ops when created_at does not match`() {
        val now = Instant.parse("2026-07-30T12:00:00Z")
        assertTrue(store.tryBegin("key-3", now))
        assertFalse(store.release("key-3", now.plusSeconds(1)))
        assertEquals(now, store.find("key-3")?.createdAt)
    }

    @Test
    fun `two workers recovering the same stale reservation - only the first release wins, second cannot clobber the recreated row`() {
        val staleCreatedAt = Instant.parse("2026-07-30T12:00:00Z")
        assertTrue(store.tryBegin("key-aba", staleCreatedAt))

        // Both workers inspect the same abandoned reservation and see the same created_at.
        val workerAInspected = store.find("key-aba")!!.createdAt
        val workerBInspected = store.find("key-aba")!!.createdAt
        assertEquals(workerAInspected, workerBInspected)

        // Worker A wins the release and recreates the reservation with a fresh created_at.
        assertTrue(store.release("key-aba", workerAInspected))
        val recreatedAt = Instant.parse("2026-07-30T12:00:31Z")
        assertTrue(store.tryBegin("key-aba", recreatedAt))

        // Worker B, unaware A already recovered, releases using the stale timestamp it inspected.
        assertFalse(store.release("key-aba", workerBInspected))

        // Worker A's fresh reservation must survive intact, not clobbered by worker B.
        val survivor = store.find("key-aba")
        assertEquals(recreatedAt, survivor?.createdAt)
        assertNull(survivor?.resourceId)

        // Worker B lost the race for good: it cannot win tryBegin either.
        assertFalse(store.tryBegin("key-aba", Instant.parse("2026-07-30T12:00:32Z")))
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
}
