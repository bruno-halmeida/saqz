package br.com.saqz.composeapp.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzNavHostTest {

    private fun runNav(block: androidx.compose.ui.test.ComposeUiTest.(NavHostController) -> Unit) =
        runComposeUiTest {
            lateinit var navController: NavHostController
            setContent {
                SaqzTheme {
                    navController = rememberNavController()
                    SaqzNavHost(navController = navController)
                }
            }
            waitForIdle()
            block(navController)
        }

    private fun NavHostController.atHome() =
        currentDestination?.hasRoute<SaqzDestination.Home>() == true

    private fun NavHostController.atCatalog() =
        currentDestination?.hasRoute<SaqzDestination.Catalog>() == true

    private val NavHostController.entryCount: Int
        get() = currentBackStack.value.count { it.destination.route != null }

    @Test
    fun startsAtHome() = runNav { nav ->
        assertTrue(nav.atHome(), "start destination must be Home")
    }

    @Test
    fun exploreOpensCatalog() = runNav { nav ->
        onNodeWithText("Explorar componentes").performClick()
        waitForIdle()
        assertTrue(nav.atCatalog(), "explore action must open Catalog")
    }

    @Test
    fun backReturnsHome() = runNav { nav ->
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Catalog) }
        waitForIdle()
        runOnIdle { nav.popBackStack() }
        waitForIdle()
        assertTrue(nav.atHome(), "back from Catalog must return to Home")
    }

    @Test
    fun homeReselectionIsIdempotent() = runNav { nav ->
        val before = nav.entryCount
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Home) }
        waitForIdle()
        assertTrue(nav.atHome())
        assertEquals(before, nav.entryCount, "reselecting Home must not add an entry")
    }

    @Test
    fun catalogReselectionIsIdempotent() = runNav { nav ->
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Catalog) }
        waitForIdle()
        val before = nav.entryCount
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Catalog) }
        waitForIdle()
        assertTrue(nav.atCatalog())
        assertEquals(before, nav.entryCount, "reselecting Catalog must not add an entry")
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
        assertTrue(nav.atHome())
        assertEquals(1, nav.entryCount, "repeated navigation must keep one live entry")
    }

    @Test
    fun restoreReturnsSelectedDestination() = runNav { nav ->
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Catalog) }
        waitForIdle()
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Home) }
        waitForIdle()
        runOnIdle { nav.navigateTopLevel(SaqzDestination.Catalog) }
        waitForIdle()
        assertTrue(nav.atCatalog(), "restoreState must return the saved Catalog destination")
    }

    @Test
    fun graphHasExactlyTwoDestinations() = runNav { nav ->
        val routed = nav.graph.count { it.route != null }
        assertEquals(2, routed, "graph must contain exactly Home and Catalog")
    }
}
