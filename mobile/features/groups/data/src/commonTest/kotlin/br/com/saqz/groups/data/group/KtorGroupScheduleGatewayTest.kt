package br.com.saqz.groups.data.group

import br.com.saqz.domain.*
import br.com.saqz.groups.domain.group.*
import br.com.saqz.network.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class KtorGroupScheduleGatewayTest {
    @Test fun `save sends only schedule fields and the loaded version`() = runTest {
        val gateway = gateway(MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/groups/group-1/schedule", request.url.encodedPath)
            assertEquals("\"7\"", request.headers[HttpHeaders.IfMatch])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("recurring", "slots", "durationMinutes", "confirmationLeadMinutes", "paused"), body.keys)
            assertEquals(90, body.getValue("durationMinutes").jsonPrimitive.int)
            respond(RESPONSE, HttpStatusCode.OK, headersOf(
                HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf("\"8\""),
            ))
        })
        val result = assertIs<SaqzResult.Success<VersionedGroupSchedule>>(
            gateway.updateSchedule(GroupId("group-1"), GroupVersionToken("\"7\""), SCHEDULE),
        ).value
        assertEquals(SCHEDULE, result.schedule)
        assertEquals(GroupVersionToken("\"8\""), result.versionToken)
    }

    @Test fun `read maps persisted settings and normalizes time`() = runTest {
        val gateway = gateway(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            respond(RESPONSE, HttpStatusCode.OK, headersOf(
                HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf("\"8\""),
            ))
        })
        assertEquals(SCHEDULE, assertIs<SaqzResult.Success<VersionedGroupSchedule>>(
            gateway.readSchedule(GroupId("group-1")),
        ).value.schedule)
    }

    @Test fun `missing ETag fails and write is never automatically retried`() = runTest {
        var requests = 0
        val gateway = gateway(MockEngine {
            requests++
            respond(RESPONSE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        assertIs<SaqzResult.Failure<GroupProfileError>>(
            gateway.updateSchedule(GroupId("group-1"), GroupVersionToken("\"7\""), SCHEDULE),
        )
        assertEquals(1, requests)
    }

    @Test fun `server failure remains failure with a single write attempt`() = runTest {
        var requests = 0
        val gateway = gateway(MockEngine {
            requests++
            respond("", HttpStatusCode.InternalServerError)
        })
        assertIs<SaqzResult.Failure<GroupProfileError>>(
            gateway.updateSchedule(GroupId("group-1"), GroupVersionToken("\"7\""), SCHEDULE),
        )
        assertEquals(1, requests)
    }

    private fun gateway(engine: MockEngine) = KtorGroupScheduleGateway(AuthenticatedNetworkClient(
        NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.test/")),
        object : IdTokenProvider {
            override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
                completion(TokenResult.Available("token"))
        },
        object : SessionInvalidator { override fun invalidate() = Unit },
    ))

    private companion object {
        val SCHEDULE = GroupSchedule(true, listOf(GroupScheduleSlot(GroupWeekday.TUESDAY, "19:30")), 90, 720, true)
        const val RESPONSE = """{"recurring":true,"slots":[{"weekday":"TUESDAY","startTime":"19:30:00"}],"durationMinutes":90,"confirmationLeadMinutes":720,"paused":true}"""
    }
}
