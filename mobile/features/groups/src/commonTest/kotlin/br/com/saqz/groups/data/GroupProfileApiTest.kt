package br.com.saqz.groups.data

import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupCreateCommand
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupTimeZone
import br.com.saqz.groups.model.GroupUpdateCommand
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class GroupProfileApiTest {
    @Test
    fun `complete create serializes every profile game and finance value`() = runTest {
        val api = fixture { request ->
            val body = request.jsonBody()
            assertEquals("POST", request.method.value)
            assertEquals("/api/groups", request.url.encodedPath)
            assertEquals(REQUEST_ID, body.getValue("requestId").jsonPrimitive.content)
            assertEquals("Beach Club", body.getValue("name").jsonPrimitive.content)
            assertEquals("BEACH_VOLLEYBALL", body.getValue("modality").jsonPrimitive.content)
            assertEquals("WOMEN", body.getValue("composition").jsonPrimitive.content)
            assertEquals("Arena Beach", body.getValue("defaultVenue").jsonObject.getValue("name").jsonPrimitive.content)
            assertEquals("MONDAY", body.getValue("regularSlots").jsonArray.single().jsonObject.getValue("weekday").jsonPrimitive.content)
            assertEquals(1500, body.getValue("defaultGameFeeCents").jsonPrimitive.long)
            assertEquals(7000, body.getValue("monthlyFeeCents").jsonPrimitive.long)
            assertEquals(10, body.getValue("monthlyDueDay").jsonPrimitive.int)
            assertEquals("America/Sao_Paulo", body.getValue("timeZone").jsonPrimitive.content)
            groupResponse(HttpStatusCode.Created)
        }

        api.createProfile(GroupCreateCommand(REQUEST_ID, zone(), completeForm()))
    }

    @Test
    fun `create normalizes blank optional values to absent JSON`() = runTest {
        val api = fixture { request ->
            val body = request.jsonBody()
            assertFalse(body.containsKey("description"))
            assertFalse(body.containsKey("city"))
            assertFalse(body.getValue("defaultVenue").jsonObject.containsKey("court"))
            groupResponse(HttpStatusCode.Created)
        }

        api.createProfile(
            GroupCreateCommand(
                REQUEST_ID,
                zone(),
                completeForm().copy(description = "  ", city = "", defaultVenue = completeForm().defaultVenue?.copy(court = " ")),
            ),
        )
    }

    @Test
    fun `create clears court style values for a non-court modality`() = runTest {
        val api = fixture { request ->
            val body = request.jsonBody()
            assertFalse(body.containsKey("playStyle"))
            assertFalse(body.containsKey("customPlayStyle"))
            groupResponse(HttpStatusCode.Created)
        }

        api.createProfile(
            GroupCreateCommand(
                REQUEST_ID,
                zone(),
                completeForm().copy(modality = GroupModality.FOOTVOLLEY, playStyle = GroupPlayStyle.CUSTOM, customPlayStyle = "Fast"),
            ),
        )
    }

    @Test
    fun `create clears obsolete custom level and play style labels`() = runTest {
        val api = fixture { request ->
            val body = request.jsonBody()
            assertFalse(body.containsKey("customLevel"))
            assertFalse(body.containsKey("customPlayStyle"))
            groupResponse(HttpStatusCode.Created)
        }

        api.createProfile(
            GroupCreateCommand(
                REQUEST_ID,
                zone(),
                completeForm().copy(
                    modality = GroupModality.COURT_VOLLEYBALL,
                    level = GroupLevel.ADVANCED,
                    customLevel = "Elite",
                    playStyle = GroupPlayStyle.FIVE_ONE,
                    customPlayStyle = "Fast",
                ),
            ),
        )
    }

    @Test
    fun `complete response decodes exact nested enums ids and integer cents`() = runTest {
        val result = fixture { groupResponse() }.read(GROUP_ID)
        val group = assertIs<NetworkResult.Success<VersionedGroupDto>>(result).value.group

        assertEquals(GroupModalityDto.BEACH_VOLLEYBALL, group.profile?.modality)
        assertEquals(GroupCompositionDto.WOMEN, group.profile?.composition)
        assertEquals(GroupLevelDto.INTERMEDIATE, group.profile?.level)
        assertEquals(VENUE_ID, group.profile?.defaultVenue?.id)
        assertEquals(SLOT_ID, group.profile?.regularSlots?.single()?.id)
        assertEquals(1500, group.financeDefaults?.defaultGameFeeCents)
        assertEquals(7000, group.financeDefaults?.monthlyFeeCents)
        assertEquals(GroupPrivacyDto.PRIVATE, group.privacy)
        assertEquals(GroupCurrencyDto.BRL, group.currency)
    }

    @Test
    fun `group DTO surface contains no storage key or public photo URL`() = runTest {
        val group = assertIs<NetworkResult.Success<VersionedGroupDto>>(fixture { groupResponse() }.read(GROUP_ID)).value.group
        val encoded = Json.encodeToString(GroupDto.serializer(), group)

        assertFalse(encoded.contains("objectKey", ignoreCase = true))
        assertFalse(encoded.contains("publicUrl", ignoreCase = true))
        assertFalse(encoded.contains("photoUrl", ignoreCase = true))
    }

    @Test
    fun `read preserves the exact quoted response ETag`() = runTest {
        val result = fixture { groupResponse(etag = "\"23\"") }.read(GROUP_ID)

        assertEquals("\"23\"", assertIs<NetworkResult.Success<VersionedGroupDto>>(result).value.etag)
    }

    @Test
    fun `complete update uses root route cleaned payload and mandatory ETag`() = runTest {
        val api = fixture { request ->
            assertEquals("PUT", request.method.value)
            assertEquals("/api/groups/$GROUP_ID", request.url.encodedPath)
            assertEquals("\"7\"", request.headers[HttpHeaders.IfMatch])
            val body = request.jsonBody()
            assertEquals("Beach Club", body.getValue("name").jsonPrimitive.content)
            assertFalse(body.containsKey("timeZone"))
            groupResponse(etag = "\"8\"")
        }

        val result = api.updateProfile(GroupUpdateCommand(GROUP_ID, "\"7\"", completeForm()))

        assertEquals("\"8\"", assertIs<NetworkResult.Success<VersionedGroupDto>>(result).value.etag)
    }

    @Test
    fun `empty update collections are omitted instead of serialized as null`() = runTest {
        val api = fixture { request ->
            val body = request.jsonBody()
            assertFalse(body.containsKey("defaultVenue"))
            assertFalse(body.containsKey("regularSlots"))
            groupResponse(etag = "\"8\"")
        }

        api.updateProfile(GroupUpdateCommand(GROUP_ID, "\"7\"", completeForm().copy(defaultVenue = null, regularSlots = emptyList())))
    }

    @Test
    fun `validation problem retains nested and indexed field paths`() = runTest {
        val api = fixture {
            respond(
                """{"status":400,"code":"VALIDATION_FAILED","correlationId":"corr","fieldErrors":{"defaultVenue.address":["is required"],"regularSlots[0].weekday":["is required"]}}""",
                HttpStatusCode.BadRequest,
                jsonHeaders(),
            )
        }

        val result = api.createProfile(GroupCreateCommand(REQUEST_ID, zone(), completeForm()))
        val problem = assertIs<NetworkError.ApiProblemError>(assertIs<NetworkResult.Failure>(result).error).problem

        assertEquals(listOf("is required"), problem.fieldErrors?.get("defaultVenue.address"))
        assertEquals(listOf("is required"), problem.fieldErrors?.get("regularSlots[0].weekday"))
    }

    private fun completeForm() = GroupSetupForm(
        name = " Beach Club ",
        modality = GroupModality.BEACH_VOLLEYBALL,
        composition = GroupComposition.WOMEN,
        description = " Training group ",
        city = " Santos ",
        level = GroupLevel.INTERMEDIATE,
        defaultVenue = GroupVenueForm(VENUE_ID, " Arena Beach ", " Rua Central 100 ", " Quadra 2 "),
        regularSlots = listOf(GroupRegularSlotForm(SLOT_ID, GroupWeekday.MONDAY, "20:00:00", 120)),
        defaultCapacity = 18,
        defaultConfirmationLeadMinutes = 180,
        defaultGameFeeCents = 1500,
        monthlyFeeCents = 7000,
        monthlyDueDay = 10,
    )

    private fun zone() = assertIs<GroupTimeZone.ParseResult.Valid>(GroupTimeZone.parse("America/Sao_Paulo")).value

    private fun fixture(response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): GroupApi {
        val network = NetworkClient(MockEngine { request -> response(request) }, NetworkConfig("test", "https://api.example.test/"))
        return GroupApi(AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator()))
    }

    private fun MockRequestHandleScope.groupResponse(
        status: HttpStatusCode = HttpStatusCode.OK,
        etag: String = "\"7\"",
    ) = respond(
        """{"id":"$GROUP_ID","name":"Beach Club","timeZone":"America/Sao_Paulo","version":${etag.removeSurrounding("\"")},"role":"OWNER","profileStatus":"COMPLETE","privacy":"PRIVATE","currency":"BRL","profile":{"modality":"BEACH_VOLLEYBALL","composition":"WOMEN","description":"Training group","city":"Santos","level":"INTERMEDIATE","defaultVenue":{"id":"$VENUE_ID","name":"Arena Beach","address":"Rua Central 100","court":"Quadra 2"},"regularSlots":[{"id":"$SLOT_ID","weekday":"MONDAY","startTime":"20:00:00","durationMinutes":120}],"defaultCapacity":18,"defaultConfirmationLeadMinutes":180},"financeDefaults":{"defaultGameFeeCents":1500,"monthlyFeeCents":7000,"monthlyDueDay":10}}""",
        status,
        headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf(etag)),
    )

    private fun HttpRequestData.jsonBody() = Json.parseToJsonElement((body as TextContent).text).jsonObject
    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")
    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) = completion(TokenResult.Available("token"))
    }
    private class NoopInvalidator : SessionInvalidator { override fun invalidate() = Unit }

    private companion object {
        const val REQUEST_ID = "018f4f4d-6634-7be1-a018-0123456789ab"
        const val GROUP_ID = "018f4f4d-6634-7be1-a018-abcdef012345"
        const val VENUE_ID = "018f4f4d-6634-7be1-a018-venue0000001"
        const val SLOT_ID = "018f4f4d-6634-7be1-a018-slot00000001"
    }
}
