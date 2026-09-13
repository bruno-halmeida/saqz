package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class KtorReceiptWalletGatewayTest {
    @Test fun availableBalanceAndPendingReceivablesStayDistinctAndExact() = runTest {
        val g = gateway(MockEngine { r ->
            assertEquals("/api/receivables/accounts/account/wallet", r.url.encodedPath)
            assertEquals("Bearer token", r.headers[HttpHeaders.Authorization])
            respond(envelope("""{"accountId":"account","availableBalanceCents":12345,"pendingReceivablesCents":6789,"refreshedAt":"2026-09-13"}"""), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(ReceiptWalletBalance("account", 12345, 6789, "2026-09-13")), g.balance("account"))
    }
    @Test fun bankSubmissionContainsLegalDetailsOnlyInBodyAndNoClientAuthenticationClaim() = runTest {
        val g = gateway(MockEngine { r ->
            assertEquals(HttpMethod.Post, r.method); assertEquals("", r.url.encodedQuery)
            val body = Json.parseToJsonElement((r.body as TextContent).text).jsonObject
            assertEquals(JsonPrimitive("request"), body["requestId"])
            assertEquals(JsonPrimitive("12345678901"), body["cpfCnpj"])
            assertEquals(JsonPrimitive("12345"), body["account"])
            assertFalse(body.containsKey("recentlyAuthenticated")); assertFalse(body.containsKey("actorUserId"))
            respond(envelope(bankJson), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(bank), g.saveDestination("account", "request", details))
        assertEquals("ReceiptBankDetails(redacted)", details.toString())
    }
    @Test fun withdrawalPostsOneExactExplicitIntentAndRejectsDifferentResponseAmount() = runTest {
        var calls = 0
        val g = gateway(MockEngine { r ->
            calls++; assertEquals(HttpMethod.Post, r.method)
            assertEquals(Json.parseToJsonElement("""{"requestId":"request","destinationId":"bank","amountCents":12345,"explicitlyAuthorized":true}"""),
                Json.parseToJsonElement((r.body as TextContent).text))
            respond(envelope(withdrawalJson.replace("12345", "12346")), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Failure(WalletError.UNCERTAIN), g.withdraw("account", "request", "bank", 12345))
        assertEquals(1, calls)
    }
    @Test fun timeoutNeverRetriesFinancialPostAndRecoveryUsesOnlyGetWithOriginalRequest() = runTest {
        val calls = mutableListOf<String>()
        val g = gateway(MockEngine { r ->
            calls += "${r.method.value} ${r.url.encodedPath}"
            if (r.method == HttpMethod.Post) respond("", HttpStatusCode.ServiceUnavailable)
            else respond(envelope(withdrawalJson), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Failure(WalletError.UNCERTAIN), g.withdraw("account", "request", "bank", 12345))
        assertEquals(SaqzResult.Success(withdrawal), g.recoverWithdrawal("account", "request"))
        assertEquals(listOf("POST /api/receivables/accounts/account/withdrawals",
            "GET /api/receivables/accounts/account/withdrawals/by-request/request"), calls)
    }
    @Test fun bankRecoveryIsReadOnlyAndResponseDoesNotExposeFullDocument() = runTest {
        val g = gateway(MockEngine { r ->
            assertEquals(HttpMethod.Get, r.method)
            assertEquals("/api/receivables/accounts/account/bank-destinations/by-request/request", r.url.encodedPath)
            respond(envelope(bankJson), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(bank), g.recoverDestination("account", "request"))
        val unsafe = gateway(MockEngine { respond(envelope(bankJson.replace("8901", "12345678901")), HttpStatusCode.OK, headers) })
        assertEquals(SaqzResult.Failure(WalletError.INVALID), unsafe.recoverDestination("account", "request"))
    }
    @Test fun recentAuthenticationAndInsufficientBalanceHaveActionableErrors() = runTest {
        for ((status, error) in listOf(HttpStatusCode.Forbidden to WalletError.RECENT_AUTHENTICATION,
            HttpStatusCode.UnprocessableEntity to WalletError.INSUFFICIENT_BALANCE,
            HttpStatusCode.NotFound to WalletError.DENIED, HttpStatusCode.Conflict to WalletError.CONFLICT)) {
            val g = gateway(MockEngine { respond("", status) })
            assertEquals(SaqzResult.Failure(error), g.withdraw("account", "request", "bank", 12345))
        }
    }
    @Test fun statementKeepsSignedCentsAndCursorIsAQueryValue() = runTest {
        val g = gateway(MockEngine { r ->
            assertEquals("cursor+/=", r.url.parameters["cursor"]); assertEquals("20", r.url.parameters["limit"])
            respond(envelope("""{"items":[{"id":"tx","kind":"TRANSFER","amountCents":-12345,"balanceCents":100,"occurredOn":"2026-09-13","description":"Saque"}],"nextCursor":"next"}"""), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(ReceiptWalletStatement(listOf(ReceiptWalletEntry("tx", "TRANSFER", -12345, 100,
            "2026-09-13", "Saque")), "next")), g.statement("account", "cursor+/="))
    }
    @Test fun mismatchedRequestIdOrUnknownWithdrawalStatusCannotBecomeSuccess() = runTest {
        for (json in listOf(withdrawalJson.replace("request", "foreign"), withdrawalJson.replace("PROCESSING", "FUTURE_STATUS"))) {
            val g = gateway(MockEngine { respond(envelope(json), HttpStatusCode.OK, headers) })
            assertEquals(SaqzResult.Failure(WalletError.INVALID), g.recoverWithdrawal("account", "request"))
        }
    }
    private fun gateway(engine: MockEngine) = KtorReceiptWalletGateway(AuthenticatedNetworkClient(
        NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://example.test/")),
        object : IdTokenProvider { override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("token")) }, object : SessionInvalidator { override fun invalidate() = Unit }))
    private fun envelope(value: String) = """{"value":$value,"requestId":"request"}"""
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    private val details = ReceiptBankDetails("001", "CHECKING", "Titular", "12345678901", "1234", "12345", "6")
    private val bank = ReceiptBankDestination("bank", "001", "CHECKING", "T***", "8901", "1234", "2345", false)
    private val bankJson = """{"id":"bank","bankCode":"001","accountType":"CHECKING","ownerName":"T***","cpfCnpjSuffix":"8901","agencySuffix":"1234","accountSuffix":"2345","verified":false}"""
    private val withdrawal = ReceiptWithdrawal("withdrawal", "request", "bank", 12345, 0, "PROCESSING", null)
    private val withdrawalJson = """{"id":"withdrawal","requestId":"request","destinationId":"bank","amountCents":12345,"feeCents":0,"status":"PROCESSING","providerTransferId":null}"""
}
