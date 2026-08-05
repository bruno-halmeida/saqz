package br.com.saqz.subscriptions.adapter.output.jdbc.migration

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionsSchemaMigrationIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
    }

    @Test
    fun `coupon redemption is unique per coupon and user`() {
        val coupon = coupon("welcome-unique")
        val userId = user("firebase-uid-unique")
        redeem(coupon, userId)

        assertFailsWith<Exception> { redeem(coupon, userId) }
    }

    @Test
    fun `same user can redeem different coupons`() {
        val userId = user("firebase-uid-multi")
        val first = coupon("welcome-a")
        val second = coupon("welcome-b")

        redeem(first, userId)
        redeem(second, userId)

        assertEquals(2, int("SELECT count(*) FROM coupon_redemptions WHERE user_id = '$userId'"))
    }

    @Test
    fun `different users can redeem the same coupon`() {
        val coupon = coupon("welcome-shared")
        val userA = user("firebase-uid-a")
        val userB = user("firebase-uid-b")

        redeem(coupon, userA)
        redeem(coupon, userB)

        assertEquals(2, int("SELECT count(*) FROM coupon_redemptions WHERE coupon_id = '$coupon'"))
    }

    @Test
    fun `asaas idempotent operations key is unique`() {
        execute(
            "INSERT INTO asaas_idempotent_operations (idempotency_key, resource_id, created_at) " +
                "VALUES ('op-1', NULL, now())",
        )
        assertFailsWith<Exception> {
            execute(
                "INSERT INTO asaas_idempotent_operations (idempotency_key, resource_id, created_at) " +
                    "VALUES ('op-1', 'sub_x', now())",
            )
        }
    }

    private fun coupon(code: String): UUID {
        val id = UUID.randomUUID()
        val uniqueCode = "$code-${UUID.randomUUID().toString().take(8)}"
        execute(
            "INSERT INTO coupons (id, code, discount_percent, created_at) VALUES " +
                "('$id', '$uniqueCode', 10, now())",
        )
        return id
    }

    private fun user(subject: String, displayName: String = "User"): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject-${UUID.randomUUID()}', true, '$displayName', now(), now())",
        )
        return id
    }

    private fun redeem(couponId: UUID, userId: UUID) {
        execute(
            "INSERT INTO coupon_redemptions (coupon_id, user_id, redeemed_at) VALUES " +
                "('$couponId', '$userId', now())",
        )
    }

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun int(sql: String): Int = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                result.getInt(1)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection
}
