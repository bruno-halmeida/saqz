package br.com.saqz.groups.data.home

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import br.com.saqz.groups.domain.home.HomeError
import br.com.saqz.groups.domain.home.HomeReadModel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorHomeGatewayTest {
    @Test
    fun `read uses home route and maps the complete member and admin contract`() = runTest {
        val result = gateway { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/me/home", request.url.encodedPath)
            respond(HOME_JSON, headers = jsonHeaders())
        }.read()

        val home = assertIs<SaqzResult.Success<HomeReadModel>>(result).value
        assertEquals("group-1", home.member.groups.single().id.value)
        assertEquals("ADMIN", home.member.groups.single().role.name)
        assertEquals(26, home.member.groups.single().memberCount)
        assertEquals("game-1", home.member.nextGame?.gameId)
        assertEquals("Quadra do Ibirapuera", home.member.nextGame?.local)
        assertEquals("WAITLISTED", home.member.nextGame?.ownAttendance?.status?.name?.uppercase())
        assertEquals(3L, home.member.nextGame?.rosterPreview?.waitlisted?.single()?.waitlistPosition)
        assertEquals("game-0", home.member.lastCompletedGame?.gameId)
        assertEquals(1, home.admin?.groups?.single()?.entryRequestCount)
        assertEquals(2400L, home.admin?.groups?.single()?.monthlyCharges?.totalCents)
        assertEquals("game-2", home.admin?.groups?.single()?.gameToSettle?.gameId)
    }

    @Test
    fun `invalid enum in the contract becomes invalid response`() = runTest {
        val result = gateway {
            respond(HOME_JSON.replace("\"ADMIN\"", "\"UNKNOWN\""), headers = jsonHeaders())
        }.read()

        assertEquals(
            DataError.InvalidResponse,
            assertIs<HomeError.Data>(assertIs<SaqzResult.Failure<*>>(result).error).error,
        )
    }

    @Test
    fun `server failure maps to a data error`() = runTest {
        val result = gateway { respond("", HttpStatusCode.ServiceUnavailable) }.read()

        assertEquals(
            DataError.Server,
            assertIs<HomeError.Data>(assertIs<SaqzResult.Failure<*>>(result).error).error,
        )
    }

    private fun gateway(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): KtorHomeGateway {
        val client = NetworkClient(
            MockEngine { request -> handler(request) },
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
        )
        return KtorHomeGateway(AuthenticatedNetworkClient(client, Tokens(), NoopInvalidator()))
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("test-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private companion object {
        val HOME_JSON = """
            {
              "member": {
                "nextGame": {
                  "groupId": "group-1",
                  "groupName": "Vôlei do CERET",
                  "gameId": "game-1",
                  "local": "Quadra do Ibirapuera",
                  "startsAt": "2026-08-12T22:30:00Z",
                  "confirmationDeadline": "2026-08-12T18:30:00Z",
                  "capacity": 12,
                  "confirmedCount": 9,
                  "declinedCount": 1,
                  "pendingCount": 2,
                  "waitlistCount": 3,
                  "ownAttendance": {"status": "WAITLISTED", "waitlistPosition": 3},
                  "membershipType": "MENSALISTA",
                  "mensalistaPriority": true,
                  "rosterPreview": {
                    "confirmed": [{"displayName": "Ana", "waitlistPosition": null}],
                    "waitlisted": [{"displayName": "Bia", "waitlistPosition": 3}]
                  }
                },
                "lastCompletedGame": {
                  "groupId": "group-1",
                  "groupName": "Vôlei do CERET",
                  "gameId": "game-0",
                  "startsAt": "2026-08-05T22:30:00Z",
                  "confirmedCount": 10,
                  "ownPlayed": true
                },
                "groups": [{
                  "id": "group-1",
                  "name": "Vôlei do CERET",
                  "role": "ADMIN",
                  "memberCount": 26,
                  "gamesPlayed": 18
                }]
              },
              "admin": {
                "groups": [{
                  "id": "group-1",
                  "name": "Vôlei do CERET",
                  "entryRequestCount": 1,
                  "monthlyCharges": {"count": 3, "totalCents": 2400},
                  "gameToSettle": {
                    "gameId": "game-2",
                    "startsAt": "2026-08-13T22:30:00Z",
                    "pendingCount": 2,
                    "totalCents": 1800
                  }
                }]
              }
            }
        """.trimIndent()
    }
}
