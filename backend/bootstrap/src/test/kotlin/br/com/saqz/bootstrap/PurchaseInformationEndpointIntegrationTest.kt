package br.com.saqz.bootstrap

import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.subscriptions.application.CheckoutIdentitySessions
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
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
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import br.com.saqz.postgrestesting.TestPostgres
import java.net.URI
import java.net.ServerSocket
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import javax.sql.DataSource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PurchaseInformationEndpointIntegrationTest.IdentityConfiguration::class)
@ActiveProfiles("test")
class PurchaseInformationEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var mailSender: JavaMailSenderImpl

    private val ownerUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val client = HttpClient.newHttpClient()

    @BeforeEach
    fun reset() {
        JdbcTemplate(dataSource).execute(
            "TRUNCATE subscription_checkout_login_tokens, " +
                "subscription_purchase_information_email_successes, " +
                "subscription_purchase_information_emails, access_users CASCADE",
        )
        JdbcTemplate(dataSource).update(
            """
            INSERT INTO access_users (
                id, firebase_subject, email, email_verified, display_name, created_at, updated_at
            ) VALUES (?, ?, ?, TRUE, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            ownerUserId,
            "purchase-subject",
            "owner@example.test",
            "Purchase Owner",
        )
        smtp.purgeEmailFromAllMailboxes()
    }

    @Test
    fun `requires authentication`() {
        assertEquals(401, post(bearer = null).statusCode())
    }

    @Test
    fun `ignores malicious body and sends to authoritative owner email`() {
        val response = post("{\"email\":\"attacker@example.test\",\"ownerUserId\":\"bad\"}")

        assertEquals(204, response.statusCode(), response.body())
        assertTrue(smtp.waitForIncomingEmail(5_000, 1))
        val delivered = smtp.receivedMessages.single()
        assertEquals("owner@example.test", delivered.allRecipients.single().toString())
        val raw = java.io.ByteArrayOutputStream().also { delivered.writeTo(it) }.toString(Charsets.UTF_8)
        assertFalse(raw.contains("attacker@example.test"), raw)
    }

    @Test
    fun `existing account keeps persisted email when token omits email claim`() {
        val response = post()

        assertEquals(204, response.statusCode(), response.body())
        assertEquals(
            "owner@example.test",
            JdbcTemplate(dataSource).queryForObject(
                "SELECT email FROM access_users WHERE id = ?",
                String::class.java,
                ownerUserId,
            ),
        )
        assertTrue(smtp.waitForIncomingEmail(5_000, 1))
        assertEquals("owner@example.test", smtp.receivedMessages.single().allRecipients.single().toString())
    }

    @Test
    fun `rejects suspended existing account before sending`() {
        JdbcTemplate(dataSource).update(
            "UPDATE access_users SET suspended_at = CURRENT_TIMESTAMP WHERE id = ?",
            ownerUserId,
        )

        val response = post()

        assertEquals(403, response.statusCode(), response.body())
        assertTrue(response.body().contains("ACCOUNT_SUSPENDED"))
        assertFalse(smtp.waitForIncomingEmail(500, 1))
    }

    @Test
    fun `returns 204 after SMTP and deduplicates a second request`() {
        assertEquals(204, post().statusCode())
        assertTrue(smtp.waitForIncomingEmail(5_000, 1))

        assertEquals(204, post().statusCode())
        assertFalse(smtp.waitForIncomingEmail(500, 2))
    }

    @Test
    fun `maps SMTP failure to 503 and releases reservation for retry`() {
        val originalPort = mailSender.port
        val unavailablePort = ServerSocket(0).use { it.localPort }
        mailSender.port = unavailablePort
        try {
            val failed = post()
            assertEquals(503, failed.statusCode(), failed.body())
            assertTrue(failed.body().contains("SUBSCRIPTION_PURCHASE_EMAIL_UNAVAILABLE"))
        } finally {
            mailSender.port = originalPort
        }

        assertEquals(204, post().statusCode())
        assertTrue(smtp.waitForIncomingEmail(5_000, 1))
    }

    @Test
    fun `purchase email carries a one-time login that signs the owner in`() {
        assertEquals(204, post().statusCode())
        assertTrue(smtp.waitForIncomingEmail(5_000, 1))
        val message = smtp.receivedMessages.single()
        val plain = message.textPart("text/plain")
        val html = message.textPart("text/html")
        val token = Regex("""https://checkout\.test/assinar/\?t=([A-Za-z0-9_-]{43})""")
            .find(plain)
            ?.groupValues
            ?.get(1)
        assertNotNull(token, plain)
        assertTrue(html.contains("href=\"https://checkout.test/assinar/?t=$token\""), html)
        assertFalse(plain.contains("customToken"))
        assertFalse(html.contains("customToken"))

        val first = redeem(token)
        assertEquals(200, first.statusCode(), first.body())
        assertTrue(first.body().contains("\"customToken\":\"checkout-custom-token\""), first.body())

        val second = redeem(token)
        assertEquals(410, second.statusCode(), second.body())
        assertTrue(second.body().contains("CHECKOUT_LOGIN_TOKEN_INVALID"), second.body())
    }

    @Test
    fun `checkout login is anonymous and rejects garbage without a session`() {
        val response = redeem("not-a-real-token")

        assertEquals(410, response.statusCode(), response.body())
        assertTrue(response.body().contains("CHECKOUT_LOGIN_TOKEN_INVALID"), response.body())
    }

    private fun post(body: String = "{}", bearer: String? = "purchase-token"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(
            URI("http://127.0.0.1:$port/subscriptions/me/purchase-information"),
        )
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun redeem(token: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(
            URI("http://127.0.0.1:$port/subscriptions/checkout-login"),
        )
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"token":"$token"}"""))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun MimeMessage.textPart(mimeType: String): String {
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

    @TestConfiguration(proxyBeanMethods = false)
    class IdentityConfiguration {
        @Bean
        @Primary
        fun purchaseInformationVerifyRequestIdentity(): VerifyRequestIdentity = VerifyRequestIdentity {
            if (it.value == "purchase-token") {
                TokenVerification.Verified(
                    RequestIdentity("purchase-subject", null, null, "Purchase Owner"),
                )
            } else {
                TokenVerification.Rejected
            }
        }

        @Bean
        @Primary
        fun purchaseInformationCheckoutIdentitySessions(): CheckoutIdentitySessions = CheckoutIdentitySessions {
            "checkout-custom-token"
        }
    }

    companion object {
        private val database = TestPostgres.empty()
        private val smtp = GreenMail(ServerSetupTest.SMTP).apply { start() }

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
            registry.add("saqz.password-reset.secret") { "segredo-de-teste-com-trinta-e-dois" }
            registry.add("saqz.branch.domain") { "https://join.test" }
            registry.add("saqz.subscription.purchase-url") { "https://checkout.test/assinar/" }
            registry.add("spring.mail.host") { "127.0.0.1" }
            registry.add("spring.mail.port") { smtp.smtp.port }
            registry.add("spring.mail.properties.mail.smtp.starttls.enable") { "false" }
            registry.add("spring.mail.properties.mail.smtp.starttls.required") { "false" }
        }
    }
}
