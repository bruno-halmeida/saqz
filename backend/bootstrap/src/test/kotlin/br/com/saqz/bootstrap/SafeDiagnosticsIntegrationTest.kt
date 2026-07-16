package br.com.saqz.bootstrap

import br.com.saqz.bootstrap.configuration.http.ApiProblemWriter
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import tools.jackson.databind.ObjectMapper

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

    @Autowired
    private lateinit var objectMapper: ObjectMapper

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

        val response = get("/test/success", "success-secret-token")

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

    @Test
    fun `401 correlation header equals problem correlation ID`() {
        val response = get("/api/session", "header-401-token")

        assertEquals(correlationId(response), correlationHeader(response))
    }

    @Test
    fun `503 correlation header equals problem correlation ID`() {
        verifier.result = TokenVerification.ProviderUnavailable
        val response = get("/api/session", "header-503-token")

        assertEquals(correlationId(response), correlationHeader(response))
    }

    @Test
    fun `500 correlation header equals problem correlation ID`() {
        verifier.result = TokenVerification.Verified(RequestIdentity("failure-subject"))
        val response = get("/test/unexpected", "header-500-token")

        assertEquals(correlationId(response), correlationHeader(response))
    }

    @Test
    fun `successful response exposes correlation header matching log`(output: CapturedOutput) {
        verifier.result = TokenVerification.Verified(RequestIdentity("safe-subject"))
        val response = get("/test/success", "header-success-token")
        val correlationId = correlationHeader(response)

        assertTrue(correlationId.isNotBlank())
        assertTrue(output.out.contains("correlationId=$correlationId status=200"))
    }

    @Test
    fun `problem omits optional fields when they are absent`() {
        val problem = objectMapper.readTree(get("/api/session", "optional-fields-token").body())

        assertFalse(problem.has("fieldErrors"))
        assertFalse(problem.has("retryAfterSeconds"))
    }

    @Test
    fun `field errors are escaped by Jackson and round trip`() {
        verifier.result = TokenVerification.Verified(RequestIdentity("problem-subject"))
        val problem = objectMapper.readTree(get("/test/problem", "escaped-problem-token").body())

        assertEquals("contains \"quote\"\nand newline", problem["fieldErrors"]["displayName"][0].stringValue())
    }

    @Test
    fun `retry after seconds is serialized as a number`() {
        verifier.result = TokenVerification.Verified(RequestIdentity("problem-subject"))
        val problem = objectMapper.readTree(get("/test/problem", "retry-problem-token").body())

        assertTrue(problem["retryAfterSeconds"].isInt)
        assertEquals(17, problem["retryAfterSeconds"].asInt())
    }

    @Test
    fun `authorization header never appears in response or logs`(output: CapturedOutput) {
        val secret = "authorization-header-fixture-secret"
        val response = get("/api/session", secret)

        assertFalse((response.body() + output.out).contains(secret))
    }

    @Test
    fun `request body never appears in response or logs`(output: CapturedOutput) {
        verifier.result = TokenVerification.Verified(RequestIdentity("failure-subject"))
        val secret = "request-body-fixture-secret"
        val response = post("/test/unexpected", "body-probe-token", secret)

        assertEquals(500, response.statusCode())
        assertFalse((response.body() + output.out).contains(secret))
    }

    private fun get(path: String, token: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port$path"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun post(path: String, token: String, body: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port$path"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "text/plain")
            .POST(HttpRequest.BodyPublishers.ofString(body))
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

    private fun correlationHeader(response: HttpResponse<String>): String =
        response.headers().firstValue("X-Correlation-ID").orElse("")

    @TestConfiguration(proxyBeanMethods = false)
    class DiagnosticsTestConfiguration {
        @Bean
        @Primary
        fun diagnosticsVerifier() = DiagnosticsVerifier()

        @Bean
        fun failureProbe() = FailureProbe()

        @Bean
        fun problemProbe(writer: ApiProblemWriter) = ProblemProbe(writer)
    }

    class DiagnosticsVerifier : VerifyRequestIdentity {
        var result: TokenVerification = TokenVerification.Rejected

        override fun execute(token: RawIdentityToken): TokenVerification = result
    }

    @RestController
    class FailureProbe {
        @GetMapping("/test/success")
        fun success() = mapOf("status" to "ok")

        @GetMapping("/test/unexpected")
        fun fail(): Nothing = error("FirebaseAuthException private-key-content service-account-content")

        @PostMapping("/test/unexpected")
        fun failWithBody(): Nothing = error("unexpected failure")
    }

    @RestController
    class ProblemProbe(
        private val writer: ApiProblemWriter,
    ) {
        @GetMapping("/test/problem")
        fun problem(request: HttpServletRequest, response: HttpServletResponse) {
            writer.write(
                request = request,
                response = response,
                status = 429,
                fieldErrors = mapOf("displayName" to listOf("contains \"quote\"\nand newline")),
                retryAfterSeconds = 17,
            )
        }
    }
}
