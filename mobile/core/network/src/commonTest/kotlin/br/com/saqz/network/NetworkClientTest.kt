package br.com.saqz.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NetworkClientTest {
    @Test
    fun `unresolved address maps only to connectivity`() = runTest {
        val result = client(MockEngine { throw UnresolvedAddressException() })
            .execute(HttpMethod.Get, "probe", serializer<ProbeResponse>())

        assertEquals(NetworkError.Connectivity, assertFailure(result))
    }

    @Test
    fun `unrelated exception maps to unknown without retaining sensitive cause`() = runTest {
        val secret = "private-exception-message"
        val messages = mutableListOf<String>()
        val client = NetworkClient(
            MockEngine { throw IllegalStateException(secret) },
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
            NetworkLogger(messages::add),
        )

        val result = client.execute(HttpMethod.Get, "probe", serializer<ProbeResponse>())

        assertEquals(NetworkError.Unknown, assertFailure(result))
        assertFalse(result.toString().contains(secret))
        assertFalse(messages.any { it.contains(secret) })
    }

    @Test
    fun `environment selects its injected base URL`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("https://dev.example.test/api/probe", request.url.toString())
            respond("{\"value\":\"ok\"}", headers = jsonHeaders())
        }

        val result = client(engine, NetworkConfig(NetworkEnvironment.Dev, "https://dev.example.test/api/"))
            .execute(HttpMethod.Get, "probe", serializer<ProbeResponse>())

        assertEquals(ProbeResponse("ok"), assertIs<NetworkResult.Success<ProbeResponse>>(result).value)
    }

    @Test
    fun `base URL rejects non HTTP schemes`() {
        assertFailsWith<IllegalArgumentException> { NetworkConfig(NetworkEnvironment.Dev, "file:///private/config") }
    }

    @Test
    fun `successful JSON response is decoded`() = runTest {
        val result = client(responding("{\"value\":\"decoded\"}"))
            .execute(HttpMethod.Get, "probe", serializer<ProbeResponse>())

        assertEquals("decoded", assertIs<NetworkResult.Success<ProbeResponse>>(result).value.value)
    }

    @Test
    fun `successful JSON ignores additive unknown fields`() = runTest {
        val result = client(responding("{\"value\":\"stable\",\"future\":true}"))
            .execute(HttpMethod.Get, "probe", serializer<ProbeResponse>())

        assertEquals("stable", assertIs<NetworkResult.Success<ProbeResponse>>(result).value.value)
    }

    @Test
    fun `complete API problem maps every stable field`() = runTest {
        val body = """{"status":429,"code":"INVITE_ATTEMPT_LIMIT","correlationId":"corr-1","fieldErrors":{"code":["invalid"]},"retryAfterSeconds":17}"""
        val result = client(responding(body, HttpStatusCode.TooManyRequests))
            .execute(HttpMethod.Get, "probe", serializer<ProbeResponse>())

        val problem = assertIs<NetworkError.ApiProblemError>(assertFailure(result)).problem
        assertEquals(429, problem.status)
        assertEquals("INVITE_ATTEMPT_LIMIT", problem.code)
        assertEquals("corr-1", problem.correlationId)
        assertEquals(listOf("invalid"), problem.fieldErrors?.get("code"))
        assertEquals(17, problem.retryAfterSeconds)
    }

    @Test
    fun `API problem accepts absent optional fields`() = runTest {
        val result = client(responding("""{"status":500,"correlationId":"corr-2"}""", HttpStatusCode.InternalServerError))
            .execute(HttpMethod.Get, "probe", serializer<ProbeResponse>())

        val problem = assertIs<NetworkError.ApiProblemError>(assertFailure(result)).problem
        assertEquals(null, problem.code)
        assertEquals(null, problem.fieldErrors)
        assertEquals(null, problem.retryAfterSeconds)
    }

    @Test
    fun `field errors preserve multiple messages per field`() = runTest {
        val body = """{"status":400,"code":"VALIDATION_FAILED","correlationId":"corr-3","fieldErrors":{"name":["short","control"]}}"""
        val result = client(responding(body, HttpStatusCode.BadRequest))
            .execute(HttpMethod.Get, "probe", serializer<ProbeResponse>())

        val problem = assertIs<NetworkError.ApiProblemError>(assertFailure(result)).problem
        assertEquals(listOf("short", "control"), problem.fieldErrors?.get("name"))
    }

    @Test
    fun `malformed error body maps only HTTP status`() = runTest {
        val result = client(responding("not-json", HttpStatusCode.BadGateway))
            .execute(HttpMethod.Get, "probe", serializer<ProbeResponse>())

        assertEquals(NetworkError.HttpStatus(502), assertFailure(result))
    }

    @Test
    fun `oversized error body is not parsed or retained`() = runTest {
        val secret = "sensitive-body-" + "x".repeat(100)
        val config = NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/", maxErrorBodyBytes = 32)
        val result = client(responding(secret, HttpStatusCode.BadGateway), config)
            .execute(HttpMethod.Get, "probe", serializer<ProbeResponse>())

        val error = assertFailure(result)
        assertEquals(NetworkError.HttpStatus(502), error)
        assertFalse(error.toString().contains(secret))
    }

    @Test
    fun `request timeout maps to stable timeout error`() = runTest {
        val engine = MockEngine {
            delay(100)
            respond("{\"value\":\"late\"}", headers = jsonHeaders())
        }
        val config = NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/", requestTimeoutMillis = 10)
        val result = client(engine, config).execute(HttpMethod.Get, "probe", serializer<ProbeResponse>())

        assertEquals(NetworkError.Timeout, assertFailure(result))
    }

    @Test
    fun `authorization token is sent but never retained in failure`() = runTest {
        val secret = "bearer-fixture-secret"
        val engine = MockEngine { request ->
            assertEquals("Bearer $secret", request.headers[HttpHeaders.Authorization])
            respondError(HttpStatusCode.ServiceUnavailable, "unavailable")
        }
        val result = client(engine).execute(HttpMethod.Get, "probe", serializer<ProbeResponse>(), secret)

        assertFalse(assertFailure(result).toString().contains(secret))
    }

    @Test
    fun `network logs describe requests and failures without secrets`() = runTest {
        val token = "private-bearer-token"
        val body = "private-response-body"
        val messages = mutableListOf<String>()
        val engine = MockEngine {
            respondError(HttpStatusCode.ServiceUnavailable, body)
        }
        val client = NetworkClient(
            engine,
            NetworkConfig(NetworkEnvironment.Dev, "https://api.example.test/"),
            NetworkLogger(messages::add),
        )

        client.execute(HttpMethod.Put, "api/session", serializer<ProbeResponse>(), token)

        assertEquals(2, messages.size)
        assertTrue(messages[0].contains("request PUT https://api.example.test/api/session authenticated=true"))
        assertTrue(messages[1].contains("status=503"))
        assertTrue(messages[1].contains("result=http-error status=503"))
        assertFalse(messages.any { it.contains(token) })
        assertFalse(messages.any { it.contains(body) })
    }

    @Test
    fun `sensitive server body is never retained in failure`() = runTest {
        val secret = "private-response-fixture-secret"
        val result = client(responding(secret, HttpStatusCode.InternalServerError))
            .execute(HttpMethod.Get, "probe", serializer<ProbeResponse>())

        assertFalse(assertFailure(result).toString().contains(secret))
    }

    @Test
    fun `no content request accepts an empty 204 response`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NoContent) }

        val result = client(engine).executeNoContent(HttpMethod.Delete, "probe")

        assertEquals(Unit, assertIs<NetworkResult.Success<Unit>>(result).value)
    }

    @Test
    fun `no content request rejects a successful response with a body`() = runTest {
        val result = client(responding("unexpected"))
            .executeNoContent(HttpMethod.Delete, "probe")

        assertEquals(NetworkError.InvalidResponse, assertFailure(result))
    }

    private fun client(
        engine: MockEngine,
        config: NetworkConfig = NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
    ) = NetworkClient(engine, config)

    private fun responding(body: String, status: HttpStatusCode = HttpStatusCode.OK) = MockEngine {
        respond(body, status, jsonHeaders())
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun assertFailure(result: NetworkResult<*>): NetworkError =
        assertIs<NetworkResult.Failure>(result).error

    @Serializable
    private data class ProbeResponse(val value: String)
}
