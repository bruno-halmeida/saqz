package br.com.saqz.groups.adapter.output.whatsapp

import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.communication.WhatsAppGroupButton
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import com.uazapi.sdk.UazapiClient
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UazapiGroupNotificationSenderTest {
    @Test fun `sends the group text to the jid with stable tracking and instance authentication`() = server { server ->
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
        val messageId = UUID.randomUUID()
        client(server).use { client ->
            assertEquals(WhatsAppDelivery.Accepted, UazapiGroupNotificationSender(client).send(groupJid, messageId, text, emptyList()))
        }
        assertEquals("/send/text", path)
        assertEquals("test-instance-token", token)
        val payload = ObjectMapper().readTree(body)
        assertEquals(groupJid, payload["number"].asText())
        assertEquals(text, payload["text"].asText())
        assertEquals("group-$messageId", payload["track_id"].asText())
        assertFalse(payload["linkPreview"].asBoolean())
        assertFalse(payload["async"].asBoolean())
    }

    @Test fun `attendance buttons become url buttons and never appear raw in the text`() = server { server ->
        var path = ""
        var body = ""
        server.createContext("/") { exchange ->
            path = exchange.requestURI.path
            body = exchange.requestBody.bufferedReader().readText()
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        val messageId = UUID.randomUUID()
        val buttons = attendanceButtons()
        client(server).use { client ->
            assertEquals(WhatsAppDelivery.Accepted, UazapiGroupNotificationSender(client).send(groupJid, messageId, text, buttons))
        }
        assertEquals("/send/menu", path)
        val payload = ObjectMapper().readTree(body)
        assertEquals(groupJid, payload["number"].asText())
        assertEquals("button", payload["type"].asText().lowercase())
        assertEquals(text, payload["text"].asText())
        buttons.forEach { button ->
            assertFalse(payload["text"].asText().contains(button.url))
        }
        assertEquals(2, payload["choices"].size())
        buttons.forEachIndexed { index, button ->
            assertEquals("${button.label}|${button.url}", payload["choices"][index].asText())
        }
        assertEquals("group-$messageId", payload["track_id"].asText())
        assertFalse(payload["async"].asBoolean())
    }

    @Test fun `a configured reminder image goes in the menu as imageButton`() {
        val payload = sentMenuPayload("https://links.saqz.app/assets/whatsapp-lembrete.jpg")
        assertEquals("https://links.saqz.app/assets/whatsapp-lembrete.jpg", payload["imageButton"].asText())
        assertEquals(2, payload["choices"].size())
    }

    @Test fun `without a reminder image the menu body has no imageButton`() {
        for (imageUrl in listOf(null, "", "  ")) {
            val payload = sentMenuPayload(imageUrl)
            assertFalse(payload.hasNonNull("imageButton"), "imageUrl=$imageUrl: $payload")
            assertEquals(2, payload["choices"].size())
        }
    }

    @Test fun `a permanently rejected menu falls back to text with every button`() = server { server ->
        val paths = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            paths += path
            bodies += exchange.requestBody.bufferedReader().readText()
            val status = if (path == "/send/menu") 400 else 200
            exchange.sendResponseHeaders(status, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        val buttons = attendanceButtons()
        client(server).use { client ->
            assertEquals(WhatsAppDelivery.Accepted, UazapiGroupNotificationSender(client).send(groupJid, UUID.randomUUID(), text, buttons))
        }
        assertEquals(listOf("/send/menu", "/send/text"), paths)
        val fallback = ObjectMapper().readTree(bodies[1])["text"].asText()
        buttons.forEach { button ->
            assertTrue(fallback.contains("${button.label}: ${button.url}"), fallback)
        }
    }

    @Test fun `the group jid is never rejected by a phone regex`() = server { server ->
        var payload = ""
        server.createContext("/") { exchange ->
            payload = exchange.requestBody.bufferedReader().readText()
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        client(server).use { client ->
            assertEquals(WhatsAppDelivery.Accepted, UazapiGroupNotificationSender(client).send(groupJid, UUID.randomUUID(), text, emptyList()))
        }
        assertEquals(groupJid, ObjectMapper().readTree(payload)["number"].asText())
    }

    @Test fun `rate limit preserves provider minimum delay`() = server { server ->
        server.createContext("/") { exchange ->
            exchange.responseHeaders.add("Retry-After", "7200")
            exchange.sendResponseHeaders(429, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        client(server).use {
            assertEquals(WhatsAppDelivery.Retry(7200), UazapiGroupNotificationSender(it).send(groupJid, UUID.randomUUID(), text, emptyList()))
        }
    }

    @Test fun `HTTP failure classification distinguishes permanent and transient errors`() {
        for (status in listOf(400, 401, 403, 404, 408, 500, 503)) server { server ->
            server.createContext("/") { exchange ->
                exchange.sendResponseHeaders(status, 2)
                exchange.responseBody.use { it.write("{}".toByteArray()) }
            }
            val expected = if (status in setOf(401, 403, 408) || status >= 500) WhatsAppDelivery.Retry() else WhatsAppDelivery.Failed
            client(server).use {
                assertEquals(expected, UazapiGroupNotificationSender(it).send(groupJid, UUID.randomUUID(), text, emptyList()), "HTTP $status")
            }
        }
    }

    @Test fun `network failure is retryable`() = server { server ->
        client(server).use { client ->
            server.stop(0)
            assertEquals(WhatsAppDelivery.Retry(), UazapiGroupNotificationSender(client).send(groupJid, UUID.randomUUID(), text, emptyList()))
        }
    }

    private fun sentMenuPayload(imageUrl: String?): JsonNode {
        var path = ""
        var body = ""
        server { server ->
            server.createContext("/") { exchange ->
                path = exchange.requestURI.path
                body = exchange.requestBody.bufferedReader().readText()
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("{}".toByteArray()) }
            }
            client(server).use { client ->
                assertEquals(
                    WhatsAppDelivery.Accepted,
                    UazapiGroupNotificationSender(client, imageUrl).send(groupJid, UUID.randomUUID(), text, attendanceButtons()),
                )
            }
        }
        assertEquals("/send/menu", path)
        return ObjectMapper().readTree(body)
    }

    private val groupJid = "120363000000000000@g.us"
    private val text = "Saqz · Vôlei do CERET\nTreino amanhã"
    private fun attendanceButtons() = listOf(
        WhatsAppGroupButton("😍 Vou, me confirma!", "https://links.saqz.app/attendance/code"),
        WhatsAppGroupButton("😢 Não conseguirei ir!", "https://links.saqz.app/attendance/code?saqz_intent=decline"),
    )
    private fun client(server: HttpServer) = UazapiClient.builder()
        .baseUrl("http://127.0.0.1:${server.address.port}").token("test-instance-token")
        .connectTimeout(Duration.ofSeconds(1)).readTimeout(Duration.ofSeconds(1)).build()
    private fun server(block: (HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try { block(server) } finally { server.stop(0) }
    }
}
