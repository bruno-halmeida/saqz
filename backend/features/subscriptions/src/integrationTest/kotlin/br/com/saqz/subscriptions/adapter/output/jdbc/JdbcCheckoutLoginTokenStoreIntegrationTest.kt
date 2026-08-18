package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.CheckoutLoginCode
import br.com.saqz.subscriptions.application.CheckoutLoginDigest
import br.com.saqz.subscriptions.application.CheckoutLoginSecret
import br.com.saqz.subscriptions.application.CheckoutLoginSecrets
import br.com.saqz.subscriptions.application.RedeemCheckoutLogin
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.time.Instant
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcCheckoutLoginTokenStoreIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var secrets: ScriptedCheckoutLoginSecrets
    private lateinit var store: JdbcCheckoutLoginTokenStore
    private val now = Instant.parse("2026-08-18T12:00:00Z")

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
        secrets = ScriptedCheckoutLoginSecrets()
        store = JdbcCheckoutLoginTokenStore(dataSource, secrets)
    }

    @Test
    fun `issue persists only the digest and returns the raw code`() {
        val userId = user()
        val raw = store.issue(userId, now)
        val code = checkNotNull(CheckoutLoginCode.from(raw))

        assertEquals(secrets.last.code.value, raw)
        assertEquals(1, count("subscription_checkout_login_tokens WHERE user_id = '$userId'"))
        assertContentEquals(
            CheckoutLoginDigest.sha256(code).toByteArray(),
            bytes("SELECT token_digest FROM subscription_checkout_login_tokens WHERE user_id = '$userId'"),
        )
        assertNull(queryTimestamp("SELECT consumed_at FROM subscription_checkout_login_tokens WHERE user_id = '$userId'"))
        assertEquals(
            now.plus(RedeemCheckoutLogin.TOKEN_TTL),
            queryTimestamp("SELECT expires_at FROM subscription_checkout_login_tokens WHERE user_id = '$userId'"),
        )
        assertFalse(raw.contains("token", ignoreCase = true))
    }

    @Test
    fun `a later issue invalidates the previous unused code`() {
        val userId = user()
        val first = store.issue(userId, now)
        val second = store.issue(userId, now.plusSeconds(5))

        assertNotEquals(first, second)
        assertNull(store.findOpen(first, now.plusSeconds(6)))
        assertEquals(userId, store.findOpen(second, now.plusSeconds(6))?.ownerUserId)
        assertEquals(2, count("subscription_checkout_login_tokens WHERE user_id = '$userId'"))
        assertEquals(
            1,
            count("subscription_checkout_login_tokens WHERE user_id = '$userId' AND consumed_at IS NULL"),
        )
    }

    @Test
    fun `findOpen ignores expired consumed and malformed codes`() {
        val userId = user()
        val raw = store.issue(userId, now)

        assertEquals(userId, store.findOpen(raw, now.plusSeconds(1))?.ownerUserId)
        assertNull(store.findOpen(raw, now.plus(RedeemCheckoutLogin.TOKEN_TTL).plusSeconds(1)))
        assertNull(store.findOpen("not-a-code", now))
        assertNull(store.findOpen("A".repeat(43), now))

        val other = store.issue(user(), now)
        val open = checkNotNull(store.findOpen(other, now.plusSeconds(1)))
        assertTrue(store.consume(open.id, now.plusSeconds(2)))
        assertNull(store.findOpen(other, now.plusSeconds(3)))
    }

    @Test
    fun `consume is a compare-and-set so a second call fails`() {
        val userId = user()
        val raw = store.issue(userId, now)
        val open = checkNotNull(store.findOpen(raw, now.plusSeconds(1)))

        assertTrue(store.consume(open.id, now.plusSeconds(2)))
        assertFalse(store.consume(open.id, now.plusSeconds(3)))
        assertNull(store.findOpen(raw, now.plusSeconds(4)))
    }

    private fun user(): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', 'checkout-${UUID.randomUUID()}', 'owner-${id}@example.test', true, 'Owner', '$now', '$now')",
        )
        return id
    }

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

    private fun queryTimestamp(sql: String): Instant? = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                assertTrue(result.next())
                result.getTimestamp(1)?.toInstant()
            }
        }
    }

    private fun bytes(sql: String): ByteArray = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                assertTrue(result.next())
                result.getBytes(1)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection

    private class ScriptedCheckoutLoginSecrets : CheckoutLoginSecrets {
        private var seed = 1
        lateinit var last: CheckoutLoginSecret

        override fun next(): CheckoutLoginSecret {
            val entropy = ByteArray(32) { seed.toByte() }
            seed += 1
            val code = checkNotNull(
                CheckoutLoginCode.from(
                    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(entropy),
                ),
            )
            last = CheckoutLoginSecret(code, CheckoutLoginDigest.sha256(code))
            return last
        }
    }
}
