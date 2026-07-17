package br.com.saqz.access.data

import br.com.saqz.network.ApiProblem
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
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupApiTest {
    @Test
    fun `create posts to the exact groups route`() = runTest {
        val fixture = fixture { request ->
            assertEquals("POST", request.method.value)
            assertEquals("/api/groups", request.url.encodedPath)
            groupResponse(HttpStatusCode.Created)
        }

        fixture.api.create(REQUEST_ID, "Training Club", "America/Sao_Paulo")
    }

    @Test
    fun `create serializes only request id name and timezone`() = runTest {
        val fixture = fixture { request ->
            val body = Json.parseToJsonElement(request.bodyText()).jsonObject
            assertEquals(setOf("requestId", "name", "timeZone"), body.keys)
            assertEquals(REQUEST_ID, body.getValue("requestId").jsonPrimitive.content)
            assertEquals("Training Club", body.getValue("name").jsonPrimitive.content)
            assertEquals("America/Sao_Paulo", body.getValue("timeZone").jsonPrimitive.content)
            groupResponse(HttpStatusCode.Created)
        }

        fixture.api.create(REQUEST_ID, "Training Club", "America/Sao_Paulo")
    }

    @Test
    fun `create decodes the authoritative owner group`() = runTest {
        val result = fixture { groupResponse(HttpStatusCode.Created) }.api
            .create(REQUEST_ID, "Training Club", "America/Sao_Paulo")

        val group = assertIs<NetworkResult.Success<GroupDto>>(result).value
        assertEquals(GROUP_ID, group.id)
        assertEquals("Training Club", group.name)
        assertEquals("America/Sao_Paulo", group.timeZone)
        assertEquals(7, group.version)
        assertEquals(GroupRoleDto.OWNER, group.role)
    }

    @Test
    fun `create retry preserves the exact request id`() = runTest {
        val bodies = mutableListOf<String>()
        val fixture = fixture { request ->
            bodies += request.bodyText()
            if (bodies.size == 1) problem(401, "AUTHENTICATION_REQUIRED") else groupResponse(HttpStatusCode.Created)
        }

        fixture.api.create(REQUEST_ID, "Training Club", "America/Sao_Paulo")

        assertEquals(2, bodies.size)
        assertEquals(bodies.first(), bodies.last())
        assertEquals(REQUEST_ID, Json.parseToJsonElement(bodies.last()).jsonObject.getValue("requestId").jsonPrimitive.content)
    }

    @Test
    fun `read gets the exact group route`() = runTest {
        val fixture = fixture { request ->
            assertEquals("GET", request.method.value)
            assertEquals("/api/groups/$GROUP_ID", request.url.encodedPath)
            groupResponse()
        }

        fixture.api.read(GROUP_ID)
    }

    @Test
    fun `read returns group and response etag`() = runTest {
        val result = fixture { groupResponse(etag = "\"7\"") }.api.read(GROUP_ID)

        val versioned = assertIs<NetworkResult.Success<VersionedGroupDto>>(result).value
        assertEquals("\"7\"", versioned.etag)
        assertEquals(GROUP_ID, versioned.group.id)
        assertEquals(GroupRoleDto.OWNER, versioned.group.role)
        assertEquals(7, versioned.group.version)
    }

    @Test
    fun `update puts to the exact settings route`() = runTest {
        val fixture = fixture { request ->
            assertEquals("PUT", request.method.value)
            assertEquals("/api/groups/$GROUP_ID/settings", request.url.encodedPath)
            groupResponse()
        }

        fixture.api.update(GROUP_ID, "\"7\"", "New Group", "Europe/Lisbon")
    }

    @Test
    fun `update sends exact settings body and mandatory etag`() = runTest {
        val fixture = fixture { request ->
            assertEquals("\"7\"", request.headers[HttpHeaders.IfMatch])
            val body = Json.parseToJsonElement(request.bodyText()).jsonObject
            assertEquals(setOf("name", "timeZone"), body.keys)
            assertEquals("New Group", body.getValue("name").jsonPrimitive.content)
            assertEquals("Europe/Lisbon", body.getValue("timeZone").jsonPrimitive.content)
            groupResponse()
        }

        fixture.api.update(GROUP_ID, "\"7\"", "New Group", "Europe/Lisbon")
    }

    @Test
    fun `update returns the new response etag`() = runTest {
        val result = fixture { groupResponse(etag = "\"8\"") }.api
            .update(GROUP_ID, "\"7\"", "New Group", "Europe/Lisbon")

        val versioned = assertIs<NetworkResult.Success<VersionedGroupDto>>(result).value
        assertEquals("\"8\"", versioned.etag)
        assertEquals(8, versioned.group.version)
    }

    @Test
    fun `read maps group not found distinctly`() = runTest {
        val result = fixture { problem(404, "GROUP_NOT_FOUND") }.api.read(GROUP_ID)

        assertProblem(result, 404, "GROUP_NOT_FOUND")
    }

    @Test
    fun `update maps access forbidden distinctly`() = runTest {
        val result = fixture { problem(403, "ACCESS_FORBIDDEN") }.api
            .update(GROUP_ID, "\"7\"", "New Group", "UTC")

        assertProblem(result, 403, "ACCESS_FORBIDDEN")
    }

    @Test
    fun `update maps version conflict distinctly`() = runTest {
        val result = fixture { problem(409, "VERSION_CONFLICT") }.api
            .update(GROUP_ID, "\"7\"", "New Group", "UTC")

        assertProblem(result, 409, "VERSION_CONFLICT")
    }

    @Test
    fun `create maps validation fields distinctly`() = runTest {
        val fixture = fixture {
            respond(
                """{"status":400,"code":"VALIDATION_FAILED","correlationId":"corr-400","fieldErrors":{"name":["invalid"]}}""",
                HttpStatusCode.BadRequest,
                jsonHeaders(),
            )
        }

        val result = fixture.api.create(REQUEST_ID, " ", "UTC")

        val error = assertIs<NetworkError.ApiProblemError>(assertIs<NetworkResult.Failure>(result).error)
        assertEquals(400, error.problem.status)
        assertEquals("VALIDATION_FAILED", error.problem.code)
        assertEquals(listOf("invalid"), error.problem.fieldErrors?.get("name"))
    }

    private fun fixture(
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Fixture {
        val network = NetworkClient(MockEngine { request -> response(request) }, NetworkConfig("test", "https://api.example.test/"))
        return Fixture(GroupApi(AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator())))
    }

    private fun MockRequestHandleScope.groupResponse(
        status: HttpStatusCode = HttpStatusCode.OK,
        etag: String? = "\"7\"",
    ): HttpResponseData {
        val version = etag?.removeSurrounding("\"")?.toLongOrNull() ?: 7
        val headers = if (etag == null) jsonHeaders() else headersOf(
            HttpHeaders.ContentType to listOf("application/json"),
            HttpHeaders.ETag to listOf(etag),
        )
        return respond(
            """{"id":"$GROUP_ID","name":"Training Club","timeZone":"America/Sao_Paulo","version":$version,"role":"OWNER"}""",
            status,
            headers,
        )
    }

    private fun MockRequestHandleScope.problem(status: Int, code: String): HttpResponseData = respond(
        """{"status":$status,"code":"$code","correlationId":"corr-$status"}""",
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun HttpRequestData.bodyText(): String = (body as TextContent).text

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun assertProblem(result: NetworkResult<*>, status: Int, code: String) {
        val problem = assertIs<NetworkError.ApiProblemError>(assertIs<NetworkResult.Failure>(result).error).problem
        assertEquals(ApiProblem(status, code, "corr-$status"), problem)
    }

    private class Tokens : IdTokenProvider {
        private var calls = 0

        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) {
            calls += 1
            completion(TokenResult.Available(if (forceRefresh) "fresh-token" else "old-token"))
        }
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private data class Fixture(val api: GroupApi)

    private companion object {
        const val REQUEST_ID = "018f4f4d-6634-7be1-a018-0123456789ab"
        const val GROUP_ID = "018f4f4d-6634-7be1-a018-abcdef012345"
    }
}
