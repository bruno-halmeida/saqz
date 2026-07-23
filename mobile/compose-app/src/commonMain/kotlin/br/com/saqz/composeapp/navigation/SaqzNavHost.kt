package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import br.com.saqz.composeapp.catalog.SaqzCatalogScreen
import br.com.saqz.composeapp.home.SaqzHomeScreen

// MODNAV-04: the app-local Home/Catalog graph renders through Navigation Compose 3 (NavDisplay)
// over an app-owned back stack, replacing legacy navigation-compose:2.9.2. Home is the start/root;
// Catalog is pushed on top so back returns to Home (preserving the previous single-NavHost model).
@Composable
fun SaqzNavHost(
    backStack: NavBackStack<NavKey> = rememberNavBackStack(
        saqzLocalNavConfiguration,
        SaqzDestination.Home,
    ),
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.popTopLevel() },
        entryProvider = entryProvider {
            entry<SaqzDestination.Home> {
                SaqzHomeScreen(onExploreComponents = { backStack.navigateTopLevel(SaqzDestination.Catalog) })
            }
            entry<SaqzDestination.Catalog> {
                SaqzCatalogScreen()
            }
        },
        modifier = modifier,
    )
}

// Single top-level navigation contract shared by the Home action and the bottom nav: Home is the
// root, so selecting Home pops back to it; selecting Catalog pushes it once (single-top), never
// duplicating the top entry.
internal fun MutableList<NavKey>.navigateTopLevel(destination: SaqzDestination) {
    when (destination) {
        SaqzDestination.Home -> while (size > 1) removeAt(size - 1)
        SaqzDestination.Catalog -> if (lastOrNull() != SaqzDestination.Catalog) add(SaqzDestination.Catalog)
    }
}

// Back pops the top-level Catalog entry to reveal the Home root; at the Home root it is a no-op
// (returns false) so the platform handles back. Both TopBar/system back route through here.
internal fun MutableList<NavKey>.popTopLevel(): Boolean {
    if (size > 1) {
        removeAt(size - 1)
        return true
    }
    return false
}
