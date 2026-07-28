package br.com.saqz.access.data.passwordreset

import br.com.saqz.access.domain.passwordreset.PasswordResetError
import br.com.saqz.access.domain.passwordreset.PasswordResetTicket
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
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

class KtorPasswordResetGatewayTest {
    @Test fun `request posts the email to the anonymous route without a bearer token`() = runTest {
        var seen: HttpRequestData? = null
        val gateway = gateway { request ->
            seen = request
            respond("", HttpStatusCode.Accepted)
        }

        assertIs<SaqzResult.Success<Unit>>(gateway.requestCode("ana@example.test"))
        assertEquals("POST", seen?.method?.value)
        assertEquals("/api/password-reset/request", seen?.url?.encodedPath)
        assertNull(seen?.headers?.get(HttpHeaders.Authorization))
        assertEquals("""{"email":"ana@example.test"}""", seen.bodyText())
    }

    @Test fun `request is not called twice for a single unsafe write`() = runTest {
        var calls = 0
        val gateway = gateway { calls += 1; problem(503, "IDENTITY_PROVIDER_UNAVAILABLE") }

        assertEquals(DataError.Server, gateway.requestCode("ana@example.test").dataFailure())
        assertEquals(1, calls)
    }

    @Test fun `verify posts email and code and maps the issued ticket`() = runTest {
        var seen: HttpRequestData? = null
        val gateway = gateway { request ->
            seen = request
            respond("""{"token":"reset-token","expiraEmSegundos":600}""", HttpStatusCode.OK, jsonHeaders())
        }

        val ticket = gateway.verifyCode("ana@example.test", "1234").success()

        assertEquals(PasswordResetTicket("reset-token", 600), ticket)
        assertEquals("/api/password-reset/verify", seen?.url?.encodedPath)
        assertNull(seen?.headers?.get(HttpHeaders.Authorization))
        assertEquals("""{"email":"ana@example.test","code":"1234"}""", seen.bodyText())
    }

    @Test fun `blank token in the ticket maps to invalid response`() = runTest {
        val gateway = gateway { respond("""{"token":"","expiraEmSegundos":600}""", HttpStatusCode.OK, jsonHeaders()) }

        assertEquals(DataError.InvalidResponse, gateway.verifyCode("ana@example.test", "1234").dataFailure())
    }

    @Test fun `wrong code keeps the remaining attempts the screen prints`() = runTest {
        val body = """{"status":400,"code":"PASSWORD_RESET_CODE_INVALID","correlationId":"private","remainingAttempts":2}"""
        val gateway = gateway { respond(body, HttpStatusCode.BadRequest, jsonHeaders()) }

        val error = gateway.verifyCode("ana@example.test", "9999").failure()

        assertEquals(PasswordResetError.CodeInvalid(remainingAttempts = 2), error)
        assertFalse(error.toString().contains("private"))
    }

    @Test fun `expired code stays distinct from a wrong code`() = runTest {
        val gateway = gateway { problem(410, "PASSWORD_RESET_CODE_EXPIRED") }

        assertEquals(PasswordResetError.CodeExpired, gateway.verifyCode("ana@example.test", "1234").failure())
    }

    @Test fun `attempt limit stays distinct from both`() = runTest {
        val gateway = gateway { problem(429, "PASSWORD_RESET_ATTEMPT_LIMIT") }

        assertEquals(PasswordResetError.AttemptLimit, gateway.verifyCode("ana@example.test", "1234").failure())
    }

    @Test fun `rate limit keeps the seconds the countdown needs`() = runTest {
        val body = """{"status":429,"code":"PASSWORD_RESET_RATE_LIMIT","correlationId":"private","retryAfterSeconds":45}"""
        val gateway = gateway { respond(body, HttpStatusCode.TooManyRequests, jsonHeaders()) }

        assertEquals(
            PasswordResetError.RateLimited(retryAfterSeconds = 45),
            gateway.requestCode("ana@example.test").failure(),
        )
    }

    @Test fun `confirm posts token and new password to the anonymous route`() = runTest {
        var seen: HttpRequestData? = null
        val gateway = gateway { request ->
            seen = request
            respond("", HttpStatusCode.NoContent)
        }

        assertIs<SaqzResult.Success<Unit>>(gateway.confirm("reset-token", "senha-nova-8"))
        assertEquals("/api/password-reset/confirm", seen?.url?.encodedPath)
        assertNull(seen?.headers?.get(HttpHeaders.Authorization))
        assertEquals("""{"token":"reset-token","novaSenha":"senha-nova-8"}""", seen.bodyText())
    }

    @Test fun `spent token sends the screen back to asking for a code`() = runTest {
        val gateway = gateway { problem(410, "PASSWORD_RESET_TOKEN_INVALID") }

        assertEquals(PasswordResetError.TokenInvalid, gateway.confirm("stale", "senha-nova-8").failure())
    }

    @Test fun `weak password arrives as a field message`() = runTest {
        val body = """{"status":400,"code":"VALIDATION_FAILED","correlationId":"private","fieldErrors":{"novaSenha":["deve ter entre 8 e 128 caracteres"]}}"""
        val gateway = gateway { respond(body, HttpStatusCode.BadRequest, jsonHeaders()) }

        val details = assertIs<PasswordResetError.Validation>(gateway.confirm("reset-token", "curta").failure()).details

        assertEquals(listOf("deve ter entre 8 e 128 caracteres"), details.fieldMessages["novaSenha"])
        assertEquals(emptyList(), details.globalMessages)
    }

    @Test fun `request timeout maps to typed timeout failure`() = runTest {
        val gateway = gateway(
            engine = MockEngine { delay(100); respond("", HttpStatusCode.Accepted) },
            timeoutMillis = 10,
        )

        assertEquals(DataError.Timeout, gateway.requestCode("ana@example.test").dataFailure())
    }

    @Test fun `unresolved address maps to typed connectivity failure`() = runTest {
        val gateway = gateway(engine = MockEngine { throw UnresolvedAddressException() })

        assertEquals(DataError.Connectivity, gateway.requestCode("ana@example.test").dataFailure())
    }

    @Test fun `unknown exception maps without retaining its sensitive message`() = runTest {
        val secret = "private-exception-detail"
        val result = gateway(engine = MockEngine { throw IllegalStateException(secret) }).requestCode("ana@example.test")

        assertEquals(DataError.Unknown, result.dataFailure())
        assertFalse(result.toString().contains(secret))
    }

    @Test fun `cancellation propagates without a failure value`() = runTest {
        val gateway = gateway(engine = MockEngine { throw CancellationException("cancelled") })

        assertFailsWith<CancellationException> { gateway.requestCode("ana@example.test") }
    }

    private fun gateway(
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = gateway(MockEngine { request -> response(request) })

    private fun gateway(engine: MockEngine, timeoutMillis: Long = 10_000) = KtorPasswordResetGateway(
        NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/", timeoutMillis)),
    )

    private fun MockRequestHandleScope.problem(status: Int, code: String) = respond(
        """{"status":$status,"code":"$code","correlationId":"private-correlation"}""",
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun HttpRequestData?.bodyText() = assertIs<TextContent>(this?.body).text

    private fun <T> SaqzResult<T, PasswordResetError>.success() = assertIs<SaqzResult.Success<T>>(this).value

    private fun SaqzResult<*, PasswordResetError>.failure() =
        assertIs<SaqzResult.Failure<PasswordResetError>>(this).error

    private fun SaqzResult<*, PasswordResetError>.dataFailure() =
        assertIs<PasswordResetError.DataFailure>(failure()).error
}
