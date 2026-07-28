package br.com.saqz.access.adapter.output.jdbc.passwordreset

import br.com.saqz.access.application.passwordreset.AttemptOutcome
import br.com.saqz.access.application.passwordreset.NewResetCode
import br.com.saqz.access.application.passwordreset.ReplaceCodeOutcome
import br.com.saqz.access.application.passwordreset.ResetSecretHasher
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
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcPasswordResetRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var repository: JdbcPasswordResetRepository
    private val hasher = ResetSecretHasher("segredo-de-teste-com-trinta-e-dois")
    private val now: Instant = Instant.parse("2026-07-28T12:00:00Z")
    private val ceiling = 5

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
        execute("TRUNCATE password_reset_codes, password_reset_rate_limits")
    }

    /**
     * O teste que o teto precisa. Ler o contador, comparar o digest e só então gravar
     * deixava N palpites simultâneos enxergarem zero e gravarem um — dez mil combinações
     * em paralelo saíam com token válido sem nunca encostar no teto. Com o incremento
     * atômico, cada thread recebe o seu próprio valor e o contador chega a N.
     */
    @Test
    fun `tentativas simultaneas contam todas, nao uma`() {
        store("atleta@saqz.test", "1234")

        val outcomes = inParallel(ceiling) { repository.consumeAttempt("atleta@saqz.test", now, ceiling) }

        val consumed = outcomes.filterIsInstance<AttemptOutcome.Consumed>()
        assertEquals(ceiling, consumed.size, outcomes.toString())
        assertEquals((1..ceiling).toSet(), consumed.map(AttemptOutcome.Consumed::attempts).toSet())
        assertEquals(ceiling, storedAttempts("atleta@saqz.test"))
    }

    /**
     * O caso do atacante: muito mais palpites simultâneos do que o teto. Nenhum deles
     * pode passar do quinto, e o excedente tem que sair como teto estourado — não como
     * tentativa válida nem como violação da constraint.
     */
    @Test
    fun `enxurrada simultanea nao passa do teto`() {
        store("atleta@saqz.test", "1234")
        val floodSize = 40

        val outcomes = inParallel(floodSize) { repository.consumeAttempt("atleta@saqz.test", now, ceiling) }

        assertEquals(ceiling, outcomes.filterIsInstance<AttemptOutcome.Consumed>().size)
        assertEquals(floodSize - ceiling, outcomes.count { it == AttemptOutcome.Exhausted })
        assertEquals(ceiling, storedAttempts("atleta@saqz.test"))
    }

    @Test
    fun `so uma verificacao simultanea correta emite token`() {
        store("atleta@saqz.test", "1234")

        val issued = inParallel(8) {
            repository.issueToken("atleta@saqz.test", hasher.ofToken("token-$it"), now.plusSeconds(300))
        }

        assertEquals(1, issued.count { it == true })
        assertNull(storedCodeDigest("atleta@saqz.test"))
    }

    @Test
    fun `guarda e devolve o digest do codigo pela tentativa`() {
        store("atleta@saqz.test", "1234")

        val outcome = repository.consumeAttempt("atleta@saqz.test", now, ceiling) as AttemptOutcome.Consumed

        assertEquals(1, outcome.attempts)
        assertTrue(outcome.codeDigest.matches(hasher.ofCode("atleta@saqz.test", "1234")))
        assertFalse(outcome.codeDigest.matches(hasher.ofCode("atleta@saqz.test", "9999")))
        assertNull(repository.consumeAttempt("ninguem@saqz.test", now, ceiling))
    }

    @Test
    fun `codigo expirado nao conta tentativa`() {
        store("atleta@saqz.test", "1234")

        assertNull(repository.consumeAttempt("atleta@saqz.test", now.plus(Duration.ofMinutes(10)), ceiling))
        assertEquals(0, storedAttempts("atleta@saqz.test"))
    }

    @Test
    fun `emitir o token apaga o codigo na mesma escrita`() {
        store("atleta@saqz.test", "1234")

        assertTrue(repository.issueToken("atleta@saqz.test", hasher.ofToken("token-secreto"), now.plusSeconds(300)))

        assertNull(storedCodeDigest("atleta@saqz.test"))
        assertNull(repository.consumeAttempt("atleta@saqz.test", now, ceiling))
        assertFalse(repository.issueToken("atleta@saqz.test", hasher.ofToken("outro"), now.plusSeconds(300)))
    }

    @Test
    fun `estourar o teto nao apaga linha que ja carrega token`() {
        store("atleta@saqz.test", "1234")
        repository.issueToken("atleta@saqz.test", hasher.ofToken("token-secreto"), now.plusSeconds(300))

        repository.retireCode("atleta@saqz.test")

        assertEquals("atleta@saqz.test", repository.consumeToken(hasher.ofToken("token-secreto"), now))
    }

    @Test
    fun `retirar o codigo estourado apaga a linha`() {
        store("atleta@saqz.test", "1234")

        repository.retireCode("atleta@saqz.test")

        assertNull(repository.consumeAttempt("atleta@saqz.test", now, ceiling))
        assertEquals(0, count("password_reset_codes"))
    }

    @Test
    fun `pedir codigo novo sobrescreve o anterior e zera as tentativas`() {
        store("atleta@saqz.test", "1234")
        repository.consumeAttempt("atleta@saqz.test", now, ceiling)
        repository.issueToken("atleta@saqz.test", hasher.ofToken("token-velho"), now.plusSeconds(300))

        val later = now.plusSeconds(60)
        val outcome = repository.replaceCode(code("atleta@saqz.test", "9999", later), later.minusSeconds(60))

        assertEquals(ReplaceCodeOutcome.Replaced, outcome)
        assertEquals(0, storedAttempts("atleta@saqz.test"))
        val consumed = repository.consumeAttempt("atleta@saqz.test", later, ceiling) as AttemptOutcome.Consumed
        assertTrue(consumed.codeDigest.matches(hasher.ofCode("atleta@saqz.test", "9999")))
        assertNull(repository.consumeToken(hasher.ofToken("token-velho"), later))
    }

    @Test
    fun `recusa substituir um codigo criado dentro da janela de reenvio`() {
        store("atleta@saqz.test", "1234")

        val soon = now.plusSeconds(30)
        val outcome = repository.replaceCode(code("atleta@saqz.test", "9999", soon), soon.minusSeconds(60))

        assertEquals(ReplaceCodeOutcome.TooSoon(now), outcome)
        val consumed = repository.consumeAttempt("atleta@saqz.test", soon, ceiling) as AttemptOutcome.Consumed
        assertTrue(consumed.codeDigest.matches(hasher.ofCode("atleta@saqz.test", "1234")))
    }

    @Test
    fun `o token vale uma vez so e leva a linha junto`() {
        store("atleta@saqz.test", "1234")
        repository.issueToken("atleta@saqz.test", hasher.ofToken("token-secreto"), now.plusSeconds(300))

        assertEquals("atleta@saqz.test", repository.consumeToken(hasher.ofToken("token-secreto"), now))
        assertNull(repository.consumeToken(hasher.ofToken("token-secreto"), now))
        assertEquals(0, count("password_reset_codes"))
    }

    @Test
    fun `token so e consumido uma vez sob concorrencia`() {
        store("atleta@saqz.test", "1234")
        repository.issueToken("atleta@saqz.test", hasher.ofToken("token-secreto"), now.plusSeconds(300))

        val consumed = inParallel(8) { repository.consumeToken(hasher.ofToken("token-secreto"), now) }

        assertEquals(1, consumed.count { it != null })
    }

    @Test
    fun `token expirado nao e consumido`() {
        store("atleta@saqz.test", "1234")
        repository.issueToken("atleta@saqz.test", hasher.ofToken("token-secreto"), now.plusSeconds(300))

        assertNull(repository.consumeToken(hasher.ofToken("token-secreto"), now.plusSeconds(300)))
        assertEquals(
            "atleta@saqz.test",
            repository.consumeToken(hasher.ofToken("token-secreto"), now.plusSeconds(299)),
        )
    }

    @Test
    fun `conta acessos por balde dentro da janela e reinicia depois dela`() {
        val floor = now.minus(Duration.ofMinutes(10))

        assertEquals(1, repository.recordRateLimit("request:10.0.0.1", now, floor).count)
        assertEquals(2, repository.recordRateLimit("request:10.0.0.1", now.plusSeconds(1), floor).count)
        assertEquals(now, repository.recordRateLimit("request:10.0.0.1", now.plusSeconds(2), floor).startedAt)
        assertEquals(1, repository.recordRateLimit("verify:10.0.0.1", now, floor).count)
        assertEquals(1, repository.recordRateLimit("request:10.0.0.2", now, floor).count)

        val later = now.plus(Duration.ofMinutes(10))
        val restarted = repository.recordRateLimit("request:10.0.0.1", later, later.minus(Duration.ofMinutes(10)))

        assertEquals(1, restarted.count)
        assertEquals(later, restarted.startedAt)
    }

    @Test
    fun `acessos simultaneos ao mesmo balde nao se perdem`() {
        val floor = now.minus(Duration.ofMinutes(10))

        val windows = inParallel(20) { repository.recordRateLimit("request:10.0.0.1", now, floor) }

        assertEquals((1..20).toSet(), windows.map { it!!.count }.toSet())
    }

    private fun <T> inParallel(threads: Int, action: (Int) -> T): List<T?> {
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        return try {
            val futures = (0 until threads).map { index ->
                pool.submit(
                    Callable {
                        ready.countDown()
                        go.await(10, TimeUnit.SECONDS)
                        action(index)
                    },
                )
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            go.countDown()
            futures.map { it.get(20, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun code(email: String, digits: String, createdAt: Instant = now) = NewResetCode(
        email = email,
        codeDigest = hasher.ofCode(email, digits),
        createdAt = createdAt,
        expiresAt = createdAt.plus(Duration.ofMinutes(10)),
    )

    private fun store(email: String, digits: String) {
        repository.replaceCode(code(email, digits), now.minusSeconds(60))
    }

    private fun storedAttempts(email: String): Int =
        queryInt("SELECT attempts FROM password_reset_codes WHERE email = '$email'")

    private fun storedCodeDigest(email: String): String? =
        queryString("SELECT encode(code_digest, 'hex') FROM password_reset_codes WHERE email = '$email'")

    private fun count(table: String): Int = queryInt("SELECT count(*) FROM $table")

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun queryInt(sql: String): Int = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                result.next()
                result.getInt(1)
            }
        }
    }

    private fun queryString(sql: String): String? = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                result.next()
                result.getString(1)
            }
        }
    }

    private fun connection() = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
}
