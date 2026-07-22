package br.com.saqz.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.util.network.UnresolvedAddressException
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PrivateMediaNetworkTest {
    @Test fun `media unresolved address maps only to connectivity`() = runTest {
        val result = client(MockEngine { throw UnresolvedAddressException() }).readBinary("photo")

        assertEquals(NetworkError.Connectivity, failure(result))
    }

    @Test fun `media unrelated exception maps to unknown`() = runTest {
        val result = client(MockEngine { throw IllegalStateException("unexpected") }).readBinary("photo")

        assertEquals(NetworkError.Unknown, failure(result))
    }

    @Test fun `oversized upload is rejected before opening source or sending request`() = runTest {
        var opens = 0
        var requests = 0
        val client = client(MockEngine { requests += 1; respond("") }, maximum = 3)

        val result = client.uploadMedia(HttpMethod.Put, "api/groups/g/photo", upload("four".encodeToByteArray()) {
            opens += 1
            ByteReadChannel.Empty
        })

        assertEquals(NetworkError.PayloadTooLarge, failure(result))
        assertEquals(0, opens)
        assertEquals(0, requests)
    }

    @Test fun `upload streams multipart bytes to exact authenticated route`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/groups/g/photo", request.url.encodedPath)
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            assertTrue(request.body.toByteArray().decodeToString().contains("image-body"))
            respond("", HttpStatusCode.NoContent)
        }

        val result = client(engine).uploadMedia(HttpMethod.Put, "api/groups/g/photo", upload("image-body".encodeToByteArray()), "token")

        assertEquals(Unit, assertIs<NetworkResult.Success<Unit>>(result).value)
    }

    @Test fun `multipart carries filename media type and declared content length`() = runTest {
        val engine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("filename=\"group-photo.png\""))
            assertTrue(body.contains("Content-Type: image/png"))
            assertTrue(body.contains("Content-Length: 3"))
            respond("")
        }

        client(engine).uploadMedia(HttpMethod.Put, "photo", upload("png".encodeToByteArray()))
    }

    @Test fun `upload carries optimistic ETag as If-Match`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("\"group-v4\"", request.headers[HttpHeaders.IfMatch])
            request.body.toByteArray()
            respond("")
        }

        client(engine).uploadMedia(HttpMethod.Put, "photo", upload("png".encodeToByteArray(), etag = "\"group-v4\""))
    }

    @Test fun `multipart writer aborts a source larger than its declaration`() = runTest {
        val upload = NetworkMediaUpload("file", "photo.png", ContentType.Image.PNG, 2, openChannel = {
            ByteReadChannel("three".encodeToByteArray())
        })
        val content = BoundedMultipartContent(upload, 8)

        assertFailsWith<MediaLimitException> { content.writeTo(ByteChannel()) }
        assertTrue(content.limitExceeded)
    }

    @Test fun `upload always cancels its opened source after streaming`() = runTest {
        val source = RecordingChannel("png".encodeToByteArray())
        val engine = MockEngine { request -> request.body.toByteArray(); respond("") }

        client(engine).uploadMedia(HttpMethod.Put, "photo", upload("png".encodeToByteArray()) { source })

        assertTrue(source.cancelled)
    }

    @Test fun `authenticated upload retries once with one logical upload`() = runTest {
        var opens = 0
        val seenTokens = mutableListOf<String?>()
        val engine = MockEngine { request ->
            seenTokens += request.headers[HttpHeaders.Authorization]
            request.body.toByteArray()
            if (seenTokens.size == 1) unauthorized() else respond("")
        }
        val fixture = authenticated(engine)
        val operation = upload("png".encodeToByteArray()) { opens += 1; ByteReadChannel("png".encodeToByteArray()) }

        val result = fixture.client.uploadMedia(HttpMethod.Put, "photo", operation)

        assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals(listOf<String?>("Bearer old-token", "Bearer fresh-token"), seenTokens)
        assertEquals(2, opens)
        assertEquals(listOf(false, true), fixture.tokens.calls)
    }

    @Test fun `second unauthorized upload invalidates session once`() = runTest {
        val engine = MockEngine { request -> request.body.toByteArray(); unauthorized() }
        val fixture = authenticated(engine)

        fixture.client.uploadMedia(HttpMethod.Put, "photo", upload("png".encodeToByteArray()))

        assertEquals(1, fixture.invalidator.calls)
    }

    @Test fun `media logs contain only method path status and duration`() = runTest {
        val token = "private-token"
        val etag = "private-etag"
        val filename = "private-name.png"
        val messages = mutableListOf<String>()
        val engine = MockEngine { request -> request.body.toByteArray(); respond("") }
        val client = NetworkClient(engine, config(), NetworkLogger(messages::add))
        val upload = NetworkMediaUpload("file", filename, ContentType.Image.PNG, 3, etag) { ByteReadChannel("png".encodeToByteArray()) }

        client.uploadMedia(HttpMethod.Put, "api/groups/g/photo", upload, token)

        assertEquals(2, messages.size)
        assertEquals("request PUT /api/groups/g/photo", messages.first())
        assertTrue(messages.last().matches(Regex("response PUT /api/groups/g/photo status=200 durationMs=\\d+")))
        assertFalse(messages.any { it.contains(token) || it.contains(etag) || it.contains(filename) || it.contains("png") })
    }

    @Test fun `binary read returns bytes media type ETag and private cache metadata`() = runTest {
        val body = "private-image".encodeToByteArray()
        val engine = MockEngine { respond(body, headers = mediaHeaders(body.size)) }

        val value = assertIs<NetworkResult.Success<NetworkBinaryBody>>(client(engine).readBinary("photo")).value

        assertTrue(value.bytes.contentEquals(body))
        assertEquals("image/webp", value.contentType)
        assertEquals("\"photo-v2\"", value.etag)
        assertEquals("private, no-cache", value.cacheControl)
    }

    @Test fun `binary read sends bearer token without exposing it in result`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("Bearer read-token", request.headers[HttpHeaders.Authorization])
            respond("x".encodeToByteArray(), headers = mediaHeaders(1))
        }

        val result = client(engine).readBinary("photo", "read-token")

        assertFalse(result.toString().contains("read-token"))
    }

    @Test fun `declared oversized binary body is rejected before channel read`() = runTest {
        val engine = MockEngine {
            respond(ByteArray(9) { 1 }, headers = mediaHeaders(9))
        }

        val result = client(engine, maximum = 8).readBinary("photo")

        assertEquals(NetworkError.PayloadTooLarge, failure(result))
    }

    @Test fun `chunked oversized binary body is bounded at maximum plus one`() = runTest {
        val headers = headers { append(HttpHeaders.ContentType, "image/webp") }
        val engine = MockEngine { respond(ByteArray(9) { 1 }, headers = headers) }

        val result = client(engine, maximum = 8).readBinary("photo")

        assertEquals(NetworkError.PayloadTooLarge, failure(result))
    }

    @Test fun `binary response without media type is invalid`() = runTest {
        val engine = MockEngine { respond("x".encodeToByteArray()) }

        val result = client(engine).readBinary("photo")

        assertEquals(NetworkError.InvalidResponse, failure(result))
    }

    @Test fun `authenticated binary read refreshes once after 401`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls += 1
            if (calls == 1) unauthorized() else respond("x".encodeToByteArray(), headers = mediaHeaders(1))
        }
        val fixture = authenticated(engine)

        val result = fixture.client.readBinary("photo")

        assertIs<NetworkResult.Success<NetworkBinaryBody>>(result)
        assertEquals(listOf(false, true), fixture.tokens.calls)
    }

    @Test fun `oversized media error body is discarded and never logged`() = runTest {
        val secret = "private-error-overflow"
        val messages = mutableListOf<String>()
        val engine = MockEngine { respond(secret, HttpStatusCode.BadGateway) }
        val client = NetworkClient(engine, config(errorMaximum = 4), NetworkLogger(messages::add))

        val result = client.readBinary("photo")

        assertEquals(NetworkError.HttpStatus(502), failure(result))
        assertFalse(result.toString().contains(secret))
        assertFalse(messages.any { it.contains(secret) })
    }

    @Test fun `media cancellation remains cancellation`() = runTest {
        val engine = MockEngine { throw CancellationException("cancel fixture") }

        assertFailsWith<CancellationException> { client(engine).readBinary("photo") }
    }

    @Test fun `binary cancellation closes source and remains cancellation`() = runTest {
        val source = CancellingChannel()
        val engine = MockEngine {
            respond(source, headers = headers { append(HttpHeaders.ContentType, "image/png") })
        }

        assertFailsWith<CancellationException> { client(engine).readBinary("photo") }
        assertTrue(source.cancelled)
    }

    private fun client(engine: MockEngine, maximum: Int = 1024, errorMaximum: Int = 32) =
        NetworkClient(engine, config(maximum, errorMaximum))

    private fun config(maximum: Int = 1024, errorMaximum: Int = 32) =
        NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/", maxErrorBodyBytes = errorMaximum, maxBinaryBodyBytes = maximum)

    private fun upload(
        bytes: ByteArray,
        etag: String? = null,
        source: (() -> ByteReadChannel)? = null,
    ) = NetworkMediaUpload(
        fileName = "group-photo.png",
        contentType = ContentType.Image.PNG,
        contentLength = bytes.size.toLong(),
        etag = etag,
        openChannel = source ?: { ByteReadChannel(bytes) },
    )

    private fun mediaHeaders(length: Int): Headers = headers {
        append(HttpHeaders.ContentType, "image/webp")
        append(HttpHeaders.ContentLength, length.toString())
        append(HttpHeaders.ETag, "\"photo-v2\"")
        append(HttpHeaders.CacheControl, "private, no-cache")
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.unauthorized() = respond(
        """{"status":401,"code":"AUTHENTICATION_REQUIRED","correlationId":"corr"}""",
        HttpStatusCode.Unauthorized,
        headers { append(HttpHeaders.ContentType, "application/problem+json") },
    )

    private fun failure(result: NetworkResult<*>): NetworkError = assertIs<NetworkResult.Failure>(result).error

    private fun authenticated(engine: MockEngine): AuthFixture {
        val tokens = Tokens()
        val invalidator = Invalidator()
        return AuthFixture(AuthenticatedNetworkClient(client(engine), tokens, invalidator), tokens, invalidator)
    }

    private class Tokens : IdTokenProvider {
        val calls = mutableListOf<Boolean>()
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) {
            calls += forceRefresh
            completion(TokenResult.Available(if (forceRefresh) "fresh-token" else "old-token"))
        }
    }

    private class Invalidator : SessionInvalidator {
        var calls = 0
        override fun invalidate() { calls += 1 }
    }

    private data class AuthFixture(
        val client: AuthenticatedNetworkClient,
        val tokens: Tokens,
        val invalidator: Invalidator,
    )

    private class RecordingChannel(bytes: ByteArray) : ByteReadChannel {
        private val delegate = ByteReadChannel(bytes)
        var cancelled = false
        override val closedCause: Throwable? get() = delegate.closedCause
        override val isClosedForRead: Boolean get() = delegate.isClosedForRead
        @io.ktor.utils.io.InternalAPI
        override val readBuffer: Source get() = delegate.readBuffer
        override suspend fun awaitContent(min: Int): Boolean = delegate.awaitContent(min)
        override fun cancel(cause: Throwable?) {
            cancelled = true
            delegate.cancel(cause)
        }
    }

    private class CancellingChannel : ByteReadChannel {
        var cancelled = false
        override val closedCause: Throwable? = null
        override val isClosedForRead: Boolean = false
        @io.ktor.utils.io.InternalAPI
        override val readBuffer: Source = Buffer()
        override suspend fun awaitContent(min: Int): Boolean = throw CancellationException("source cancelled")
        override fun cancel(cause: Throwable?) { cancelled = true }
    }
}
