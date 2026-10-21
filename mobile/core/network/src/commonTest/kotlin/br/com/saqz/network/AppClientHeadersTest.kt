package br.com.saqz.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppClientHeadersTest {
    private val ios = AppClient(platform = "ios", version = "1.0.3", build = "14")

    @Test
    fun `every request carries platform version and build`() = runTest {
        val seen = mutableListOf<Identification>()
        val client = client(ios, seen)

        client.execute(HttpMethod.Get, "probe", serializer<Probe>())

        assertEquals(listOf(Identification("ios", "1.0.3", "14")), seen)
    }

    @Test
    fun `the header names are the ones the backend reads`() = runTest {
        var names: Set<String> = emptySet()
        val engine = MockEngine { request ->
            names = request.headers.names().filter { it.startsWith("X-Saqz-") }.toSet()
            respond("{\"value\":\"ok\"}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }

        NetworkClient(engine, config(ios)).execute(HttpMethod.Get, "probe", serializer<Probe>())

        assertEquals(setOf("X-Saqz-Platform", "X-Saqz-App-Version", "X-Saqz-App-Build"), names)
    }

    @Test
    fun `a missing build omits only the build header`() = runTest {
        val seen = mutableListOf<Identification>()
        val client = client(AppClient(platform = "android", version = "1.0.3"), seen)

        client.execute(HttpMethod.Get, "probe", serializer<Probe>())

        assertEquals(listOf(Identification("android", "1.0.3", null)), seen)
    }

    @Test
    fun `a version outside X dot Y dot Z is omitted and the call still works`() = runTest {
        listOf("web", "web (dev)", "", "1.0", "1", "1.0.3.4", "v1.0.3", "1.0.3-beta", " 1.0.3", "1.0.3 ", "1..3", "1.0.x", "-1.0.3", "1.0.3\n", "١.٠.٣")
            .forEach { version ->
                val seen = mutableListOf<Identification>()
                val client = client(AppClient("android", version, "7"), seen)

                val result = client.execute(HttpMethod.Get, "probe", serializer<Probe>())

                assertIs<NetworkResult.Success<Probe>>(result, version)
                assertEquals(listOf(Identification("android", null, "7")), seen, "version=[$version]")
            }
    }

    @Test
    fun `a version longer than 32 characters is omitted`() = runTest {
        val limit = "1." + "0".repeat(28) + ".3"
        val tooLong = "1." + "0".repeat(29) + ".3"
        assertEquals(32, limit.length)
        val seen = mutableListOf<Identification>()

        client(AppClient("ios", limit, null), seen).execute(HttpMethod.Get, "probe", serializer<Probe>())
        client(AppClient("ios", tooLong, null), seen).execute(HttpMethod.Get, "probe", serializer<Probe>())

        assertEquals(listOf(Identification("ios", limit, null), Identification("ios", null, null)), seen)
    }

    @Test
    fun `a build that is not only digits is omitted`() = runTest {
        listOf("", "abc", "14a", "1.4", "-1", " 14", "14 ", "+14", "1234567890", "٣٤").forEach { build ->
            val seen = mutableListOf<Identification>()
            val client = client(AppClient("ios", "1.0.3", build), seen)

            val result = client.execute(HttpMethod.Get, "probe", serializer<Probe>())

            assertIs<NetworkResult.Success<Probe>>(result, build)
            assertEquals(listOf(Identification("ios", "1.0.3", null)), seen, "build=[$build]")
        }
    }

    @Test
    fun `a build of up to nine digits is kept`() = runTest {
        val seen = mutableListOf<Identification>()

        client(AppClient("ios", "1.0.3", "0"), seen).execute(HttpMethod.Get, "probe", serializer<Probe>())
        client(AppClient("ios", "1.0.3", "999999999"), seen).execute(HttpMethod.Get, "probe", serializer<Probe>())

        assertEquals(listOf(Identification("ios", "1.0.3", "0"), Identification("ios", "1.0.3", "999999999")), seen)
    }

    @Test
    fun `only the two known platforms are sent`() = runTest {
        listOf("android", "ios").forEach { platform ->
            val seen = mutableListOf<Identification>()
            client(AppClient(platform, "1.0.3", "14"), seen).execute(HttpMethod.Get, "probe", serializer<Probe>())
            assertEquals(listOf(Identification(platform, "1.0.3", "14")), seen)
        }
        listOf("", "web", "Android", "IOS", "desktop", "ios ", "android;ios").forEach { platform ->
            val seen = mutableListOf<Identification>()

            val result = client(AppClient(platform, "1.0.3", "14"), seen)
                .execute(HttpMethod.Get, "probe", serializer<Probe>())

            assertIs<NetworkResult.Success<Probe>>(result, platform)
            assertEquals(listOf(Identification(null, "1.0.3", "14")), seen, "platform=[$platform]")
        }
    }

    @Test
    fun `without an app client no identification header is sent`() = runTest {
        val seen = mutableListOf<Identification>()
        val client = client(null, seen)

        client.execute(HttpMethod.Get, "probe", serializer<Probe>())

        assertEquals(listOf(Identification(null, null, null)), seen)
    }

    @Test
    fun `every kind of request goes out with the same identification`() = runTest {
        val seen = mutableListOf<Identification>()
        val engine = MockEngine { request ->
            seen += identification(request.headers)
            when (request.method) {
                HttpMethod.Delete, HttpMethod.Put -> respond("", HttpStatusCode.NoContent)
                else -> respond("{\"value\":\"ok\"}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val client = NetworkClient(engine, config(ios))
        val upload = NetworkMediaUpload("file", "photo.png", ContentType.Image.PNG, 3) { ByteReadChannel("png".encodeToByteArray()) }

        client.execute(HttpMethod.Get, "json", serializer<Probe>())
        client.executeNoContent(HttpMethod.Delete, "empty")
        client.uploadMedia(HttpMethod.Put, "photo", upload)
        client.readBinary("photo")

        assertEquals(List(4) { Identification("ios", "1.0.3", "14") }, seen)
    }

    @Test
    fun `retries and the refresh after a 401 carry the identification on every attempt`() = runTest {
        val seen = mutableListOf<Identification>()
        val engine = MockEngine { request ->
            seen += identification(request.headers)
            when {
                seen.size == 1 -> respondError(HttpStatusCode.ServiceUnavailable)
                request.headers[HttpHeaders.Authorization] == "Bearer old" -> respond(
                    """{"status":401,"code":"AUTHENTICATION_REQUIRED","correlationId":"c"}""",
                    HttpStatusCode.Unauthorized,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond("{\"value\":\"ok\"}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val tokens = object : IdTokenProvider {
            override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
                completion(TokenResult.Available(if (forceRefresh) "fresh" else "old"))
        }
        val authenticated = AuthenticatedNetworkClient(NetworkClient(engine, config(ios)), tokens, object : SessionInvalidator {
            override fun invalidate() = Unit
        })

        val result = retryTransport(RetrySafety.Read, delayMillis = {}) {
            authenticated.execute(HttpMethod.Get, "probe", serializer<Probe>())
        }

        assertIs<NetworkResult.Success<Probe>>(result)
        assertEquals(List(3) { Identification("ios", "1.0.3", "14") }, seen)
    }

    @Test
    fun `per request headers keep working next to the identification`() = runTest {
        var etag: String? = null
        var seen: Identification? = null
        val engine = MockEngine { request ->
            etag = request.headers[HttpHeaders.IfMatch]
            seen = identification(request.headers)
            respond("{\"value\":\"ok\"}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }

        NetworkClient(engine, config(ios)).execute(
            HttpMethod.Put,
            "probe",
            serializer<Probe>(),
            request = NetworkRequest(headers = mapOf(HttpHeaders.IfMatch to "\"v1\"")),
        )

        assertEquals("\"v1\"", etag)
        assertEquals(Identification("ios", "1.0.3", "14"), seen)
    }

    private data class Identification(val platform: String?, val version: String?, val build: String?)

    private fun client(app: AppClient?, seen: MutableList<Identification>): NetworkClient {
        val engine = MockEngine { request ->
            seen += identification(request.headers)
            respond("{\"value\":\"ok\"}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return NetworkClient(engine, config(app))
    }

    private fun config(app: AppClient?) = NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/", appClient = app)

    private fun identification(headers: Headers) =
        Identification(headers["X-Saqz-Platform"], headers["X-Saqz-App-Version"], headers["X-Saqz-App-Build"])

    @Serializable
    private data class Probe(val value: String)
}
