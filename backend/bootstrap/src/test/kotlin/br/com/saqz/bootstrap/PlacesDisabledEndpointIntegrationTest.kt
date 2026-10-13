package br.com.saqz.bootstrap

import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import tools.jackson.databind.ObjectMapper

/** Sem chave do Google o recurso está desligado: availability avisa o app e as buscas respondem 503. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PlacesDisabledEndpointIntegrationTest.Configuration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.flyway.enabled=false", "saqz.firebase.emulator.enabled=true", "saqz.places.google.api-key="])
class PlacesDisabledEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var json: ObjectMapper

    @Test
    fun `places is reported off without a key and searches answer 503`() {
        assertEquals(401, request("/api/places/availability", actor = null).statusCode())

        val actor = UUID.randomUUID()
        val availability = request("/api/places/availability", actor)
        assertEquals(200, availability.statusCode(), availability.body())
        assertEquals(false, json.readTree(availability.body())["enabled"].booleanValue())
        assertEquals(503, request("/api/places/autocomplete?q=arena&session=s", actor).statusCode())
        assertEquals(503, request("/api/places/details?placeId=abc&session=s", actor).statusCode())
    }

    private fun request(path: String, actor: UUID?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        if (actor != null) builder.header("Authorization", "Bearer $actor")
        return HttpClient.newHttpClient().send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Configuration {
        @Bean fun placesDisabledTestDataSource(): DataSource = TestPostgres.migrated("classpath:db/migration").dataSource

        @Bean @Primary fun placesDisabledTestVerifier() = object : VerifyRequestIdentity {
            override fun execute(token: RawIdentityToken) =
                TokenVerification.Verified(RequestIdentity(token.value, emailVerified = true, displayName = "Test Person"))
        }
    }
}
