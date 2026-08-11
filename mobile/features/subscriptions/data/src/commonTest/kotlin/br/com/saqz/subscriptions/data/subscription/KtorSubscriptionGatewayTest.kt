package br.com.saqz.subscriptions.data.subscription

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.*
import br.com.saqz.network.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class KtorSubscriptionGatewayTest {
    @Test
    fun `my subscription maps retained fields and ignores legacy extras`() = runTest {
        val value = success(gateway { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/subscriptions/me", request.url.encodedPath)
            json(MY_SUBSCRIPTION)
        }.mySubscription())

        assertEquals(SubscriptionStatus.Active, value.status)
        assertTrue(value.entitled)
        assertEquals(Plan.Organizador, value.plan)
        assertEquals(SubscriptionCycle.Monthly, value.cycle)
        assertEquals("2026-08-30T00:00:00Z", value.currentPeriodEnd)
        assertEquals(2, value.usage.groupsUsed)
        assertEquals(3, value.usage.groupsLimit)
        assertNull(value.canceledAt)
    }

    @Test
    fun `my subscription not found maps distinctly`() = runTest {
        val result = gateway { problemResponse(404, "SUBSCRIPTION_NOT_FOUND") }.mySubscription()
        assertEquals(SubscriptionError.NotFound, assertIs<SaqzResult.Failure<SubscriptionError>>(result).error)
    }

    @Test
    fun `cancel sends no body and does not retry on failure`() = runTest {
        var calls = 0
        val result = gateway { request ->
            calls++
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/subscriptions/me/cancel", request.url.encodedPath)
            assertTrue(request.body !is TextContent)
            unavailable()
        }.cancel()

        assertEquals(1, calls)
        assertIs<SaqzResult.Failure<SubscriptionError>>(result)
    }

    @Test
    fun `cancel maps canceled status and dates`() = runTest {
        val value = success(gateway { json(CANCELED) }.cancel())
        assertEquals(SubscriptionStatus.Canceled, value.status)
        assertEquals("2026-08-01T00:00:00Z", value.canceledAt)
    }

    @Test
    fun `receipts sends pagination query and maps list`() = runTest {
        val value = success(gateway { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/subscriptions/me/receipts", request.url.encodedPath)
            assertEquals("20", request.url.parameters["limit"])
            assertEquals("40", request.url.parameters["offset"])
            json(RECEIPTS)
        }.receipts(limit = 20, offset = 40))

        assertEquals(1, value.size)
        assertEquals("pay-1", value.single().asaasPaymentId)
        assertEquals(4_990L, value.single().valueCents)
    }

    @Test
    fun `receipts empty list maps to empty`() = runTest {
        val value = success(gateway { json("""{"receipts":[]}""") }.receipts(limit = 20, offset = 0))
        assertTrue(value.isEmpty())
    }

    @Test fun `timeout maps shared error`() = assertDataError(DataError.Timeout, NetworkError.Timeout.toSubscriptionError())
    @Test fun `connectivity maps shared error`() = assertDataError(DataError.Connectivity, NetworkError.Connectivity.toSubscriptionError())
    @Test fun `payload too large maps shared error`() = assertDataError(DataError.PayloadTooLarge, NetworkError.PayloadTooLarge.toSubscriptionError())
    @Test fun `unavailable maps server error`() = assertDataError(DataError.Server, NetworkError.Unavailable.toSubscriptionError())
    @Test fun `unknown maps unknown error`() = assertDataError(DataError.Unknown, NetworkError.Unknown.toSubscriptionError())
    @Test fun `invalid response maps shared error`() = assertDataError(DataError.InvalidResponse, NetworkError.InvalidResponse.toSubscriptionError())
    @Test fun `unknown 5xx problem maps server error`() = assertDataError(DataError.Server, problem(503, "UNKNOWN").toSubscriptionError())
    @Test fun `unknown 4xx problem maps unknown error`() = assertDataError(DataError.Unknown, problem(400, "UNKNOWN").toSubscriptionError())

    /**
     * Achado do Codex no PR #181: uma versão anterior do decode do VUL-196 dava default a
     * `ApiProblem.status`, e um 503 com corpo genérico (`{}`, sem o "problem" padrão do
     * backend — o caso real de um proxy na frente da API) decodificava como
     * `ApiProblem(status=0)` em vez de cair em `HttpStatus(503)`. `isRetryableFailure` só
     * re-tenta `ApiProblemError` com `status` em 500..599, então esse 503 parava de ser
     * retentado silenciosamente. Este teste prova que o retry acontece de novo.
     */
    @Test
    fun `my subscription retries on a 503 with a generic empty body`() = runTest {
        var calls = 0
        val result = gateway { request ->
            calls++
            if (calls == 1) respond("{}", HttpStatusCode.ServiceUnavailable, jsonHeaders()) else json(MY_SUBSCRIPTION)
        }.mySubscription()

        assertIs<SaqzResult.Success<MySubscription>>(result)
        assertEquals(2, calls)
    }

    @Test
    fun `read retries three times with exact schedule`() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()
        val result = gateway(delays = delays) {
            calls++
            if (calls < 4) unavailable() else json(MY_SUBSCRIPTION)
        }.mySubscription()

        assertIs<SaqzResult.Success<MySubscription>>(result)
        assertEquals(4, calls)
        assertEquals(listOf(500L, 1_000L, 2_000L), delays)
    }

    @Test
    fun `transport cancellation propagates`() = runTest {
        val gateway = gateway { throw CancellationException("cancel") }
        assertFailsWith<CancellationException> { gateway.mySubscription() }
    }

    @Test
    fun `retry delay cancellation propagates`() = runTest {
        val gateway = KtorSubscriptionGateway(auth(Tokens()) { unavailable() }) {
            throw CancellationException("cancel delay")
        }
        assertFailsWith<CancellationException> { gateway.mySubscription() }
    }

    private fun gateway(
        delays: MutableList<Long>? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = KtorSubscriptionGateway(auth(Tokens(), handler)) { delay -> delays?.add(delay) }

    private fun auth(
        tokens: IdTokenProvider,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): AuthenticatedNetworkClient {
        val network = NetworkClient(
            MockEngine { request -> handler(request) },
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
        )
        return AuthenticatedNetworkClient(network, tokens, NoopInvalidator())
    }

    private fun MockRequestHandleScope.json(body: String) = respond(body, headers = jsonHeaders())

    private fun MockRequestHandleScope.unavailable() = respond(
        """{"status":503,"code":"TEMPORARY","correlationId":"safe"}""",
        HttpStatusCode.ServiceUnavailable,
        jsonHeaders(),
    )

    private fun MockRequestHandleScope.problemResponse(
        status: Int,
        code: String,
        fields: Map<String, List<String>>? = null,
    ) = respond(
        Json.encodeToString(ApiProblem.serializer(), ApiProblem(status, code, "safe-correlation", fields)),
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun problem(status: Int, code: String, fields: Map<String, List<String>>? = null) =
        NetworkError.ApiProblemError(ApiProblem(status, code, "safe-correlation", fields))

    private fun <T> success(result: SaqzResult<T, SubscriptionError>) =
        assertIs<SaqzResult.Success<T>>(result).value

    private fun assertDataError(expected: DataError, actual: SubscriptionError) =
        assertEquals(expected, assertIs<SubscriptionError.Data>(actual).error)

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("obviously-fake-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private companion object {
        const val MY_SUBSCRIPTION = """{"status":"ACTIVE","entitled":true,"plan":"ORGANIZADOR","cycle":"MONTHLY","pendingPlan":"ILIMITADO","pendingPlanEffectiveAt":"2026-09-01T00:00:00Z","currentPeriodEnd":"2026-08-30T00:00:00Z","paymentMethod":"PIX","usage":{"groupsUsed":2,"groupsLimit":3},"readOnly":false,"pastDueSince":null,"cardLast4":"4242","cardBrand":"visa","canceledAt":null}"""
        const val CANCELED = """{"status":"CANCELED","canceledAt":"2026-08-01T00:00:00Z","currentPeriodEnd":"2026-08-30T00:00:00Z"}"""
        const val RECEIPTS = """{"receipts":[{"asaasEventId":"evt-1","asaasPaymentId":"pay-1","valueCents":4990,"confirmedAt":"2026-07-01T00:00:00Z","processedAt":"2026-07-01T00:05:00Z"}]}"""
    }
}
