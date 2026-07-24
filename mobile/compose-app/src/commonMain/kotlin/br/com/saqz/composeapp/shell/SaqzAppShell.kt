package br.com.saqz.composeapp.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.Text
import br.com.saqz.composeapp.SaqzAppEnvironment
import br.com.saqz.composeapp.navigation.SaqzDestination
import br.com.saqz.composeapp.navigation.SaqzNavHost
import br.com.saqz.composeapp.navigation.navigateTopLevel
import br.com.saqz.composeapp.navigation.saqzLocalNavConfiguration
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.nav_components
import br.com.saqz.composeapp.resources.nav_home
import br.com.saqz.composeapp.toPreferences
import br.com.saqz.core.common.state.SaqzUiState
import br.com.saqz.designsystem.component.SaqzBottomNav
import br.com.saqz.designsystem.component.SaqzBottomNavItem
import br.com.saqz.designsystem.component.SaqzStateHost
import br.com.saqz.designsystem.theme.SaqzTheme

internal const val SaqzShellContentTag = "saqz-shell-content"
internal const val SaqzShellSlotTag = "saqz-shell-slot"
internal const val SaqzShellNavTag = "saqz-shell-nav"

// The accessible mobile shell: theme + a content slot (state host over the type-safe NavHost)
// + the bottom nav. The content slot takes safeDrawing (top/horizontal) plus the IME inset and
// reserves the measured nav height so the last action always clears the chrome; the bottom nav
// is the single consumer of the bottom inset. It hosts no avatar, account entry, profile or third
// destination — only Home and Componentes.
@Composable
internal fun SaqzAppShell(
    environment: SaqzAppEnvironment = SaqzAppEnvironment(),
    backStack: NavBackStack<NavKey> = rememberNavBackStack(
        saqzLocalNavConfiguration,
        SaqzDestination.Home,
    ),
) {
    SaqzTheme(preferences = environment.toPreferences()) {
        // Home is the root; a Catalog on top means Componentes is the active tab.
        val atCatalog = backStack.lastOrNull() == SaqzDestination.Catalog
        var navHeightPx by remember { mutableStateOf(0) }
        val navHeight = with(LocalDensity.current) { navHeightPx.toDp() }

        Box(modifier = Modifier.fillMaxSize()) {
            // Content viewport spans the full height (scrolls under the bottom chrome)...
            Box(modifier = Modifier.fillMaxSize().testTag(SaqzShellContentTag)) {
                SaqzStateHost(
                    state = environment.startupState,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                            ),
                        )
                        .imePadding()
                        // ...while the reserved bottom padding keeps every action reachable.
                        .padding(bottom = navHeight)
                        .testTag(SaqzShellSlotTag),
                    content = { SaqzNavHost(backStack = backStack, modifier = Modifier.fillMaxSize()) },
                )
            }
            SaqzBottomNav(
                items = listOf(
                    SaqzBottomNavItem(
                        label = stringResource(Res.string.nav_home),
                        selected = !atCatalog,
                        onClick = { backStack.navigateTopLevel(SaqzDestination.Home) },
                        icon = { Text("•") },
                    ),
                    SaqzBottomNavItem(
                        label = stringResource(Res.string.nav_components),
                        selected = atCatalog,
                        onClick = { backStack.navigateTopLevel(SaqzDestination.Catalog) },
                        icon = { Text("•") },
                    ),
                ),
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { navHeightPx = it.height }
                    .testTag(SaqzShellNavTag),
            )
        }
    }
}
