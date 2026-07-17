package br.com.saqz.access.testing

import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.time.Duration

fun PostgreSQLContainer.startAndAwaitJdbc() {
    start()
    val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
    var lastFailure: Exception? = null
    while (System.nanoTime() < deadline) {
        try {
            DriverManager.getConnection(jdbcUrl, username, password).use { return }
        } catch (failure: Exception) {
            lastFailure = failure
            Thread.sleep(100)
        }
    }
    throw IllegalStateException("PostgreSQL port did not become JDBC-ready", lastFailure)
}
