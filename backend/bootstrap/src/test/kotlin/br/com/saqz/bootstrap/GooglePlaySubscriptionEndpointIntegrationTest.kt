package br.com.saqz.bootstrap

import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.subscriptions.application.GooglePlayLookup
import br.com.saqz.subscriptions.application.GooglePlayPurchasesApi
import br.com.saqz.subscriptions.domain.GooglePlayPurchase
import br.com.saqz.subscriptions.domain.GooglePlayState
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ponta a ponta pelo HTTP com a Google Play Developer API trocada por [FakeGooglePlay]. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GooglePlaySubscriptionEndpointIntegrationTest.Fixture::class)
@ActiveProfiles("test")
class GooglePlaySubscriptionEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var google: FakeGooglePlay
    private val client = HttpClient.newHttpClient()
    private lateinit var token: String

    @BeforeEach
    fun signIn() {
        token = UUID.randomUUID().toString()
        assertEquals(200, request("PUT", "/api/session").statusCode())
    }

    @Test
    fun `a Play purchase sent by the app gives the plan and is acknowledged`() {
        val purchaseToken = "tok-${System.nanoTime()}"
        google.purchases[purchaseToken] = purchase(purchaseToken, accountId())

        val response = submit(purchaseToken)

        assertEquals(200, response.statusCode(), response.body())
        val body = mapper.readTree(response.body())
        assertEquals("GOOGLE_PLAY", body["provider"].stringValue())
        assertEquals("ORGANIZADOR", body["plan"].stringValue())
        assertTrue(body["entitled"].booleanValue())
        assertTrue(purchaseToken in google.acknowledged)
        assertEquals("SUBSCRIBED", mapper.readTree(request("GET", "/subscriptions/trial").body())["status"].stringValue())
    }

    @Test
    fun `an unknown token is unprocessable and another account's purchase is a conflict`() {
        val unknown = submit("nao-existe")
        assertEquals(422, unknown.statusCode(), unknown.body())
        assertEquals("GOOGLE_PLAY_PURCHASE_INVALID", mapper.readTree(unknown.body())["code"].stringValue())

        val purchaseToken = "tok-${System.nanoTime()}"
        google.purchases[purchaseToken] = purchase(purchaseToken, UUID.randomUUID().toString())
        val stolen = submit(purchaseToken)
        assertEquals(409, stolen.statusCode(), stolen.body())
        assertEquals("GOOGLE_PLAY_PURCHASE_OWNED_BY_ANOTHER_ACCOUNT", mapper.readTree(stolen.body())["code"].stringValue())
    }

    @Test
    fun `a Pub Sub notification reaches the anonymous webhook and refreshes the state`() {
        val purchaseToken = "tok-${System.nanoTime()}"
        val accountId = accountId()
        google.purchases[purchaseToken] = purchase(purchaseToken, accountId)
        submit(purchaseToken)
        google.purchases[purchaseToken] = purchase(purchaseToken, accountId, GooglePlayState.ON_HOLD)

        val delivered = notify(purchaseToken, "segredo")

        assertEquals(204, delivered.statusCode(), delivered.body())
        val me = mapper.readTree(request("GET", "/subscriptions/me").body())
        assertEquals("PAST_DUE", me["status"].stringValue())
        assertFalse(me["entitled"].booleanValue())
    }

    @Test
    fun `the webhook refuses a wrong token`() {
        assertEquals(401, notify("qualquer", "errado").statusCode())
    }

    private fun accountId(): String =
        mapper.readTree(request("GET", "/subscriptions/app-store/account-token").body())["appAccountToken"].stringValue()

    private fun submit(purchaseToken: String) = request(
        "POST",
        "/subscriptions/google-play/purchases",
        """{"productId":"app.saqz.organizador","purchaseToken":"$purchaseToken"}""",
    )

    private fun notify(purchaseToken: String, secret: String): HttpResponse<String> {
        val data = """{"version":"1.0","packageName":"app.saqz","eventTimeMillis":"1","subscriptionNotification":""" +
            """{"version":"1.0","notificationType":5,"purchaseToken":"$purchaseToken","subscriptionId":"app.saqz.organizador"}}"""
        val envelope = """{"message":{"data":"${Base64.getEncoder().encodeToString(data.toByteArray())}",""" +
            """"messageId":"${System.nanoTime()}"},"subscription":"projects/saquz-app/subscriptions/play"}"""
        return request("POST", "/webhooks/google-play?token=$secret", envelope, actor = null)
    }

    private fun purchase(purchaseToken: String, accountId: String, state: GooglePlayState = GooglePlayState.ACTIVE) =
        GooglePlayPurchase(
            purchaseToken = purchaseToken,
            productId = "app.saqz.organizador",
            basePlanId = "mensal",
            state = state,
            expiresAt = Instant.now().plus(Duration.ofDays(30)),
            autoRenew = true,
            canceledAt = null,
            obfuscatedAccountId = accountId,
            linkedPurchaseToken = null,
            latestOrderId = "GPA.$purchaseToken",
            acknowledged = false,
            testPurchase = true,
        )

    private fun request(method: String, path: String, body: String? = null, actor: String? = token): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .header("Content-Type", "application/json")
            .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        actor?.let { builder.header("Authorization", "Bearer $it") }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    class FakeGooglePlay : GooglePlayPurchasesApi {
        val purchases = ConcurrentHashMap<String, GooglePlayPurchase>()
        val acknowledged: MutableSet<String> = ConcurrentHashMap.newKeySet()

        override fun subscription(purchaseToken: String) =
            purchases[purchaseToken]?.let { GooglePlayLookup.Found(it) } ?: GooglePlayLookup.NotFound

        override fun acknowledge(productId: String, purchaseToken: String) {
            acknowledged += purchaseToken
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Fixture {
        @Bean @Primary fun verifier() = VerifyRequestIdentity {
            TokenVerification.Verified(RequestIdentity(it.value, "${it.value}@example.test", true, "Assinante"))
        }

        @Bean @Primary fun fakeGooglePlay() = FakeGooglePlay()
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
            registry.add("saqz.google-play.webhook-token") { "segredo" }
        }
    }
}
