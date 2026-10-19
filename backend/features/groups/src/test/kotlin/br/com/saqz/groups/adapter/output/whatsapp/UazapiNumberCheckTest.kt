package br.com.saqz.groups.adapter.output.whatsapp

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import com.uazapi.sdk.UazapiClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** DEB-095: a consulta só responde "sim" ou "não" quando a Uazapi disse; o resto é `null`, nunca erro. */
class UazapiNumberCheckTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private var status = 200
    private var payload = """[{"query":"5511987654321","isInWhatsapp":true,"jid":"5511987654321@s.whatsapp.net"}]"""
    private var delayMillis = 0L
    private var requestedBody: String? = null

    init {
        server.createContext("/chat/check") { exchange ->
            requestedBody = exchange.requestBody.readAllBytes().decodeToString()
            if (delayMillis > 0) Thread.sleep(delayMillis)
            val bytes = payload.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach fun stop() = server.stop(0)

    private fun check(timeoutMillis: Long = 4_000) = UazapiNumberCheck(
        UazapiClient.builder().baseUrl("http://127.0.0.1:${server.address.port}").token("test-instance-token")
            .connectTimeout(Duration.ofSeconds(1)).readTimeout(Duration.ofSeconds(5)).build(),
        timeoutMillis,
    )

    @Test fun `numero com WhatsApp responde verdadeiro e vai sem o mais`() {
        assertEquals(true, check().hasWhatsApp("+5511987654321"))
        assertEquals(listOf("5511987654321"), ObjectMapper().readTree(requestedBody).get("numbers").map { it.asText() })
    }

    @Test fun `numero sem WhatsApp responde falso`() {
        payload = """[{"query":"5511987654321","isInWhatsapp":false,"jid":""}]"""

        assertEquals(false, check().hasWhatsApp("+5511987654321"))
    }

    @Test fun `erro do provedor resposta estranha e numero malformado ficam sem resposta`() {
        status = 500
        assertNull(check().hasWhatsApp("+5511987654321"))

        status = 200
        payload = """{"unexpected":true}"""
        assertNull(check().hasWhatsApp("+5511987654321"))

        payload = "[]"
        assertNull(check().hasWhatsApp("+5511987654321"))
        assertNull(check().hasWhatsApp("não é número"))
    }

    @Test fun `demora acima do teto fica sem resposta`() {
        delayMillis = 1_500

        assertNull(check(timeoutMillis = 200).hasWhatsApp("+5511987654321"))
    }
}
