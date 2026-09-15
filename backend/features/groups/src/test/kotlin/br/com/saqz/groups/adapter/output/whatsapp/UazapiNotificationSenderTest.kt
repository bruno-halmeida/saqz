package br.com.saqz.groups.adapter.output.whatsapp

import br.com.saqz.groups.application.communication.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import com.uazapi.sdk.UazapiClient
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import kotlin.test.*

class UazapiNotificationSenderTest {
    @Test fun `sends individual text with stable tracking and instance authentication`() = server { server ->
        var path = ""
        var token = ""
        var body = ""
        server.createContext("/") { exchange ->
            path = exchange.requestURI.path
            token = exchange.requestHeaders.getFirst("token")
            body = exchange.requestBody.bufferedReader().readText()
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        client(server).use { client ->
            assertEquals(WhatsAppDelivery.Accepted, UazapiNotificationSender(client).send(message))
        }
        assertEquals("/send/text", path)
        assertEquals("test-instance-token", token)
        val payload = ObjectMapper().readTree(body)
        assertEquals("5511999999999", payload["number"].asText())
        assertEquals(message.body, payload["text"].asText())
        assertEquals("notification-42", payload["track_id"].asText())
        assertFalse(payload["linkPreview"].asBoolean())
        assertFalse(payload["async"].asBoolean())
    }

    @Test fun `rate limit preserves provider minimum delay`() = server { server ->
        server.createContext("/") { exchange ->
            exchange.responseHeaders.add("Retry-After", "7200")
            exchange.sendResponseHeaders(429, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        client(server).use { assertEquals(WhatsAppDelivery.Retry(7200), UazapiNotificationSender(it).send(message)) }
    }

    @Test fun `HTTP failure classification distinguishes permanent and transient errors`() {
        for (status in listOf(400, 401, 403, 404, 408, 500, 503)) server { server ->
            server.createContext("/") { exchange ->
                exchange.sendResponseHeaders(status, 2)
                exchange.responseBody.use { it.write("{}".toByteArray()) }
            }
            val expected = if (status == 408 || status >= 500) WhatsAppDelivery.Retry() else WhatsAppDelivery.Failed
            client(server).use { assertEquals(expected, UazapiNotificationSender(it).send(message), "HTTP $status") }
        }
    }

    @Test fun `invalid phone never reaches provider and network failure is retryable`() = server { server ->
        client(server).use { client ->
            assertEquals(WhatsAppDelivery.Failed, UazapiNotificationSender(client).send(message.copy(phone = "group@g.us")))
            server.stop(0)
            assertEquals(WhatsAppDelivery.Retry(), UazapiNotificationSender(client).send(message))
        }
    }

    private val message = WhatsAppNotification(42, "+5511999999999", "Saqz: confirme sua presença.")
    private fun client(server: HttpServer) = UazapiClient.builder()
        .baseUrl("http://127.0.0.1:${server.address.port}").token("test-instance-token")
        .connectTimeout(Duration.ofSeconds(1)).readTimeout(Duration.ofSeconds(1)).build()
    private fun server(block: (HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try { block(server) } finally { server.stop(0) }
    }
}
