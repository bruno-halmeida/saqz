package br.com.saqz.bootstrap

import br.com.saqz.identity.api.AuthenticatedPrincipal
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.security.core.Authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(BearerSecurityIntegrationTest.SecurityTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class BearerSecurityIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var verifier: RecordingVerifyRequestIdentity

    @BeforeEach
    fun resetVerifier() {
        verifier.result = TokenVerification.Rejected
        verifier.tokens.clear()
    }

    @Test
    fun `missing credentials return exact unauthorized problem without verification`() {
        assertUnauthorized(request())
        assertTrue(verifier.tokens.isEmpty())
    }

    @Test
    fun `empty malformed and non-Bearer credentials return exact unauthorized problem without verification`() {
        listOf("Bearer", "Bearer ", "Basic abc", "not-a-scheme").forEach { authorization ->
            assertUnauthorized(request(authorization))
        }
        assertTrue(verifier.tokens.isEmpty())
    }

    @Test
    fun `invalid credentials return exact unauthorized problem and no security context`() {
        assertUnauthorized(request("Bearer invalid-token"))
        assertEquals(listOf(RawIdentityToken("invalid-token")), verifier.tokens)
    }

    @Test
    fun `expired credentials return exact unauthorized problem and no security context`() {
        assertUnauthorized(request("Bearer expired-token"))
        assertEquals(listOf(RawIdentityToken("expired-token")), verifier.tokens)
    }

    @Test
    fun `revoked credentials return exact unauthorized problem and no security context`() {
        assertUnauthorized(request("Bearer revoked-token"))
        assertEquals(listOf(RawIdentityToken("revoked-token")), verifier.tokens)
    }

    @Test
    fun `verified credentials establish only provider-neutral principal`() {
        verifier.result = TokenVerification.Verified(
            AuthenticatedPrincipal("subject-7", "person@example.test", true),
        )

        val response = send(request("Bearer verified-token"))

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"subject\":\"subject-7\""))
        assertTrue(response.body().contains("\"principalType\":\"AuthenticatedPrincipal\""))
        assertFalse(response.body().contains("Firebase"))
    }

    private fun request(authorization: String? = null): HttpRequest {
        val builder = HttpRequest.newBuilder().uri(URI("http://127.0.0.1:$port/test/principal")).GET()
        if (authorization != null) builder.header("Authorization", authorization)
        return builder.build()
    }

    private fun assertUnauthorized(request: HttpRequest) {
        val response = send(request)
        assertEquals(401, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertTrue(response.body().contains("\"status\":401"))
        assertTrue(response.body().contains("\"code\":\"AUTHENTICATION_REQUIRED\""))
    }

    private fun send(request: HttpRequest): HttpResponse<String> =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())

    @TestConfiguration(proxyBeanMethods = false)
    class SecurityTestConfiguration {
        @Bean
        @Primary
        fun recordingVerifier() = RecordingVerifyRequestIdentity()

        @Bean
        fun principalProbe() = PrincipalProbe()
    }

    class RecordingVerifyRequestIdentity : VerifyRequestIdentity {
        val tokens = mutableListOf<RawIdentityToken>()
        var result: TokenVerification = TokenVerification.Rejected

        override fun execute(token: RawIdentityToken): TokenVerification {
            tokens += token
            return result
        }
    }

    @RestController
    class PrincipalProbe {
        @GetMapping("/test/principal")
        fun principal(authentication: Authentication): Map<String, String> {
            val principal = authentication.principal as AuthenticatedPrincipal
            return mapOf(
                "subject" to principal.subject,
                "principalType" to principal::class.simpleName.orEmpty(),
            )
        }
    }
}
