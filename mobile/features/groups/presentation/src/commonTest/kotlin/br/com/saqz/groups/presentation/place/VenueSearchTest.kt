package br.com.saqz.groups.presentation.place

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.place.PlaceDetails
import br.com.saqz.groups.domain.place.PlaceSuggestion
import br.com.saqz.groups.domain.place.PlacesGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupProfileGateway
import br.com.saqz.groups.presentation.gameeditor.GameEditorIntent
import br.com.saqz.groups.presentation.gameeditor.GameEditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakePlacesGateway(var enabled: Boolean = true) : PlacesGateway {
    val queries = mutableListOf<Pair<String, String>>()
    val detailCalls = mutableListOf<Pair<String, String>>()
    var suggestions = listOf(PlaceSuggestion("p1", "CERET", "Tatuapé, São Paulo", "CERET, Tatuapé"))
    var detailsResult: SaqzResult<PlaceDetails, DataError> =
        SaqzResult.Success(PlaceDetails("p1", "CERET — Quadra 2", "R. Canuto Abreu, s/n, São Paulo", -23.5, -46.6))

    override suspend fun availability(): SaqzResult<Boolean, DataError> = SaqzResult.Success(enabled)

    override suspend fun autocomplete(query: String, session: String): SaqzResult<List<PlaceSuggestion>, DataError> {
        queries += query to session
        return SaqzResult.Success(suggestions)
    }

    override suspend fun details(placeId: String, session: String): SaqzResult<PlaceDetails, DataError> {
        detailCalls += placeId to session
        return detailsResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VenueSearchTest {
    private fun TestScope.search(gateway: PlacesGateway?, sessions: Iterator<String> = generateSequence(1) { it + 1 }.map { "s$it" }.iterator()) =
        VenueSearch(CoroutineScope(StandardTestDispatcher(testScheduler)), gateway, newSession = { sessions.next() })

    @Test
    fun `without a gateway or with the backend flag off nothing is searched`() = runTest {
        val off = FakePlacesGateway(enabled = false)
        val search = search(off)
        search.refreshAvailability()
        advanceUntilIdle()
        search.onQuery("ceret quadra")
        advanceUntilIdle()

        assertFalse(search.state.value.available)
        assertTrue(off.queries.isEmpty())

        val none = search(null)
        none.refreshAvailability()
        none.onQuery("ceret quadra")
        advanceUntilIdle()
        assertFalse(none.state.value.available)
    }

    @Test
    fun `needs three characters and waits for the debounce`() = runTest {
        val gateway = FakePlacesGateway()
        val search = search(gateway)
        search.refreshAvailability()
        advanceUntilIdle()
        assertTrue(search.state.value.available)

        search.onQuery("ce")
        advanceUntilIdle()
        assertTrue(gateway.queries.isEmpty())

        search.onQuery("cer")
        advanceTimeBy(300)
        search.onQuery("cere")
        advanceTimeBy(300)
        assertTrue(gateway.queries.isEmpty())
        advanceTimeBy(100)
        advanceUntilIdle()

        assertEquals(listOf("cere" to "s1"), gateway.queries)
        assertEquals(1, search.state.value.suggestions.size)
    }

    @Test
    fun `the same session token carries the typing and the details that close it`() = runTest {
        val gateway = FakePlacesGateway()
        val search = search(gateway)
        search.refreshAvailability()
        advanceUntilIdle()
        search.onQuery("cer")
        advanceUntilIdle()
        search.onQuery("ceret")
        advanceUntilIdle()
        var picked: PlaceDetails? = null
        search.select(gateway.suggestions.first()) { picked = it }
        advanceUntilIdle()

        assertEquals(listOf("cer" to "s1", "ceret" to "s1"), gateway.queries)
        assertEquals(listOf("p1" to "s1"), gateway.detailCalls)
        assertEquals("CERET — Quadra 2", picked?.name)
        assertTrue(search.state.value.suggestions.isEmpty())

        // A sessão seguinte começa com token novo.
        search.onQuery("outra quadra")
        advanceUntilIdle()
        assertEquals("s2", gateway.queries.last().second)
    }

    @Test
    fun `a failed details keeps the suggestions and picks nothing`() = runTest {
        val gateway = FakePlacesGateway().apply { detailsResult = SaqzResult.Failure(DataError.Server) }
        val search = search(gateway)
        search.refreshAvailability()
        advanceUntilIdle()
        search.onQuery("ceret")
        advanceUntilIdle()
        var picked = false
        search.select(gateway.suggestions.first()) { picked = true }
        advanceUntilIdle()

        assertFalse(picked)
        assertEquals(1, search.state.value.suggestions.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameEditorPlacesTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `picking a suggestion fills the venue name and address`() = runTest {
        val places = FakePlacesGateway()
        val vm = GameEditorViewModel(
            "group-1", null, SavedStateHandle(), FakeGameGateway(), FakeGroupGateway(), FakeGroupProfileGateway(),
            placesGateway = places,
        )
        assertTrue(vm.state.value.venueSearch.available)

        vm.onIntent(GameEditorIntent.UpdateVenueName("ceret"))
        advanceUntilIdle()
        val suggestion = vm.state.value.venueSearch.suggestions.single()
        vm.onIntent(GameEditorIntent.SelectPlaceSuggestion(suggestion))
        advanceUntilIdle()

        assertEquals("CERET — Quadra 2", vm.state.value.form.venue?.name)
        assertEquals("R. Canuto Abreu, s/n, São Paulo", vm.state.value.form.venue?.address)
        assertTrue(vm.state.value.venueSearch.suggestions.isEmpty())
    }

    @Test
    fun `free text still works when the search is off`() = runTest {
        val vm = GameEditorViewModel(
            "group-1", null, SavedStateHandle(), FakeGameGateway(), FakeGroupGateway(), FakeGroupProfileGateway(),
            placesGateway = FakePlacesGateway(enabled = false),
        )
        vm.onIntent(GameEditorIntent.UpdateVenueName("Quadra da praça"))
        advanceUntilIdle()

        assertFalse(vm.state.value.venueSearch.available)
        assertEquals("Quadra da praça", vm.state.value.form.venue?.name)
    }
}
