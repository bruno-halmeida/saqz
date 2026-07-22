package br.com.saqz.groups.data.photo

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoByteSource
import br.com.saqz.groups.domain.photo.GroupPhotoError
import br.com.saqz.groups.domain.photo.GroupPhotoMediaType
import br.com.saqz.groups.domain.photo.GroupPhotoReadResult
import br.com.saqz.groups.domain.photo.GroupPhotoUploadCommand
import br.com.saqz.groups.domain.photo.GroupPhotoVersionToken
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
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KtorGroupPhotoGatewayTest {
    @Test
    fun `upload uses exact route method etag and multipart content`() = runTest {
        fixture { request ->
            assertEquals("PUT", request.method.value)
            assertEquals("/api/groups/$GROUP_ID/photo", request.url.encodedPath)
            assertEquals(GROUP_ETAG, request.headers[HttpHeaders.IfMatch])
            assertEquals("multipart/form-data", request.body.contentType?.withoutParameters().toString())
            noContent(HttpStatusCode.OK, PHOTO_ETAG)
        }.gateway.upload(upload())
    }

    @Test
    fun `upload maps updated version`() = runTest {
        assertEquals(PHOTO_ETAG, fixture { noContent(HttpStatusCode.OK, PHOTO_ETAG) }
            .gateway.upload(upload()).success().version.value)
    }

    @Test
    fun `upload missing etag is invalid`() = runTest {
        assertEquals(DataError.InvalidResponse, fixture { respond("", HttpStatusCode.OK) }
            .gateway.upload(upload()).dataError())
    }

    @Test
    fun `upload declared above network limit maps media limit`() = runTest {
        var calls = 0
        val result = fixture(maximum = 2) { calls++; noContent(HttpStatusCode.OK, PHOTO_ETAG) }
            .gateway.upload(upload())
        assertIs<GroupPhotoError.MediaLimit>(result.failure())
        assertEquals(0, calls)
    }

    @Test
    fun `read sends exact route method and conditional version`() = runTest {
        fixture { request ->
            assertEquals("GET", request.method.value)
            assertEquals("/api/groups/$GROUP_ID/photo", request.url.encodedPath)
            assertEquals(PHOTO_ETAG, request.headers[HttpHeaders.IfNoneMatch])
            photo()
        }.gateway.read(GroupId(GROUP_ID), GroupPhotoVersionToken(PHOTO_ETAG))
    }

    @Test
    fun `unconditional read omits version header`() = runTest {
        fixture { request ->
            assertEquals(null, request.headers[HttpHeaders.IfNoneMatch])
            photo()
        }.gateway.read(GroupId(GROUP_ID))
    }

    @Test
    fun `read maps bytes content type and version`() = runTest {
        val available = assertIs<GroupPhotoReadResult.Available>(fixture { photo() }
            .gateway.read(GroupId(GROUP_ID)).success())
        assertContentEquals(PHOTO_BYTES, available.bytes)
        assertEquals("image/png", available.contentType)
        assertEquals(PHOTO_ETAG, available.version.value)
    }

    @Test
    fun `read maps 304 to not modified`() = runTest {
        assertEquals(GroupPhotoReadResult.NotModified, fixture { respond("", HttpStatusCode.NotModified) }
            .gateway.read(GroupId(GROUP_ID)).success())
    }

    @Test
    fun `read missing etag is invalid`() = runTest {
        assertEquals(DataError.InvalidResponse, fixture {
            respond(PHOTO_BYTES, headers = headersOf(HttpHeaders.ContentType, "image/png"))
        }.gateway.read(GroupId(GROUP_ID)).dataError())
    }

    @Test
    fun `read missing content type is invalid`() = runTest {
        assertEquals(DataError.InvalidResponse, fixture {
            respond(PHOTO_BYTES, headers = headersOf(HttpHeaders.ETag, PHOTO_ETAG))
        }.gateway.read(GroupId(GROUP_ID)).dataError())
    }

    @Test
    fun `read body above limit maps media limit`() = runTest {
        assertIs<GroupPhotoError.MediaLimit>(fixture(maximum = 2) { photo() }
            .gateway.read(GroupId(GROUP_ID)).failure())
    }

    @Test
    fun `remove uses exact route method and mandatory etag`() = runTest {
        fixture { request ->
            assertEquals("DELETE", request.method.value)
            assertEquals("/api/groups/$GROUP_ID/photo", request.url.encodedPath)
            assertEquals(GROUP_ETAG, request.headers[HttpHeaders.IfMatch])
            noContent(HttpStatusCode.NoContent, NEXT_ETAG)
        }.gateway.remove(GroupId(GROUP_ID), GroupPhotoVersionToken(GROUP_ETAG))
    }

    @Test
    fun `remove maps updated version`() = runTest {
        assertEquals(NEXT_ETAG, fixture { noContent(HttpStatusCode.NoContent, NEXT_ETAG) }.gateway
            .remove(GroupId(GROUP_ID), GroupPhotoVersionToken(GROUP_ETAG)).success().version.value)
    }

    @Test
    fun `remove missing etag is invalid`() = runTest {
        assertEquals(DataError.InvalidResponse, fixture { respond("", HttpStatusCode.NoContent) }.gateway
            .remove(GroupId(GROUP_ID), GroupPhotoVersionToken(GROUP_ETAG)).dataError())
    }

    @Test
    fun `conflict maps stale version`() = runTest {
        assertIs<GroupPhotoError.StaleVersion>(fixture { problem(409, "VERSION_CONFLICT") }
            .gateway.upload(upload()).failure())
    }

    @Test
    fun `not found maps feature outcome`() = runTest {
        assertIs<GroupPhotoError.NotFound>(fixture { problem(404, "PHOTO_NOT_FOUND") }
            .gateway.read(GroupId(GROUP_ID)).failure())
    }

    @Test
    fun `payload status maps media limit`() = runTest {
        assertIs<GroupPhotoError.MediaLimit>(fixture { problem(413, "MEDIA_LIMIT") }
            .gateway.upload(upload()).failure())
    }

    @Test
    fun `shared statuses map typed data failures`() = runTest {
        assertEquals(DataError.Unauthenticated, fixture { problem(401, "AUTH") }.gateway.upload(upload()).dataError())
        assertEquals(DataError.Forbidden, fixture { problem(403, "FORBIDDEN") }.gateway.upload(upload()).dataError())
        assertEquals(DataError.Server, fixture { problem(503, "SERVER") }.gateway.upload(upload()).dataError())
    }

    @Test
    fun `read retries server failures and exhausts four calls`() = runTest {
        var calls = 0
        val fixture = fixture { calls++; problem(503, "SERVER") }
        assertEquals(DataError.Server, fixture.gateway.read(GroupId(GROUP_ID)).dataError())
        assertEquals(4, calls)
        assertEquals(listOf(500L, 1000L, 2000L), fixture.delays)
    }

    @Test
    fun `read stops retrying after success`() = runTest {
        var calls = 0
        val fixture = fixture { if (++calls == 1) problem(503, "SERVER") else photo() }
        fixture.gateway.read(GroupId(GROUP_ID)).success()
        assertEquals(2, calls)
        assertEquals(listOf(500L), fixture.delays)
    }

    @Test
    fun `unsafe writes never retry`() = runTest {
        var uploads = 0
        val uploadFixture = fixture { uploads++; problem(503, "SERVER") }
        uploadFixture.gateway.upload(upload())
        var removals = 0
        val removeFixture = fixture { removals++; problem(503, "SERVER") }
        removeFixture.gateway.remove(GroupId(GROUP_ID), GroupPhotoVersionToken(GROUP_ETAG))
        assertEquals(1, uploads)
        assertEquals(1, removals)
        assertTrue(uploadFixture.delays.isEmpty() && removeFixture.delays.isEmpty())
    }

    @Test
    fun `timeout and connectivity map typed failures`() = runTest {
        assertEquals(DataError.Timeout, fixture(MockEngine { throw HttpRequestTimeoutException(it) })
            .gateway.read(GroupId(GROUP_ID)).dataError())
        assertEquals(DataError.Connectivity, fixture(MockEngine { throw UnresolvedAddressException() })
            .gateway.read(GroupId(GROUP_ID)).dataError())
    }

    @Test
    fun `cancellation at call propagates`() = runTest {
        assertFailsWith<CancellationException> {
            fixture(MockEngine { throw CancellationException("cancel") }).gateway.read(GroupId(GROUP_ID))
        }
    }

    @Test
    fun `cancellation during backoff propagates`() = runTest {
        val fixture = fixture(retryDelay = { throw CancellationException("cancel") }) {
            problem(503, "SERVER")
        }
        assertFailsWith<CancellationException> { fixture.gateway.read(GroupId(GROUP_ID)) }
    }

    @Test
    fun `unknown transport failure is credential safe`() = runTest {
        val result = fixture(MockEngine { throw IllegalStateException("secret-token") })
            .gateway.read(GroupId(GROUP_ID))
        assertEquals(DataError.Unknown, result.dataError())
        assertFalse(result.toString().contains("secret-token"))
    }

    private fun upload() = GroupPhotoUploadCommand(
        GroupId(GROUP_ID),
        GroupPhotoVersionToken(GROUP_ETAG),
        EncodedGroupPhoto(GroupPhotoMediaType.PNG, 3, GroupPhotoByteSource { PHOTO_BYTES }),
    )

    private fun fixture(
        maximum: Int = 5 * 1024 * 1024,
        retryDelay: suspend (Long) -> Unit = {},
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = fixture(MockEngine { response(it) }, maximum, retryDelay)

    private fun fixture(
        engine: MockEngine,
        maximum: Int = 5 * 1024 * 1024,
        retryDelay: suspend (Long) -> Unit = {},
    ): Fixture {
        val delays = mutableListOf<Long>()
        val delay: suspend (Long) -> Unit = { value -> delays += value; retryDelay(value) }
        val network = NetworkClient(
            engine,
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/", maxBinaryBodyBytes = maximum),
        )
        val authenticated = AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator())
        return Fixture(KtorGroupPhotoGateway(authenticated, delay), delays)
    }

    private fun MockRequestHandleScope.photo() = respond(PHOTO_BYTES, headers = headersOf(
        HttpHeaders.ContentType to listOf("image/png"),
        HttpHeaders.ETag to listOf(PHOTO_ETAG),
        HttpHeaders.CacheControl to listOf("private, no-cache"),
    ))

    private fun MockRequestHandleScope.noContent(status: HttpStatusCode, etag: String) =
        respond("", status, headersOf(HttpHeaders.ETag, etag))

    private fun MockRequestHandleScope.problem(status: Int, code: String) = respond(
        """{"status":$status,"code":"$code","correlationId":"private"}""",
        HttpStatusCode.fromValue(status),
        headersOf(HttpHeaders.ContentType, "application/problem+json"),
    )

    private inline fun <reified T> SaqzResult<T, GroupPhotoError>.success() =
        assertIs<SaqzResult.Success<T>>(this).value

    private fun SaqzResult<*, GroupPhotoError>.failure() =
        assertIs<SaqzResult.Failure<GroupPhotoError>>(this).error

    private fun SaqzResult<*, GroupPhotoError>.dataError() =
        assertIs<GroupPhotoError.DataFailure>(failure()).error

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("fake-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private data class Fixture(
        val gateway: KtorGroupPhotoGateway,
        val delays: MutableList<Long>,
    )

    private companion object {
        const val GROUP_ID = "018f4f4d-6634-7be1-a018-abcdef012345"
        const val GROUP_ETAG = "\"7\""
        const val NEXT_ETAG = "\"8\""
        const val PHOTO_ETAG = "\"photo-2\""
        val PHOTO_BYTES = byteArrayOf(1, 2, 3)
    }
}
