package br.com.saqz.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpMethod
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthenticatedNetworkClientTest {
    @Test
    fun `authenticated client loads current token without forced refresh`() = runTest {
        val fixture = fixture { resourceResponse() }

        fixture.execute()

        assertEquals(listOf(false), fixture.tokens.forceRefreshCalls)
    }

    @Test
    fun `authenticated client sends PUT to the configured route without body`() = runTest {
        val fixture = fixture { request ->
            assertEquals("PUT", request.method.value)
            assertEquals("/api/resource", request.url.encodedPath)
            assertEquals(0, request.body.contentLength ?: 0)
            resourceResponse()
        }

        fixture.execute()
    }

    @Test
    fun `authenticated client decodes the requested payload`() = runTest {
        val fixture = fixture {
            resourceResponse("""{"value":"decoded"}""")
        }

        val payload = assertIs<NetworkResult.Success<TransportPayload>>(fixture.execute()).value

        assertEquals("decoded", payload.value)
    }

    @Test
    fun `first 401 requests one forced token refresh`() = runTest {
        val fixture = refreshingFixture()

        fixture.execute()

        assertEquals(listOf(false, true), fixture.tokens.forceRefreshCalls)
    }

    @Test
    fun `retry sends the refreshed bearer token`() = runTest {
        val seen = mutableListOf<String?>()
        val fixture = refreshingFixture { request -> seen += request.headers[HttpHeaders.Authorization] }

        fixture.execute()

        assertEquals(listOf<String?>("Bearer old-token", "Bearer fresh-token"), seen)
    }

    @Test
    fun `401 flow performs at most one retry`() = runTest {
        var requests = 0
        val fixture = fixture(response = {
            requests += 1
            unauthorized()
        })

        fixture.execute()

        assertEquals(2, requests)
    }

    @Test
    fun `second 401 invalidates the local session`() = runTest {
        val fixture = fixture(response = { unauthorized() })

        fixture.execute()

        assertEquals(1, fixture.invalidator.calls)
    }

    @Test
    fun `second 401 remains an unauthorized problem`() = runTest {
        val fixture = fixture(response = { unauthorized() })

        val error = assertIs<NetworkResult.Failure>(fixture.execute()).error

        assertEquals(401, assertIs<NetworkError.ApiProblemError>(error).problem.status)
    }

    @Test
    fun `concurrent 401 burst performs one forced refresh`() = runTest {
        val fixture = refreshingFixture(beforeResponse = { delay(1) })

        List(8) { async { fixture.execute() } }.awaitAll()

        assertEquals(1, fixture.tokens.forceRefreshCalls.count { it })
    }

    @Test
    fun `concurrent 401 burst returns success to every caller`() = runTest {
        val fixture = refreshingFixture(beforeResponse = { delay(1) })

        val results = List(8) { async { fixture.execute() } }.awaitAll()

        assertTrue(results.all { it is NetworkResult.Success<*> })
        assertEquals(0, fixture.invalidator.calls)
    }

    @Test
    fun `server 500 neither refreshes nor invalidates`() = runTest {
        val fixture = fixture { serverError() }

        fixture.execute()

        assertEquals(listOf(false), fixture.tokens.forceRefreshCalls)
        assertEquals(0, fixture.invalidator.calls)
    }

    @Test
    fun `request timeout neither refreshes nor invalidates`() = runTest {
        val engine = MockEngine {
            delay(100)
            resourceResponse()
        }
        val fixture = fixture(engine = engine, timeoutMillis = 10)

        val result = fixture.execute()

        assertEquals(NetworkError.Timeout, assertIs<NetworkResult.Failure>(result).error)
        assertEquals(0, fixture.invalidator.calls)
    }

    @Test
    fun `current token failure returns unavailable without HTTP request`() = runTest {
        var requests = 0
        val fixture = fixture(
            tokens = RecordingTokenProvider(current = TokenResult.Unavailable),
            response = {
                requests += 1
                resourceResponse()
            },
        )

        val result = fixture.execute()

        assertEquals(NetworkError.Unavailable, assertIs<NetworkResult.Failure>(result).error)
        assertEquals(0, requests)
    }

    @Test
    fun `refresh failure returns unavailable without invalidating session`() = runTest {
        val tokens = RecordingTokenProvider(refresh = TokenResult.Unavailable)
        val fixture = fixture(tokens = tokens, response = { unauthorized() })

        val result = fixture.execute()

        assertEquals(NetworkError.Unavailable, assertIs<NetworkResult.Failure>(result).error)
        assertEquals(0, fixture.invalidator.calls)
    }

    @Test
    fun `403 problem neither refreshes nor invalidates`() = runTest {
        val fixture = fixture { forbidden() }

        fixture.execute()

        assertEquals(listOf(false), fixture.tokens.forceRefreshCalls)
        assertEquals(0, fixture.invalidator.calls)
    }

    private fun refreshingFixture(
        beforeResponse: suspend () -> Unit = {},
        observe: (HttpRequestData) -> Unit = {},
    ): Fixture = fixture { request ->
        beforeResponse()
        observe(request)
        if (request.headers[HttpHeaders.Authorization] == "Bearer old-token") unauthorized() else resourceResponse()
    }

    private fun fixture(
        tokens: RecordingTokenProvider = RecordingTokenProvider(),
        timeoutMillis: Long = 10_000,
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Fixture = fixture(MockEngine { request -> response(request) }, tokens, timeoutMillis)

    private fun fixture(
        engine: MockEngine,
        tokens: RecordingTokenProvider = RecordingTokenProvider(),
        timeoutMillis: Long = 10_000,
    ): Fixture {
        val invalidator = RecordingInvalidator()
        val network = NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/", timeoutMillis))
        val authenticated = AuthenticatedNetworkClient(network, tokens, invalidator)
        return Fixture(authenticated, tokens, invalidator)
    }

    private fun MockRequestHandleScope.resourceResponse(body: String = resourceJson()) =
        respond(body, HttpStatusCode.OK, jsonHeaders())

    private fun MockRequestHandleScope.unauthorized() = respond(
        """{"status":401,"code":"AUTHENTICATION_REQUIRED","correlationId":"corr-401"}""",
        HttpStatusCode.Unauthorized,
        jsonHeaders(),
    )

    private fun MockRequestHandleScope.forbidden() = respond(
        """{"status":403,"code":"EMAIL_NOT_VERIFIED","correlationId":"corr-403"}""",
        HttpStatusCode.Forbidden,
        jsonHeaders(),
    )

    private fun MockRequestHandleScope.serverError() = respond(
        """{"status":500,"correlationId":"corr-500"}""",
        HttpStatusCode.InternalServerError,
        jsonHeaders(),
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun resourceJson() = """{"value":"ok"}"""

    private class RecordingTokenProvider(
        private val current: TokenResult = TokenResult.Available("old-token"),
        private val refresh: TokenResult = TokenResult.Available("fresh-token"),
    ) : IdTokenProvider {
        val forceRefreshCalls = mutableListOf<Boolean>()

        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) {
            forceRefreshCalls += forceRefresh
            completion(if (forceRefresh) refresh else current)
        }
    }

    private class RecordingInvalidator : SessionInvalidator {
        var calls = 0

        override fun invalidate() {
            calls += 1
        }
    }

    private data class Fixture(
        val client: AuthenticatedNetworkClient,
        val tokens: RecordingTokenProvider,
        val invalidator: RecordingInvalidator,
    ) {
        suspend fun execute(): NetworkResult<TransportPayload> = client.execute(
            method = HttpMethod.Put,
            path = "api/resource",
            responseSerializer = TransportPayload.serializer(),
        )
    }

    @Serializable
    private data class TransportPayload(val value: String)
}
