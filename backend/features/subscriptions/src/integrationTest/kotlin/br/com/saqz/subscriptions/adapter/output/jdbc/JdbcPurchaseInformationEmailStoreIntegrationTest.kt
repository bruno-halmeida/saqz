package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.application.PurchaseInformationReservation
import br.com.saqz.subscriptions.application.PurchaseInformationReservationResult
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcPurchaseInformationEmailStoreIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var store: JdbcPurchaseInformationEmailStore
    private val now = Instant.parse("2026-08-10T12:00:00Z")

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
        store = JdbcPurchaseInformationEmailStore(dataSource)
    }

    @Test
    fun `findEmail reads only the active authoritative access user email`() {
        val active = user(email = "  exact@example.test  ")
        val noEmail = user(email = null)
        val deleted = user(email = "deleted@example.test")
        execute("UPDATE access_users SET deleted_at = '$now' WHERE id = '$deleted'")

        assertEquals("  exact@example.test  ", store.findEmail(active))
        assertNull(store.findEmail(noEmail))
        assertNull(store.findEmail(deleted))
        assertNull(store.findEmail(UUID.randomUUID()))
    }

    @Test
    fun `first reservation wins and a live second attempt returns exact stale deadline`() {
        val userId = user()
        val first = assertReserved(store.reserve(userId, now))

        val second = store.reserve(userId, now.plusSeconds(59))

        assertEquals(
            PurchaseInformationReservationResult.InProgress(retryAfterSeconds = 1),
            second,
        )
        assertEquals(first.token, reservationToken(userId))
    }

    @Test
    fun `eight concurrent reservations install exactly one UUID generation`() {
        val userId = user()
        val outcomes = inParallel(8) { store.reserve(userId, now) }

        assertEquals(1, outcomes.count { it is PurchaseInformationReservationResult.Reserved })
        assertEquals(7, outcomes.count { it is PurchaseInformationReservationResult.InProgress })
        assertEquals(1, count("subscription_purchase_information_emails"))
        assertEquals(1, count("subscription_purchase_information_emails WHERE user_id = '$userId'"))
        assertEquals(
            1,
            count("subscription_purchase_information_emails WHERE user_id = '$userId' AND reservation_id IS NOT NULL"),
        )
    }

    @Test
    fun `complete is compare and set, clears reservation, and records exactly one success`() {
        val userId = user()
        val reservation = assertReserved(store.reserve(userId, now))

        assertTrue(store.complete(reservation, now.plusSeconds(2)))
        assertFalse(store.complete(reservation, now.plusSeconds(3)))
        assertNull(reservationToken(userId))
        assertEquals(
            1,
            count("subscription_purchase_information_email_successes WHERE user_id = '$userId'"),
        )
        assertEquals(
            now.plusSeconds(2),
            successAt(userId),
        )
    }

    @Test
    fun `release is compare and clear, preserves history, and permits a retry`() {
        val userId = user()
        val reservation = assertReserved(store.reserve(userId, now))

        assertTrue(store.release(reservation))
        assertFalse(store.release(reservation))
        assertNull(reservationToken(userId))
        assertEquals(0, count("subscription_purchase_information_email_successes WHERE user_id = '$userId'"))
        assertReserved(store.reserve(userId, now.plusSeconds(1)))
    }

    @Test
    fun `wrong UUID cannot release or complete a live reservation`() {
        val userId = user()
        val reservation = assertReserved(store.reserve(userId, now))
        val wrong = PurchaseInformationReservation(userId, UUID.randomUUID().toString())

        assertFalse(store.release(wrong))
        assertFalse(store.complete(wrong, now.plusSeconds(1)))
        assertEquals(reservation.token, reservationToken(userId))
    }

    @Test
    fun `stale reservation is replaceable exactly at threshold but live one is not`() {
        val userId = user()
        val first = assertReserved(store.reserve(userId, now))

        assertEquals(
            PurchaseInformationReservationResult.InProgress(retryAfterSeconds = 1),
            store.reserve(userId, now.plusSeconds(59)),
        )
        val replacement = assertReserved(store.reserve(userId, now.plusSeconds(60)))
        assertNotEquals(first.token, replacement.token)
        assertEquals(replacement.token, reservationToken(userId))
    }

    @Test
    fun `ABA stale worker cannot clear or complete a replacement UUID`() {
        val userId = user()
        val stale = assertReserved(store.reserve(userId, now))
        val replacement = assertReserved(store.reserve(userId, now.plusSeconds(60)))

        assertFalse(store.release(stale))
        assertFalse(store.complete(stale, now.plusSeconds(61)))
        assertEquals(replacement.token, reservationToken(userId))
        assertTrue(store.complete(replacement, now.plusSeconds(61)))
    }

    @Test
    fun `dedupe is active before fifteen minutes and expires at the exact boundary`() {
        val userId = user()
        successful(userId, now)

        assertEquals(
            PurchaseInformationReservationResult.AlreadySent,
            store.reserve(userId, now.plus(Duration.ofMinutes(14).plusSeconds(59))),
        )
        assertReserved(store.reserve(userId, now.plus(Duration.ofMinutes(15))))
    }

    @Test
    fun `failed send release does not consume quota or dedupe history`() {
        val userId = user()
        val failed = assertReserved(store.reserve(userId, now))
        assertTrue(store.release(failed))

        val retry = assertReserved(store.reserve(userId, now.plusSeconds(1)))
        assertTrue(store.release(retry))
        assertEquals(0, count("subscription_purchase_information_email_successes WHERE user_id = '$userId'"))
    }

    @Test
    fun `rate limit returns exact retry and expires on the earliest success in the rolling window`() {
        val userId = user()
        successful(userId, now)
        successful(userId, now.plus(Duration.ofMinutes(30)))
        successful(userId, now.plus(Duration.ofMinutes(45)))

        // At 13:00 the first success has left the rolling window, so one new send is
        // allowed; the three active successes then include 12:30, 12:45 and 13:00.
        val afterEarliestExpiry = now.plus(Duration.ofHours(1))
        val fourth = assertReserved(store.reserve(userId, afterEarliestExpiry))
        assertTrue(store.complete(fourth, afterEarliestExpiry))
        // The third success's own dedupe period ends at 13:15. At that point all
        // three rolling successes (12:30, 12:45, 13:00) remain active.
        assertEquals(
            PurchaseInformationReservationResult.RateLimited(retryAfterSeconds = 900),
            store.reserve(userId, afterEarliestExpiry.plus(Duration.ofMinutes(15))),
        )
    }

    @Test
    fun `rolling window permits three successes but never a fourth while all three are active`() {
        val userId = user()
        val times = listOf(
            now,
            now.plus(Duration.ofMinutes(16)),
            now.plus(Duration.ofMinutes(32)),
        )
        times.forEach { successful(userId, it) }

        val blocked = store.reserve(userId, now.plus(Duration.ofMinutes(47)))
        assertEquals(
            PurchaseInformationReservationResult.RateLimited(retryAfterSeconds = 780),
            blocked,
        )
        assertEquals(3, count("subscription_purchase_information_email_successes WHERE user_id = '$userId'"))
    }

    @Test
    fun `different users have independent reservation rows`() {
        val first = user()
        val second = user()

        assertReserved(store.reserve(first, now))
        assertReserved(store.reserve(second, now))

        assertEquals(2, count("subscription_purchase_information_emails"))
        assertEquals(1, count("subscription_purchase_information_emails WHERE user_id = '$first'"))
        assertEquals(1, count("subscription_purchase_information_emails WHERE user_id = '$second'"))
    }

    private fun assertReserved(result: PurchaseInformationReservationResult) =
        assertIs<PurchaseInformationReservationResult.Reserved>(result).reservation

    private fun successful(userId: UUID, at: Instant): PurchaseInformationReservation {
        val reservation = assertReserved(store.reserve(userId, at))
        assertTrue(store.complete(reservation, at))
        return reservation
    }

    private fun user(email: String? = "owner-${UUID.randomUUID()}@example.test"): UUID {
        val id = UUID.randomUUID()
        val emailSql = email?.let { "'$it'" } ?: "NULL"
        execute(
            "INSERT INTO access_users (id, firebase_subject, email, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', 'purchase-${UUID.randomUUID()}', $emailSql, true, 'Purchase User', '$now', '$now')",
        )
        return id
    }

    private fun reservationToken(userId: UUID): String? = queryString(
        "SELECT reservation_id::text FROM subscription_purchase_information_emails WHERE user_id = '$userId'",
    )

    private fun successAt(userId: UUID): Instant? = queryTimestamp(
        "SELECT succeeded_at FROM subscription_purchase_information_email_successes WHERE user_id = '$userId'",
    )

    private fun count(tableExpression: String): Int = queryInt("SELECT count(*) FROM $tableExpression")

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun queryInt(sql: String): Int = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                assertTrue(result.next())
                result.getInt(1)
            }
        }
    }

    private fun queryString(sql: String): String? = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                assertTrue(result.next())
                result.getString(1)
            }
        }
    }

    private fun queryTimestamp(sql: String): Instant? = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                assertTrue(result.next())
                result.getTimestamp(1)?.toInstant()
            }
        }
    }

    private fun connection(): Connection = dataSource.connection

    private fun <T> inParallel(threads: Int, action: () -> T): List<T> {
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        return try {
            val futures = (0 until threads).map {
                pool.submit(
                    Callable {
                        ready.countDown()
                        assertTrue(start.await(10, TimeUnit.SECONDS))
                        action()
                    },
                )
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            futures.map { it.get(20, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
