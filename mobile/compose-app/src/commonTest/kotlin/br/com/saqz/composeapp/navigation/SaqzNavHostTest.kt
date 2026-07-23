package br.com.saqz.composeapp.navigation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SaqzNavHostTest {

    private fun runNav(block: ComposeUiTest.(NavBackStack<NavKey>) -> Unit) =
        runComposeUiTest {
            lateinit var backStack: NavBackStack<NavKey>
            setContent {
                SaqzTheme {
                    backStack = rememberNavBackStack(saqzLocalNavConfiguration, SaqzDestination.Home)
                    SaqzNavHost(backStack = backStack)
                }
            }
            waitForIdle()
            block(backStack)
        }

    @Test
    fun startsAtHome() = runNav { nav ->
        assertEquals(SaqzDestination.Home, nav.last(), "start destination must be Home")
    }

    @Test
    fun exploreOpensCatalog() = runNav { nav ->
        onNodeWithText("Explorar componentes").performClick()
        waitForIdle()
        assertEquals(SaqzDestination.Catalog, nav.last(), "explore action must open Catalog")
    }

    @Test
    fun backReturnsHome() = runNav { nav ->
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Catalog) }
        waitForIdle()
        runOnIdle { nav.popTopLevel() }
        waitForIdle()
        assertEquals(SaqzDestination.Home, nav.last(), "back from Catalog must return to Home")
    }

    @Test
    fun homeReselectionIsIdempotent() = runNav { nav ->
        val before = nav.size
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Home) }
        waitForIdle()
        assertEquals(SaqzDestination.Home, nav.last())
        assertEquals(before, nav.size, "reselecting Home must not add an entry")
    }

    @Test
    fun catalogReselectionIsIdempotent() = runNav { nav ->
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Catalog) }
        waitForIdle()
        val before = nav.size
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Catalog) }
        waitForIdle()
        assertEquals(SaqzDestination.Catalog, nav.last())
        assertEquals(before, nav.size, "reselecting Catalog must not add an entry")
    }

    @Test
    fun repeatedSequenceHasNoDuplicates() = runNav { nav ->
        repeat(3) {
            runOnIdle { nav.navigateTopLevel(SaqzDestination.Catalog) }
            waitForIdle()
            runOnIdle { nav.navigateTopLevel(SaqzDestination.Home) }
            waitForIdle()
        }
        // Back at Home with a single live destination entry — no accumulation.
        assertEquals(SaqzDestination.Home, nav.last())
        assertEquals(1, nav.size, "repeated navigation must keep one live entry")
    }

    @Test
    fun restoreReturnsSelectedDestination() = runNav { nav ->
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Catalog) }
        waitForIdle()
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Home) }
        waitForIdle()
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Catalog) }
        waitForIdle()
        assertEquals(SaqzDestination.Catalog, nav.last(), "reselecting must return the Catalog destination")
    }

    @Test
    fun reachableDestinationsAreExactlyHomeAndCatalog() = runNav { nav ->
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Catalog) }
        waitForIdle()
        assertEquals<Set<NavKey>>(
            setOf(SaqzDestination.Home, SaqzDestination.Catalog),
            nav.toSet(),
            "local graph must contain exactly Home and Catalog",
        )
    }
}
