package br.com.saqz.subscriptions.adapter.output.jdbc.migration

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionsEnumMigrationIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.empty(owner = this).dataSource
    }

    @Test
    fun `v39 registers subscription enums and converts the closed-set columns`() {
        flyway().migrate()

        assertEquals(
            listOf(
                "subscription_billing_type" to listOf("PIX", "CREDIT_CARD"),
                "subscription_cycle" to listOf("MONTHLY", "ANNUAL"),
                "subscription_plan" to listOf("TITULAR", "ORGANIZADOR", "ILIMITADO"),
                "subscription_status" to listOf("ACTIVE", "PAST_DUE", "CANCELED"),
            ),
            enumCatalog(),
        )
        assertEquals(
            mapOf(
                "billing_type" to "subscription_billing_type",
                "cycle" to "subscription_cycle",
                "pending_plan" to "subscription_plan",
                "pending_upgrade_plan" to "subscription_plan",
                "plan" to "subscription_plan",
                "status" to "subscription_status",
            ),
            columnTypes(),
        )
        assertTrue(queryStrings("SELECT conname FROM pg_constraint WHERE conrelid = 'subscriptions'::regclass AND contype = 'c' AND conname LIKE 'ck_subscriptions_%'").isEmpty())
        assertEquals("ACTIVE", string("SELECT column_default FROM information_schema.columns WHERE table_name = 'subscriptions' AND column_name = 'status'").let { it.substringAfter("'").substringBefore("'") })
    }

    @Test
    fun `v38 to v39 preserves every label including null billing and pending plans`() {
        flyway("38").migrate()
        val owner = user("enum-upgrade")
        execute(
            """
            INSERT INTO subscriptions (
                owner_user_id, plan, cycle, status, asaas_customer_id, asaas_subscription_id,
                billing_type, pending_plan, pending_upgrade_plan, current_period_end,
                first_confirmed_at, created_at, updated_at
            ) VALUES (
                '$owner', 'ORGANIZADOR', 'ANNUAL', 'PAST_DUE', 'cus-$owner', 'sub-$owner',
                NULL, 'ILIMITADO', 'TITULAR', now() + interval '1 year',
                now(), now(), now()
            )
            """.trimIndent(),
        )

        flyway().migrate()

        assertEquals("ORGANIZADOR", string("SELECT plan::text FROM subscriptions WHERE owner_user_id = '$owner'"))
        assertEquals("ANNUAL", string("SELECT cycle::text FROM subscriptions WHERE owner_user_id = '$owner'"))
        assertEquals("PAST_DUE", string("SELECT status::text FROM subscriptions WHERE owner_user_id = '$owner'"))
        assertEquals(null, nullableString("SELECT billing_type::text FROM subscriptions WHERE owner_user_id = '$owner'"))
        assertEquals("ILIMITADO", string("SELECT pending_plan::text FROM subscriptions WHERE owner_user_id = '$owner'"))
        assertEquals("TITULAR", string("SELECT pending_upgrade_plan::text FROM subscriptions WHERE owner_user_id = '$owner'"))
        assertEquals("subscription_plan", string("SELECT udt_name FROM information_schema.columns WHERE table_name = 'subscriptions' AND column_name = 'plan'"))
    }

    @Test
    fun `v39 keeps the active status default`() {
        flyway().migrate()
        val owner = user("enum-default")
        execute(
            """
            INSERT INTO subscriptions (
                owner_user_id, plan, cycle, asaas_customer_id, asaas_subscription_id,
                current_period_end, created_at, updated_at
            ) VALUES (
                '$owner', 'TITULAR', 'MONTHLY', 'cus-$owner', 'sub-$owner',
                now() + interval '1 month', now(), now()
            )
            """.trimIndent(),
        )

        assertEquals("ACTIVE", string("SELECT status::text FROM subscriptions WHERE owner_user_id = '$owner'"))
    }

    @Test
    fun `v39 rejects labels that are not in the enum`() {
        flyway().migrate()
        val owner = user("enum-reject")
        assertFailsWith<Exception> {
            execute(
                """
                INSERT INTO subscriptions (
                    owner_user_id, plan, cycle, status, asaas_customer_id, asaas_subscription_id,
                    current_period_end, created_at, updated_at
                ) VALUES (
                    '$owner', 'QUADRA', 'MONTHLY', 'ACTIVE', 'cus-$owner', 'sub-$owner',
                    now() + interval '1 month', now(), now()
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `v39 reuses a matching type created outside Flyway`() {
        flyway("38").migrate()
        execute("CREATE TYPE public.subscription_plan AS ENUM ('TITULAR', 'ORGANIZADOR', 'ILIMITADO')")
        flyway().migrate()
        assertEquals("subscription_plan", string("SELECT udt_name FROM information_schema.columns WHERE table_name = 'subscriptions' AND column_name = 'plan'"))
    }

    @Test
    fun `v39 refuses a homonymous type with different labels`() {
        flyway("38").migrate()
        execute("CREATE TYPE public.subscription_plan AS ENUM ('TITULAR')")
        assertFailsWith<Exception> { flyway().migrate() }
    }

    private fun flyway(target: String? = null): Flyway {
        val configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations(*allSubscriptionsFeatureMigrationLocations())
            .cleanDisabled(false)
        if (target != null) configuration.target(target)
        return configuration.load()
    }

    private fun user(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject-${UUID.randomUUID()}', true, 'User', now(), now())",
        )
        return id
    }

    private fun enumCatalog(): List<Pair<String, List<String>>> {
        val labels = mutableMapOf<String, MutableList<String>>()
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT t.typname, e.enumlabel
                    FROM pg_type t
                    JOIN pg_namespace n ON n.oid = t.typnamespace
                    JOIN pg_enum e ON e.enumtypid = t.oid
                    WHERE n.nspname = 'public' AND t.typname LIKE 'subscription_%'
                    ORDER BY t.typname, e.enumsortorder
                    """.trimIndent(),
                ).use { result ->
                    while (result.next()) {
                        labels.getOrPut(result.getString("typname")) { mutableListOf() }
                            .add(result.getString("enumlabel"))
                    }
                }
            }
        }
        return labels.toSortedMap().map { it.key to it.value }
    }

    private fun columnTypes(): Map<String, String> {
        val types = linkedMapOf<String, String>()
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT column_name, udt_name
                    FROM information_schema.columns
                    WHERE table_name = 'subscriptions'
                      AND column_name IN (
                          'plan', 'pending_plan', 'pending_upgrade_plan',
                          'cycle', 'status', 'billing_type'
                      )
                    ORDER BY column_name
                    """.trimIndent(),
                ).use { result ->
                    while (result.next()) types[result.getString("column_name")] = result.getString("udt_name")
                }
            }
        }
        return types
    }

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun string(sql: String): String = query(sql) { it.getString(1) }

    private fun nullableString(sql: String): String? = query(sql) { it.getString(1) }

    private fun queryStrings(sql: String): List<String> {
        val values = mutableListOf<String>()
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    while (result.next()) values += result.getString(1)
                }
            }
        }
        return values
    }

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                read(result)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection
}
