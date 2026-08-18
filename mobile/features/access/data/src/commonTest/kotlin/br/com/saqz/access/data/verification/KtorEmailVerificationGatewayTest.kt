package br.com.saqz.access.data.verification

import br.com.saqz.access.domain.verification.EmailVerificationError
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class KtorEmailVerificationGatewayTest {
    @Test
    fun `request posts empty body to the authenticated route`() = runTest {
        var seen: HttpRequestData? = null
        val gateway = gateway { request ->
            seen = request
            respond("", HttpStatusCode.Accepted)
        }

        assertIs<SaqzResult.Success<Unit>>(gateway.request())
        assertEquals("POST", seen?.method?.value)
        assertEquals("/api/email-verification/request", seen?.url?.encodedPath)
        assertEquals("Bearer session-token", seen?.headers?.get(HttpHeaders.Authorization))
        assertEquals(0L, seen?.body?.contentLength)
        assertNull(seen?.body?.contentType)
    }

    @Test
    fun `request is not called twice for a single unsafe write`() = runTest {
        var calls = 0
        val gateway = gateway { calls += 1; problem(503, "IDENTITY_PROVIDER_UNAVAILABLE") }

        assertEquals(DataError.Server, gateway.request().dataFailure())
        assertEquals(1, calls)
    }

    @Test
    fun `rate limit keeps the seconds the banner can ignore`() = runTest {
        val body = """{"status":429,"code":"EMAIL_VERIFICATION_RATE_LIMIT","correlationId":"private","retryAfterSeconds":42}"""
        val gateway = gateway { respond(body, HttpStatusCode.TooManyRequests, jsonHeaders()) }

        val result = gateway.request()
        assertEquals(EmailVerificationError.RateLimited(retryAfterSeconds = 42), result.failure())
        assertFalse(result.toString().contains("private"))
    }

    @Test
    fun `missing session maps to unauthenticated`() = runTest {
        val gateway = gateway { problem(401, "AUTHENTICATION_REQUIRED") }

        assertEquals(DataError.Unauthenticated, gateway.request().dataFailure())
    }

    @Test
    fun `provider outage maps to server failure`() = runTest {
        val gateway = gateway { problem(503, "IDENTITY_PROVIDER_UNAVAILABLE") }

        assertEquals(DataError.Server, gateway.request().dataFailure())
    }

    @Test
    fun `timeout maps to typed timeout failure`() = runTest {
        val gateway = gateway(
            engine = MockEngine { delay(100); respond("", HttpStatusCode.Accepted) },
            timeoutMillis = 10,
        )

        assertEquals(DataError.Timeout, gateway.request().dataFailure())
    }

    @Test
    fun `cancellation propagates without a failure value`() = runTest {
        val gateway = gateway(engine = MockEngine { throw CancellationException("cancelled") })

        assertFailsWith<CancellationException> { gateway.request() }
    }

    private fun gateway(
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = gateway(MockEngine { request -> response(request) })

    private fun gateway(engine: MockEngine, timeoutMillis: Long = 10_000) = KtorEmailVerificationGateway(
        AuthenticatedNetworkClient(
            NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/", timeoutMillis)),
            Tokens(),
            NoopInvalidator(),
        ),
    )

    private fun MockRequestHandleScope.problem(status: Int, code: String) = respond(
        """{"status":$status,"code":"$code","correlationId":"private-correlation"}""",
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun SaqzResult<*, EmailVerificationError>.failure() =
        assertIs<SaqzResult.Failure<EmailVerificationError>>(this).error

    private fun SaqzResult<*, EmailVerificationError>.dataFailure() =
        assertIs<EmailVerificationError.DataFailure>(failure()).error

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) {
            completion(TokenResult.Available("session-token"))
        }
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }
}
