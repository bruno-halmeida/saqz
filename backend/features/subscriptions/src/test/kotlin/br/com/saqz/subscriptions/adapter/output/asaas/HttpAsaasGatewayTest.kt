package br.com.saqz.subscriptions.adapter.output.asaas

import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.application.AsaasConcurrentOperationException
import br.com.saqz.subscriptions.application.AsaasGateway
import br.com.saqz.subscriptions.application.CardDeclinedException
import br.com.saqz.subscriptions.application.PaymentMethodUnavailableException
import br.com.saqz.subscriptions.application.CreditCardDetails
import br.com.saqz.subscriptions.application.CreditCardHolderInfo
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
    private lateinit var gateway: AsaasGateway

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

        assertEquals("sub_XYZ", id.asaasSubscriptionId)
        assertNull(id.creditCard)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("\"billingType\":\"PIX\""))
        assertEquals("sub_XYZ", store.find("sub-owner-1-TITULAR")?.resourceId)
    }

    @Test
    fun `createSubscriptionStartingAt sends the future nextDueDate and the card token without card data`() {
        server.enqueue(json(200, """{"id":"sub_REACT"}"""))

        val id = gateway.createSubscriptionStartingAt(
            asaasCustomerId = "cus_ABC", plan = Plan.TITULAR, cycle = SubscriptionCycle.MONTHLY,
            valueCents = 3_990, billingType = AsaasBillingType.CREDIT_CARD,
            nextDueDate = java.time.LocalDate.parse("2026-11-12"), idempotencyKey = "subscription-reactivate:o:sub_old",
            creditCardToken = "tok_123", remoteIp = "203.0.113.9",
        )

        assertEquals("sub_REACT", id)
        val request = server.takeRequest()
        assertEquals("/v3/subscriptions", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"nextDueDate\":\"2026-11-12\""))
        assertTrue(body.contains("\"creditCardToken\":\"tok_123\""))
        assertTrue(body.contains("\"remoteIp\":\"203.0.113.9\""))
        assertFalse(body.contains("\"creditCard\":"))
        assertEquals("sub_REACT", store.find("subscription-reactivate:o:sub_old")?.resourceId)
    }

    @Test
    fun `createSubscriptionStartingAt with PIX has no card fields and a retry does not post twice`() {
        server.enqueue(json(200, """{"id":"sub_PIX"}"""))
        fun call() = gateway.createSubscriptionStartingAt(
            "cus_ABC", Plan.TITULAR, SubscriptionCycle.ANNUAL, 39_990, AsaasBillingType.PIX,
            java.time.LocalDate.parse("2027-01-10"), "subscription-reactivate:o:sub_pix",
        )

        assertEquals("sub_PIX", call())
        assertEquals("sub_PIX", call())

        assertEquals(1, server.requestCount)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"billingType\":\"PIX\""))
        assertTrue(body.contains("\"cycle\":\"YEARLY\""))
        assertFalse(body.contains("creditCardToken"))
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
    fun `createSubscription includes creditCard, creditCardHolderInfo and remoteIp in the body`() {
        server.enqueue(json(200, """{"id":"sub_CARD_FULL","creditCard":{"creditCardToken":"tok_1","creditCardNumber":"1111","creditCardBrand":"VISA"}}"""))

        val result = gateway.createSubscription(
            asaasCustomerId = "cus_CARD",
            plan = Plan.ORGANIZADOR,
            cycle = SubscriptionCycle.MONTHLY,
            valueCents = 5_990,
            billingType = AsaasBillingType.CREDIT_CARD,
            idempotencyKey = "sub-card-full-1",
            creditCard = sampleCreditCard(),
            creditCardHolderInfo = sampleCreditCardHolderInfo(),
            remoteIp = "203.0.113.5",
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"holderName\":\"Bruno Almeida\""))
        assertTrue(body.contains("\"number\":\"4111111111111111\""))
        assertTrue(body.contains("\"postalCode\":\"01310930\""))
        assertTrue(body.contains("\"remoteIp\":\"203.0.113.5\""))
        assertEquals("sub_CARD_FULL", result.asaasSubscriptionId)
        assertEquals("tok_1", result.creditCard?.token)
        assertEquals("1111", result.creditCard?.lastFourDigits)
        assertEquals("VISA", result.creditCard?.brand)
    }

    @Test
    fun `createSubscription maps a declined card to CardDeclinedException, not a generic AsaasException`() {
        server.enqueue(
            json(400, """{"errors":[{"code":"invalid_creditCard","description":"Transação não autorizada."}]}"""),
        )

        val error = assertThrows<CardDeclinedException> {
            gateway.createSubscription(
                asaasCustomerId = "cus_CARD",
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                valueCents = 3_990,
                billingType = AsaasBillingType.CREDIT_CARD,
                idempotencyKey = "sub-card-declined-1",
                creditCard = sampleCreditCard(),
                creditCardHolderInfo = sampleCreditCardHolderInfo(),
                remoteIp = "203.0.113.5",
            )
        }

        assertEquals("invalid_creditCard", error.asaasCode)
        assertEquals("Transação não autorizada.", error.asaasDescription)
        assertNull(store.find("sub-card-declined-1"))
    }

    @Test
    fun `createSubscription does not map an unrelated 4xx like an invalid api key to CardDeclinedException`() {
        server.enqueue(
            json(401, """{"errors":[{"code":"invalid_access_token","description":"Chave de API inválida."}]}"""),
        )

        val error = assertThrows<RuntimeException> {
            gateway.createSubscription(
                asaasCustomerId = "cus_CARD",
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                valueCents = 3_990,
                billingType = AsaasBillingType.CREDIT_CARD,
                idempotencyKey = "sub-card-authfail-1",
                creditCard = sampleCreditCard(),
                creditCardHolderInfo = sampleCreditCardHolderInfo(),
                remoteIp = "203.0.113.5",
            )
        }

        val asaasError = error as AsaasException
        assertEquals(401, asaasError.statusCode)
        assertFalse(CardDeclinedException::class.java.isInstance(error))
    }

    @Test
    fun `createSubscription maps the issuer refusal 400 invalid_action to CardDeclinedException`() {
        server.enqueue(
            json(
                400,
                """{"errors":[{"code":"invalid_action","description":"Transação não autorizada. Verifique os dados do cartão de crédito e tente novamente."}]}""",
            ),
        )

        val error = assertThrows<CardDeclinedException> { createCardSubscription("sub-card-invalid-action-1") }

        assertEquals("invalid_action", error.asaasCode)
        assertTrue(error.asaasDescription.startsWith("Transação não autorizada"))
        assertNull(store.find("sub-card-invalid-action-1"))
    }

    @Test
    fun `createSubscription keeps an invalid_action with another description as a generic AsaasException`() {
        server.enqueue(
            json(400, """{"errors":[{"code":"invalid_action","description":"Não foi possível executar esta ação."}]}"""),
        )

        val error = assertThrows<AsaasException> { createCardSubscription("sub-card-invalid-action-2") }

        assertEquals(400, error.statusCode)
    }

    @Test
    fun `createSubscription does not map 429 with a not-authorized text to CardDeclinedException`() {
        server.enqueue(
            json(429, """{"errors":[{"code":"invalid_action","description":"Transação não autorizada."}]}"""),
        )

        val error = assertThrows<AsaasException> { createCardSubscription("sub-card-ratelimit-1") }

        assertEquals(429, error.statusCode)
    }

    @Test
    fun `createSubscription maps 400 invalid_billingType to PaymentMethodUnavailableException for pix`() {
        server.enqueue(
            json(
                400,
                """{"errors":[{"code":"invalid_billingType","description":"A forma de pagamento não é permitida para assinaturas."}]}""",
            ),
        )

        val error = assertThrows<PaymentMethodUnavailableException> {
            gateway.createSubscription(
                "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 3_990, AsaasBillingType.PIX, "sub-pix-unavailable-1",
            )
        }

        assertEquals(AsaasBillingType.PIX, error.billingType)
        assertEquals("invalid_billingType", error.asaasCode)
        assertNull(store.find("sub-pix-unavailable-1"))
    }

    @Test
    fun `createSubscription does not map 401 to PaymentMethodUnavailableException`() {
        server.enqueue(
            json(401, """{"errors":[{"code":"invalid_billingType","description":"x"}]}"""),
        )

        val error = assertThrows<AsaasException> {
            gateway.createSubscription(
                "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 3_990, AsaasBillingType.PIX, "sub-pix-401",
            )
        }

        assertEquals(401, error.statusCode)
    }

    private fun createCardSubscription(idempotencyKey: String) = gateway.createSubscription(
        asaasCustomerId = "cus_CARD",
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        valueCents = 3_990,
        billingType = AsaasBillingType.CREDIT_CARD,
        idempotencyKey = idempotencyKey,
        creditCard = sampleCreditCard(),
        creditCardHolderInfo = sampleCreditCardHolderInfo(),
        remoteIp = "203.0.113.5",
    )

    @Test
    fun `createSubscription uses 60s timeout for credit card billing but 15s for pix`() {
        val timeouts = mutableListOf<Duration?>()
        val delegate = HttpClient.newHttpClient()
        val capturingClient = capturingHttpClient(delegate) { timeouts += it }
        val capturingGateway = HttpAsaasGateway(
            settings = AsaasClientSettings(baseUrl = server.url("/v3").toString().trimEnd('/'), apiKey = apiKey),
            idempotencyStore = InMemoryAsaasIdempotencyStore(),
            httpClient = capturingClient,
            clock = fixedClock,
            idempotencyPollWait = { },
        )
        server.enqueue(json(200, """{"id":"sub_PIX_TIMEOUT"}"""))
        server.enqueue(json(200, """{"id":"sub_CARD_TIMEOUT"}"""))

        capturingGateway.createSubscription(
            "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-timeout-pix",
        )
        capturingGateway.createSubscription(
            asaasCustomerId = "cus_1",
            plan = Plan.TITULAR,
            cycle = SubscriptionCycle.MONTHLY,
            valueCents = 100,
            billingType = AsaasBillingType.CREDIT_CARD,
            idempotencyKey = "sub-timeout-card",
            creditCard = sampleCreditCard(),
            creditCardHolderInfo = sampleCreditCardHolderInfo(),
            remoteIp = "203.0.113.5",
        )

        assertEquals(listOf(Duration.ofSeconds(15), Duration.ofSeconds(60)), timeouts)
    }

    private fun sampleCreditCard() = CreditCardDetails(
        holderName = "Bruno Almeida",
        number = "4111111111111111",
        expiryMonth = "12",
        expiryYear = "2030",
        ccv = "123",
    )

    private fun sampleCreditCardHolderInfo() = CreditCardHolderInfo(
        name = "Bruno Almeida",
        email = "bruno@example.com",
        cpfCnpj = "52998224725",
        postalCode = "01310930",
        addressNumber = "100",
        phone = "11999999999",
    )

    /** Delegates the real call to [delegate] but first records the timeout the caller asked for. */
    private fun capturingHttpClient(delegate: HttpClient, onSend: (Duration?) -> Unit): HttpClient =
        object : HttpClient() {
            override fun cookieHandler() = delegate.cookieHandler()
            override fun connectTimeout() = delegate.connectTimeout()
            override fun followRedirects(): Redirect = delegate.followRedirects()
            override fun proxy() = delegate.proxy()
            override fun sslContext(): javax.net.ssl.SSLContext = delegate.sslContext()
            override fun sslParameters(): javax.net.ssl.SSLParameters = delegate.sslParameters()
            override fun authenticator() = delegate.authenticator()
            override fun version(): Version = delegate.version()
            override fun executor() = delegate.executor()
            override fun <T : Any?> send(
                request: HttpRequest,
                responseBodyHandler: HttpResponse.BodyHandler<T>,
            ): HttpResponse<T> {
                onSend(request.timeout().orElse(null))
                return delegate.send(request, responseBodyHandler)
            }
            override fun <T : Any?> sendAsync(
                request: HttpRequest,
                responseBodyHandler: HttpResponse.BodyHandler<T>,
            ) = throw UnsupportedOperationException()
            override fun <T : Any?> sendAsync(
                request: HttpRequest,
                responseBodyHandler: HttpResponse.BodyHandler<T>,
                pushPromiseHandler: HttpResponse.PushPromiseHandler<T>?,
            ) = throw UnsupportedOperationException()
            override fun newWebSocketBuilder() = throw UnsupportedOperationException()
        }

    @Test
    fun `createSubscription maps annual cycle to YEARLY`() {
        server.enqueue(json(200, """{"id":"sub_YEAR"}"""))

        gateway.createSubscription(
            "cus_1", Plan.ILIMITADO, SubscriptionCycle.ANNUAL, 80_910, AsaasBillingType.PIX, "sub-annual-1",
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"cycle\":\"YEARLY\""))
        assertTrue(body.contains("\"value\":809.10"))
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

        assertEquals("sub_FIRST", first.asaasSubscriptionId)
        assertEquals("sub_FIRST", second.asaasSubscriptionId)
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
        assertEquals("sub_RECOVERED", recovered.asaasSubscriptionId)
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

        assertEquals("sub_ALREADY", id.asaasSubscriptionId)
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

        assertEquals("sub_NEW", id.asaasSubscriptionId)
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

        assertEquals("sub_ORPHAN", id.asaasSubscriptionId)
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
    fun `cancelSubscription accepts already removed subscriptions on retry`() {
        server.enqueue(json(404, """{"errors":[{"code":"not_found","description":"Not found"}]}"""))
        gateway.cancelSubscription("sub_removed")
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/v3/subscriptions/sub_removed", request.path)
    }

    @Test
    fun `cancelSubscription preserves provider failures so deletion can retry`() {
        server.enqueue(json(503, """{"errors":[{"code":"unavailable","description":"Unavailable"}]}"""))
        val error = assertThrows<AsaasException> { gateway.cancelSubscription("sub_retry") }
        assertEquals(503, error.statusCode)
    }

    @Test
    fun `cancelPayment deletes the pending charge and accepts one that is already gone`() {
        server.enqueue(json(200, """{"deleted":true,"id":"pay_1"}"""))
        gateway.cancelPayment("pay_1")
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/v3/payments/pay_1", request.path)

        server.enqueue(json(404, """{"errors":[{"code":"not_found","description":"Not found"}]}"""))
        gateway.cancelPayment("pay_gone")

        server.enqueue(json(400, """{"errors":[{"code":"invalid_action","description":"Cobrança já paga"}]}"""))
        assertThrows<AsaasException> { gateway.cancelPayment("pay_paid") }
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
    fun `regeneratePixPayload gets qr code and returns copia-e-cola payload with image`() {
        val payload =
            "00020101021226730014br.gov.bcb.pix2551pix-h.asaas.com/pixqrcode/cobv/pay_1"
        server.enqueue(
            json(
                200,
                """{"encodedImage":"img","payload":"$payload","expirationDate":"2026-07-30 23:59:59"}""",
            ),
        )

        val pix = gateway.regeneratePixPayload("pay_1")

        assertEquals(payload, pix.payload)
        assertEquals("img", pix.encodedImage)
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `regeneratePixPayload tolerates missing encodedImage`() {
        server.enqueue(json(200, """{"payload":"000201PIX-SEM-QR"}"""))

        val pix = gateway.regeneratePixPayload("pay_1")

        assertEquals("000201PIX-SEM-QR", pix.payload)
        assertNull(pix.encodedImage)
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

    // --- Consultas de cobranca ---

    @Test
    fun `findLatestPaymentIdForSubscription asks for the newest charge of the subscription`() {
        server.enqueue(json(200, """{"object":"list","data":[{"id":"pay_LAST"}]}"""))

        val id = gateway.findLatestPaymentIdForSubscription("sub 1")

        assertEquals("pay_LAST", id)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v3/payments?subscription=sub+1&limit=1&offset=0", request.path)
    }

    @Test
    fun `findLatestPaymentIdForSubscription is null when there is no charge or no usable id`() {
        server.enqueue(json(200, emptyList()))
        server.enqueue(json(200, """{"data":[{"id":""}]}"""))
        server.enqueue(json(200, """{"data":[{"status":"PENDING"}]}"""))
        server.enqueue(json(200, """{"data":"nao-e-lista"}"""))
        server.enqueue(json(200, """{}"""))

        repeat(5) { assertNull(gateway.findLatestPaymentIdForSubscription("sub_1")) }
    }

    @Test
    fun `findPaymentInvoiceUrl returns the invoice link and falls back to the bank slip`() {
        server.enqueue(json(200, """{"id":"pay_1","invoiceUrl":"https://asaas.test/i/1","bankSlipUrl":"https://asaas.test/b/1"}"""))
        server.enqueue(json(200, """{"id":"pay_1","bankSlipUrl":"https://asaas.test/b/1"}"""))
        server.enqueue(json(200, """{"id":"pay_1","invoiceUrl":"","bankSlipUrl":"https://asaas.test/b/1"}"""))
        server.enqueue(json(200, """{"id":"pay_1"}"""))
        server.enqueue(json(200, """{"id":"pay_1","invoiceUrl":"","bankSlipUrl":""}"""))

        assertEquals("https://asaas.test/i/1", gateway.findPaymentInvoiceUrl("pay_1"))
        assertEquals("/v3/payments/pay_1", server.takeRequest().path)
        assertEquals("https://asaas.test/b/1", gateway.findPaymentInvoiceUrl("pay_1"))
        assertEquals("https://asaas.test/b/1", gateway.findPaymentInvoiceUrl("pay_1"))
        assertNull(gateway.findPaymentInvoiceUrl("pay_1"))
        assertNull(gateway.findPaymentInvoiceUrl("pay_1"))
    }

    @Test
    fun `findPayment returns the id, status and invoice link of the charge`() {
        server.enqueue(json(200, """{"id":"pay_1","status":"RECEIVED","invoiceUrl":"https://asaas.test/i/1"}"""))

        val payment = gateway.findPayment("pay_1")

        assertEquals("pay_1", payment?.id)
        assertEquals("RECEIVED", payment?.status)
        assertEquals("https://asaas.test/i/1", payment?.invoiceUrl)
    }

    @Test
    fun `findPayment is null without an id and keeps a missing status null`() {
        server.enqueue(json(200, """{"status":"RECEIVED"}"""))
        server.enqueue(json(200, """{"id":""}"""))
        server.enqueue(json(200, """{"id":"pay_2","status":""}"""))
        server.enqueue(json(200, """{"id":"pay_3"}"""))

        assertNull(gateway.findPayment("pay_x"))
        assertNull(gateway.findPayment("pay_x"))
        assertNull(gateway.findPayment("pay_2")?.status)
        assertEquals("pay_3", gateway.findPayment("pay_3")?.id)
    }

    // --- Cabecalhos e erros ---

    @Test
    fun `every request carries the api key, the user agent and asks for json`() {
        server.enqueue(json(200, """{"id":"pay_1"}"""))

        gateway.findPayment("pay_1")

        val request = server.takeRequest()
        assertEquals(apiKey, request.getHeader("access_token"))
        assertEquals(AsaasClientSettings.DEFAULT_USER_AGENT, request.getHeader("User-Agent"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertNull(request.getHeader("Content-Type"))
    }

    @Test
    fun `requests with a body are sent as json with the configured user agent`() {
        val custom = HttpAsaasGateway(
            settings = AsaasClientSettings(
                baseUrl = server.url("/v3").toString().trimEnd('/'),
                apiKey = apiKey,
                userAgent = "saqz-teste/1.0",
            ),
            idempotencyStore = store,
            clock = fixedClock,
            idempotencyPollWait = { },
        )
        server.enqueue(json(200, """{"id":"sub_1"}"""))

        custom.updateSubscriptionValue("sub_1", 5_990)

        val request = server.takeRequest()
        assertEquals("saqz-teste/1.0", request.getHeader("User-Agent"))
        assertEquals("application/json", request.getHeader("Content-Type"))
    }

    @Test
    fun `status 299 is success and 300 is an error`() {
        server.enqueue(json(299, """{"id":"pay_1"}"""))
        server.enqueue(json(300, """{"errors":[{"code":"redirect","description":"Movido"}]}"""))

        assertEquals("pay_1", gateway.findPayment("pay_1")?.id)
        val error = assertThrows<AsaasException> { gateway.findPayment("pay_1") }
        assertEquals(300, error.statusCode)
    }

    @Test
    fun `an empty success body is read as an empty object`() {
        server.enqueue(json(200, ""))

        assertNull(gateway.findPayment("pay_1"))
    }

    @Test
    fun `error message lists every asaas error and the first one is kept on the exception`() {
        server.enqueue(
            json(
                422,
                """{"errors":[{"code":"invalid_a","description":"Primeiro"},{"code":"invalid_b","description":"Segundo"}]}""",
            ),
        )

        val error = assertThrows<AsaasException> { gateway.findPayment("pay_1") }

        assertEquals("Asaas GET /payments/pay_1 failed with HTTP 422: invalid_a: Primeiro; invalid_b: Segundo", error.message)
        assertEquals("invalid_a", error.errorCode)
        assertEquals("Primeiro", error.errorDescription)
    }

    @Test
    fun `error message shows the raw body when it has no asaas errors and flags an empty one`() {
        server.enqueue(json(502, "<html>bad gateway</html>"))
        server.enqueue(json(503, ""))
        server.enqueue(json(500, """{"errors":[]}"""))

        val html = assertThrows<AsaasException> { gateway.findPayment("pay_1") }
        assertEquals("Asaas GET /payments/pay_1 failed with HTTP 502: <html>bad gateway</html>", html.message)
        assertNull(html.errorCode)

        val empty = assertThrows<AsaasException> { gateway.findPayment("pay_1") }
        assertEquals("Asaas GET /payments/pay_1 failed with HTTP 503: (empty body)", empty.message)

        val noErrors = assertThrows<AsaasException> { gateway.findPayment("pay_1") }
        assertEquals("""Asaas GET /payments/pay_1 failed with HTTP 500: {"errors":[]}""", noErrors.message)
    }

    @Test
    fun `a long raw error body is cut at 500 characters`() {
        server.enqueue(json(502, "x".repeat(800)))

        val error = assertThrows<AsaasException> { gateway.findPayment("pay_1") }

        assertEquals("Asaas GET /payments/pay_1 failed with HTTP 502: ${"x".repeat(500)}", error.message)
    }

    @Test
    fun `an error without code or description falls back to unknown and an empty description`() {
        server.enqueue(json(400, """{"errors":[{}]}"""))

        val error = assertThrows<AsaasException> { gateway.findPayment("pay_1") }

        assertEquals("unknown", error.errorCode)
        assertEquals("", error.errorDescription)
        assertEquals("Asaas GET /payments/pay_1 failed with HTTP 400: unknown", error.message)
    }

    // --- Fronteiras da recusa de cartao ---

    private fun cardFailure(status: Int, code: String, description: String): Throwable {
        server.enqueue(json(status, """{"errors":[{"code":"$code","description":"$description"}]}"""))
        return assertThrows<Throwable> { createCardSubscription("sub-card-$status-$code-${description.length}") }
    }

    @Test
    fun `card decline is recognized for 4xx up to 499 and by code variants`() {
        assertTrue(cardFailure(499, "invalid_creditCard", "Recusado") is CardDeclinedException)
        assertTrue(cardFailure(400, "invalid_creditCardHolderInfo", "Titular inválido") is CardDeclinedException)
        assertTrue(cardFailure(400, "INVALID_CREDITCARD", "Recusado") is CardDeclinedException)
    }

    @Test
    fun `a 5xx with a card decline code stays a generic asaas failure`() {
        val failure = cardFailure(500, "invalid_creditCard", "Recusado")

        assertFalse(failure is CardDeclinedException)
        assertEquals(500, (failure as AsaasException).statusCode)
    }

    @Test
    fun `the not-authorized text only counts for invalid_action with status 400 on the card path`() {
        assertTrue(cardFailure(400, "invalid_action", "Transação não autorizada.") is CardDeclinedException)
        assertFalse(cardFailure(400, "invalid_action", "Ação inválida.") is CardDeclinedException)
        assertFalse(cardFailure(422, "invalid_action", "Transação não autorizada.") is CardDeclinedException)
    }

    @Test
    fun `a card decline code on a pix subscription is a generic failure`() {
        server.enqueue(json(400, """{"errors":[{"code":"invalid_creditCard","description":"Recusado"}]}"""))

        val error = assertThrows<AsaasException> {
            gateway.createSubscription(
                "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-pix-decline",
            )
        }

        assertEquals("invalid_creditCard", error.errorCode)
    }

    @Test
    fun `billing type refusal is recognized regardless of case and only for 400`() {
        server.enqueue(json(400, """{"errors":[{"code":"INVALID_BILLINGTYPE","description":"Forma não permitida"}]}"""))
        server.enqueue(json(422, """{"errors":[{"code":"invalid_billingType","description":"Forma não permitida"}]}"""))

        val unavailable = assertThrows<PaymentMethodUnavailableException> {
            gateway.createSubscription(
                "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-bt-1",
            )
        }
        assertEquals(AsaasBillingType.PIX, unavailable.billingType)

        val other = assertThrows<AsaasException> {
            gateway.createSubscription(
                "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-bt-2",
            )
        }
        assertEquals(422, other.statusCode)
    }

    // --- Idempotencia: espera, reserva abandonada e falha definitiva ---

    private fun gatewayWith(
        maxPolls: Int = HttpAsaasGateway.DEFAULT_MAX_IDEMPOTENCY_POLLS,
        wait: (Int) -> Unit = { },
        abandonAfter: Duration = HttpAsaasGateway.DEFAULT_ABANDON_AFTER,
    ) = HttpAsaasGateway(
        settings = AsaasClientSettings(baseUrl = server.url("/v3").toString().trimEnd('/'), apiKey = apiKey),
        idempotencyStore = store,
        clock = fixedClock,
        maxIdempotencyPolls = maxPolls,
        idempotencyPollWait = wait,
        abandonAfter = abandonAfter,
    )

    @Test
    fun `a live reservation is polled maxIdempotencyPolls times and waits between polls only`() {
        store.tryBegin("sub-wait", fixedInstant)
        server.enqueue(json(200, emptyList()))
        val waits = mutableListOf<Int>()

        assertThrows<AsaasConcurrentOperationException> {
            gatewayWith(maxPolls = 4, wait = { waits += it }).createSubscription(
                "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-wait",
            )
        }

        assertEquals(listOf(0, 1, 2), waits)
    }

    @Test
    fun `the other worker finishing during the poll returns its resource without any request`() {
        store.tryBegin("sub-late", fixedInstant)

        val creation = gatewayWith(wait = { attempt -> if (attempt == 1) store.complete("sub-late", "sub_LATE") })
            .createSubscription(
                "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-late",
            )

        assertEquals("sub_LATE", creation.asaasSubscriptionId)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `no poll waits when there is a single attempt`() {
        store.tryBegin("sub-single", fixedInstant)
        server.enqueue(json(200, emptyList()))
        val waits = mutableListOf<Int>()

        assertThrows<AsaasConcurrentOperationException> {
            gatewayWith(maxPolls = 1, wait = { waits += it }).createSubscription(
                "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-single",
            )
        }

        assertTrue(waits.isEmpty())
    }

    @Test
    fun `a reservation exactly as old as the abandon limit is released and recreated`() {
        store.tryBegin("sub-edge", fixedInstant.minus(Duration.ofSeconds(30)))
        server.enqueue(json(200, emptyList()))
        server.enqueue(json(200, """{"id":"sub_EDGE"}"""))

        val creation = gateway.createSubscription(
            "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-edge",
        )

        assertEquals("sub_EDGE", creation.asaasSubscriptionId)
    }

    @Test
    fun `a reservation one second younger than the abandon limit is kept`() {
        store.tryBegin("sub-young", fixedInstant.minus(Duration.ofSeconds(29)))
        server.enqueue(json(200, emptyList()))

        assertThrows<AsaasConcurrentOperationException> {
            gateway.createSubscription(
                "cus_1", Plan.TITULAR, SubscriptionCycle.MONTHLY, 100, AsaasBillingType.PIX, "sub-young",
            )
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 4xx failure is definitive and does not look for a charge already created`() {
        server.enqueue(json(499, """{"errors":[{"code":"x","description":"y"}]}"""))

        assertThrows<AsaasException> { gateway.createOneOffCharge("cus_1", 100, "Teste", "charge-4xx") }

        assertEquals(1, server.requestCount)
        assertNull(store.find("charge-4xx"))
    }

    @Test
    fun `a 5xx failure looks for a charge Asaas may have created before giving up`() {
        server.enqueue(json(500, """{"errors":[{"code":"internal","description":"erro"}]}"""))
        server.enqueue(json(200, emptyList()))

        val error = assertThrows<AsaasException> { gateway.createOneOffCharge("cus_1", 100, "Teste", "charge-5xx") }

        assertEquals(500, error.statusCode)
        assertEquals(2, server.requestCount)
        assertEquals("POST", server.takeRequest().method)
        val lookup = server.takeRequest()
        assertEquals("GET", lookup.method)
        assertEquals("/v3/payments?externalReference=charge-5xx&limit=1", lookup.path)
        assertNull(store.find("charge-5xx"))
    }

    @Test
    fun `a 5xx failure followed by a lookup that also fails rethrows the original error`() {
        server.enqueue(json(500, """{"errors":[{"code":"internal","description":"erro"}]}"""))
        server.enqueue(json(503, """{"errors":[{"code":"down","description":"fora"}]}"""))

        val error = assertThrows<AsaasException> { gateway.createOneOffCharge("cus_1", 100, "Teste", "charge-both") }

        assertEquals(500, error.statusCode)
    }

    @Test
    fun `the default poll wait sleeps 50 ms times the attempt number`() {
        val startedAt = System.nanoTime()
        HttpAsaasGateway.defaultIdempotencyPollWait(0)
        val first = Duration.ofNanos(System.nanoTime() - startedAt)
        assertTrue(first >= Duration.ofMillis(50), "attempt 0 waited only $first")

        val secondStartedAt = System.nanoTime()
        HttpAsaasGateway.defaultIdempotencyPollWait(2)
        val third = Duration.ofNanos(System.nanoTime() - secondStartedAt)
        assertTrue(third >= Duration.ofMillis(150), "attempt 2 waited only $third")
    }

    // --- Configuracao ---

    @Test
    fun `settings read the alternative property names, trim the base url and default the user agent`() {
        val settings = AsaasClientSettings.fromProperties { key ->
            when (key) {
                "saqz.asaas.api-key" -> "prop-key"
                "saqz.asaas.base-url" -> "https://asaas.test/v3/"
                else -> null
            }
        }

        assertEquals("prop-key", settings.apiKey)
        assertEquals("https://asaas.test/v3", settings.baseUrl)
        assertEquals("saqz-backend", settings.userAgent)
    }

    @Test
    fun `settings default to the sandbox url and accept a custom user agent`() {
        val settings = AsaasClientSettings.fromProperties { key ->
            when (key) {
                "SAQZ_ASAAS_API_KEY" -> "env-key"
                "saqz.asaas.user-agent" -> "saqz-custom"
                else -> null
            }
        }

        assertEquals("https://api-sandbox.asaas.com/v3", settings.baseUrl)
        assertEquals("saqz-custom", settings.userAgent)
        val fromEnv = AsaasClientSettings.fromProperties { key ->
            when (key) {
                "SAQZ_ASAAS_API_KEY" -> "env-key"
                "SAQZ_ASAAS_USER_AGENT" -> "saqz-env"
                else -> null
            }
        }
        assertEquals("saqz-env", fromEnv.userAgent)
    }

    @Test
    fun `settings refuse a blank api key or base url`() {
        assertThrows<IllegalArgumentException> { AsaasClientSettings(baseUrl = "https://asaas.test", apiKey = " ") }
        assertThrows<IllegalArgumentException> { AsaasClientSettings(baseUrl = " ", apiKey = "k") }
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
