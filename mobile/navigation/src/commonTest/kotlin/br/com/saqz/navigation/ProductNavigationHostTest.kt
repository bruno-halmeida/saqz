package br.com.saqz.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.groups.navigation.GroupsRoute
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * MODNAV-01, ACCESSNAV-04, BACK-02/04, TAB-01/03, RESTORE-01: one active display, the
 * access/home/tab topology, the shared back handler, and restore-without-empty-display.
 */
@OptIn(ExperimentalTestApi::class)
class ProductNavigationHostTest {

    private class FakeOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private fun taggedProvider(): (NavKey) -> NavEntry<NavKey> = entryProvider {
        entry<AccessRoute.Login> { Tagged("access-content") }
        entry<ProductRoute.AppHome> { Tagged("home-content") }
        entry<GroupsRoute.Selector> { Tagged("groups-content") }
        entry<GroupsRoute.Games> { Tagged("groups-games-content") }
        entry<GroupsRoute.Notices> { Tagged("notices-content") }
        entry<GroupsRoute.More> { Tagged("more-content") }
    }

    private fun session(
        home: MutableList<NavKey> = mutableStateListOfKeys(ProductRoute.AppHome),
        groups: MutableList<NavKey> = mutableStateListOfKeys(GroupsRoute.Selector),
        notices: MutableList<NavKey> = mutableStateListOfKeys(GroupsRoute.Notices),
        more: MutableList<NavKey> = mutableStateListOfKeys(GroupsRoute.More),
        initialTab: ProductTab = ProductTab.HOME,
    ) = NavigationSession(
        stacks = mapOf(
            ProductTab.HOME to home,
            ProductTab.GROUPS to groups,
            ProductTab.NOTICES to notices,
            ProductTab.MORE to more,
        ),
        initialTab = initialTab,
    )

    @Test
    fun `access mode shows only the access entries`() = runComposeUiTest {
        val s = session()
        renderHost(NavigationMode.ACCESS, s, access = mutableStateListOfKeys(AccessRoute.Login))

        onNodeWithTag("access-content").assertIsDisplayed()
        onNodeWithTag("home-content").assertDoesNotExist()
    }

    @Test
    fun `home tab shows only the home entry`() = runComposeUiTest {
        val s = session(initialTab = ProductTab.HOME)
        renderHost(NavigationMode.AUTHENTICATED, s)

        onNodeWithTag("home-content").assertIsDisplayed()
        onNodeWithTag("groups-content").assertDoesNotExist()
    }

    @Test
    fun `non-home tab shows its content and root back selects inicio`() = runComposeUiTest {
        val s = session(initialTab = ProductTab.GROUPS)
        val back = renderHost(NavigationMode.AUTHENTICATED, s)

        onNodeWithTag("groups-content").assertIsDisplayed()

        runOnIdle { back() } // BACK-04: at the Grupos root, back selects Início

        onNodeWithTag("home-content").assertIsDisplayed()
        onNodeWithTag("groups-content").assertDoesNotExist()
    }

    @Test
    fun `shared back handler pops the active stack once`() = runComposeUiTest {
        val groups = mutableStateListOfKeys(GroupsRoute.Selector, GroupsRoute.Games)
        val s = session(groups = groups, initialTab = ProductTab.GROUPS)
        val back = renderHost(NavigationMode.AUTHENTICATED, s)

        onNodeWithTag("groups-games-content").assertIsDisplayed()

        runOnIdle { back() }

        assertEquals(listOf<NavKey>(GroupsRoute.Selector), s.stackFor(ProductTab.GROUPS).toList())
        onNodeWithTag("groups-content").assertIsDisplayed()
    }

    @Test
    fun `restored selected tab and stacks render without an empty display`() = runComposeUiTest {
        val groups = mutableStateListOfKeys(GroupsRoute.Selector, GroupsRoute.Games)
        val s = session(groups = groups, initialTab = ProductTab.GROUPS)
        renderHost(NavigationMode.AUTHENTICATED, s)

        // Restoration lands on Grupos with a deep stack: the top entry renders, never empty.
        onNodeWithTag("groups-games-content").assertIsDisplayed()
    }

    /** Renders the host bound to [s]; returns the shared back lambda for the test to invoke. */
    private fun androidx.compose.ui.test.ComposeUiTest.renderHost(
        mode: NavigationMode,
        s: NavigationSession,
        access: MutableList<NavKey> = mutableStateListOfKeys(AccessRoute.Login),
    ): () -> Unit {
        val provider = taggedProvider()
        var tab by mutableStateOf(s.selectedTab)
        val onBack: () -> Unit = {
            s.goBack()
            tab = s.selectedTab
        }
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides remember { FakeOwner() }) {
                ProductNavigationHost(
                    mode = mode,
                    selectedTab = tab,
                    accessBackStack = access,
                    homeBackStack = s.stackFor(ProductTab.HOME),
                    groupsBackStack = s.stackFor(ProductTab.GROUPS),
                    noticesBackStack = s.stackFor(ProductTab.NOTICES),
                    moreBackStack = s.stackFor(ProductTab.MORE),
                    entryProvider = provider,
                    onBack = onBack,
                )
            }
        }
        return onBack
    }
}

@Composable
private fun Tagged(tag: String) {
    Text(tag, modifier = Modifier.fillMaxSize().testTag(tag))
}

private fun mutableStateListOfKeys(vararg keys: NavKey): MutableList<NavKey> =
    androidx.compose.runtime.mutableStateListOf(*keys)
