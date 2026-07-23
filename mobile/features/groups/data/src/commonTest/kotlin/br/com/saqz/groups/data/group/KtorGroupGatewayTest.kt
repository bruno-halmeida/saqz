package br.com.saqz.groups.data.group

import br.com.saqz.domain.*
import br.com.saqz.groups.domain.group.*
import br.com.saqz.network.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class KtorGroupGatewayTest {
    @Test fun `create uses exact route and method`() =
        runTest {
            fixture { req ->
                assertEquals(HttpMethod.Post, req.method)
                assertEquals("/api/groups", req.url.encodedPath)
                group()
            }.gateway.create(create())
        }

    @Test fun `create maps exact basic body`() =
        runTest {
            fixture { req ->
                assertEquals(setOf("requestId", "name", "timeZone"), req.bodyJson().keys)
                group()
            }.gateway.create(create())
        }

    @Test fun `create preserves command key`() =
        runTest {
            fixture { req ->
                assertEquals(KEY, req.bodyJson()["requestId"]?.jsonPrimitive?.content)
                group()
            }.gateway.create(create())
        }

    @Test fun `create maps authoritative group`() =
        runTest {
            assertEquals(
                GroupRole.OWNER,
                fixture { group() }
                    .gateway
                    .create(create())
                    .success<Group>()
                    .role,
            )
        }

    @Test fun `read uses exact route`() =
        runTest {
            fixture { req ->
                assertEquals("/api/groups/$ID", req.url.encodedPath)
                group()
            }.gateway.read(GroupId(ID))
        }

    @Test fun `read preserves exact version token`() =
        runTest {
            assertEquals(
                "\"7\"",
                fixture { group() }
                    .gateway
                    .read(GroupId(ID))
                    .success<VersionedGroup>()
                    .versionToken.value,
            )
        }

    @Test fun `settings update uses exact route`() =
        runTest {
            fixture { req ->
                assertEquals("/api/groups/$ID/settings", req.url.encodedPath)
                group()
            }.gateway.update(settings())
        }

    @Test fun `settings update sends mandatory version`() =
        runTest {
            fixture { req ->
                assertEquals("\"6\"", req.headers[HttpHeaders.IfMatch])
                group()
            }.gateway.update(settings())
        }

    @Test fun `profile create maps complete form`() =
        runTest {
            fixture { req ->
                assertEquals("COURT_VOLLEYBALL", req.bodyJson()["modality"]?.jsonPrimitive?.content)
                group()
            }.gateway.createProfile(profileCreate())
        }

    @Test fun `profile create includes timezone and key`() =
        runTest {
            fixture { req ->
                assertEquals(KEY, req.bodyJson()["requestId"]?.jsonPrimitive?.content)
                assertEquals("UTC", req.bodyJson()["timeZone"]?.jsonPrimitive?.content)
                group()
            }.gateway.createProfile(profileCreate())
        }

    @Test fun `profile update uses root route`() =
        runTest {
            fixture { req ->
                assertEquals("/api/groups/$ID", req.url.encodedPath)
                group()
            }.gateway.updateProfile(profileUpdate())
        }

    @Test fun `profile update sends exact token`() =
        runTest {
            fixture { req ->
                assertEquals("\"6\"", req.headers[HttpHeaders.IfMatch])
                group()
            }.gateway.updateProfile(profileUpdate())
        }

    @Test fun `complete response maps nested values`() =
        runTest {
            val g =
                fixture { group(completeJson()) }
                    .gateway
                    .read(GroupId(ID))
                    .success<VersionedGroup>()
                    .group
            assertEquals("Gym", g.profile?.defaultVenue?.name)
            assertEquals(2500, g.financeDefaults?.defaultGameFeeCents)
        }

    @Test fun `older response defaults profile status privacy and currency`() =
        runTest {
            val g =
                fixture { group() }
                    .gateway
                    .read(GroupId(ID))
                    .success<VersionedGroup>()
                    .group
            assertEquals(GroupProfileStatus.COMPLETE, g.profileStatus)
            assertEquals(GroupPrivacy.PRIVATE, g.privacy)
            assertEquals(GroupCurrency.BRL, g.currency)
        }

    @Test fun `missing required id maps invalid response`() =
        runTest {
            assertEquals(
                DataError.InvalidResponse,
                fixture {
                    group(baseJson().replace("\"id\":\"$ID\"", "\"id\":\"\""))
                }.gateway.read(GroupId(ID)).dataError(),
            )
        }

    @Test fun `missing version token maps invalid response`() =
        runTest {
            assertEquals(
                DataError.InvalidResponse,
                fixture {
                    respond(baseJson(), HttpStatusCode.OK, jsonHeaders())
                }.gateway.read(GroupId(ID)).dataError(),
            )
        }

    @Test fun `malformed venue maps invalid response`() =
        runTest {
            assertEquals(
                DataError.InvalidResponse,
                fixture {
                    group(completeJson().replace("\"name\":\"Gym\"", "\"name\":\"\""))
                }.gateway.read(GroupId(ID)).dataError(),
            )
        }

    @Test fun `validation preserves indexed field paths`() =
        runTest {
            val e =
                fixture {
                    problem(400, "VALIDATION_FAILED", "\"regularSlots[0].startTime\":[\"required\"]")
                }.gateway.createProfile(profileCreate()).failure()
            assertEquals(listOf("required"), assertIs<GroupProfileError.Validation>(e).details.fieldMessages["regularSlots[0].startTime"])
        }

    @Test fun `forbidden maps shared forbidden`() =
        runTest {
            assertEquals(DataError.Forbidden, fixture { problem(403, "FORBIDDEN") }.gateway.read(GroupId(ID)).dataError())
        }

    @Test fun `not found maps shared not found`() =
        runTest {
            assertEquals(DataError.NotFound, fixture { problem(404, "NOT_FOUND") }.gateway.read(GroupId(ID)).dataError())
        }

    @Test fun `conflict maps feature conflict`() =
        runTest {
            assertIs<GroupProfileError.Conflict>(
                fixture { problem(409, "VERSION_CONFLICT") }.gateway.updateProfile(profileUpdate()).failure(),
            )
        }

    @Test fun `timeout maps typed timeout`() =
        runTest {
            assertEquals(
                DataError.Timeout,
                fixture(
                    MockEngine {
                        throw io.ktor.client.plugins
                            .HttpRequestTimeoutException(it)
                    },
                ).gateway.read(GroupId(ID)).dataError(),
            )
        }

    @Test fun `connectivity maps typed connectivity`() =
        runTest {
            assertEquals(
                DataError.Connectivity,
                fixture(MockEngine { throw UnresolvedAddressException() }).gateway.read(GroupId(ID)).dataError(),
            )
        }

    @Test fun `unknown maps credential safe unknown`() =
        runTest {
            val e = fixture(MockEngine { throw IllegalStateException("secret") }).gateway.read(GroupId(ID))
            assertEquals(DataError.Unknown, e.dataError())
            assertFalse(e.toString().contains("secret"))
        }

    @Test fun `read retries server failure and exhausts four calls`() =
        runTest {
            var calls = 0
            val f =
                fixture {
                    calls++
                    problem(503, "TEMP")
                }
            assertEquals(DataError.Server, f.gateway.read(GroupId(ID)).dataError())
            assertEquals(4, calls)
            assertEquals(listOf(500L, 1000L, 2000L), f.delays)
        }

    @Test fun `idempotent create retries and keeps body`() =
        runTest {
            val bodies = mutableListOf<String>()
            val f =
                fixture { req ->
                    bodies +=
                        req.bodyText()
                    ; if (bodies.size ==
                        1
                    ) {
                        problem(503, "TEMP")
                    } else {
                        group(status = HttpStatusCode.Created)
                    }
                }
            ; assertIs<SaqzResult.Success<Group>>(f.gateway.create(create()))
            assertEquals(bodies.first(), bodies.last())
        }

    @Test fun `unsafe update never retries`() =
        runTest {
            var calls = 0
            val f =
                fixture {
                    calls++
                    problem(503, "TEMP")
                }
            f.gateway.updateProfile(profileUpdate())
            assertEquals(1, calls)
            assertTrue(f.delays.isEmpty())
        }

    @Test fun `cancellation propagates`() =
        runTest {
            assertFailsWith<CancellationException> {
                fixture(
                    MockEngine { throw CancellationException("cancel") },
                ).gateway.read(GroupId(ID))
            }
        }

    private fun fixture(response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        fixture(MockEngine { response(it) })

    private fun fixture(engine: MockEngine): Fixture {
        val delays = mutableListOf<Long>()
        val net = NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.test/"))
        val auth =
            AuthenticatedNetworkClient(
                net,
                Tokens(),
                object : SessionInvalidator {
                    override fun invalidate() = Unit
                },
            )
        return Fixture(
            KtorGroupGateway(auth, retryDelay = {
                delays +=
                    it
            }),
            delays,
        )
    }

    private fun MockRequestHandleScope.group(
        body: String = baseJson(),
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        body,
        status,
        headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf("\"7\"")),
    )

    private fun MockRequestHandleScope.problem(
        status: Int,
        code: String,
        fields: String? = null,
    ) = respond(
        """{"status":$status,"code":"$code","correlationId":"private"${fields?.let{",\"fieldErrors\":{$it}"}.orEmpty()}}""",
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun HttpRequestData.bodyText(): String = (body as TextContent).text

    private fun HttpRequestData.bodyJson() = Json.parseToJsonElement(bodyText()).jsonObject

    private fun create() = CreateGroupCommand(KEY, "Group", GroupTimeZone("UTC"))

    private fun settings() = UpdateGroupSettingsCommand(GroupId(ID), GroupVersionToken("\"6\""), "New", GroupTimeZone("UTC"))

    private fun form() = GroupSetupForm("Group", GroupModality.COURT_VOLLEYBALL, GroupComposition.MIXED, defaultCapacity = 12)

    private fun profileCreate() = CreateGroupProfileCommand(KEY, GroupTimeZone("UTC"), form())

    private fun profileUpdate() = UpdateGroupProfileCommand(GroupId(ID), GroupVersionToken("\"6\""), form())

    private inline fun <reified T> SaqzResult<*, GroupProfileError>.success(): T = assertIs<SaqzResult.Success<T>>(this).value

    private fun SaqzResult<*, GroupProfileError>.failure() = assertIs<SaqzResult.Failure<GroupProfileError>>(this).error

    private fun SaqzResult<*, GroupProfileError>.dataError() = assertIs<GroupProfileError.DataFailure>(failure()).error

    private class Tokens : IdTokenProvider {
        override fun token(
            forceRefresh: Boolean,
            completion: (TokenResult) -> Unit,
        ) = completion(TokenResult.Available("token"))
    }

    private data class Fixture(
        val gateway: KtorGroupGateway,
        val delays: MutableList<Long>,
    )

    companion object {
        const val ID = "group-1"
        const val KEY = "command-1"

        fun baseJson() = """{"id":"$ID","name":"Group","timeZone":"UTC","version":7,"role":"OWNER"}"""

        fun completeJson() =
            """{"id":"$ID","name":"Group","timeZone":"UTC","version":7,"role":"OWNER","profile":{"modality":"COURT_VOLLEYBALL","composition":"MIXED","defaultVenue":{"id":"v","name":"Gym","address":"Street"},"regularSlots":[{"weekday":"MONDAY","startTime":"19:00","durationMinutes":90}]},"financeDefaults":{"defaultGameFeeCents":2500}}"""
    }
}
