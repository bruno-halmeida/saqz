package br.com.saqz.bootstrap.configuration.http

import br.com.saqz.access.application.clientapp.ClientAppRecordResult
import br.com.saqz.access.application.clientapp.ClientAppVersionStore
import br.com.saqz.access.application.clientapp.RecordClientApp
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.client.AppVersion
import br.com.saqz.sharedkernel.client.CLIENT_APP_REQUEST_ATTRIBUTE
import br.com.saqz.sharedkernel.client.ClientApp
import br.com.saqz.sharedkernel.client.ClientPlatform
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ClientAppFilterTest {
    private val store = RecordingStore()
    private val recordClientApp = RecordClientApp(store)
    private val filter = ClientAppFilter { recordClientApp }

    @AfterEach
    fun clean() {
        MDC.clear()
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `the three headers become the request attribute and the MDC app label`() {
        val request = request(platform = "ios", version = "1.0.3", build = "14")
        var seenInChain: String? = null

        filter.doFilter(request, MockHttpServletResponse(), FilterChain { _, _ -> seenInChain = MDC.get("app") })

        assertEquals("ios/1.0.3(14)", seenInChain)
        assertEquals(
            ClientApp.Identified(ClientPlatform.IOS, AppVersion(1, 0, 3), 14),
            request.getAttribute(CLIENT_APP_REQUEST_ATTRIBUTE),
        )
        assertEquals(request.getAttribute(CLIENT_APP_REQUEST_ATTRIBUTE), requestClientApp(request))
    }

    @Test
    fun `a request without headers is the legacy client and logs a dash`() {
        val request = request()
        var seenInChain: String? = null

        filter.doFilter(request, MockHttpServletResponse(), FilterChain { _, _ -> seenInChain = MDC.get("app") })

        assertEquals("-", seenInChain)
        assertSame(ClientApp.Legacy, requestClientApp(request))
    }

    @Test
    fun `values outside the pattern are dropped and never refuse the call`() {
        val response = MockHttpServletResponse()
        var seenInChain: String? = null

        filter.doFilter(
            request(platform = "web", version = "web (dev)", build = "abc"),
            response,
            FilterChain { _, _ -> seenInChain = MDC.get("app") },
        )

        assertEquals("web", seenInChain)
        assertEquals(200, response.status)
    }

    @Test
    fun `an unknown platform is legacy even with a valid version and build`() {
        val request = request(platform = "symbian", version = "1.0.3", build = "14")
        var seenInChain: String? = null

        filter.doFilter(request, MockHttpServletResponse(), FilterChain { _, _ -> seenInChain = MDC.get("app") })

        assertEquals("-", seenInChain)
        assertSame(ClientApp.Legacy, requestClientApp(request))
    }

    @Test
    fun `a request the filter did not see reads as legacy`() {
        assertSame(ClientApp.Legacy, requestClientApp(request(platform = "ios", version = "1.0.3")))
    }

    @Test
    fun `the version is recorded after the chain answered, for the authenticated identity`() {
        val events = mutableListOf<String>()
        store.onRecord = { events += "record" }
        val request = request(platform = "ios", version = "1.0.3", build = "14")

        filter.doFilter(request, MockHttpServletResponse(), FilterChain { _, _ ->
            authenticate("maria")
            events += "chain"
        })

        assertEquals(listOf("chain", "record"), events)
        assertEquals(
            listOf(Recorded("maria", ClientApp.Identified(ClientPlatform.IOS, AppVersion(1, 0, 3), 14))),
            store.recorded,
        )
    }

    @Test
    fun `a call that did not authenticate is not recorded`() {
        filter.doFilter(request(platform = "ios", version = "1.0.3", build = "14"), MockHttpServletResponse(), FilterChain { _, _ -> })

        assertTrue(store.recorded.isEmpty())
    }

    @Test
    fun `a principal that is not the request identity is not recorded`() {
        filter.doFilter(request(platform = "ios", version = "1.0.3"), MockHttpServletResponse(), FilterChain { _, _ ->
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken("anonymousUser", null, emptyList())
        })

        assertTrue(store.recorded.isEmpty())
    }

    @Test
    fun `the legacy client is never recorded, even when authenticated`() {
        filter.doFilter(request(), MockHttpServletResponse(), FilterChain { _, _ -> authenticate("maria") })

        assertTrue(store.recorded.isEmpty())
    }

    @Test
    fun `without a database there is nothing to record and the call still goes through`() {
        val noDatabase = ClientAppFilter { null }
        val response = MockHttpServletResponse()
        var chainRan = false

        noDatabase.doFilter(request(platform = "ios", version = "1.0.3"), response, FilterChain { _, _ ->
            authenticate("maria")
            chainRan = true
        })

        assertTrue(chainRan)
        assertEquals(200, response.status)
    }

    @Test
    fun `a failing store never breaks the call`() {
        store.failure = IllegalStateException("database down")
        val response = MockHttpServletResponse()
        var chainRuns = 0

        filter.doFilter(request(platform = "android", version = "1.0.3", build = "14"), response, FilterChain { _, res ->
            authenticate("maria")
            chainRuns++
            (res as MockHttpServletResponse).status = 201
        })

        assertEquals(1, chainRuns)
        assertEquals(201, response.status)
        assertEquals(1, store.recorded.size)
    }

    @Test
    fun `an exception from the chain still propagates and the recording failure does not hide it`() {
        store.failure = IllegalStateException("database down")

        val thrown = assertFailsWith<IllegalArgumentException> {
            filter.doFilter(request(platform = "ios", version = "1.0.3"), MockHttpServletResponse(), FilterChain { _, _ ->
                authenticate("maria")
                throw IllegalArgumentException("controller blew up")
            })
        }

        assertEquals("controller blew up", thrown.message)
    }

    @Test
    fun `repeated calls of one user reach the store once`() {
        repeat(20) {
            filter.doFilter(request(platform = "ios", version = "1.0.3", build = "14"), MockHttpServletResponse(), FilterChain { _, _ ->
                authenticate("maria")
            })
            SecurityContextHolder.clearContext()
        }

        assertEquals(1, store.recorded.size)
    }

    private fun request(platform: String? = null, version: String? = null, build: String? = null) =
        MockHttpServletRequest("GET", "/api/anything").apply {
            platform?.let { addHeader("X-Saqz-Platform", it) }
            version?.let { addHeader("X-Saqz-App-Version", it) }
            build?.let { addHeader("X-Saqz-App-Build", it) }
        }

    private fun authenticate(subject: String) {
        SecurityContextHolder.setContext(
            SecurityContextHolder.createEmptyContext().apply {
                authentication = UsernamePasswordAuthenticationToken(RequestIdentity(subject), null, emptyList())
            },
        )
    }

    private data class Recorded(val subject: String, val app: ClientApp.Identified)

    private class RecordingStore : ClientAppVersionStore {
        val recorded = mutableListOf<Recorded>()
        var failure: RuntimeException? = null
        var onRecord: () -> Unit = {}

        override fun record(
            subject: String,
            app: ClientApp.Identified,
            now: Instant,
            refreshAfter: Duration,
        ): ClientAppRecordResult {
            recorded += Recorded(subject, app)
            onRecord()
            failure?.let { throw it }
            return ClientAppRecordResult.WRITTEN
        }
    }
}
