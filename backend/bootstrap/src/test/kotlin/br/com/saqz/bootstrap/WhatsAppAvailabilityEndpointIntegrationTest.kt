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

/** Sem a propriedade o WhatsApp está desligado, e o endpoint existe mesmo assim: é ele que avisa o app. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(WhatsAppAvailabilityEndpointIntegrationTest.Configuration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.flyway.enabled=false", "saqz.firebase.emulator.enabled=true"])
class WhatsAppAvailabilityEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var json: ObjectMapper

    @Test
    fun `whatsapp is reported off by default and only to a session`() {
        assertEquals(401, request(actor = null).statusCode())

        val response = request(actor = UUID.randomUUID())
        assertEquals(200, response.statusCode(), response.body())
        assertEquals(false, json.readTree(response.body())["enabled"].booleanValue())
    }

    private fun request(actor: UUID?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/whatsapp/availability"))
        if (actor != null) builder.header("Authorization", "Bearer $actor")
        return HttpClient.newHttpClient().send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Configuration {
        @Bean fun whatsAppAvailabilityTestDataSource(): DataSource = TestPostgres.migrated("classpath:db/migration").dataSource

        @Bean @Primary fun whatsAppAvailabilityTestVerifier() = object : VerifyRequestIdentity {
            override fun execute(token: RawIdentityToken) =
                TokenVerification.Verified(RequestIdentity(token.value, emailVerified = true, displayName = "Test Person"))
        }
    }
}
