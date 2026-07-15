package br.com.saqz.core.common.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SaqzUiStateTest {
    @Test
    fun loadingIsSingleton() {
        assertSame(SaqzUiState.Loading, SaqzUiState.Loading)
    }

    @Test
    fun contentCarriesValue() {
        assertEquals(42, SaqzUiState.Content(42).value)
    }

    @Test
    fun contentIsCovariant() {
        val narrow: SaqzUiState.Content<String> = SaqzUiState.Content("saqz")
        val wide: SaqzUiState<Any> = narrow
        assertEquals("saqz", (wide as SaqzUiState.Content<Any>).value)
    }

    @Test
    fun emptyAndErrorAreDistinct() {
        assertNotEquals<SaqzUiState<Nothing>>(SaqzUiState.Empty, SaqzUiState.Error)
    }

    @Test
    fun whenCoversExactlyFourStates() {
        val states = listOf(
            SaqzUiState.Loading,
            SaqzUiState.Content("value"),
            SaqzUiState.Empty,
            SaqzUiState.Error,
        )
        val labels = states.map { state ->
            when (state) {
                is SaqzUiState.Loading -> "loading"
                is SaqzUiState.Content -> "content"
                is SaqzUiState.Empty -> "empty"
                is SaqzUiState.Error -> "error"
            }
        }
        assertEquals(listOf("loading", "content", "empty", "error"), labels)
        assertTrue(labels.toSet().size == 4)
    }
}
