package br.com.saqz.subscriptions.adapter.output.asaas

import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpAsaasGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: HttpAsaasGateway

    private val fixedClock = Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC)
    private val apiKey = "test-api-key-not-a-real-secret"

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        gateway = HttpAsaasGateway(
            settings = AsaasClientSettings(
                baseUrl = server.url("/v3").toString().trimEnd('/'),
                apiKey = apiKey,
            ),
            clock = fixedClock,
        )
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
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v3/customers", request.path)
        assertEquals(apiKey, request.getHeader("access_token"))
        assertEquals("saqz-backend", request.getHeader("User-Agent"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"name\":\"Bruno\""))
        assertTrue(body.contains("\"email\":\"bruno@example.com\""))
        assertTrue(body.contains("\"cpfCnpj\":\"52998224725\""))
        assertTrue(body.contains("\"externalReference\":\"$ownerId\""))
    }

    @Test
    fun `createCustomer throws on asaas 4xx`() {
        server.enqueue(
            json(
                400,
                """{"errors":[{"code":"invalid_cpfCnpj","description":"CPF inválido"}]}""",
            ),
        )

        val error = assertThrows<AsaasException> {
            gateway.createCustomer(UUID.randomUUID(), "X", "x@y.com", "000")
        }

        assertEquals(400, error.statusCode)
        assertTrue(error.message!!.contains("invalid_cpfCnpj"))
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
        )

        assertEquals("sub_XYZ", id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v3/subscriptions", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"customer\":\"cus_ABC\""))
        assertTrue(body.contains("\"billingType\":\"PIX\""))
        assertTrue(body.contains("\"value\":39.90"))
        assertTrue(body.contains("\"cycle\":\"MONTHLY\""))
        assertTrue(body.contains("\"nextDueDate\":\"2026-07-30\""))
        assertTrue(body.contains("\"description\":\"Assinatura Saqz TITULAR\""))
    }

    @Test
    fun `createSubscription posts CREDIT_CARD billing type when requested`() {
        server.enqueue(json(200, """{"id":"sub_CARD"}"""))

        val id = gateway.createSubscription(
            asaasCustomerId = "cus_CARD",
            plan = Plan.ORGANIZADOR,
            cycle = SubscriptionCycle.MONTHLY,
            valueCents = 5_990,
            billingType = AsaasBillingType.CREDIT_CARD,
        )

        assertEquals("sub_CARD", id)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"billingType\":\"CREDIT_CARD\""))
        assertTrue(body.contains("\"customer\":\"cus_CARD\""))
        assertTrue(body.contains("\"value\":59.90"))
    }

    @Test
    fun `createSubscription maps annual cycle to YEARLY`() {
        server.enqueue(json(200, """{"id":"sub_YEAR"}"""))

        gateway.createSubscription(
            "cus_1",
            Plan.ILIMITADO,
            SubscriptionCycle.ANNUAL,
            89_900,
            AsaasBillingType.PIX,
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"cycle\":\"YEARLY\""))
        assertTrue(body.contains("\"value\":899.00"))
    }

    @Test
    fun `createSubscription throws on asaas 4xx`() {
        server.enqueue(
            json(400, """{"errors":[{"code":"invalid_customer","description":"Customer inválido"}]}"""),
        )

        val error = assertThrows<AsaasException> {
            gateway.createSubscription(
                "bad",
                Plan.TITULAR,
                SubscriptionCycle.MONTHLY,
                100,
                AsaasBillingType.PIX,
            )
        }
        assertEquals(400, error.statusCode)
        assertTrue(error.message!!.contains("invalid_customer"))
    }

    @Test
    fun `updateSubscriptionValue puts only the new value without touching pending payments`() {
        server.enqueue(json(200, """{"id":"sub_1","value":59.90}"""))

        gateway.updateSubscriptionValue("sub_1", 5_990)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v3/subscriptions/sub_1", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"value\":59.90"))
        assertFalse(body.contains("updatePendingPayments"))
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
    fun `createOneOffCharge looks up by externalReference then posts with idempotency key`() {
        server.enqueue(json(200, """{"object":"list","data":[],"hasMore":false,"totalCount":0}"""))
        server.enqueue(json(200, """{"object":"payment","id":"pay_PRORATA"}"""))

        val id = gateway.createOneOffCharge(
            asaasCustomerId = "cus_ABC",
            valueCents = 1_250,
            description = "Upgrade prorata",
            idempotencyKey = "upgrade-user-1-from-TITULAR",
        )

        assertEquals("pay_PRORATA", id)

        val lookup = server.takeRequest()
        assertEquals("GET", lookup.method)
        assertEquals(
            "/v3/payments?externalReference=upgrade-user-1-from-TITULAR&limit=1",
            lookup.path,
        )

        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/v3/payments", create.path)
        val body = create.body.readUtf8()
        assertTrue(body.contains("\"customer\":\"cus_ABC\""))
        assertTrue(body.contains("\"billingType\":\"PIX\""))
        assertTrue(body.contains("\"value\":12.50"))
        assertTrue(body.contains("\"dueDate\":\"2026-07-30\""))
        assertTrue(body.contains("\"description\":\"Upgrade prorata\""))
        assertTrue(body.contains("\"externalReference\":\"upgrade-user-1-from-TITULAR\""))
    }

    @Test
    fun `createOneOffCharge reuses existing payment when externalReference already exists`() {
        server.enqueue(
            json(
                200,
                """{"object":"list","data":[{"id":"pay_EXISTING","externalReference":"upgrade-1"}],"hasMore":false,"totalCount":1}""",
            ),
        )

        val id = gateway.createOneOffCharge("cus_ABC", 1_250, "Upgrade prorata", "upgrade-1")

        assertEquals("pay_EXISTING", id)
        assertEquals(1, server.requestCount)
        val lookup = server.takeRequest()
        assertEquals("GET", lookup.method)
        assertTrue(lookup.path!!.contains("externalReference=upgrade-1"))
    }

    @Test
    fun `createOneOffCharge throws on asaas 4xx`() {
        server.enqueue(json(200, """{"object":"list","data":[]}"""))
        server.enqueue(
            json(400, """{"errors":[{"code":"invalid_value","description":"Valor inválido"}]}"""),
        )

        val error = assertThrows<AsaasException> {
            gateway.createOneOffCharge("cus_1", 0, "x", "key-1")
        }
        assertEquals(400, error.statusCode)
        assertTrue(error.message!!.contains("invalid_value"))
    }

    @Test
    fun `createOneOffCharge rejects blank idempotency key`() {
        assertThrows<IllegalArgumentException> {
            gateway.createOneOffCharge("cus_1", 100, "x", "  ")
        }
        assertEquals(0, server.requestCount)
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
            httpClient = interruptingClient,
            clock = fixedClock,
        )
        Thread.interrupted()

        val error = assertThrows<AsaasException> {
            interruptingGateway.createCustomer(UUID.randomUUID(), "X", "x@y.com", "000")
        }

        assertTrue(error.cause is InterruptedException)
        assertTrue(Thread.interrupted(), "interrupt flag must be restored")
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

        val result = gateway.regeneratePixPayload("pay_1")

        assertEquals(payload, result)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v3/payments/pay_1/pixQrCode", request.path)
        assertEquals(apiKey, request.getHeader("access_token"))
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
        assertTrue(error.message!!.contains("invalid_payment"))
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

    private fun json(code: Int, body: String): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .addHeader("Content-Type", "application/json")
            .setBody(body)
}
