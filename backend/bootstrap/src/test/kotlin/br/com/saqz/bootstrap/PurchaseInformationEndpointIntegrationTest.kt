package br.com.saqz.bootstrap

import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
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
            "TRUNCATE subscription_purchase_information_email_successes, " +
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
        assertEquals("owner@example.test", smtp.receivedMessages.single().allRecipients.single().toString())
        assertFalse(smtp.receivedMessages.single().content.toString().contains("attacker@example.test"))
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

    private fun post(body: String = "{}", bearer: String? = "purchase-token"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(
            URI("http://127.0.0.1:$port/subscriptions/me/purchase-information"),
        )
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
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
