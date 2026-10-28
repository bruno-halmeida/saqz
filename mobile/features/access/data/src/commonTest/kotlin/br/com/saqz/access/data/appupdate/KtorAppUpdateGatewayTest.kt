package br.com.saqz.access.data.appupdate

import br.com.saqz.access.domain.appupdate.AppUpdateError
import br.com.saqz.access.domain.appupdate.AppUpdateVersions
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class KtorAppUpdateGatewayTest {
    @Test fun `reads minimum and recommended from the full features response, anonymously`() = runTest {
        var seen: HttpRequestData? = null
        val gateway = gateway { request ->
            seen = request
            ok(
                """
                {
                  "flags": {
                    "chat": { "enabled": false, "values": {} },
                    "whatsapp": { "enabled": true, "values": {} }
                  },
                  "platform": "IOS",
                  "appVersion": { "minimum": "1.0.12", "recommended": "1.0.14" }
                }
                """.trimIndent(),
            )
        }

        assertEquals(AppUpdateVersions("1.0.12", "1.0.14"), gateway.versions().success())
        assertEquals("GET", seen?.method?.value)
        assertEquals("/api/features", seen?.url?.encodedPath)
        assertNull(seen?.headers?.get(HttpHeaders.Authorization))
    }

    @Test fun `a null appVersion means no versions`() = runTest {
        val gateway = gateway { ok("""{"flags":{},"platform":null,"appVersion":null}""") }

        assertEquals(AppUpdateVersions(null, null), gateway.versions().success())
    }

    @Test fun `null fields stay null`() = runTest {
        val gateway = gateway { ok("""{"flags":{},"platform":"ANDROID","appVersion":{"minimum":null,"recommended":"1.0.16"}}""") }

        assertEquals(AppUpdateVersions(null, "1.0.16"), gateway.versions().success())
    }

    @Test fun `a network failure is a typed error`() = runTest {
        val gateway = gateway { throw UnresolvedAddressException() }

        val failure = assertIs<SaqzResult.Failure<AppUpdateError>>(gateway.versions())
        assertEquals(AppUpdateError.Data(DataError.Connectivity), failure.error)
    }

    private fun gateway(
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = KtorAppUpdateGateway(
        NetworkClient(
            MockEngine { request -> response(request) },
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/", 10_000),
        ),
    )

    private fun MockRequestHandleScope.ok(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun <T> SaqzResult<T, AppUpdateError>.success() = assertIs<SaqzResult.Success<T>>(this).value
}
