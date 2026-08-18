package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.output.mail.EmailVerificationMailer
import br.com.saqz.access.application.emailverification.VerificationLinkGenerator
import br.com.saqz.access.application.emailverification.VerificationLinkMailer
import br.com.saqz.access.application.emailverification.VerificationLinksUnavailable
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.RequestIdentity
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import jakarta.mail.Multipart
import jakarta.mail.internet.MimeMessage
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
import kotlin.test.assertTrue

/**
 * O Firebase trava o HTML da confirmação; este caminho é o que veste o e-mail. Postgres
 * real para o teto, SMTP real via GreenMail, token de mentira. O Admin SDK só entra como
 * gerador de link falso — gerar o oob de verdade depende do emulador.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EmailVerificationEndpointIntegrationTest.EmailVerificationTestConfiguration::class)
@ActiveProfiles("test")
class EmailVerificationEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var verifier: SessionVerifier
    @Autowired private lateinit var links: FakeVerificationLinks
    @Autowired private lateinit var clock: MovableClock
    @Autowired private lateinit var mailer: FailableLinkMailer
    @Autowired private lateinit var dataSource: DataSource

    private val client: HttpClient = HttpClient.newHttpClient()

    @BeforeEach
    fun reset() {
        JdbcTemplate(dataSource).execute("TRUNCATE password_reset_codes, password_reset_rate_limits")
        smtp.purgeEmailFromAllMailboxes()
        clock.reset()
        mailer.failing = false
        links.unavailable = false
        links.missing = false
        links.emails.clear()
        verifier.principal = identity()
    }

    @Test
    fun `pede o link e entrega o html com o botao escondendo a url`() {
        assertEquals(202, request().statusCode())

        assertTrue(smtp.waitForIncomingEmail(5_000, 1), "e-mail não chegou no GreenMail")
        val message = smtp.receivedMessages.single()
        assertEquals("atleta@saqz.test", message.allRecipients.single().toString())
        assertEquals("Confirme seu e-mail no Saqz", message.subject)
        val html = message.htmlText()
        val plain = message.plainText()
        assertEquals(listOf("atleta@saqz.test"), links.emails)
        assertTrue(html.contains("Confirmar e-mail"), html)
        assertTrue(html.contains("cid:saqz-mark"), html)
        assertTrue(html.contains("href=\"${FakeVerificationLinks.HREF}\""), html)
        assertFalse(html.contains(FakeVerificationLinks.RAW_QUERY), html)
        assertTrue(plain.contains(FakeVerificationLinks.LINK), plain)
    }

    @Test
    fun `conta ja confirmada responde 202 sem mandar nada`() {
        verifier.principal = identity(emailVerified = true)

        assertEquals(202, request().statusCode())
        assertFalse(smtp.waitForIncomingEmail(1_000, 1))
        assertTrue(links.emails.isEmpty())
    }

    @Test
    fun `sem bearer responde autenticacao obrigatoria`() {
        val response = client.send(
            HttpRequest.newBuilder(uri()).POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertProblem(response, 401, "AUTHENTICATION_REQUIRED")
    }

    @Test
    fun `reenvio antes de um minuto devolve o quanto falta`() {
        assertEquals(202, request().statusCode())
        clock.advance(Duration.ofSeconds(18))

        val response = request()

        assertProblem(response, 429, "EMAIL_VERIFICATION_RATE_LIMIT")
        assertEquals(42, json(response)["retryAfterSeconds"].asInt())
        assertEquals("42", response.headers().firstValue("Retry-After").orElse(""))
        assertEquals(1, smtp.receivedMessages.size)
    }

    @Test
    fun `conta que sumiu no provedor responde 202 sem e-mail`() {
        links.missing = true

        assertEquals(202, request().statusCode())
        assertFalse(smtp.waitForIncomingEmail(1_000, 1))
    }

    @Test
    fun `falha de entrega nao muda a resposta`() {
        mailer.failing = true

        assertEquals(202, request().statusCode())
        assertFalse(smtp.waitForIncomingEmail(1_000, 1))
    }

    @Test
    fun `provedor de identidade fora do ar responde indisponibilidade`() {
        links.unavailable = true

        assertProblem(request(), 503, "IDENTITY_PROVIDER_UNAVAILABLE")
    }

    private fun request(): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(uri())
            .header("Authorization", "Bearer session-token")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun uri() = URI("http://127.0.0.1:$port/api/email-verification/request")

    private fun identity(
        email: String? = "atleta@saqz.test",
        emailVerified: Boolean? = false,
    ) = RequestIdentity("subject-session", email, emailVerified, "Atleta")

    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())

    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertEquals(code, json(response)["code"].stringValue())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class EmailVerificationTestConfiguration {
        @Bean @Primary fun sessionVerifier() = SessionVerifier()
        @Bean @Primary fun fakeVerificationLinks() = FakeVerificationLinks()
        @Bean @Primary fun movableClock() = MovableClock()
        @Bean @Primary fun failableLinkMailer(mailer: EmailVerificationMailer) = FailableLinkMailer(mailer)
    }

    class SessionVerifier : VerifyRequestIdentity {
        lateinit var principal: RequestIdentity
        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(principal)
    }

    class FakeVerificationLinks : VerificationLinkGenerator {
        val emails = mutableListOf<String>()
        var missing = false
        var unavailable = false

        override fun generate(email: String): String? {
            emails += email
            if (unavailable) throw VerificationLinksUnavailable()
            return if (missing) null else LINK
        }

        companion object {
            const val LINK = "https://saqz-dev.firebaseapp.com/__/auth/action?oobCode=abc&mode=verifyEmail"
            const val HREF = "https://saqz-dev.firebaseapp.com/__/auth/action?oobCode=abc&amp;mode=verifyEmail"
            const val RAW_QUERY = "oobCode=abc&mode=verifyEmail"
        }
    }

    class FailableLinkMailer(private val mailer: EmailVerificationMailer) : VerificationLinkMailer {
        var failing = false
        override fun send(recipient: String, confirmationLink: String) {
            if (failing) throw IllegalStateException("SMTP fora do ar")
            mailer.send(recipient, confirmationLink)
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
            val START: Instant = Instant.parse("2026-08-18T15:00:00Z")
        }
    }

    companion object {
        private val database = TestPostgres.empty()
        private val smtp = GreenMail(ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP)).apply { start() }

        @JvmStatic
        @AfterAll
        fun stopDependencies() {
            smtp.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { database.jdbcUrl }
            registry.add("spring.datasource.username") { database.username }
            registry.add("spring.datasource.password") { database.password }
            registry.add("saqz.firebase.emulator.enabled") { "true" }
            registry.add("saqz.branch.domain") { "https://join.test" }
            registry.add("saqz.subscription.purchase-url") { "https://checkout.test/assinar/" }
            registry.add("saqz.password-reset.secret") { "segredo-de-teste-com-trinta-e-dois" }
            registry.add("spring.mail.host") { "127.0.0.1" }
            registry.add("spring.mail.port") { smtp.smtp.port }
            registry.add("spring.mail.properties.mail.smtp.starttls.enable") { "false" }
            registry.add("spring.mail.properties.mail.smtp.starttls.required") { "false" }
        }
    }
}

private fun MimeMessage.plainText(): String = firstPart("text/plain")

private fun MimeMessage.htmlText(): String = firstPart("text/html")

private fun MimeMessage.firstPart(mimeType: String): String {
    val found = findPart(content, mimeType)
    return found ?: error("mensagem sem parte $mimeType")
}

private fun findPart(content: Any?, mimeType: String): String? {
    if (content is String) return content.takeIf { mimeType.startsWith("text/plain") }
    if (content !is Multipart) return null
    for (index in 0 until content.count) {
        val part = content.getBodyPart(index)
        if (part.isMimeType(mimeType) && part.content is String) return part.content.toString()
        findPart(part.content, mimeType)?.let { return it }
    }
    return null
}
