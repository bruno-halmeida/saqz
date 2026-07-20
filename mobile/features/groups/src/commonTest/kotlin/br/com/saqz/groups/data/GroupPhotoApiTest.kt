package br.com.saqz.groups.data

import br.com.saqz.groups.port.EncodedGroupPhoto
import br.com.saqz.groups.port.GroupPhotoByteSource
import br.com.saqz.groups.port.GroupPhotoMediaType
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupPhotoApiTest {
    @Test fun `upload uses exact private route current etag and media type`() = runTest {
        val api = fixture { request ->
            assertEquals("PUT", request.method.value)
            assertEquals("/api/groups/$GROUP_ID/photo", request.url.encodedPath)
            assertEquals(GROUP_ETAG, request.headers[HttpHeaders.IfMatch])
            assertEquals("multipart/form-data", request.body.contentType?.withoutParameters()?.toString())
            noContent(HttpStatusCode.OK, PHOTO_ETAG)
        }

        api.upload(upload())
    }

    @Test fun `upload returns exact private photo etag`() = runTest {
        val result = fixture { noContent(HttpStatusCode.OK, PHOTO_ETAG) }.upload(upload())

        assertEquals(PHOTO_ETAG, assertIs<NetworkResult.Success<GroupPhotoReceipt>>(result).value.etag)
    }

    @Test fun `upload without response etag is invalid`() = runTest {
        val result = fixture { respond("", HttpStatusCode.OK) }.upload(upload())

        assertEquals(NetworkError.InvalidResponse, assertIs<NetworkResult.Failure>(result).error)
    }

    @Test fun `read sends conditional etag and returns bounded bytes`() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val result = fixture { request ->
            assertEquals("GET", request.method.value)
            assertEquals("/api/groups/$GROUP_ID/photo", request.url.encodedPath)
            assertEquals(PHOTO_ETAG, request.headers[HttpHeaders.IfNoneMatch])
            respond(bytes, HttpStatusCode.OK, photoHeaders())
        }.read(GROUP_ID, PHOTO_ETAG)

        val photo = assertIs<GroupPhotoReadResult.Available>(assertIs<NetworkResult.Success<GroupPhotoReadResult>>(result).value)
        assertContentEquals(bytes, photo.bytes)
        assertEquals("image/png", photo.contentType)
        assertEquals(PHOTO_ETAG, photo.etag)
    }

    @Test fun `conditional 304 maps to not modified`() = runTest {
        val result = fixture { respond("", HttpStatusCode.NotModified) }.read(GROUP_ID, PHOTO_ETAG)

        assertEquals(GroupPhotoReadResult.NotModified, assertIs<NetworkResult.Success<GroupPhotoReadResult>>(result).value)
    }

    @Test fun `read without response etag is invalid`() = runTest {
        val result = fixture {
            respond(byteArrayOf(1), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png"))
        }.read(GROUP_ID)

        assertEquals(NetworkError.InvalidResponse, assertIs<NetworkResult.Failure>(result).error)
    }

    @Test fun `remove uses exact route and mandatory current etag`() = runTest {
        val result = fixture { request ->
            assertEquals("DELETE", request.method.value)
            assertEquals("/api/groups/$GROUP_ID/photo", request.url.encodedPath)
            assertEquals(GROUP_ETAG, request.headers[HttpHeaders.IfMatch])
            respond("", HttpStatusCode.NoContent)
        }.remove(GROUP_ID, GROUP_ETAG)

        assertIs<NetworkResult.Success<Unit>>(result)
    }

    private fun upload() = GroupPhotoUploadCommand(
        GROUP_ID,
        GROUP_ETAG,
        EncodedGroupPhoto(GroupPhotoMediaType.PNG, 3, GroupPhotoByteSource { byteArrayOf(1, 2, 3) }),
    )

    private fun fixture(response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): GroupPhotoApi {
        val network = NetworkClient(MockEngine { request -> response(request) }, NetworkConfig("test", "https://api.example.test/"))
        return GroupPhotoApi(AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator()))
    }

    private fun MockRequestHandleScope.noContent(status: HttpStatusCode, etag: String) =
        respond("", status, headersOf(HttpHeaders.ETag, etag))

    private fun photoHeaders() = headersOf(
        HttpHeaders.ContentType to listOf("image/png"),
        HttpHeaders.ETag to listOf(PHOTO_ETAG),
        HttpHeaders.CacheControl to listOf("private, no-cache"),
    )

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("token"))
    }

    private class NoopInvalidator : SessionInvalidator { override fun invalidate() = Unit }

    private companion object {
        const val GROUP_ID = "018f4f4d-6634-7be1-a018-abcdef012345"
        const val GROUP_ETAG = "\"7\""
        const val PHOTO_ETAG = "\"photo-2\""
    }
}
