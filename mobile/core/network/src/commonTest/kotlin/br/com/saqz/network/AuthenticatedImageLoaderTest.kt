package br.com.saqz.network

import coil3.toUri
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthenticatedImageLoaderTest {
    @Test
    fun `URL relativa da foto e resolvida contra a base da API`() = runTest {
        var requestedUrl = ""
        val client = authenticatedClient(MockEngine { request ->
            requestedUrl = request.url.toString()
            imageResponse()
        })

        val result = loadAuthenticatedImage(client, "/api/session/photo?v=digest-1".toUri())

        assertIs<NetworkResult.Success<NetworkBinaryBody>>(result)
        assertEquals("https://api.example.test/api/session/photo?v=digest-1", requestedUrl)
    }

    @Test
    fun `requisicao da foto envia o bearer da sessao`() = runTest {
        var authorization: String? = null
        val client = authenticatedClient(MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            imageResponse()
        })

        val result = loadAuthenticatedImage(client, "/api/session/photo?v=digest-1".toUri())

        assertIs<NetworkResult.Success<NetworkBinaryBody>>(result)
        assertEquals("Bearer session-token", authorization)
    }

    @Test
    fun `digest permanece na query usada pelo transporte`() {
        val oldRequest = authenticatedImageRequest("/api/session/photo?v=old")
        val newRequest = authenticatedImageRequest("/api/session/photo?v=new")

        assertEquals("old", oldRequest.query["v"])
        assertEquals("new", newRequest.query["v"])
        assertEquals("/api/session/photo", oldRequest.path)
        assertEquals("/api/session/photo", newRequest.path)
    }

    private fun authenticatedClient(engine: MockEngine): AuthenticatedNetworkClient = AuthenticatedNetworkClient(
        network = NetworkClient(
            engine = engine,
            config = NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
        ),
        tokenProvider = FixedTokenProvider,
        sessionInvalidator = NoOpSessionInvalidator,
    )

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.imageResponse() = respond(
        content = "photo-bytes".encodeToByteArray(),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString()),
    )
}

private object FixedTokenProvider : IdTokenProvider {
    override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) {
        completion(TokenResult.Available("session-token"))
    }
}

private object NoOpSessionInvalidator : SessionInvalidator {
    override fun invalidate() = Unit
}
