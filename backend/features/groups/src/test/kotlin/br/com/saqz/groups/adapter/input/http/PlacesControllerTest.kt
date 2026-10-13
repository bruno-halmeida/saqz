package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.places.PlaceDetails
import br.com.saqz.groups.application.places.PlaceSuggestion
import br.com.saqz.groups.application.places.PlacesLookup
import br.com.saqz.groups.application.places.PlacesProvider
import br.com.saqz.groups.application.places.PlacesRateLimitedException
import br.com.saqz.groups.application.places.PlacesRateLimiter
import br.com.saqz.groups.application.places.PlacesUnavailableException
import br.com.saqz.sharedkernel.RequestIdentity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlacesControllerTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }

    private val clock = MutableClock(Instant.parse("2026-10-13T12:00:10Z"))
    private val provider = object : PlacesProvider {
        override fun autocomplete(query: String, session: String) =
            listOf(PlaceSuggestion("p1", "Arena", "Rua A", "Arena, Rua A"))

        override fun details(placeId: String, session: String) = PlaceDetails(placeId, "Arena", "Rua A", -23.5, null)
    }
    private val identity = RequestIdentity("user-1")
    private val controller = PlacesController(PlacesLookup(provider, PlacesRateLimiter(clock)))

    private fun fields(block: () -> Unit) = assertFailsWith<InvalidGroupRequestException> { block() }.fieldErrors.keys

    @Test
    fun `availability reflects whether a provider is configured`() {
        assertTrue(controller.availability().enabled)
        assertEquals(false, PlacesController(PlacesLookup(null, PlacesRateLimiter(clock))).availability().enabled)
    }

    @Test
    fun `autocomplete maps suggestions`() {
        val response = controller.autocomplete(identity, " are ", "sess")

        assertEquals(PlaceSuggestionResponse("p1", "Arena", "Rua A", "Arena, Rua A"), response.suggestions.single())
    }

    @Test
    fun `autocomplete rejects short or missing query and bad session`() {
        assertEquals(setOf("q"), fields { controller.autocomplete(identity, "ar ", "s") })
        assertEquals(setOf("q"), fields { controller.autocomplete(identity, null, "s") })
        assertEquals(setOf("session"), fields { controller.autocomplete(identity, "arena", null) })
        assertEquals(setOf("session"), fields { controller.autocomplete(identity, "arena", "x".repeat(65)) })
        assertEquals(setOf("q", "session"), fields { controller.autocomplete(identity, null, null) })
    }

    @Test
    fun `details validates place id and session`() {
        assertEquals("p_1-A", controller.details(identity, "p_1-A", "s").placeId)
        assertEquals(setOf("placeId"), fields { controller.details(identity, "a/b", "s") })
        assertEquals(setOf("placeId"), fields { controller.details(identity, "a".repeat(257), "s") })
        assertEquals(setOf("placeId", "session"), fields { controller.details(identity, null, "") })
    }

    @Test
    fun `lookups without a provider are unavailable`() {
        val off = PlacesController(PlacesLookup(null, PlacesRateLimiter(clock)))

        assertFailsWith<PlacesUnavailableException> { off.autocomplete(identity, "arena", "s") }
        assertFailsWith<PlacesUnavailableException> { off.details(identity, "p1", "s") }
    }

    @Test
    fun `sixty requests per minute are shared between autocomplete and details then 429`() {
        repeat(30) { controller.autocomplete(identity, "arena", "s") }
        repeat(30) { controller.details(identity, "p1", "s") }

        val limited = assertFailsWith<PlacesRateLimitedException> { controller.autocomplete(identity, "arena", "s") }
        assertEquals(50, limited.retryAfterSeconds)
        assertFailsWith<PlacesRateLimitedException> { controller.details(identity, "p1", "s") }
        // outro usuário não é afetado
        controller.autocomplete(RequestIdentity("user-2"), "arena", "s")

        clock.now = clock.now.plus(Duration.ofSeconds(51))
        controller.autocomplete(identity, "arena", "s")
    }

    @Test
    fun `invalid requests do not consume the quota`() {
        repeat(100) { fields { controller.autocomplete(identity, "a", "s") } }

        controller.autocomplete(identity, "arena", "s")
    }

    @Test
    fun `stale windows are swept`() {
        val limiter = PlacesRateLimiter(clock)
        repeat(5) { limiter.acquire("user-$it") }
        assertEquals(5, limiter.trackedUsers())

        clock.now = clock.now.plusSeconds(120)
        limiter.acquire("other")

        assertEquals(1, limiter.trackedUsers())
    }
}
