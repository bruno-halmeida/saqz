package br.com.saqz.bootstrap

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ConfigurableApplicationContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SaqzApplicationTest {
    @Autowired
    private lateinit var context: ConfigurableApplicationContext

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `application starts`() {
        assertTrue(context.isActive)
    }

    @Test
    fun `health is public and up`() {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port/actuator/health"))
            .GET()
            .build()

        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"status\":\"UP\""))
    }
}
