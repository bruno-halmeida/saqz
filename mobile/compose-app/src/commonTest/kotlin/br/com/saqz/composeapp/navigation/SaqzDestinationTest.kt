package br.com.saqz.composeapp.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SaqzDestinationTest {
    @Test
    fun homeRoundTrips() {
        val encoded = Json.encodeToString(SaqzDestination.Home)
        assertEquals(SaqzDestination.Home, Json.decodeFromString<SaqzDestination.Home>(encoded))
    }

    @Test
    fun catalogRoundTrips() {
        val encoded = Json.encodeToString(SaqzDestination.Catalog)
        assertEquals(SaqzDestination.Catalog, Json.decodeFromString<SaqzDestination.Catalog>(encoded))
    }

    @Test
    fun destinationsAreDistinct() {
        assertNotEquals<SaqzDestination>(SaqzDestination.Home, SaqzDestination.Catalog)
    }

    @Test
    fun inventoryContainsExactlyHomeAndCatalog() {
        val all: List<SaqzDestination> = listOf(SaqzDestination.Home, SaqzDestination.Catalog)
        all.forEach { destination ->
            // Exhaustive when (no else) fails to compile if a destination is added/removed.
            when (destination) {
                SaqzDestination.Home -> Unit
                SaqzDestination.Catalog -> Unit
            }
        }
        assertEquals(2, all.toSet().size)
    }
}
