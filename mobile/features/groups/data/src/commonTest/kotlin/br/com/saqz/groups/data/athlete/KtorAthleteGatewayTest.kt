package br.com.saqz.groups.data.athlete

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.Athlete
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteLevel
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthletePreferredSide
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.athlete.AthleteStats
import br.com.saqz.groups.domain.athlete.UpdateAthleteCommand
import br.com.saqz.groups.domain.athlete.UpdateOwnAthleteProfileCommand
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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorAthleteGatewayTest {
    @Test
    fun `roster maps expanded member attributes and nullable financial fields`() = runTest {
        val athlete = fixture { roster() }.gateway.roster(GroupId(GROUP_ID), AthleteRosterFilter())
            .success<List<AthleteRosterEntry>>()
            .single()

        assertEquals("member-1", athlete.userId)
        assertEquals("Raio", athlete.nickname)
        assertEquals(AthletePosition.PONTA, athlete.position)
        assertEquals(AthletePosition.CENTRAL, athlete.secondaryPosition)
        assertEquals(AthleteLevel.AVANCADO, athlete.level)
        assertEquals(AthletePreferredSide.DIREITA, athlete.preferredSide)
        assertEquals(190, athlete.heightCm)
        assertEquals(null, athlete.monthlyFeeCents)
        assertEquals(null, athlete.monthlyDueDay)
        assertEquals("2026-08-01T12:00:00Z", athlete.joinedAt)
        assertEquals(br.com.saqz.groups.domain.group.GroupRole.OWNER, athlete.role)
    }

    @Test
    fun `roster sends existing filters and include inactive`() = runTest {
        fixture { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals(
                "/api/groups/$GROUP_ID/athletes?search=Ana%20%26%20Bia&type=MENSALISTA&position=PONTA&financialStatus=PENDENTE&includeInactive=true",
                request.url.encodedPath + "?" + request.url.encodedQuery,
            )
            roster()
        }.gateway.roster(
            GroupId(GROUP_ID),
            AthleteRosterFilter(
                search = "Ana & Bia",
                membershipType = AthleteMembershipType.MENSALISTA,
                position = AthletePosition.PONTA,
                financialStatus = br.com.saqz.groups.domain.athlete.AthleteFinancialStatus.PENDENTE,
                includeInactive = true,
            ),
        )
    }

    @Test
    fun `roster tolerates missing financial status as unknown`() = runTest {
        val athlete = fixture {
            respond(
                """{"athletes":[{"userId":"member-1","displayName":"Member","membershipType":"AVULSO","active":true}]}""",
                headers = jsonHeaders(),
            )
        }.gateway.roster(GroupId(GROUP_ID), AthleteRosterFilter())
            .success<List<AthleteRosterEntry>>()
            .single()

        assertEquals(br.com.saqz.groups.domain.athlete.AthleteFinancialStatus.DESCONHECIDO, athlete.financialStatus)
        assertEquals(null, athlete.monthlyFeeCents)
    }

    @Test
    fun `own profile patch sends expanded attributes`() = runTest {
        fixture { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("/api/groups/$GROUP_ID/athletes/me", request.url.encodedPath)
            assertEquals(
                setOf("nickname", "position", "secondaryPosition", "level", "preferredSide", "heightCm"),
                request.bodyJson().keys,
            )
            assertEquals("Raio", request.bodyJson().getValue("nickname").jsonPrimitive.content)
            athleteResponse()
        }.gateway.updateOwnProfile(
            UpdateOwnAthleteProfileCommand(
                GroupId(GROUP_ID),
                nickname = "Raio",
                position = AthletePosition.PONTA,
                secondaryPosition = AthletePosition.CENTRAL,
                level = AthleteLevel.INTERMEDIARIO,
                preferredSide = AthletePreferredSide.TANTO_FAZ,
                heightCm = 185,
            ),
        )
    }

    @Test
    fun `admin patch sends attributes membership and billing overrides`() = runTest {
        val athlete = fixture { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("/api/groups/$GROUP_ID/athletes/$USER_ID", request.url.encodedPath)
            val body = request.bodyJson()
            assertEquals("MENSALISTA", body.getValue("membershipType").jsonPrimitive.content)
            assertEquals(false, body.getValue("active").jsonPrimitive.boolean)
            assertEquals(15000, body.getValue("monthlyFeeCents").jsonPrimitive.int)
            assertEquals(12, body.getValue("monthlyDueDay").jsonPrimitive.int)
            athleteResponse()
        }.gateway.updateAthlete(
            UpdateAthleteCommand(
                groupId = GroupId(GROUP_ID),
                userId = USER_ID,
                position = AthletePosition.OPOSTO,
                membershipType = AthleteMembershipType.MENSALISTA,
                active = false,
                nickname = "Raio",
                secondaryPosition = AthletePosition.PONTA,
                level = AthleteLevel.AVANCADO,
                preferredSide = null,
                heightCm = 190,
                monthlyFeeCents = 15000,
                monthlyDueDay = 12,
            ),
        ).success<Athlete>()

        assertEquals("Raio", athlete.nickname)
        assertEquals(15000, athlete.monthlyFeeCents)
    }

    @Test
    fun `stats maps games nullable attendance and absences`() = runTest {
        val stats = fixture { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/groups/$GROUP_ID/athletes/$USER_ID/stats", request.url.encodedPath)
            respond("""{"games":3,"attendanceRate":66,"absences":1}""", headers = jsonHeaders())
        }.gateway.stats(GroupId(GROUP_ID), USER_ID).success<AthleteStats>()

        assertEquals(3, stats.games)
        assertEquals(66, stats.attendanceRate)
        assertEquals(1, stats.absences)
    }

    @Test
    fun `stats preserves null attendance`() = runTest {
        val stats = fixture {
            respond("""{"games":0,"attendanceRate":null,"absences":0}""", headers = jsonHeaders())
        }.gateway.stats(GroupId(GROUP_ID), USER_ID).success<AthleteStats>()

        assertEquals(null, stats.attendanceRate)
    }

    @Test
    fun `remove uses exact route and maps no content`() = runTest {
        val result = fixture { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/groups/$GROUP_ID/athletes/$USER_ID", request.url.encodedPath)
            respond("", HttpStatusCode.NoContent)
        }.gateway.removeAthlete(GroupId(GROUP_ID), USER_ID)

        assertIs<SaqzResult.Success<Unit>>(result)
        assertEquals(Unit, result.value)
    }

    @Test
    fun `422 validation keeps field errors typed`() = runTest {
        val error = fixture {
            respond(
                """{"status":422,"code":"VALIDATION_FAILED","correlationId":"private","fieldErrors":{"heightCm":["must be between 100 and 250"]}}""",
                HttpStatusCode.UnprocessableEntity,
                jsonHeaders(),
            )
        }.gateway.updateAthlete(
            UpdateAthleteCommand(GroupId(GROUP_ID), USER_ID, null, AthleteMembershipType.AVULSO, true),
        ).failure()

        val validation = assertIs<AthleteError.Validation>(error)
        assertEquals(listOf("must be between 100 and 250"), validation.details.fieldMessages["heightCm"])
    }

    @Test
    fun `forbidden stats maps shared forbidden error`() = runTest {
        val error = fixture { problem(403, "ACCESS_FORBIDDEN") }
            .gateway.stats(GroupId(GROUP_ID), USER_ID)
            .failure()

        assertEquals(DataError.Forbidden, assertIs<AthleteError.DataFailure>(error).error)
    }

    private fun fixture(response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        Fixture(
            KtorAthleteGateway(
                AuthenticatedNetworkClient(
                    NetworkClient(
                        MockEngine { response(it) },
                        NetworkConfig(NetworkEnvironment.Test, "https://api.test/"),
                    ),
                    Tokens(),
                    object : SessionInvalidator {
                        override fun invalidate() = Unit
                    },
                ),
                retryDelay = {},
            ),
        )

    private fun MockRequestHandleScope.roster() = respond(
        """{"athletes":[{"userId":"member-1","displayName":"Member","role":"OWNER","phone":null,"position":"PONTA","membershipType":"MENSALISTA","active":true,"financialStatus":"DESCONHECIDO","nickname":"Raio","secondaryPosition":"CENTRAL","level":"AVANCADO","preferredSide":"DIREITA","heightCm":190,"monthlyFeeCents":null,"monthlyDueDay":null,"joinedAt":"2026-08-01T12:00:00Z"}]}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.athleteResponse() = respond(
        """{"userId":"$USER_ID","displayName":"Member","role":"ADMIN","position":"OPOSTO","membershipType":"MENSALISTA","active":false,"nickname":"Raio","secondaryPosition":"PONTA","level":"AVANCADO","preferredSide":null,"heightCm":190,"monthlyFeeCents":15000,"monthlyDueDay":12}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.problem(status: Int, code: String) = respond(
        """{"status":$status,"code":"$code","correlationId":"private"}""",
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun HttpRequestData.bodyText(): String = (body as TextContent).text

    private fun HttpRequestData.bodyJson() = Json.parseToJsonElement(bodyText()).jsonObject

    private inline fun <reified T> SaqzResult<*, br.com.saqz.groups.domain.athlete.AthleteError>.success(): T =
        assertIs<SaqzResult.Success<T>>(this).value

    private fun SaqzResult<*, br.com.saqz.groups.domain.athlete.AthleteError>.failure() =
        assertIs<SaqzResult.Failure<br.com.saqz.groups.domain.athlete.AthleteError>>(this).error

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("token"))
    }

    private data class Fixture(val gateway: KtorAthleteGateway)

    companion object {
        private const val GROUP_ID = "group-1"
        private const val USER_ID = "user-1"
    }
}
