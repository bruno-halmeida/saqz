package br.com.saqz.receivables.data

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import br.com.saqz.receivables.domain.ReceivablesAvailability
import br.com.saqz.receivables.domain.ReceivablesError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorReceivablesAvailabilityGatewayTest {
    @Test
    fun authenticatedGetHasNoClientIdentityAndDoesNotCache() = runTest {
        var calls = 0
        val gateway = gateway(MockEngine { request ->
            calls++
            assertEquals("GET", request.method.value)
            assertEquals("/api/receivables/availability", request.url.encodedPath)
            assertEquals("", request.url.encodedQuery)
            assertEquals("Bearer session-token", request.headers[HttpHeaders.Authorization])
            respond(body(enabled = calls == 1), HttpStatusCode.OK, jsonHeaders)
        })
        assertEquals(SaqzResult.Success(ReceivablesAvailability(true, true)), gateway.get())
        assertEquals(SaqzResult.Success(ReceivablesAvailability(false, false)), gateway.get())
        assertEquals(2, calls)
    }

    @Test
    fun missingMalformedOrContradictoryFieldsFailClosed() = runTest {
        listOf(
            "{}",
            """{"backendEnabled":true,"mobileEnabled":true}""",
            """{"backendEnabled":false,"mobileEnabled":true,"maintenanceAvailable":true}""",
            """{"backendEnabled":true,"mobileEnabled":true,"maintenanceAvailable":false}""",
            """{"backendEnabled":"true","mobileEnabled":true,"maintenanceAvailable":true}""",
        ).forEach { response ->
            val gateway = gateway(MockEngine { respond(response, HttpStatusCode.OK, jsonHeaders) })
            assertEquals(failure(DataError.InvalidResponse), gateway.get())
        }
    }

    @Test
    fun rolloutErrorEnvelopeMapsUsingHttpStatus() = runTest {
        listOf(401 to DataError.Unauthenticated, 403 to DataError.Forbidden, 404 to DataError.NotFound).forEach { (status, error) ->
            val gateway = gateway(MockEngine {
                respond("""{"error":"UNAUTHORIZED","requestId":"private"}""", HttpStatusCode.fromValue(status), jsonHeaders)
            })
            assertEquals(failure(error), gateway.get())
        }
    }

    @Test
    fun safeReadRetriesServerFailure() = runTest {
        var calls = 0
        val gateway = gateway(MockEngine {
            calls++
            if (calls == 1) respond("", HttpStatusCode.ServiceUnavailable)
            else respond(body(true), HttpStatusCode.OK, jsonHeaders)
        })
        assertEquals(SaqzResult.Success(ReceivablesAvailability(true, true)), gateway.get())
        assertEquals(2, calls)
    }

    @Test
    fun cancellationPropagates() = runTest {
        val gateway = gateway(MockEngine { throw CancellationException("cancelled") })
        assertFailsWith<CancellationException> { gateway.get() }
    }

    private fun gateway(engine: MockEngine) = KtorReceivablesAvailabilityGateway(
        AuthenticatedNetworkClient(
            NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/")),
            Tokens(),
            NoopInvalidator(),
        ),
    )

    private fun body(enabled: Boolean) =
        """{"backendEnabled":$enabled,"mobileEnabled":$enabled,"maintenanceAvailable":true}"""

    private fun failure(error: DataError) = SaqzResult.Failure(ReceivablesError.Data(error))

    private val jsonHeaders = headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.CacheControl to listOf("no-store"))

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("session-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }
}
