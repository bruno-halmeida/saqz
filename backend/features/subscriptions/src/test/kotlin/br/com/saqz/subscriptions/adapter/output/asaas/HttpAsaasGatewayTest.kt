package br.com.saqz.subscriptions.adapter.output.asaas

import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.application.AsaasConcurrentOperationException
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpAsaasGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryAsaasIdempotencyStore
    private lateinit var gateway: HttpAsaasGateway

    private val fixedInstant = Instant.parse("2026-07-30T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val apiKey = "test-api-key-not-a-real-secret"

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = InMemoryAsaasIdempotencyStore()
        gateway = newGateway()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `createCustomer posts customer payload and returns asaas id`() {
        server.enqueue(json(200, """{"object":"customer","id":"cus_ABC123"}"""))

        val ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val id = gateway.createCustomer(ownerId, "Bruno", "bruno@example.com", "52998224725")

        assertEquals("cus_ABC123", id)
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/v3/customers", create.path)
        assertEquals(apiKey, create.getHeader("access_token"))
        val body = create.body.readUtf8()
        assertTrue(body.contains("\"name\":\"Bruno\""))
        assertTrue(body.contains("\"externalReference\":\"$ownerId\""))
        assertEquals("cus_ABC123", store.find("customer:$ownerId")?.resourceId)
    }

    @Test
    fun `createCustomer reuses store reservation for same owner without second post`() {
        val ownerId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        server.enqueue(json(200, """{"id":"cus_FIRST"}"""))

        val first = gateway.createCustomer(ownerId, "Bruno", "bruno@example.com", "52998224725")
        val second = gateway.createCustomer(ownerId, "Bruno", "bruno@example.com", "52998224725")

        assertEquals("cus_FIRST", first)
        assertEquals("cus_FIRST", second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `createCustomer concurrent live reservation throws without second post`() {
        val ownerId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        store.tryBegin("customer:$ownerId", fixedInstant)
        server.enqueue(json(200, emptyList()))

        assertThrows<AsaasConcurrentOperationException> {
            gateway.createCustomer(ownerId, "Bruno", "bruno@example.com", "52998224725")
        }
        assertEquals(1, server.requestCount)
        assertEquals("GET", server.takeRequest().method)
        assertNull(store.find("customer:$ownerId")?.resourceId)
    }

    @Test
    fun `createCustomer reconciles after ambiguous truncated success body`() {
        val ownerId = UUID.fromString("44444444-4444-4444-4444-444444444444")
        server.enqueue(json(200, """{}"""))
        server.enqueue(
            json(
                200,
                """{"object":"list","data":[{"id":"cus_RECOVERED","externalReference":"$ownerId"}]}""",
            ),
        )

        val id = gateway.createCustomer(ownerId, "Bruno", "bruno@example.com", "52998224725")

        assertEquals("cus_RECOVERED", id)
        assertEquals("cus_RECOVERED", store.find("customer:$ownerId")?.resourceId)
    }

    @Test
    fun `createCustomer throws on asaas 4xx and releases reservation`() {
        server.enqueue(
            json(
                400,
                """{"errors":[{"code":"invalid_cpfCnpj","description":"CPF inválido"}]}""",
            ),
        )

        val ownerId = UUID.randomUUID()
        val error = assertThrows<AsaasException> {
            gateway.createCustomer(ownerId, "X", "x@y.com", "000")
        }

        assertEquals(400, error.statusCode)
        assertNull(store.find("customer:$ownerId"))
    }

    @Test
    fun `createSubscription posts recurring subscription with PIX billing type`() {
        server.enqueue(json(200, """{"object":"subscription","id":"sub_XYZ"}"""))

        val id = gateway.createSubscription(
            asaasCustomerId = "cus_ABC",
            plan = Plan.TITULAR,
            cycle = SubscriptionCycle.MONTHLY,
            valueCents = 3_990,
            billingType = AsaasBillingType.PIX,
            idempotencyKey = "sub-owner-1-TITULAR",
        )

        assertEquals("sub_XYZ", id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("\"billingType\":\"PIX\""))
        assertEquals("sub_XYZ", store.find("sub-owner-1-TITULAR")?.resourceId)
    }

    @Test
    fun `createSubscription posts CREDIT_CARD billing type when requested`() {
        server.enqueue(json(200, """{"id":"sub_CARD"}"""))

        gateway.createSubscription(
            "cus_CARD", Plan.ORGANIZADOR, SubscriptionCycle.MONTHLY, 5_990,
            AsaasBillingType.CREDIT_CARD, "sub-card-1",
        )

        assertTrue(server.takeRequest().body.readUtf8().contains("\"billingType\":\"CREDIT_CARD\""))
    }

    @Test
    fun `createSubscription maps annual cycle to YEARLY`() {
        server.enqueue(json(200, """{"id":"sub_YEAR"}"""))

        gateway.createSubscription(
            "cus_1", Plan.ILIMITADO, SubscriptionCycle.ANNUAL, 89_900, AsaasBillingType.PIX, "sub-annual-1",
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"cycle\":\"YEARLY\""))
        assertTrue(body.contains("\"value\":899.00"))
    }

    @Test
    fun `createSubscription returns cached id without second asaas call`() {
        server.enqueue(json(200, """{"id":"sub_FIRST"}"""))

        val first = gateway.createSubscription(
            "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 3_990, AsaasBillingType.PIX, "sub-retry-1",
        )
        val second = gateway.createSubscription(
            "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 3_990, AsaasBillingType.PIX, "sub-retry-1",
        )

        assertEquals("sub_FIRST", first)
        assertEquals("sub_FIRST", second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `createSubscription on definitive 4xx releases reservation for retry`() {
        server.enqueue(
            json(400, """{"errors":[{"code":"invalid_customer","description":"Customer inválido"}]}"""),
        )

        assertThrows<AsaasException> {
            gateway.createSubscription(
                "bad", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-fail-1",
            )
        }
        assertNull(store.find("sub-fail-1"))

        server.enqueue(json(200, """{"id":"sub_RECOVERED"}"""))
        val recovered = gateway.createSubscription(
            "cus_ok", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-fail-1",
        )
        assertEquals("sub_RECOVERED", recovered)
    }

    @Test
    fun `createSubscription ambiguous failure reconciles existing asaas resource`() {
        server.enqueue(json(200, """{}"""))
        server.enqueue(
            json(
                200,
                """{"object":"list","data":[{"id":"sub_ALREADY","externalReference":"sub-ambig-1"}]}""",
            ),
        )

        val id = gateway.createSubscription(
            "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-ambig-1",
        )

        assertEquals("sub_ALREADY", id)
        assertEquals("sub_ALREADY", store.find("sub-ambig-1")?.resourceId)
    }

    @Test
    fun `createSubscription live reservation is not released early`() {
        store.tryBegin("sub-live", fixedInstant)
        server.enqueue(json(200, emptyList()))

        assertThrows<AsaasConcurrentOperationException> {
            gateway.createSubscription(
                "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-live",
            )
        }
        assertTrue(store.find("sub-live") != null)
        assertNull(store.find("sub-live")?.resourceId)
        // no POST — only optional reconcile GET
        assertTrue(server.requestCount <= 1)
        if (server.requestCount == 1) {
            assertEquals("GET", server.takeRequest().method)
        }
    }

    @Test
    fun `createSubscription abandoned old reservation is released then recreated`() {
        store.tryBegin("sub-old", fixedInstant.minus(Duration.ofSeconds(31)))
        server.enqueue(json(200, emptyList()))
        server.enqueue(json(200, """{"id":"sub_NEW"}"""))

        val id = gateway.createSubscription(
            "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-old",
        )

        assertEquals("sub_NEW", id)
        assertEquals(2, server.requestCount)
        assertEquals("GET", server.takeRequest().method)
        assertEquals("POST", server.takeRequest().method)
    }

    @Test
    fun `createSubscription recovers abandoned reservation via asaas reconciliation`() {
        store.tryBegin("sub-abandoned", fixedInstant.minus(Duration.ofSeconds(31)))
        server.enqueue(
            json(
                200,
                """{"object":"list","data":[{"id":"sub_ORPHAN","externalReference":"sub-abandoned"}]}""",
            ),
        )

        val id = gateway.createSubscription(
            "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-abandoned",
        )

        assertEquals("sub_ORPHAN", id)
        assertEquals("sub_ORPHAN", store.find("sub-abandoned")?.resourceId)
    }

    @Test
    fun `updateSubscriptionValue puts only the new value without touching pending payments`() {
        server.enqueue(json(200, """{"id":"sub_1","value":59.90}"""))

        gateway.updateSubscriptionValue("sub_1", 5_990)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"value\":59.90"))
        assertFalse(body.contains("updatePendingPayments"))
    }

    @Test
    fun `cancelSubscription deletes asaas subscription`() {
        server.enqueue(json(200, """{"deleted":true,"id":"sub_1"}"""))

        gateway.cancelSubscription("sub_1")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/v3/subscriptions/sub_1", request.path)
    }

    @Test
    fun `updateSubscriptionValue throws on asaas 4xx`() {
        server.enqueue(
            json(404, """{"errors":[{"code":"not_found","description":"Assinatura não encontrada"}]}"""),
        )

        val error = assertThrows<AsaasException> {
            gateway.updateSubscriptionValue("missing", 100)
        }
        assertEquals(404, error.statusCode)
    }

    @Test
    fun `createOneOffCharge posts pix payment with externalReference and returns charge id`() {
        server.enqueue(json(200, """{"object":"payment","id":"pay_PRORATA"}"""))

        val id = gateway.createOneOffCharge(
            "cus_ABC", 1_250, "Upgrade prorata", "upgrade-user-1-from-TITULAR",
        )

        assertEquals("pay_PRORATA", id)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"externalReference\":\"upgrade-user-1-from-TITULAR\""))
        assertEquals("pay_PRORATA", store.find("upgrade-user-1-from-TITULAR")?.resourceId)
    }

    @Test
    fun `createOneOffCharge returns cached id without second asaas call`() {
        server.enqueue(json(200, """{"id":"pay_FIRST"}"""))

        val first = gateway.createOneOffCharge("cus_ABC", 1_250, "Upgrade", "upgrade-1")
        val second = gateway.createOneOffCharge("cus_ABC", 1_250, "Upgrade", "upgrade-1")

        assertEquals("pay_FIRST", first)
        assertEquals("pay_FIRST", second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `createOneOffCharge ambiguous failure reconciles existing payment`() {
        server.enqueue(json(200, """{}"""))
        server.enqueue(
            json(
                200,
                """{"object":"list","data":[{"id":"pay_EXISTING","externalReference":"upgrade-ambig"}]}""",
            ),
        )

        val id = gateway.createOneOffCharge("cus_1", 100, "x", "upgrade-ambig")

        assertEquals("pay_EXISTING", id)
        assertEquals("pay_EXISTING", store.find("upgrade-ambig")?.resourceId)
    }

    @Test
    fun `createOneOffCharge throws on asaas 4xx`() {
        server.enqueue(
            json(400, """{"errors":[{"code":"invalid_value","description":"Valor inválido"}]}"""),
        )

        val error = assertThrows<AsaasException> {
            gateway.createOneOffCharge("cus_1", 0, "x", "key-1")
        }
        assertEquals(400, error.statusCode)
    }

    @Test
    fun `createOneOffCharge rejects blank idempotency key`() {
        assertThrows<IllegalArgumentException> {
            gateway.createOneOffCharge("cus_1", 100, "x", "  ")
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `regeneratePixPayload gets qr code and returns copia-e-cola payload`() {
        val payload =
            "00020101021226730014br.gov.bcb.pix2551pix-h.asaas.com/pixqrcode/cobv/pay_1"
        server.enqueue(
            json(
                200,
                """{"encodedImage":"img","payload":"$payload","expirationDate":"2026-07-30 23:59:59"}""",
            ),
        )

        assertEquals(payload, gateway.regeneratePixPayload("pay_1"))
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `regeneratePixPayload throws on asaas 4xx`() {
        server.enqueue(
            json(400, """{"errors":[{"code":"invalid_payment","description":"Cobrança inválida"}]}"""),
        )

        val error = assertThrows<AsaasException> {
            gateway.regeneratePixPayload("pay_bad")
        }
        assertEquals(400, error.statusCode)
    }

    @Test
    fun `settings require api key from environment without hardcoding secrets`() {
        val settings = AsaasClientSettings.fromProperties { key ->
            when (key) {
                "SAQZ_ASAAS_API_KEY" -> "from-env-key"
                "SAQZ_ASAAS_BASE_URL" -> "https://api.asaas.com/v3"
                else -> null
            }
        }

        assertEquals("from-env-key", settings.apiKey)
        assertEquals("https://api.asaas.com/v3", settings.baseUrl)
    }

    @Test
    fun `settings fail when api key is missing`() {
        assertThrows<IllegalStateException> {
            AsaasClientSettings.fromProperties { null }
        }
    }

    @Test
    fun `default http client configures connect timeout`() {
        assertEquals(HttpAsaasGateway.CONNECT_TIMEOUT, HttpAsaasGateway.defaultHttpClient().connectTimeout().get())
    }

    @Test
    fun `interrupted request restores interrupt flag`() {
        val interruptingClient = object : java.net.http.HttpClient() {
            override fun cookieHandler() = java.util.Optional.empty<java.net.CookieHandler>()
            override fun connectTimeout() = java.util.Optional.of(HttpAsaasGateway.CONNECT_TIMEOUT)
            override fun followRedirects() = Redirect.NEVER
            override fun proxy() = java.util.Optional.empty<java.net.ProxySelector>()
            override fun sslContext() = javax.net.ssl.SSLContext.getDefault()
            override fun sslParameters() = sslContext().defaultSSLParameters
            override fun authenticator() = java.util.Optional.empty<java.net.Authenticator>()
            override fun version() = Version.HTTP_1_1
            override fun executor() = java.util.Optional.empty<java.util.concurrent.Executor>()
            override fun <T : Any?> send(
                request: java.net.http.HttpRequest,
                responseBodyHandler: java.net.http.HttpResponse.BodyHandler<T>,
            ): java.net.http.HttpResponse<T> = throw InterruptedException("cancelled")
            override fun <T : Any?> sendAsync(
                request: java.net.http.HttpRequest,
                responseBodyHandler: java.net.http.HttpResponse.BodyHandler<T>,
            ) = throw UnsupportedOperationException()
            override fun <T : Any?> sendAsync(
                request: java.net.http.HttpRequest,
                responseBodyHandler: java.net.http.HttpResponse.BodyHandler<T>,
                pushPromiseHandler: java.net.http.HttpResponse.PushPromiseHandler<T>?,
            ) = throw UnsupportedOperationException()
            override fun newWebSocketBuilder() = throw UnsupportedOperationException()
        }
        val interruptingGateway = HttpAsaasGateway(
            settings = AsaasClientSettings(
                baseUrl = server.url("/v3").toString().trimEnd('/'),
                apiKey = apiKey,
            ),
            idempotencyStore = InMemoryAsaasIdempotencyStore(),
            httpClient = interruptingClient,
            clock = fixedClock,
            idempotencyPollWait = { },
        )
        Thread.interrupted()

        val error = assertThrows<AsaasException> {
            interruptingGateway.createCustomer(UUID.randomUUID(), "X", "x@y.com", "000")
        }

        assertTrue(error.cause is InterruptedException)
        assertTrue(Thread.interrupted(), "interrupt flag must be restored")
    }

    private fun newGateway(
        abandonAfter: Duration = HttpAsaasGateway.DEFAULT_ABANDON_AFTER,
    ): HttpAsaasGateway =
        HttpAsaasGateway(
            settings = AsaasClientSettings(
                baseUrl = server.url("/v3").toString().trimEnd('/'),
                apiKey = apiKey,
            ),
            idempotencyStore = store,
            clock = fixedClock,
            idempotencyPollWait = { },
            abandonAfter = abandonAfter,
        )

    private fun emptyList(): String = """{"object":"list","data":[],"hasMore":false,"totalCount":0}"""

    private fun json(code: Int, body: String): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .addHeader("Content-Type", "application/json")
            .setBody(body)
}
