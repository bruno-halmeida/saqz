package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class KtorRecurringPaymentsGatewayTest {
    @Test fun currentDiscoveryUsesAuthenticatedAccountAndGroupAndPreservesAbsence() = runTest {
        var calls = 0
        val gateway = recurrenceGateway(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/receivables/recurrences/current", request.url.encodedPath)
            assertEquals(mapOf("accountId" to listOf("account"), "groupId" to listOf("group")),
                request.url.parameters.entries().associate { it.toPair() })
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            calls++
            respond(if (calls == 1) envelope("null", "server") else envelope(recurrenceJson, "server"),
                HttpStatusCode.OK, jsonHeaders)
        })
        assertEquals(SaqzResult.Success(null), gateway.discover("account", "group"))
        assertEquals(SaqzResult.Success(recurrence), gateway.discover("account", "group"))
    }

    @Test fun previewSendsExactContextAndReturnsLiteralServerMoneyAndFingerprint() = runTest {
        val gateway = recurrenceGateway(MockEngine { request ->
            assertEquals("/api/receivables/recurrences/preview", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(Json.parseToJsonElement("""{"requestId":"request","accountId":"account","groupId":"group",
                "method":"PIX","firstDueDate":"2026-10-10"}"""), bodyJson(request))
            respond(envelope(reviewJson, "request"), HttpStatusCode.OK, jsonHeaders)
        })
        assertEquals(SaqzResult.Success(review), gateway.preview(previewCommand))
    }

    @Test fun authorizeSubmitsOnceAndRecoversExactAcceptedRequestByGet() = runTest {
        val bodies = mutableListOf<String>()
        val gateway = recurrenceGateway(MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                assertEquals("/api/receivables/recurrences", request.url.encodedPath)
                bodies += (request.body as TextContent).text
                respond("", HttpStatusCode.ServiceUnavailable)
            } else {
                assertEquals(HttpMethod.Get, request.method)
                assertEquals("/api/receivables/recurrences/by-request/request", request.url.encodedPath)
                respond(envelope(recurrenceJson, "request"), HttpStatusCode.OK, jsonHeaders)
            }
        })
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), gateway.authorize(review, acceptance))
        assertEquals(1, bodies.size)
        assertEquals(SaqzResult.Success(recurrence), gateway.recover(
            RecurrenceAttempt("request", "payer", "account", "group", null, "AUTHORIZE")))
        assertEquals(1, bodies.size)
        assertEquals(Json.parseToJsonElement("""{"requestId":"request","accountId":"account","groupId":"group","method":"PIX",
            "firstDueDate":"2026-10-10","fingerprint":"${"a".repeat(64)}","accepted":true,
            "payer":{"name":"Pessoa Teste","cpfCnpj":"12345678909"}}"""), Json.parseToJsonElement(bodies[0]))
    }

    @Test fun cancelResumeAndRecoveryUseOnlyTheirDocumentedResources() = runTest {
        val paths = mutableListOf<String>()
        val gateway = recurrenceGateway(MockEngine { request ->
            paths += request.url.encodedPath
            when {
                request.url.encodedPath.endsWith("/cancel") -> {
                    assertEquals(Json.parseToJsonElement("""{"requestId":"request"}"""), bodyJson(request))
                    respond(envelope(recurrenceJson.replace("ACTIVE", "STOPPED"), "request"), HttpStatusCode.OK, jsonHeaders)
                }
                request.url.encodedPath.endsWith("/resume") -> respond(envelope(
                    recurrenceJson.replace("\"recurrence\"", "\"new-recurrence\""), "request"), HttpStatusCode.OK, jsonHeaders)
                else -> respond(envelope(recurrenceJson, "request"), HttpStatusCode.OK, jsonHeaders)
            }
        })
        assertEquals("STOPPED", assertIs<SaqzResult.Success<PaymentRecurrence>>(gateway.cancel(recurrence, "request")).value.status)
        val stopped = recurrence.copy(status = "STOPPED")
        assertEquals("new-recurrence", assertIs<SaqzResult.Success<PaymentRecurrence>>(
            gateway.resume(stopped, review, acceptance)).value.id)
        assertEquals(SaqzResult.Success(recurrence), gateway.recover(
            RecurrenceAttempt("request", "payer", "account", "group", null, "AUTHORIZE")))
        assertEquals(listOf("/api/receivables/recurrences/recurrence/cancel",
            "/api/receivables/recurrences/recurrence/resume", "/api/receivables/recurrences/by-request/request"), paths)
    }

    @Test fun recoveryNotFoundIsPendingAbsenceButForeignOrUncorrelatedPayloadFailsClosed() = runTest {
        val attempt = RecurrenceAttempt("request", "payer", "account", "group", null, "AUTHORIZE")
        val missing = recurrenceGateway(MockEngine { respond("{}", HttpStatusCode.NotFound, jsonHeaders) })
        assertEquals(SaqzResult.Success(null), missing.recover(attempt))
        for (response in listOf(envelope(recurrenceJson, "other"), envelope(recurrenceJson.replace("payer", "other"), "request"),
            envelope(recurrenceJson.replace("10490", "10491"), "request"), "{")) {
            val gateway = recurrenceGateway(MockEngine { respond(response, HttpStatusCode.OK, jsonHeaders) })
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), gateway.recover(attempt), response)
        }
    }

    @Test fun invalidCommandsNeverReachNetworkAndUnsafeCardUrlCannotBecomeSuccess() = runTest {
        val gateway = recurrenceGateway(MockEngine { error("invalid command reached network") })
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway.preview(previewCommand.copy(firstDueDate = "13/09/2026")))
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway.authorize(review, acceptance.copy(accepted = false)))
        val unsafe = recurrenceGateway(MockEngine { respond(envelope(recurrenceJson.replace("PIX", "CARD")
            .replace("\"hostedCheckoutUrl\":null", "\"hostedCheckoutUrl\":\"https://asaas.com.evil.test/x\""), "request"),
            HttpStatusCode.OK, jsonHeaders) })
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), unsafe.authorize(review.copy(method = ReceiptMethod.CARD),
            acceptance.copy(method = ReceiptMethod.CARD)))
    }

    @Test fun pixRenewalUpdatesSameObligationAndRecoveryIsReadOnly() = runTest {
        val paths = mutableListOf<String>()
        val gateway = pixGateway(MockEngine { request ->
            paths += request.url.encodedPath
            if (request.method == HttpMethod.Post) assertEquals(Json.parseToJsonElement(
                """{"requestId":"request","dueDate":"2026-09-20"}"""), bodyJson(request))
            respond(envelope(renewalJson, "request"), HttpStatusCode.OK, jsonHeaders)
        })
        assertEquals(SaqzResult.Success(renewal), gateway.renew(order, expiredInstrument, PixRenewalCommand("request", "2026-09-20")))
        assertEquals(SaqzResult.Success(renewal), gateway.recover(order, expiredInstrument, "request"))
        assertEquals(listOf("/api/receivables/orders/order/pix-renewal",
            "/api/receivables/orders/order/pix-renewal/request"), paths)
    }

    @Test fun pixRenewalTimeoutSubmitsOnceThenRecoversAndMismatchedSnapshotStaysUncertain() = runTest {
        val bodies = mutableListOf<String>()
        val timeout = pixGateway(MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                bodies += (request.body as TextContent).text
                throw io.ktor.client.plugins.HttpRequestTimeoutException(request)
            }
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/receivables/orders/order/pix-renewal/request", request.url.encodedPath)
            respond(envelope(renewalJson, "request"), HttpStatusCode.OK, jsonHeaders)
        })
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN),
            timeout.renew(order, expiredInstrument, PixRenewalCommand("request", "2026-09-20")))
        assertEquals(1, bodies.size)
        assertEquals(Json.parseToJsonElement("""{"requestId":"request","dueDate":"2026-09-20"}"""),
            Json.parseToJsonElement(bodies.single()))
        assertEquals(SaqzResult.Success(renewal), timeout.recover(order, expiredInstrument, "request"))
        assertEquals(1, bodies.size)
        for (changed in listOf(renewalJson.replace("\"instrument\"", "\"new-instrument\""),
            renewalJson.replace("\"payment\"", "\"new-payment\""), renewalJson.replace("1061", "1062"))) {
            val gateway = pixGateway(MockEngine { respond(envelope(changed, "request"), HttpStatusCode.OK, jsonHeaders) })
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN),
                gateway.renew(order, expiredInstrument, PixRenewalCommand("request", "2026-09-20")))
        }
    }

    @Test fun pixRecovery404IsAbsenceBut403RemainsDeniedAndMalformedSuccessFails() = runTest {
        val missing = pixGateway(MockEngine { respond("{}", HttpStatusCode.NotFound, jsonHeaders) })
        assertEquals(SaqzResult.Success(null), missing.recover(order, expiredInstrument, "request"))
        val denied = pixGateway(MockEngine { respond("{}", HttpStatusCode.Forbidden, jsonHeaders) })
        assertEquals(SaqzResult.Failure(ReceiptError.DENIED), denied.recover(order, expiredInstrument, "request"))
        val malformed = pixGateway(MockEngine { respond(envelope("null", "request"), HttpStatusCode.OK, jsonHeaders) })
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), malformed.recover(order, expiredInstrument, "request"))
    }

    @Test fun verifierFinancialTimeoutDoesNotReplayRecurrencePost() = runTest {
        for (operation in listOf("authorize", "resume", "cancel")) {
            var posts = 0
            val gateway = recurrenceGateway(MockEngine { request ->
                posts++; throw io.ktor.client.plugins.HttpRequestTimeoutException(request)
            })
            val result = when (operation) {
                "authorize" -> gateway.authorize(review, acceptance)
                "resume" -> gateway.resume(recurrence.copy(status = "STOPPED"), review, acceptance)
                else -> gateway.cancel(recurrence, "request")
            }
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), result)
            assertEquals(1, posts, "$operation must await GET recovery")
        }
    }
    @Test fun verifierFinancialTimeoutDoesNotReplayRenewalPost() = runTest {
        var posts = 0
        val gateway = pixGateway(MockEngine { request ->
            posts++; throw io.ktor.client.plugins.HttpRequestTimeoutException(request)
        })
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN),
            gateway.renew(order, expiredInstrument, PixRenewalCommand("request", "2026-09-20")))
        assertEquals(1, posts, "renewal must await GET recovery")
    }
    @Test fun verifierAcceptedRecurrenceSnapshotRemainsUncertain() = runTest {
        val gateway = recurrenceGateway(MockEngine {
            respond(envelope(recurrenceJson, "request"), HttpStatusCode.Accepted, jsonHeaders)
        })
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), gateway.authorize(review, acceptance))
    }
    @Test fun verifierAcceptedRenewalSnapshotRemainsUncertain() = runTest {
        val gateway = pixGateway(MockEngine {
            respond(envelope(renewalJson, "request"), HttpStatusCode.Accepted, jsonHeaders)
        })
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN),
            gateway.renew(order, expiredInstrument, PixRenewalCommand("request", "2026-09-20")))
    }
    @Test fun verifierRenewalRejectsDifferentDueDate() = runTest {
        val gateway = pixGateway(MockEngine {
            respond(envelope(renewalJson.replace("2026-09-20", "2026-09-21"), "request"), HttpStatusCode.OK, jsonHeaders)
        })
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN),
            gateway.renew(order, expiredInstrument, PixRenewalCommand("request", "2026-09-20")))
    }
    @Test fun verifierAcceptanceRejectsDifferentFirstDueDate() = runTest {
        val gateway = recurrenceGateway(MockEngine {
            respond(envelope(recurrenceJson.replace("2026-10-10", "2026-10-11"), "request"), HttpStatusCode.OK, jsonHeaders)
        })
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), gateway.authorize(review, acceptance))
    }

    private fun recurrenceGateway(engine: MockEngine) = KtorRecurrenceGateway(client(engine))
    private fun pixGateway(engine: MockEngine) = KtorPixRenewalGateway(client(engine))
    private fun client(engine: MockEngine) = AuthenticatedNetworkClient(
        NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://example.test/")),
        object : IdTokenProvider {
            override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) = completion(TokenResult.Available("token"))
        }, object : SessionInvalidator { override fun invalidate() = Unit })
    private fun bodyJson(request: io.ktor.client.request.HttpRequestData) =
        Json.parseToJsonElement((request.body as TextContent).text)
}

private val fingerprint = "a".repeat(64)
private val review = RecurrenceReview("account", "group", "payer", ReceiptMethod.PIX, 10_000, 490, 10_490, 300, 190,
    10_000, "schedule", "terms", "2026-10-10", "MONTHLY", fingerprint)
private val recurrence = PaymentRecurrence("recurrence", "account", "group", "payer", ReceiptMethod.PIX, 10_000, 490,
    10_490, "2026-10-10", "ACTIVE", "subscription", null, null)
private val previewCommand = RecurrencePreviewCommand("request", "account", "group", ReceiptMethod.PIX, "2026-10-10")
private val acceptance = RecurrenceAcceptanceCommand("request", "account", "group", ReceiptMethod.PIX, "2026-10-10",
    fingerprint, true, MemberPaymentPayer("Pessoa Teste", "12345678909"))
private val quote = MemberPaymentQuote("schedule", "terms", ReceiptMethod.PIX, 1000, 61, 1061, 1000, 20, 41)
private val order = MemberPaymentOrder("order", "account", "charge", "group", "payer", "2026-09-13", "ISSUED",
    listOf(quote), fingerprint)
private val expiredInstrument = MemberPaymentInstrument("instrument", "account", "order", quote, "EXPIRED", "payment", null,
    "old", null, null, false, false, false, false, "2026-09-12T23:59:59Z")
private val renewal = PixRenewal("order", "instrument", "payment", ReceiptMethod.PIX, "ACTIVE", 1000, 61, 1061,
    "2026-09-20", "new-pix", "new-image", "2026-09-20T23:59:59Z")
private val reviewJson = """{"accountId":"account","groupId":"group","memberUserId":"payer","method":"PIX",
    "baseCents":10000,"feesCents":490,"totalCents":10490,"commissionCents":300,"expectedProviderFeeCents":190,
    "expectedNetCents":10000,"feeScheduleId":"schedule","termsVersion":"terms","firstDueDate":"2026-10-10",
    "cycle":"MONTHLY","fingerprint":"$fingerprint"}"""
private val recurrenceJson = """{"id":"recurrence","accountId":"account","groupId":"group","memberUserId":"payer",
    "method":"PIX","baseCents":10000,"feesCents":490,"totalCents":10490,"firstDueDate":"2026-10-10","status":"ACTIVE",
    "providerSubscriptionId":"subscription","hostedCheckoutUrl":null,"cutoffAt":null}"""
private val renewalJson = """{"orderId":"order","instrumentId":"instrument","providerPaymentId":"payment","method":"PIX",
    "status":"ACTIVE","baseCents":1000,"feesCents":61,"totalCents":1061,"dueDate":"2026-09-20","pixPayload":"new-pix",
    "pixImage":"new-image","expiresAt":"2026-09-20T23:59:59Z"}"""
private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private fun envelope(value: String, request: String) = """{"value":$value,"requestId":"$request"}"""
