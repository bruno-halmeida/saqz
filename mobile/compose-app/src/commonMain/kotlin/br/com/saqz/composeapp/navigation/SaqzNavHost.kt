package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.saqz.composeapp.catalog.SaqzCatalogScreen
import br.com.saqz.composeapp.home.SaqzHomeScreen

@Composable
fun SaqzNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = SaqzDestination.Home,
        modifier = modifier,
    ) {
        composable<SaqzDestination.Home> {
            SaqzHomeScreen(onExploreComponents = { navController.navigateTopLevel(SaqzDestination.Catalog) })
        }
        composable<SaqzDestination.Catalog> {
            SaqzCatalogScreen()
        }
    }
}

// Single top-level navigation contract shared by the Home action and (later) the bottom nav:
// one entry per destination, saved/restored state, popping back to the Home start.
internal fun NavHostController.navigateTopLevel(destination: SaqzDestination) {
    navigate(destination) {
        launchSingleTop = true
        restoreState = true
        popUpTo(SaqzDestination.Home) { saveState = true }
    }
}
