package br.com.saqz.bootstrap

import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ponta a ponta pelo HTTP com o ambiente XCODE (JWS sem assinatura da Apple, como o .storekit
 * local). A verificação da cadeia real fica em AppleSignedDataVerifierTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AppStoreSubscriptionEndpointIntegrationTest.Fixture::class)
@ActiveProfiles("test")
class AppStoreSubscriptionEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var mapper: ObjectMapper
    private val client = HttpClient.newHttpClient()
    private val now = Instant.now()
    private lateinit var token: String

    @BeforeEach
    fun signIn() {
        token = UUID.randomUUID().toString()
        assertEquals(200, request("PUT", "/api/session").statusCode())
    }

    @Test
    fun `a purchase sent by the app gives the organizer plan and ends the trial offer`() {
        val accountToken = appAccountToken()
        val transaction = transaction(accountToken, transactionId = "${System.nanoTime()}")

        val submitted = request("POST", "/subscriptions/app-store/transactions", """{"signedTransaction":"$transaction"}""")

        assertEquals(200, submitted.statusCode(), submitted.body())
        val body = mapper.readTree(submitted.body())
        assertEquals("APP_STORE", body["provider"].stringValue())
        assertEquals("ACTIVE", body["status"].stringValue())
        assertEquals("ORGANIZADOR", body["plan"].stringValue())
        assertTrue(body["entitled"].booleanValue())
        assertEquals(submitted.body(), request("GET", "/subscriptions/me").body())
        assertEquals("SUBSCRIBED", mapper.readTree(request("GET", "/subscriptions/trial").body())["status"].stringValue())
    }

    @Test
    fun `another account cannot take over a purchase`() {
        val transaction = transaction(appAccountToken(), transactionId = "${System.nanoTime()}")
        request("POST", "/subscriptions/app-store/transactions", """{"signedTransaction":"$transaction"}""")
        signIn()

        val stolen = request("POST", "/subscriptions/app-store/transactions", """{"signedTransaction":"$transaction"}""")

        assertEquals(409, stolen.statusCode(), stolen.body())
        assertEquals("APP_STORE_TRANSACTION_OWNED_BY_ANOTHER_ACCOUNT", mapper.readTree(stolen.body())["code"].stringValue())
    }

    @Test
    fun `an unverifiable transaction is unprocessable`() {
        val response = request("POST", "/subscriptions/app-store/transactions", """{"signedTransaction":"forjado"}""")

        assertEquals(422, response.statusCode(), response.body())
        assertEquals("APP_STORE_TRANSACTION_INVALID", mapper.readTree(response.body())["code"].stringValue())
    }

    @Test
    fun `a refund notification from Apple reaches the anonymous webhook and cuts access`() {
        val accountToken = appAccountToken()
        val transactionId = "${System.nanoTime()}"
        request("POST", "/subscriptions/app-store/transactions", """{"signedTransaction":"${transaction(accountToken, transactionId)}"}""")
        val refund = jws(
            mapOf(
                "notificationType" to "REFUND",
                "notificationUUID" to UUID.randomUUID().toString(),
                "version" to "2.0",
                "signedDate" to now.toEpochMilli(),
                "data" to mapOf(
                    "bundleId" to "app.saqz",
                    "environment" to "Xcode",
                    "signedTransactionInfo" to transaction(accountToken, transactionId, revocationDate = now),
                ),
            ),
        )

        val delivered = request("POST", "/webhooks/app-store", """{"signedPayload":"$refund"}""", actor = null)

        assertEquals(200, delivered.statusCode(), delivered.body())
        val me = mapper.readTree(request("GET", "/subscriptions/me").body())
        assertEquals("CANCELED", me["status"].stringValue())
        assertFalse(me["entitled"].booleanValue())
    }

    @Test
    fun `the webhook refuses payloads not signed for this app`() {
        val response = request("POST", "/webhooks/app-store", """{"signedPayload":"a.b.c"}""", actor = null)

        assertEquals(401, response.statusCode(), response.body())
    }

    private fun appAccountToken(): String {
        val response = request("GET", "/subscriptions/app-store/account-token")
        assertEquals(200, response.statusCode(), response.body())
        return mapper.readTree(response.body())["appAccountToken"].stringValue()
    }

    private fun transaction(accountToken: String, transactionId: String, revocationDate: Instant? = null) = jws(
        mapOf(
            "transactionId" to transactionId,
            "originalTransactionId" to transactionId,
            "bundleId" to "app.saqz",
            "productId" to "app.saqz.organizador.mensal",
            "purchaseDate" to now.toEpochMilli(),
            "expiresDate" to now.plus(Duration.ofDays(30)).toEpochMilli(),
            "type" to "Auto-Renewable Subscription",
            "appAccountToken" to accountToken,
            "environment" to "Xcode",
            "signedDate" to (revocationDate ?: now).toEpochMilli(),
            "revocationDate" to revocationDate?.toEpochMilli(),
            "price" to 59_900,
            "currency" to "BRL",
        ),
    )

    private fun jws(payload: Map<String, Any?>): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"ES256"}""".toByteArray())
        return "$header.${encoder.encodeToString(mapper.writeValueAsBytes(payload.filterValues { it != null }))}.c2ln"
    }

    private fun request(method: String, path: String, body: String? = null, actor: String? = token): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .header("Content-Type", "application/json")
            .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        actor?.let { builder.header("Authorization", "Bearer $it") }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Fixture {
        @Bean @Primary fun verifier() = VerifyRequestIdentity {
            TokenVerification.Verified(RequestIdentity(it.value, "${it.value}@example.test", true, "Assinante"))
        }
    }

    companion object {
        private val database = TestPostgres.empty()

        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { database.jdbcUrl }
            registry.add("spring.datasource.username") { database.username }
            registry.add("spring.datasource.password") { database.password }
            registry.add("saqz.firebase.emulator.enabled") { "true" }
            registry.add("saqz.links.domain") { "https://join.test" }
            registry.add("saqz.password-reset.secret") { "segredo-de-teste-com-trinta-e-dois" }
            registry.add("saqz.app-store.environments") { "XCODE" }
            registry.add("saqz.app-store.online-checks") { "false" }
        }
    }
}
