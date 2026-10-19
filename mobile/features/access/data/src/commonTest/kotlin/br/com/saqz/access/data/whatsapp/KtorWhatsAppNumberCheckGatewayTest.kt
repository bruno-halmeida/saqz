package br.com.saqz.access.data.whatsapp

import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KtorWhatsAppNumberCheckGatewayTest {
    private var seen: HttpRequestData? = null

    @Test
    fun `asks the authenticated route with the phone and reads a no`() = runTest {
        val gateway = gateway(HttpStatusCode.OK, """{"hasWhatsApp":false}""")

        assertEquals(false, gateway.hasWhatsApp("+5511987654321"))
        assertEquals("GET", seen?.method?.value)
        assertEquals("/api/whatsapp/number-check", seen?.url?.encodedPath)
        assertEquals("+5511987654321", seen?.url?.parameters?.get("phone"))
        assertEquals("Bearer session-token", seen?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun `reads a yes`() = runTest {
        assertEquals(true, gateway(HttpStatusCode.OK, """{"hasWhatsApp":true}""").hasWhatsApp("+5511987654321"))
    }

    @Test
    fun `no answer and every failure stay unknown`() = runTest {
        assertNull(gateway(HttpStatusCode.OK, """{"hasWhatsApp":null}""").hasWhatsApp("+5511987654321"))
        assertNull(gateway(HttpStatusCode.OK, """{}""").hasWhatsApp("+5511987654321"))
        assertNull(gateway(HttpStatusCode.OK, "not json").hasWhatsApp("+5511987654321"))
        assertNull(gateway(HttpStatusCode.InternalServerError, "").hasWhatsApp("+5511987654321"))
        assertNull(gateway(HttpStatusCode.Unauthorized, "").hasWhatsApp("+5511987654321"))
    }

    private fun gateway(status: HttpStatusCode, body: String) = KtorWhatsAppNumberCheckGateway(
        AuthenticatedNetworkClient(
            NetworkClient(
                MockEngine { request ->
                    seen = request
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
                NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/", 10_000),
            ),
            Tokens(),
            NoopInvalidator(),
        ),
    )

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) {
            completion(TokenResult.Available("session-token"))
        }
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }
}
