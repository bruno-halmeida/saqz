package br.com.saqz.bootstrap

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.bootstrap.configuration.IdentitySecurityConfiguration
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.beans.factory.annotation.Autowired
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
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdminWebCorsIntegrationTest.CorsTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "saqz.firebase.emulator.enabled=true",
        "saqz.adminweb.origins=http://127.0.0.1:8123",
    ],
)
class AdminWebCorsIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    private val client: HttpClient = HttpClient.newHttpClient()

    @Test
    fun `preflight de origem liberada passa sem bearer e devolve os cabecalhos`() {
        val response = options("/admin/me", origin = "http://127.0.0.1:8123")

        assertEquals(200, response.statusCode())
        assertEquals(
            "http://127.0.0.1:8123",
            response.headers().firstValue("Access-Control-Allow-Origin").orElse(""),
        )
    }

    @Test
    fun `preflight de origem desconhecida e recusado`() {
        val response = options("/admin/me", origin = "https://malicioso.example")

        assertEquals(403, response.statusCode())
    }

    @Test
    fun `requisicao real de origem liberada carrega o cabecalho de resposta`() {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/admin/me"))
            .GET()
            .header("Origin", "http://127.0.0.1:8123")
            .header("Authorization", "Bearer admin-token")
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertEquals(
            "http://127.0.0.1:8123",
            response.headers().firstValue("Access-Control-Allow-Origin").orElse(""),
        )
    }

    @Test
    fun `preflight do checkout passa sem bearer nos paths de assinatura`() {
        listOf("/subscriptions" to "POST", "/subscriptions/me/receipts" to "GET", "/subscriptions/checkout-login" to "POST", "/coupons/validate" to "POST")
            .forEach { (path, method) ->
                val response = options(path, origin = "http://127.0.0.1:8123", requestMethod = method)

                assertEquals(200, response.statusCode(), "preflight de $path")
                assertEquals(
                    "http://127.0.0.1:8123",
                    response.headers().firstValue("Access-Control-Allow-Origin").orElse(""),
                    "header de $path",
                )
            }
    }

    @Test
    fun `preflight do catalogo de planos passa sem bearer`() {
        val response = options("/plans", origin = "http://127.0.0.1:8123")

        assertEquals(200, response.statusCode())
        assertEquals(
            "http://127.0.0.1:8123",
            response.headers().firstValue("Access-Control-Allow-Origin").orElse(""),
        )
    }

    @Test
    fun `preflight fora das superficies web nao libera a origem`() {
        // Sem config registrada o Spring não devolve 403 — devolve a resposta sem o header
        // Allow-Origin, e é a ausência dele que faz o browser bloquear a chamada.
        val response = options("/groups", origin = "http://127.0.0.1:8123")

        assertEquals(
            "",
            response.headers().firstValue("Access-Control-Allow-Origin").orElse(""),
        )
    }

    @Test
    fun `property vazia nao registra cors para nenhum path`() {
        val source = IdentitySecurityConfiguration().corsConfigurationSource("")

        listOf("/admin/me", "/plans", "/subscriptions").forEach { path ->
            assertNull(source.getCorsConfiguration(MockHttpServletRequest("OPTIONS", path)), path)
        }
    }

    private fun options(path: String, origin: String, requestMethod: String = "GET"): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .header("Origin", origin)
            .header("Access-Control-Request-Method", requestMethod)
            .header("Access-Control-Request-Headers", "Authorization")
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class CorsTestConfiguration {
        @Bean
        @Primary
        fun corsVerifier(): VerifyRequestIdentity = VerifyRequestIdentity {
            when (it.value) {
                "admin-token" -> TokenVerification.Verified(RequestIdentity("admin-subject", "admin@saqz.test", true))
                else -> TokenVerification.Rejected
            }
        }

        @Bean
        fun corsLookup(): PlatformAdminLookup = PlatformAdminLookup { subject ->
            if (subject == "admin-subject") PlatformAdminView(UUID.randomUUID(), "admin@saqz.test", "Ana Admin") else null
        }

        @Bean
        fun corsMeController(lookup: PlatformAdminLookup) =
            br.com.saqz.access.adapter.input.http.PlatformAdminMeController(lookup)
    }
}
