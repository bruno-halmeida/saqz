package br.com.saqz.bootstrap

import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.RequestIdentity
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.ObjectMapper

/** Com a chave configurada e um Google falso: availability true, busca e detalhes passam pelo backend. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.flyway.enabled=false", "saqz.firebase.emulator.enabled=true", "saqz.places.google.api-key=test-key"],
)
@Import(PlacesEndpointIntegrationTest.Configuration::class)
@ActiveProfiles("test")
class PlacesEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var json: ObjectMapper

    companion object {
        private val google = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/places:autocomplete") { exchange ->
                val ok = exchange.requestHeaders.getFirst("X-Goog-Api-Key") == "test-key"
                val bytes = (
                    if (ok) {
                        """{"suggestions":[{"placePrediction":{"placeId":"abc","text":{"text":"Arena, Rua A"},
                        "structuredFormat":{"mainText":{"text":"Arena"},"secondaryText":{"text":"Rua A"}}}}]}"""
                    } else "{}"
                    ).toByteArray()
                exchange.sendResponseHeaders(if (ok) 200 else 403, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            createContext("/v1/places/abc") { exchange ->
                val bytes = """{"id":"abc","displayName":{"text":"Arena"},"formattedAddress":"Rua A, 10",
                    "location":{"latitude":-23.5,"longitude":-46.6}}""".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            createContext("/v1/places/broken") { exchange ->
                exchange.sendResponseHeaders(500, -1)
                exchange.close()
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun googleProperties(registry: DynamicPropertyRegistry) {
            registry.add("saqz.places.google.base-url") { "http://127.0.0.1:${google.address.port}" }
        }
    }

    @Test
    fun `places endpoints need a session`() {
        assertEquals(401, get("/api/places/availability", actor = null).statusCode())
        assertEquals(401, get("/api/places/autocomplete?q=arena&session=s", actor = null).statusCode())
        assertEquals(401, get("/api/places/details?placeId=abc&session=s", actor = null).statusCode())
    }

    @Test
    fun `availability is on and autocomplete and details go through the fake google`() {
        val actor = UUID.randomUUID()

        val availability = get("/api/places/availability", actor)
        assertEquals(200, availability.statusCode(), availability.body())
        assertEquals(true, json.readTree(availability.body())["enabled"].booleanValue())

        val suggestions = get("/api/places/autocomplete?q=are&session=sess-1", actor)
        assertEquals(200, suggestions.statusCode(), suggestions.body())
        val first = json.readTree(suggestions.body())["suggestions"][0]
        assertEquals("abc", first["placeId"].asString())
        assertEquals("Arena", first["name"].asString())
        assertEquals("Rua A", first["address"].asString())
        assertEquals("Arena, Rua A", first["description"].asString())

        val details = get("/api/places/details?placeId=abc&session=sess-1", actor)
        assertEquals(200, details.statusCode(), details.body())
        val place = json.readTree(details.body())
        assertEquals("Rua A, 10", place["address"].asString())
        assertEquals(-23.5, place["latitude"].doubleValue())
        assertEquals(-46.6, place["longitude"].doubleValue())
    }

    @Test
    fun `bad parameters are 400 and a google failure is 502`() {
        val actor = UUID.randomUUID()

        assertEquals(400, get("/api/places/autocomplete?q=ar&session=s", actor).statusCode())
        assertEquals(400, get("/api/places/autocomplete?q=arena", actor).statusCode())
        assertEquals(400, get("/api/places/details?placeId=a%2Fb&session=s", actor).statusCode())
        assertEquals(502, get("/api/places/details?placeId=broken&session=s", actor).statusCode())
    }

    private fun get(path: String, actor: UUID?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        if (actor != null) builder.header("Authorization", "Bearer $actor")
        return HttpClient.newHttpClient().send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Configuration {
        @Bean fun placesTestDataSource(): DataSource = TestPostgres.migrated("classpath:db/migration").dataSource

        @Bean @Primary fun placesTestVerifier() = object : VerifyRequestIdentity {
            override fun execute(token: RawIdentityToken) =
                TokenVerification.Verified(RequestIdentity(token.value, emailVerified = true, displayName = "Test Person"))
        }
    }
}
