package br.com.saqz.bootstrap

import br.com.saqz.identity.api.AuthenticatedPrincipal
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
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
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SessionEndpointIntegrationTest.SessionTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class SessionEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var verifier: SessionVerifier

    @Test
    fun `valid principal returns exact session fields`() {
        verifier.principal = AuthenticatedPrincipal("subject-8", "session@example.test", true)

        val response = session()

        assertEquals(200, response.statusCode())
        assertSession(response.body(), "subject-8", "\"session@example.test\"", "true")
    }

    @Test
    fun `missing email claims return both optional fields as null`() {
        verifier.principal = AuthenticatedPrincipal("subject-without-email", null, null)

        assertSession(session().body(), "subject-without-email", "null", "null")
    }

    @Test
    fun `same principal returns equal fields twice without state`() {
        verifier.principal = AuthenticatedPrincipal("stable-subject", "stable@example.test", false)

        val first = session().body()
        val second = session().body()

        assertEquals(first, second)
        assertSession(second, "stable-subject", "\"stable@example.test\"", "false")
    }

    private fun session(): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port/api/session"))
            .header("Authorization", "Bearer session-token")
            .GET()
            .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun assertSession(body: String, subject: String, emailJson: String, emailVerifiedJson: String) {
        assertEquals(
            setOf(
                "\"subject\":\"$subject\"",
                "\"email\":$emailJson",
                "\"emailVerified\":$emailVerifiedJson",
            ),
            body.removeSurrounding("{", "}").split(',').toSet(),
        )
    }

    @TestConfiguration(proxyBeanMethods = false)
    class SessionTestConfiguration {
        @Bean
        @Primary
        fun sessionVerifier() = SessionVerifier()
    }

    class SessionVerifier : VerifyRequestIdentity {
        lateinit var principal: AuthenticatedPrincipal

        override fun execute(token: br.com.saqz.identity.application.RawIdentityToken) =
            TokenVerification.Verified(principal)
    }
}
