package br.com.saqz.groups.adapter.output.places

import br.com.saqz.groups.application.places.PlacesUpstreamException
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpGooglePlacesProviderTest {
    private class Seen(val method: String, val uri: String, val headers: Map<String, String>, val body: String)

    private val seen = CopyOnWriteArrayList<Seen>()
    private var status = 200
    private var response = "{}"
    private var delayMs = 0L
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            seen += Seen(
                exchange.requestMethod,
                exchange.requestURI.toString(),
                exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() },
                exchange.requestBody.readBytes().decodeToString(),
            )
            if (delayMs > 0) Thread.sleep(delayMs)
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }
    private val baseUrl = "http://127.0.0.1:${server.address.port}"
    private val mapper = ObjectMapper()

    @AfterTest fun stop() = server.stop(0)

    private fun provider(readTimeout: Duration = Duration.ofSeconds(4)) =
        HttpGooglePlacesProvider("secret-key", baseUrl, readTimeout = readTimeout)

    @Test
    fun `autocomplete sends key header session language and brazil bias and parses suggestions`() {
        response = """{"suggestions":[
            {"placePrediction":{"placeId":"abc_1","text":{"text":"Arena Beach, Rua A, Santos"},
              "structuredFormat":{"mainText":{"text":"Arena Beach"},"secondaryText":{"text":"Rua A, Santos"}}}},
            {"queryPrediction":{"text":{"text":"arena"}}}]}"""

        val result = provider().autocomplete("arena", "sess-1")

        assertEquals(1, result.size)
        assertEquals("abc_1", result[0].placeId)
        assertEquals("Arena Beach", result[0].name)
        assertEquals("Rua A, Santos", result[0].address)
        assertEquals("Arena Beach, Rua A, Santos", result[0].description)
        val request = seen.single()
        assertEquals("POST", request.method)
        assertEquals("/v1/places:autocomplete", request.uri)
        assertEquals("secret-key", request.headers["x-goog-api-key"])
        val body = mapper.readTree(request.body)
        assertEquals("arena", body["input"].asText())
        assertEquals("sess-1", body["sessionToken"].asText())
        assertEquals("pt-BR", body["languageCode"].asText())
        assertEquals("br", body["regionCode"].asText())
        assertEquals("br", body["includedRegionCodes"][0].asText())
        assertEquals(-33.75, body["locationBias"]["rectangle"]["low"]["latitude"].asDouble())
        assertEquals(5.27, body["locationBias"]["rectangle"]["high"]["latitude"].asDouble())
    }

    @Test
    fun `autocomplete keeps at most five suggestions`() {
        val items = (1..8).joinToString(",") {
            """{"placePrediction":{"placeId":"p$it","text":{"text":"Lugar $it"}}}"""
        }
        response = """{"suggestions":[$items]}"""

        val result = provider().autocomplete("lugar", "s")

        assertEquals(5, result.size)
        assertEquals("Lugar 1", result[0].name)
        assertEquals("", result[0].address)
    }

    @Test
    fun `details uses field mask key header and session in the query and parses location`() {
        response = """{"id":"abc_1","displayName":{"text":"Arena Beach"},"formattedAddress":"Rua A, 10 - Santos",
            "location":{"latitude":-23.5,"longitude":-46.6}}"""

        val result = provider().details("abc_1", "sess 1")

        assertEquals("abc_1", result.placeId)
        assertEquals("Arena Beach", result.name)
        assertEquals("Rua A, 10 - Santos", result.address)
        assertEquals(-23.5, result.latitude)
        assertEquals(-46.6, result.longitude)
        val request = seen.single()
        assertEquals("GET", request.method)
        assertEquals("/v1/places/abc_1?sessionToken=sess+1&languageCode=pt-BR&regionCode=BR", request.uri)
        assertEquals("secret-key", request.headers["x-goog-api-key"])
        assertEquals("id,displayName,formattedAddress,location", request.headers["x-goog-fieldmask"])
        assertTrue("secret-key" !in request.uri)
    }

    @Test
    fun `details without location returns null coordinates`() {
        response = """{"id":"x","displayName":{"text":"Quadra"},"formattedAddress":"Rua B"}"""

        val result = provider().details("x", "s")

        assertNull(result.latitude)
        assertNull(result.longitude)
    }

    @Test
    fun `non success status becomes an upstream failure without leaking the key`() {
        status = 403
        response = """{"error":{"message":"API key not valid"}}"""

        val failure = assertFailsWith<PlacesUpstreamException> { provider().autocomplete("arena", "s") }

        assertTrue("secret-key" !in failure.message.orEmpty())
        assertEquals(1, seen.size)
    }

    @Test
    fun `read timeout becomes an upstream failure with no retry`() {
        delayMs = 1_000

        assertFailsWith<PlacesUpstreamException> {
            provider(readTimeout = Duration.ofMillis(250)).details("abc", "s")
        }
        // Em máquina lenta (runner do CI) o prazo do cliente pode vencer antes de o pedido chegar ao servidor, que então
        // não registra nada: exigir exatamente uma chamada fazia o teste falhar sem defeito. O que importa é não haver
        // nova tentativa: espera o atraso inteiro passar (uma repetição chegaria depois dele) e confere no máximo uma.
        Thread.sleep(delayMs + 500)
        assertTrue(seen.size <= 1, "o cliente tentou de novo depois do timeout: ${seen.size} chamadas")
    }

    @Test
    fun `unreachable server becomes an upstream failure`() {
        server.stop(0)

        assertFailsWith<PlacesUpstreamException> { provider().autocomplete("arena", "s") }
    }
}
