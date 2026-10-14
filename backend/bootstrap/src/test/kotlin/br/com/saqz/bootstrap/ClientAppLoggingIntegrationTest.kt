package br.com.saqz.bootstrap

import br.com.saqz.bootstrap.configuration.http.requestClientApp
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import jakarta.servlet.http.HttpServletRequest
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
import kotlin.test.assertTrue

/**
 * A versão do app (cabeçalhos X-Saqz-*) sai em toda linha de log da requisição, pelo MDC `app` e pelo
 * `logging.pattern.level` de application.properties, inclusive na resposta 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ClientAppLoggingIntegrationTest.Configuration::class)
@ExtendWith(OutputCaptureExtension::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class ClientAppLoggingIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var verifier: ClientAppVerifier

    @BeforeEach
    fun reset() {
        verifier.result = TokenVerification.Verified(RequestIdentity("log-subject"))
    }

    @Test
    fun `the completion line of an authenticated call carries the app platform version and build`(output: CapturedOutput) {
        val response = get("/test/client-app", app = Headers("ios", "1.0.3", "14"))

        assertEquals(200, response.statusCode())
        val line = completionLine(output, correlationId(response))
        assertTrue(line.contains("sub=log-subject app=ios/1.0.3(14)]"), line)
    }

    @Test
    fun `every log line of the request has the app, not only the completion one`(output: CapturedOutput) {
        val response = get("/test/client-app-log", app = Headers("android", "1.0.3", "14"))
        val correlationId = correlationId(response)
        completionLine(output, correlationId)

        val inside = output.out.lines().single { it.contains("probe_inside_request") }
        assertTrue(inside.contains("corr=$correlationId"), inside)
        assertTrue(inside.contains("app=android/1.0.3(14)]"), inside)
    }

    @Test
    fun `a rejected token is logged with the app too, because the filter runs before the authentication`(output: CapturedOutput) {
        verifier.result = TokenVerification.Rejected

        val response = get("/api/session", app = Headers("android", "1.0.2", "13"))

        assertEquals(401, response.statusCode())
        val line = completionLine(output, correlationId(response))
        assertTrue(line.contains("status=401"), line)
        assertTrue(line.contains("app=android/1.0.2(13)]"), line)
    }

    @Test
    fun `a request without the headers is logged with a dash, as the old app`(output: CapturedOutput) {
        val response = get("/test/client-app")

        assertEquals("-", response.body())
        assertTrue(completionLine(output, correlationId(response)).contains("app=-]"))
    }

    @Test
    fun `a missing token without the headers still logs the dash`(output: CapturedOutput) {
        val response = get("/api/session", token = null)

        assertEquals(401, response.statusCode())
        assertTrue(completionLine(output, correlationId(response)).contains("app=-]"))
    }

    @Test
    fun `a value outside the pattern is dropped from the log and the call still works`(output: CapturedOutput) {
        val response = get("/test/client-app", app = Headers("web", "web (dev)", "abc"))

        assertEquals(200, response.statusCode())
        assertEquals("web", response.body())
        assertTrue(completionLine(output, correlationId(response)).contains("app=web]"))
    }

    @Test
    fun `an unknown platform is logged as the old app`(output: CapturedOutput) {
        val response = get("/test/client-app", app = Headers("symbian", "1.0.3", "14"))

        assertEquals("-", response.body())
        assertTrue(completionLine(output, correlationId(response)).contains("app=-]"))
    }

    @Test
    fun `the app of one request never leaks into the next one`(output: CapturedOutput) {
        val ids = (1..12).map { turn ->
            val identified = turn % 2 == 1
            val response = get("/test/client-app", app = if (identified) Headers("ios", "1.0.3", "14") else null)
            correlationId(response) to identified
        }

        ids.forEach { (id, identified) ->
            val expected = if (identified) "app=ios/1.0.3(14)]" else "app=-]"
            assertTrue(completionLine(output, id).contains(expected), "$id expected $expected")
        }
    }

    @Test
    fun `controllers read the app from the request`() {
        assertEquals("ios/1.0.3(14)", get("/test/client-app", app = Headers("ios", "1.0.3", "14")).body())
        assertEquals("web/1.0.3", get("/test/client-app", app = Headers("web", "1.0.3", null)).body())
        assertEquals("-", get("/test/client-app").body())
    }

    private class Headers(val platform: String, val version: String, val build: String?)

    private fun get(path: String, token: String? = "client-app-token", app: Headers? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder().uri(URI("http://127.0.0.1:$port$path")).GET()
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (app != null) {
            builder.header("X-Saqz-Platform", app.platform)
            builder.header("X-Saqz-App-Version", app.version)
            app.build?.let { builder.header("X-Saqz-App-Build", it) }
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun correlationId(response: HttpResponse<String>): String =
        response.headers().firstValue("X-Correlation-ID").orElse("")

    // A resposta chega ao cliente antes da linha de log ser gravada: espera pela linha em vez de olhar um retrato.
    private fun completionLine(output: CapturedOutput, correlationId: String): String {
        val marker = "request_complete correlationId=$correlationId"
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline && output.out.lines().none { it.contains(marker) }) {
            Thread.sleep(25)
        }
        return output.out.lines().singleOrNull { it.contains(marker) }
            ?: error("a linha $marker não apareceu no log")
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Configuration {
        @Bean
        @Primary
        fun clientAppVerifier() = ClientAppVerifier()

        @Bean
        fun clientAppProbe() = ClientAppProbe()
    }

    class ClientAppVerifier : VerifyRequestIdentity {
        var result: TokenVerification = TokenVerification.Rejected

        override fun execute(token: RawIdentityToken): TokenVerification = result
    }

    @RestController
    class ClientAppProbe {
        private val logger = org.slf4j.LoggerFactory.getLogger(ClientAppProbe::class.java)

        @GetMapping("/test/client-app")
        fun app(request: HttpServletRequest): String = requestClientApp(request).label

        @GetMapping("/test/client-app-log")
        fun appAndLog(request: HttpServletRequest): String {
            logger.info("probe_inside_request")
            return requestClientApp(request).label
        }
    }
}
