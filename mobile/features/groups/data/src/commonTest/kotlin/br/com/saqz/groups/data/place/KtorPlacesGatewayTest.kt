package br.com.saqz.groups.data.place

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.place.PlaceDetails
import br.com.saqz.groups.domain.place.PlaceSuggestion
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorPlacesGatewayTest {
    @Test fun availabilityReadsTheServerFlag() = runTest {
        for (enabled in listOf(true, false)) {
            var captured: HttpRequestData? = null
            val result = gateway { request ->
                captured = request
                respond("""{"enabled":$enabled}""", headers = jsonHeaders)
            }.availability()
            val request = checkNotNull(captured)
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/places/availability", request.url.encodedPath)
            assertEquals("Bearer fake-token", request.headers[HttpHeaders.Authorization])
            assertEquals(SaqzResult.Success(enabled), result)
        }
    }

    @Test fun serverWithoutTheEndpointIsAFailureNotAnAnswer() = runTest {
        assertEquals(
            SaqzResult.Failure(DataError.NotFound),
            gateway { respond("", HttpStatusCode.NotFound) }.availability(),
        )
    }

    @Test fun autocompleteSendsQueryAndSessionAndMapsSuggestions() = runTest {
        var captured: HttpRequestData? = null
        val result = gateway { request ->
            captured = request
            respond(
                """{"suggestions":[{"placeId":"p1","name":"CERET","address":"Tatuapé, São Paulo","description":"CERET, Tatuapé"},
                    {"placeId":"","name":"sem id","address":"","description":""}]}""",
                headers = jsonHeaders,
            )
        }.autocomplete("ceret", "session-1")
        val request = checkNotNull(captured)
        assertEquals("/api/places/autocomplete", request.url.encodedPath)
        assertEquals("ceret", request.url.parameters["q"])
        assertEquals("session-1", request.url.parameters["session"])
        assertEquals(
            SaqzResult.Success(listOf(PlaceSuggestion("p1", "CERET", "Tatuapé, São Paulo", "CERET, Tatuapé"))),
            result,
        )
    }

    @Test fun detailsClosesTheSessionAndMapsCoordinates() = runTest {
        var captured: HttpRequestData? = null
        val result = gateway { request ->
            captured = request
            respond(
                """{"placeId":"p1","name":"CERET","address":"R. Canuto Abreu, s/n","latitude":-23.5,"longitude":-46.6}""",
                headers = jsonHeaders,
            )
        }.details("p1", "session-1")
        val request = checkNotNull(captured)
        assertEquals("/api/places/details", request.url.encodedPath)
        assertEquals("p1", request.url.parameters["placeId"])
        assertEquals("session-1", request.url.parameters["session"])
        assertEquals(SaqzResult.Success(PlaceDetails("p1", "CERET", "R. Canuto Abreu, s/n", -23.5, -46.6)), result)
    }

    @Test fun serviceUnavailableAndRateLimitAreFailures() = runTest {
        assertEquals(
            SaqzResult.Failure(DataError.Server),
            gateway { respond("", HttpStatusCode.ServiceUnavailable) }.autocomplete("ceret", "s"),
        )
        assertEquals(
            SaqzResult.Failure(DataError.Unknown),
            gateway { respond("", HttpStatusCode.TooManyRequests) }.details("p1", "s"),
        )
    }

    private fun gateway(response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorPlacesGateway {
        val network = NetworkClient(MockEngine { response(it) }, NetworkConfig(NetworkEnvironment.Test, "https://api.test/"))
        return KtorPlacesGateway(
            AuthenticatedNetworkClient(
                network,
                object : IdTokenProvider {
                    override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
                        completion(TokenResult.Available("fake-token"))
                },
                object : SessionInvalidator { override fun invalidate() = Unit },
            ),
        )
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
}
