package br.com.saqz.access.adapter.output.jdbc.passwordreset

import br.com.saqz.access.application.passwordreset.ReplaceCodeOutcome
import br.com.saqz.access.application.passwordreset.ResetDigest
import br.com.saqz.access.application.passwordreset.StoredResetCode
import br.com.saqz.access.testing.startAndAwaitJdbc
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcPasswordResetRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var repository: JdbcPasswordResetRepository
    private val now: Instant = Instant.parse("2026-07-28T12:00:00Z")

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        repository = JdbcPasswordResetRepository(dataSource)
    }

    @AfterAll
    fun stopDatabase() {
        postgres.stop()
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE password_reset_codes, password_reset_ip_limits")
    }

    @Test
    fun `guarda e le o codigo pelo e-mail`() {
        repository.replaceCode(code("atleta@saqz.test", "1234"), now.minusSeconds(60))

        val stored = repository.findByEmail("atleta@saqz.test")

        assertEquals("atleta@saqz.test", stored?.email)
        assertEquals(0, stored?.attempts)
        assertEquals(now, stored?.createdAt)
        assertEquals(now.plus(Duration.ofMinutes(10)), stored?.expiresAt)
        assertTrue(stored!!.codeDigest.matches(ResetDigest.ofCode("atleta@saqz.test", "1234")))
        assertNull(repository.findByEmail("ninguem@saqz.test"))
    }

    @Test
    fun `pedir codigo novo sobrescreve o anterior e zera as tentativas`() {
        repository.replaceCode(code("atleta@saqz.test", "1234"), now.minusSeconds(60))
        repository.recordAttempt("atleta@saqz.test", 3)
        repository.issueToken("atleta@saqz.test", ResetDigest.ofToken("token-velho"), now.plusSeconds(300))

        val later = now.plusSeconds(60)
        val outcome = repository.replaceCode(code("atleta@saqz.test", "9999", later), later.minusSeconds(60))

        assertEquals(ReplaceCodeOutcome.Replaced, outcome)
        val stored = repository.findByEmail("atleta@saqz.test")!!
        assertEquals(0, stored.attempts)
        assertTrue(stored.codeDigest.matches(ResetDigest.ofCode("atleta@saqz.test", "9999")))
        assertNull(repository.consumeToken(ResetDigest.ofToken("token-velho"), later))
    }

    @Test
    fun `recusa substituir um codigo criado dentro da janela de reenvio`() {
        repository.replaceCode(code("atleta@saqz.test", "1234"), now.minusSeconds(60))

        val soon = now.plusSeconds(30)
        val outcome = repository.replaceCode(code("atleta@saqz.test", "9999", soon), soon.minusSeconds(60))

        assertEquals(ReplaceCodeOutcome.TooSoon(now), outcome)
        assertTrue(
            repository.findByEmail("atleta@saqz.test")!!
                .codeDigest.matches(ResetDigest.ofCode("atleta@saqz.test", "1234")),
        )
    }

    @Test
    fun `o token vale uma vez so e leva o codigo junto`() {
        repository.replaceCode(code("atleta@saqz.test", "1234"), now.minusSeconds(60))
        repository.issueToken("atleta@saqz.test", ResetDigest.ofToken("token-secreto"), now.plusSeconds(300))

        assertEquals("atleta@saqz.test", repository.consumeToken(ResetDigest.ofToken("token-secreto"), now))
        assertNull(repository.consumeToken(ResetDigest.ofToken("token-secreto"), now))
        assertNull(repository.findByEmail("atleta@saqz.test"))
    }

    @Test
    fun `token expirado nao e consumido`() {
        repository.replaceCode(code("atleta@saqz.test", "1234"), now.minusSeconds(60))
        repository.issueToken("atleta@saqz.test", ResetDigest.ofToken("token-secreto"), now.plusSeconds(300))

        assertNull(repository.consumeToken(ResetDigest.ofToken("token-secreto"), now.plusSeconds(300)))
        assertEquals("atleta@saqz.test", repository.consumeToken(ResetDigest.ofToken("token-secreto"), now.plusSeconds(299)))
    }

    @Test
    fun `apagar o codigo e idempotente`() {
        repository.replaceCode(code("atleta@saqz.test", "1234"), now.minusSeconds(60))

        repository.delete("atleta@saqz.test")
        repository.delete("atleta@saqz.test")

        assertNull(repository.findByEmail("atleta@saqz.test"))
    }

    @Test
    fun `conta pedidos por IP dentro da janela e reinicia depois dela`() {
        val floor = now.minus(Duration.ofMinutes(10))

        assertEquals(1, repository.recordIpRequest("10.0.0.1", now, floor).count)
        assertEquals(2, repository.recordIpRequest("10.0.0.1", now.plusSeconds(1), floor).count)
        assertEquals(now, repository.recordIpRequest("10.0.0.1", now.plusSeconds(2), floor).startedAt)
        assertEquals(1, repository.recordIpRequest("10.0.0.2", now, floor).count)

        val later = now.plus(Duration.ofMinutes(10))
        val restarted = repository.recordIpRequest("10.0.0.1", later, later.minus(Duration.ofMinutes(10)))

        assertEquals(1, restarted.count)
        assertEquals(later, restarted.startedAt)
    }

    private fun code(email: String, digits: String, createdAt: Instant = now) = StoredResetCode(
        email = email,
        codeDigest = ResetDigest.ofCode(email, digits),
        attempts = 0,
        createdAt = createdAt,
        expiresAt = createdAt.plus(Duration.ofMinutes(10)),
    )

    private fun execute(sql: String) {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }
}
