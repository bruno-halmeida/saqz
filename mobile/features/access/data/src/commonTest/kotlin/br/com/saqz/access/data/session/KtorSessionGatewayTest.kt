package br.com.saqz.access.data.session

import br.com.saqz.access.domain.session.AccessError
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class KtorSessionGatewayTest {
    @Test fun `bootstrap sends payloadless PUT to exact authenticated route`() = runTest {
        val fixture = fixture { request ->
            assertEquals("PUT", request.method.value)
            assertEquals("/api/session", request.url.encodedPath)
            assertEquals("Bearer session-token", request.headers[HttpHeaders.Authorization])
            assertEquals(0, request.body.contentLength ?: 0)
            sessionResponse()
        }

        fixture.gateway.bootstrap()

        assertEquals(listOf(false), fixture.tokens.forceRefreshCalls)
    }

    @Test fun `bootstrap maps complete user and membership`() = runTest {
        val result = fixture {
            sessionResponse("""{"user":{"id":"user-1","email":"person@example.test","displayName":"Person"},"memberships":[{"groupId":"group-1","groupName":"Group","role":"ADMIN"}]}""")
        }.gateway.bootstrap().success()

        assertEquals("user-1", result.user.id)
        assertEquals("person@example.test", result.user.email)
        assertEquals("Person", result.user.displayName)
        assertEquals("group-1", result.memberships.single().groupId.value)
        assertEquals("Group", result.memberships.single().groupName)
        assertEquals("ADMIN", result.memberships.single().role.value)
    }

    @Test fun `bootstrap preserves nullable email`() = runTest {
        assertNull(fixture { sessionResponse() }.gateway.bootstrap().success().user.email)
    }

    @Test fun `bootstrap preserves empty memberships`() = runTest {
        assertEquals(emptyList(), fixture { sessionResponse() }.gateway.bootstrap().success().memberships)
    }

    @Test fun `bootstrap preserves multiple memberships in order`() = runTest {
        val body = """{"user":{"id":"user-1","email":null,"displayName":"Person"},"memberships":[{"groupId":"one","groupName":"One","role":"OWNER"},{"groupId":"two","groupName":"Two","role":"ATHLETE"}]}"""

        val memberships = fixture { sessionResponse(body) }.gateway.bootstrap().success().memberships

        assertEquals(listOf("one", "two"), memberships.map { it.groupId.value })
        assertEquals(listOf("OWNER", "ATHLETE"), memberships.map { it.role.value })
    }

    @Test fun `missing required user field maps to invalid response`() = runTest {
        val result = fixture {
            sessionResponse("""{"user":{"email":null,"displayName":"Person"},"memberships":[]}""")
        }.gateway.bootstrap()

        assertEquals(DataError.InvalidResponse, result.dataFailure())
    }

    @Test fun `blank required membership field maps to invalid response`() = runTest {
        val body = """{"user":{"id":"user-1","email":null,"displayName":"Person"},"memberships":[{"groupId":"","groupName":"Group","role":"ADMIN"}]}"""

        assertEquals(DataError.InvalidResponse, fixture { sessionResponse(body) }.gateway.bootstrap().dataFailure())
    }

    @Test fun `second unauthenticated response maps to explicit access error`() = runTest {
        assertEquals(AccessError.Unauthenticated, fixture { problem(401, "AUTHENTICATION_REQUIRED") }.gateway.bootstrap().failure())
    }

    @Test fun `email not verified problem maps without transport details`() = runTest {
        assertEquals(AccessError.EmailNotVerified, fixture { problem(403, "EMAIL_NOT_VERIFIED") }.gateway.bootstrap().failure())
    }

    @Test fun `other forbidden problem maps to explicit forbidden error`() = runTest {
        assertEquals(AccessError.Forbidden, fixture { problem(403, "GROUP_FORBIDDEN") }.gateway.bootstrap().failure())
    }

    @Test fun `structured validation preserves field messages with empty globals`() = runTest {
        val body = """{"status":400,"code":"VALIDATION_FAILED","correlationId":"private-correlation","fieldErrors":{"email":["invalid"]}}"""
        val error = fixture { respond(body, HttpStatusCode.BadRequest, jsonHeaders()) }.gateway.bootstrap().failure()
        val details = assertIs<AccessError.Validation>(error).details

        assertEquals(emptyList(), details.globalMessages)
        assertEquals(listOf("invalid"), details.fieldMessages["email"])
        assertFalse(error.toString().contains("private-correlation"))
    }

    @Test fun `not found maps to shared not found failure`() = runTest {
        assertEquals(DataError.NotFound, fixture { problem(404, "SESSION_NOT_FOUND") }.gateway.bootstrap().dataFailure())
    }

    @Test fun `server failure is returned after one unsafe write call`() = runTest {
        var calls = 0
        val fixture = fixture {
            calls += 1
            problem(503, "TEMPORARY")
        }

        assertEquals(DataError.Server, fixture.gateway.bootstrap().dataFailure())
        assertEquals(1, calls)
    }

    @Test fun `request timeout maps to typed timeout failure`() = runTest {
        val fixture = fixture(
            engine = MockEngine { delay(100); sessionResponse() },
            timeoutMillis = 10,
        )

        assertEquals(DataError.Timeout, fixture.gateway.bootstrap().dataFailure())
    }

    @Test fun `unresolved address maps to typed connectivity failure`() = runTest {
        val fixture = fixture(engine = MockEngine { throw UnresolvedAddressException() })

        assertEquals(DataError.Connectivity, fixture.gateway.bootstrap().dataFailure())
    }

    @Test fun `unknown exception maps without retaining its sensitive message`() = runTest {
        val secret = "private-exception-detail"
        val result = fixture(engine = MockEngine { throw IllegalStateException(secret) }).gateway.bootstrap()

        assertEquals(DataError.Unknown, result.dataFailure())
        assertFalse(result.toString().contains(secret))
    }

    @Test fun `token unavailability maps to unknown without an HTTP call`() = runTest {
        var calls = 0
        val fixture = fixture(
            tokens = RecordingTokenProvider(current = TokenResult.Unavailable),
            response = { calls += 1; sessionResponse() },
        )

        assertEquals(DataError.Unknown, fixture.gateway.bootstrap().dataFailure())
        assertEquals(0, calls)
    }

    @Test fun `cancellation propagates without a failure value`() = runTest {
        val fixture = fixture(engine = MockEngine { throw CancellationException("cancelled") })

        assertFailsWith<CancellationException> { fixture.gateway.bootstrap() }
    }

    private fun fixture(
        tokens: RecordingTokenProvider = RecordingTokenProvider(),
        timeoutMillis: Long = 10_000,
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = fixture(MockEngine { request -> response(request) }, tokens, timeoutMillis)

    private fun fixture(
        engine: MockEngine,
        tokens: RecordingTokenProvider = RecordingTokenProvider(),
        timeoutMillis: Long = 10_000,
    ): Fixture {
        val network = NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/", timeoutMillis))
        val authenticated = AuthenticatedNetworkClient(network, tokens, object : SessionInvalidator {
            override fun invalidate() = Unit
        })
        return Fixture(KtorSessionGateway(authenticated), tokens)
    }

    private fun MockRequestHandleScope.sessionResponse(body: String = emptySessionJson()) =
        respond(body, HttpStatusCode.OK, jsonHeaders())

    private fun MockRequestHandleScope.problem(status: Int, code: String) = respond(
        """{"status":$status,"code":"$code","correlationId":"private-correlation"}""",
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun emptySessionJson() =
        """{"user":{"id":"user-1","email":null,"displayName":"Person"},"memberships":[]}"""

    private fun SaqzResult<br.com.saqz.access.domain.session.AccessSession, AccessError>.success() =
        assertIs<SaqzResult.Success<br.com.saqz.access.domain.session.AccessSession>>(this).value

    private fun SaqzResult<*, AccessError>.failure() = assertIs<SaqzResult.Failure<AccessError>>(this).error

    private fun SaqzResult<*, AccessError>.dataFailure() = assertIs<AccessError.DataFailure>(failure()).error

    private class RecordingTokenProvider(
        private val current: TokenResult = TokenResult.Available("session-token"),
        private val refresh: TokenResult = TokenResult.Available("fresh-token"),
    ) : IdTokenProvider {
        val forceRefreshCalls = mutableListOf<Boolean>()

        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) {
            forceRefreshCalls += forceRefresh
            completion(if (forceRefresh) refresh else current)
        }
    }

    private data class Fixture(
        val gateway: KtorSessionGateway,
        val tokens: RecordingTokenProvider,
    )
}
