package br.com.saqz.bootstrap

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * O scrape do Prometheus vive na porta de gestao, anonimo, e nao existe na porta publica.
 * Em producao a porta de gestao e a 9090 (MANAGEMENT_SERVER_PORT no compose.yaml); aqui e aleatoria.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["management.server.port=0"],
)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class ManagementEndpointsIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @LocalManagementPort
    private var managementPort: Int = 0

    @Test
    fun `prometheus scrape is served anonymously on the management port`() {
        val response = get("http://127.0.0.1:$managementPort/actuator/prometheus")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("jvm_memory_used_bytes"), response.body().take(300))
    }

    @Test
    fun `health stays on the management port`() {
        val response = get("http://127.0.0.1:$managementPort/actuator/health")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"status\":\"UP\""))
    }

    @Test
    fun `prometheus scrape is not served on the public port`() {
        val response = get("http://127.0.0.1:$port/actuator/prometheus")

        assertNotEquals(200, response.statusCode())
    }

    private fun get(url: String): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder().uri(URI(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
}
