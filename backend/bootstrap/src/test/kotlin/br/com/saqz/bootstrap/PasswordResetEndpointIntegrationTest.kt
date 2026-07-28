package br.com.saqz.bootstrap

import br.com.saqz.access.application.passwordreset.PasswordAccounts
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Caminho completo do VUL-80 contra a fiação de produção: Postgres real, migração
 * real, repositório JDBC real e o e-mail saindo pelo `JavaMailSender` do
 * `application.properties`. Só o provedor de identidade é falso — trocar a senha no
 * Firebase de verdade depende do emulador, que não faz parte do `check`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PasswordResetEndpointIntegrationTest.PasswordResetTestConfiguration::class)
@ActiveProfiles("test")
class PasswordResetEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var accounts: FakePasswordAccounts
    @Autowired private lateinit var clock: MovableClock
    @Autowired private lateinit var dataSource: DataSource

    private val client: HttpClient = HttpClient.newHttpClient()

    @BeforeEach
    fun reset() {
        JdbcTemplate(dataSource).execute("TRUNCATE password_reset_codes, password_reset_ip_limits")
        smtp.purgeEmailFromAllMailboxes()
        clock.reset()
        accounts.passwords.clear()
        accounts.passwords["atleta@saqz.test"] = "senha-antiga"
    }

    @Test
    fun `pedir codigo trocar e entrar com a senha nova`() {
        assertEquals(202, request("atleta@saqz.test").statusCode())

        val code = deliveredCode()
        val verified = verify("atleta@saqz.test", code)
        val token = json(verified)["token"].stringValue()

        assertEquals(200, verified.statusCode(), verified.body())
        assertEquals(300, json(verified)["expiraEmSegundos"].asInt())
        assertEquals(204, confirm(token, "senha-nova-forte").statusCode())
        assertEquals("senha-nova-forte", accounts.passwords.getValue("atleta@saqz.test"))
    }

    @Test
    fun `os tres passos passam sem bearer`() {
        val responses = listOf(
            request("atleta@saqz.test"),
            verify("atleta@saqz.test", "0000"),
            confirm("token-inexistente", "senha-nova-forte"),
        )

        assertTrue(responses.none { it.statusCode() == 401 }, responses.map(HttpResponse<String>::statusCode).toString())
    }

    @Test
    fun `e-mail sem conta responde 202 sem entregar nada`() {
        val response = request("ninguem@saqz.test")

        assertEquals(202, response.statusCode())
        assertTrue(response.body().isEmpty())
        assertFalse(smtp.waitForIncomingEmail(1_000, 1))
    }

    @Test
    fun `e-mail com e sem conta respondem exatamente igual`() {
        val existente = request("atleta@saqz.test")
        val inexistente = request("ninguem@saqz.test")

        assertEquals(existente.statusCode(), inexistente.statusCode())
        assertEquals(existente.body(), inexistente.body())
    }

    @Test
    fun `codigo errado diz quantas tentativas restam`() {
        request("atleta@saqz.test")
        val code = deliveredCode()

        (4 downTo 1).forEach { remaining ->
            val response = verify("atleta@saqz.test", wrong(code))
            assertProblem(response, 400, "PASSWORD_RESET_CODE_INVALID")
            assertEquals(remaining, json(response)["remainingAttempts"].asInt())
        }
    }

    @Test
    fun `a quinta tentativa errada estoura o teto e mata o codigo`() {
        request("atleta@saqz.test")
        val code = deliveredCode()
        repeat(4) { verify("atleta@saqz.test", wrong(code)) }

        assertProblem(verify("atleta@saqz.test", wrong(code)), 429, "PASSWORD_RESET_ATTEMPT_LIMIT")
        assertProblem(verify("atleta@saqz.test", code), 410, "PASSWORD_RESET_CODE_EXPIRED")
    }

    @Test
    fun `codigo expirado responde separado de codigo errado`() {
        request("atleta@saqz.test")
        val code = deliveredCode()

        clock.advance(Duration.ofMinutes(10))

        val expired = verify("atleta@saqz.test", code)
        assertProblem(expired, 410, "PASSWORD_RESET_CODE_EXPIRED")
        assertFalse(expired.body().contains("remainingAttempts"), expired.body())
    }

    @Test
    fun `token reusado nao troca a senha de novo`() {
        request("atleta@saqz.test")
        val token = json(verify("atleta@saqz.test", deliveredCode()))["token"].stringValue()
        confirm(token, "senha-nova-forte")

        val reused = confirm(token, "senha-do-invasor")

        assertProblem(reused, 410, "PASSWORD_RESET_TOKEN_INVALID")
        assertEquals("senha-nova-forte", accounts.passwords.getValue("atleta@saqz.test"))
    }

    @Test
    fun `token expirado nao troca a senha`() {
        request("atleta@saqz.test")
        val token = json(verify("atleta@saqz.test", deliveredCode()))["token"].stringValue()

        clock.advance(Duration.ofMinutes(5))

        assertProblem(confirm(token, "senha-do-invasor"), 410, "PASSWORD_RESET_TOKEN_INVALID")
        assertEquals("senha-antiga", accounts.passwords.getValue("atleta@saqz.test"))
    }

    @Test
    fun `senha curta demais e recusada sem gastar o token`() {
        request("atleta@saqz.test")
        val token = json(verify("atleta@saqz.test", deliveredCode()))["token"].stringValue()

        val weak = confirm(token, "1234567")

        assertProblem(weak, 400, "VALIDATION_FAILED")
        assertTrue(weak.body().contains("novaSenha"))
        assertEquals("senha-antiga", accounts.passwords.getValue("atleta@saqz.test"))
        assertEquals(204, confirm(token, "senha-nova-forte").statusCode())
    }

    @Test
    fun `reenvio antes dos 60 segundos devolve o quanto falta`() {
        request("atleta@saqz.test")
        clock.advance(Duration.ofSeconds(18))

        val response = request("atleta@saqz.test")

        assertProblem(response, 429, "PASSWORD_RESET_RATE_LIMIT")
        assertEquals(42, json(response)["retryAfterSeconds"].asInt())
        assertEquals("42", response.headers().firstValue("Retry-After").orElse(""))
    }

    @Test
    fun `reenvio depois dos 60 segundos entrega um codigo novo que invalida o anterior`() {
        request("atleta@saqz.test")
        val first = deliveredCode()

        // Um sorteio repetido acontece uma vez em dez mil e não provaria invalidação
        // nenhuma, então se repetir pede-se outro em vez de deixar o teste instável.
        var delivered = 1
        var second = first
        while (second == first) {
            clock.advance(Duration.ofSeconds(60))
            assertEquals(202, request("atleta@saqz.test").statusCode())
            delivered++
            second = deliveredCode(delivered)
        }

        assertNotEquals(first, second)
        assertProblem(verify("atleta@saqz.test", first), 400, "PASSWORD_RESET_CODE_INVALID")
        assertEquals(200, verify("atleta@saqz.test", second).statusCode())
    }

    @Test
    fun `limita pedidos por IP mesmo variando o e-mail`() {
        repeat(10) { index ->
            assertEquals(202, request("pessoa$index@saqz.test").statusCode(), "pedido $index")
        }

        val response = request("maisum@saqz.test")

        assertProblem(response, 429, "PASSWORD_RESET_RATE_LIMIT")
        assertEquals(600, json(response)["retryAfterSeconds"].asInt())
    }

    @Test
    fun `o codigo nunca aparece em resposta alguma`() {
        request("atleta@saqz.test")
        val code = deliveredCode()

        val bodies = listOf(
            request("atleta@saqz.test").body(),
            verify("atleta@saqz.test", wrong(code)).body(),
            verify("atleta@saqz.test", code).body(),
        )

        assertTrue(bodies.none { it.contains(code) }, bodies.toString())
    }

    private fun deliveredCode(expected: Int = 1): String {
        assertTrue(smtp.waitForIncomingEmail(5_000, expected), "e-mail não chegou no GreenMail")
        val body = smtp.receivedMessages.last().content.toString()
        return Regex("Seu código de acesso é (\\d{4})").find(body)?.groupValues?.get(1)
            ?: error("corpo sem código de quatro dígitos: $body")
    }

    private fun wrong(code: String): String = if (code == "0000") "1111" else "0000"

    private fun request(email: String) = post("request", mapOf("email" to email))

    private fun verify(email: String, code: String) = post("verify", mapOf("email" to email, "code" to code))

    private fun confirm(token: String, novaSenha: String) =
        post("confirm", mapOf("token" to token, "novaSenha" to novaSenha))

    private fun post(step: String, body: Map<String, String>): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/password-reset/$step"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())

    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertEquals(code, json(response)["code"].stringValue())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class PasswordResetTestConfiguration {
        @Bean @Primary fun fakePasswordAccounts() = FakePasswordAccounts()
        @Bean @Primary fun movableClock() = MovableClock()
    }

    class FakePasswordAccounts : PasswordAccounts {
        val passwords = mutableMapOf<String, String>()

        override fun exists(email: String) = email in passwords

        override fun updatePassword(email: String, newPassword: String): Boolean {
            if (email !in passwords) return false
            passwords[email] = newPassword
            return true
        }
    }

    class MovableClock : Clock() {
        private var now: Instant = START

        fun reset() {
            now = START
        }

        fun advance(amount: Duration) {
            now = now.plus(amount)
        }

        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this

        private companion object {
            val START: Instant = Instant.parse("2026-07-28T12:00:00Z")
        }
    }

    companion object {
        private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).apply { start() }
        private val smtp = GreenMail(ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP)).apply { start() }

        @JvmStatic
        @AfterAll
        fun stopDependencies() {
            smtp.stop()
            postgres.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("saqz.firebase.emulator.enabled") { "true" }
            registry.add("saqz.branch.domain") { "https://join.test" }
            registry.add("spring.mail.host") { "127.0.0.1" }
            registry.add("spring.mail.port") { smtp.smtp.port }
            // O GreenMail não anuncia STARTTLS; o que a exigência protege está no
            // SmtpStarttlsIntegrationTest, aqui o que interessa é o código chegar.
            registry.add("spring.mail.properties.mail.smtp.starttls.enable") { "false" }
            registry.add("spring.mail.properties.mail.smtp.starttls.required") { "false" }
        }
    }
}
