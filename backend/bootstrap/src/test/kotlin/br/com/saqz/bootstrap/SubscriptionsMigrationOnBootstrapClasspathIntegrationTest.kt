package br.com.saqz.bootstrap

import br.com.saqz.bootstrap.configuration.AccessSessionConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionsMigrationOnBootstrapClasspathIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeAll
    fun startDatabase() {
        postgres.start()
        val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
        var ready = false
        while (!ready && System.nanoTime() < deadline) {
            ready = runCatching { DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use {} }.isSuccess
            if (!ready) Thread.sleep(100)
        }
        check(ready) { "PostgreSQL port did not become JDBC-ready" }
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    @AfterAll
    fun stopDatabase() = postgres.stop()

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
