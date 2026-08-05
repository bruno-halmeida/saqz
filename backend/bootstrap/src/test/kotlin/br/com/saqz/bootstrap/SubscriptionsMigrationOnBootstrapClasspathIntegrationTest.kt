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
    }

    private fun tableExists(table: String): Boolean =
        connection().use { connection -> connection.metaData.getTables(null, null, table, null).use { it.next() } }

    private fun connection(): Connection = dataSource.connection
}
