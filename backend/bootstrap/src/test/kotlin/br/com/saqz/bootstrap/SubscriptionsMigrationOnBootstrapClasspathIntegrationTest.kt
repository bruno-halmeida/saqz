package br.com.saqz.bootstrap

import br.com.saqz.bootstrap.configuration.AccessSessionConfiguration
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.Connection
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionsMigrationOnBootstrapClasspathIntegrationTest {
    private val dataSource = TestPostgres.empty().dataSource

    @Test
    fun `bootstrap's own Flyway bean migrates the subscriptions schema from the aggregated classpath`() {
        AccessSessionConfiguration().accessFlyway(dataSource).migrate()

        assertTrue(tableExists("subscriptions"))
        assertTrue(tableExists("coupons"))
        assertTrue(tableExists("coupon_redemptions"))
        assertTrue(tableExists("subscription_events"))
        assertTrue(typeExists("subscription_plan"))
        assertTrue(typeExists("subscription_cycle"))
        assertTrue(typeExists("subscription_status"))
        assertTrue(typeExists("subscription_billing_type"))
        assertTrue(typeExists("game_status"))
        assertTrue(typeExists("attendance_status"))
        assertTrue(typeExists("attendance_source"))
        assertTrue(typeExists("charge_kind"))
        assertTrue(typeExists("charge_status"))
        assertTrue(typeExists("charge_paid_method"))
        assertTrue(typeExists("expense_category"))
        assertTrue(typeExists("expense_status"))
        assertTrue(typeExists("expense_direction"))
        assertTrue(typeExists("expense_action"))
        assertTrue(typeExists("athlete_position"))
        assertTrue(typeExists("athlete_membership_type"))
        assertTrue(typeExists("athlete_level"))
        assertTrue(typeExists("athlete_preferred_side"))
        assertTrue(typeExists("group_role"))
        assertTrue(typeExists("phone_visibility"))
        assertTrue(typeExists("group_profile_status"))
        assertTrue(typeExists("group_modality"))
        assertTrue(typeExists("group_composition"))
        assertTrue(typeExists("group_level"))
        assertTrue(typeExists("court_play_style"))
        assertTrue(typeExists("promotion_mode"))
    }

    private fun tableExists(table: String): Boolean =
        connection().use { connection -> connection.metaData.getTables(null, null, table, null).use { it.next() } }

    private fun typeExists(name: String): Boolean = connection().use { connection ->
        connection.prepareStatement("SELECT to_regtype(?) IS NOT NULL").use { statement ->
            statement.setString(1, "public.$name")
            statement.executeQuery().use { result ->
                check(result.next())
                result.getBoolean(1)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection
}
