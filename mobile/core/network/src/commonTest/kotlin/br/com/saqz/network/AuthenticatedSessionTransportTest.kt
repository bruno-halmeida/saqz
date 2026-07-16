package br.com.saqz.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthenticatedSessionTransportTest {
    @Test
    fun `session API loads current token without forced refresh`() = runTest {
        val fixture = fixture { sessionResponse() }

        fixture.api.bootstrap()

        assertEquals(listOf(false), fixture.tokens.forceRefreshCalls)
    }

    @Test
    fun `session API sends PUT to the exact session route without body`() = runTest {
        val fixture = fixture { request ->
            assertEquals("PUT", request.method.value)
            assertEquals("/api/session", request.url.encodedPath)
            assertEquals(0, request.body.contentLength ?: 0)
            sessionResponse()
        }

        fixture.api.bootstrap()
    }

    @Test
    fun `session API decodes user and memberships`() = runTest {
        val fixture = fixture {
            sessionResponse(
                """{"user":{"id":"user-1","email":"person@example.test","displayName":"Person"},"memberships":[{"groupId":"group-1","groupName":"Group","role":"ADMIN"}]}""",
            )
        }

        val session = assertIs<NetworkResult.Success<SessionDto>>(fixture.api.bootstrap()).value

        assertEquals("user-1", session.user.id)
        assertEquals("ADMIN", session.memberships.single().role)
    }

    @Test
    fun `first 401 requests one forced token refresh`() = runTest {
        val fixture = refreshingFixture()

        fixture.api.bootstrap()

        assertEquals(listOf(false, true), fixture.tokens.forceRefreshCalls)
    }

    @Test
    fun `retry sends the refreshed bearer token`() = runTest {
        val seen = mutableListOf<String?>()
        val fixture = refreshingFixture { request -> seen += request.headers[HttpHeaders.Authorization] }

        fixture.api.bootstrap()

        assertEquals(listOf<String?>("Bearer old-token", "Bearer fresh-token"), seen)
    }

    @Test
    fun `401 flow performs at most one retry`() = runTest {
        var requests = 0
        val fixture = fixture(response = {
            requests += 1
            unauthorized()
        })

        fixture.api.bootstrap()

        assertEquals(2, requests)
    }

    @Test
    fun `second 401 invalidates the local session`() = runTest {
        val fixture = fixture(response = { unauthorized() })

        fixture.api.bootstrap()

        assertEquals(1, fixture.invalidator.calls)
    }

    @Test
    fun `second 401 remains an unauthorized problem`() = runTest {
        val fixture = fixture(response = { unauthorized() })

        val error = assertIs<NetworkResult.Failure>(fixture.api.bootstrap()).error

        assertEquals(401, assertIs<NetworkError.ApiProblemError>(error).problem.status)
    }

    @Test
    fun `concurrent 401 burst performs one forced refresh`() = runTest {
        val fixture = refreshingFixture(beforeResponse = { delay(1) })

        List(8) { async { fixture.api.bootstrap() } }.awaitAll()

        assertEquals(1, fixture.tokens.forceRefreshCalls.count { it })
    }

    @Test
    fun `concurrent 401 burst returns success to every caller`() = runTest {
        val fixture = refreshingFixture(beforeResponse = { delay(1) })

        val results = List(8) { async { fixture.api.bootstrap() } }.awaitAll()

        assertTrue(results.all { it is NetworkResult.Success<*> })
        assertEquals(0, fixture.invalidator.calls)
    }

    @Test
    fun `server 500 neither refreshes nor invalidates session`() = runTest {
        val fixture = fixture { serverError() }

        fixture.api.bootstrap()

        assertEquals(listOf(false), fixture.tokens.forceRefreshCalls)
        assertEquals(0, fixture.invalidator.calls)
    }

    @Test
    fun `request timeout neither refreshes nor invalidates session`() = runTest {
        val engine = MockEngine {
            delay(100)
            sessionResponse()
        }
        val fixture = fixture(engine = engine, timeoutMillis = 10)

        val result = fixture.api.bootstrap()

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
                sessionResponse()
            },
        )

        val result = fixture.api.bootstrap()

        assertEquals(NetworkError.Unavailable, assertIs<NetworkResult.Failure>(result).error)
        assertEquals(0, requests)
    }

    @Test
    fun `refresh failure returns unavailable without invalidating session`() = runTest {
        val tokens = RecordingTokenProvider(refresh = TokenResult.Unavailable)
        val fixture = fixture(tokens = tokens, response = { unauthorized() })

        val result = fixture.api.bootstrap()

        assertEquals(NetworkError.Unavailable, assertIs<NetworkResult.Failure>(result).error)
        assertEquals(0, fixture.invalidator.calls)
    }

    @Test
    fun `403 problem neither refreshes nor invalidates session`() = runTest {
        val fixture = fixture { forbidden() }

        fixture.api.bootstrap()

        assertEquals(listOf(false), fixture.tokens.forceRefreshCalls)
        assertEquals(0, fixture.invalidator.calls)
    }

    private fun refreshingFixture(
        beforeResponse: suspend () -> Unit = {},
        observe: (HttpRequestData) -> Unit = {},
    ): Fixture = fixture { request ->
        beforeResponse()
        observe(request)
        if (request.headers[HttpHeaders.Authorization] == "Bearer old-token") unauthorized() else sessionResponse()
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
        val network = NetworkClient(engine, NetworkConfig("test", "https://api.example.test/", timeoutMillis))
        val authenticated = AuthenticatedNetworkClient(network, tokens, invalidator)
        return Fixture(SessionApi(authenticated), tokens, invalidator)
    }

    private fun MockRequestHandleScope.sessionResponse(body: String = emptySessionJson()) =
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

    private fun emptySessionJson() =
        """{"user":{"id":"user-1","email":null,"displayName":"Person"},"memberships":[]}"""

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
        val api: SessionApi,
        val tokens: RecordingTokenProvider,
        val invalidator: RecordingInvalidator,
    )
}
