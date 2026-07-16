package br.com.saqz.bootstrap

import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SafeDiagnosticsIntegrationTest.DiagnosticsTestConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class SafeDiagnosticsIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var verifier: DiagnosticsVerifier

    @BeforeEach
    fun reset() {
        verifier.result = TokenVerification.Rejected
    }

    @Test
    fun `rejected credential has exact 401 problem and matching correlation log`(output: CapturedOutput) {
        val response = get("/api/session", "rejected-secret-token")
        val correlationId = correlationId(response)

        assertProblem(response, 401, "AUTHENTICATION_REQUIRED")
        assertTrue(correlationId.isNotBlank())
        assertTrue(output.out.contains("correlationId=$correlationId status=401"))
    }

    @Test
    fun `provider failure has exact 503 problem and matching correlation log`(output: CapturedOutput) {
        verifier.result = TokenVerification.ProviderUnavailable

        val response = get("/api/session", "provider-secret-token")
        val correlationId = correlationId(response)

        assertProblem(response, 503, "IDENTITY_PROVIDER_UNAVAILABLE")
        assertTrue(output.out.contains("correlationId=$correlationId status=503"))
    }

    @Test
    fun `unexpected failure has generic 500 problem and matching correlation log`(output: CapturedOutput) {
        verifier.result = TokenVerification.Verified(RequestIdentity("failure-subject", null, null))
        val response = get("/test/unexpected", "unexpected-secret-token")
        val correlationId = correlationId(response)

        assertEquals(500, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertTrue(response.body().contains("\"status\":500"))
        assertFalse(response.body().contains("FirebaseAuthException"))
        assertFalse(response.body().contains("private-key-content"))
        assertTrue(output.out.contains("correlationId=$correlationId status=500"))
    }

    @Test
    fun `successful response and log redact bearer and credential fixtures`(output: CapturedOutput) {
        verifier.result = TokenVerification.Verified(RequestIdentity("safe-subject", null, null))

        val response = get("/api/session", "success-secret-token")

        assertEquals(200, response.statusCode())
        assertFalse((response.body() + output.out).contains("success-secret-token"))
        assertFalse((response.body() + output.out).contains("private-key-content"))
    }

    @Test
    fun `all error responses and logs redact fixture secrets and Firebase details`(output: CapturedOutput) {
        val rejected = get("/api/session", "rejected-fixture-secret")
        verifier.result = TokenVerification.ProviderUnavailable
        val unavailable = get("/api/session", "provider-fixture-secret")
        verifier.result = TokenVerification.Verified(RequestIdentity("failure-subject", null, null))
        val unexpected = get("/test/unexpected", "failure-fixture-secret")
        val captured = rejected.body() + unavailable.body() + unexpected.body() + output.out

        listOf(
            "rejected-fixture-secret",
            "provider-fixture-secret",
            "failure-fixture-secret",
            "service-account-content",
            "private-key-content",
            "FirebaseAuthException",
        ).forEach { forbidden -> assertFalse(captured.contains(forbidden), forbidden) }
    }

    @Test
    fun `separate requests receive separate non-empty correlation IDs`() {
        val first = correlationId(get("/api/session", "first-token"))
        val second = correlationId(get("/api/session", "second-token"))

        assertTrue(first.isNotBlank())
        assertTrue(second.isNotBlank())
        assertNotEquals(first, second)
    }

    private fun get(path: String, token: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port$path"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertTrue(response.body().contains("\"status\":$status"))
        assertTrue(response.body().contains("\"code\":\"$code\""))
    }

    private fun correlationId(response: HttpResponse<String>): String =
        Regex("\"correlationId\":\"([^\"]+)\"").find(response.body())!!.groupValues[1]

    @TestConfiguration(proxyBeanMethods = false)
    class DiagnosticsTestConfiguration {
        @Bean
        @Primary
        fun diagnosticsVerifier() = DiagnosticsVerifier()

        @Bean
        fun failureProbe() = FailureProbe()
    }

    class DiagnosticsVerifier : VerifyRequestIdentity {
        var result: TokenVerification = TokenVerification.Rejected

        override fun execute(token: RawIdentityToken): TokenVerification = result
    }

    @RestController
    class FailureProbe {
        @GetMapping("/test/unexpected")
        fun fail(): Nothing = error("FirebaseAuthException private-key-content service-account-content")
    }
}
