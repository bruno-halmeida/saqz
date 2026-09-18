package br.com.saqz.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class CorrelationRetryTest {
    @Test
    fun `retries share one correlation id and count the attempts`() = runTest {
        val seen = mutableListOf<Pair<String?, String?>>()
        val engine = MockEngine { request ->
            seen += request.headers["X-Correlation-ID"] to request.headers["X-Correlation-Attempt"]
            if (seen.size < 3) respondError(HttpStatusCode.ServiceUnavailable)
            else respond("{\"value\":\"ok\"}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"))

        val result = retryTransport(RetrySafety.Read, delayMillis = {}) {
            client.execute(HttpMethod.Get, "probe", serializer<CorrelationProbe>())
        }

        assertIs<NetworkResult.Success<CorrelationProbe>>(result)
        assertEquals(listOf("1", "2", "3"), seen.map { it.second })
        assertEquals(1, seen.map { it.first }.distinct().size)
        assertEquals(36, seen.first().first?.length)
    }

    @Test
    fun `separate operations and calls without retry get their own id with attempt one`() = runTest {
        val seen = mutableListOf<Pair<String?, String?>>()
        val engine = MockEngine { request ->
            seen += request.headers["X-Correlation-ID"] to request.headers["X-Correlation-Attempt"]
            respond("{\"value\":\"ok\"}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"))

        retryTransport(RetrySafety.Read, delayMillis = {}) {
            client.execute(HttpMethod.Get, "probe", serializer<CorrelationProbe>())
        }
        client.execute(HttpMethod.Get, "probe", serializer<CorrelationProbe>())

        assertEquals(listOf("1", "1"), seen.map { it.second })
        assertNotEquals(seen[0].first, seen[1].first)
    }
}

@Serializable
private data class CorrelationProbe(val value: String)
