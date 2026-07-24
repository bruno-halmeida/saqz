package br.com.saqz.navigation.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import br.com.saqz.designsystem.component.SaqzTopBarBackTag
import br.com.saqz.designsystem.component.SaqzTopBarTag
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.navigation.GroupsRoute
import br.com.saqz.navigation.NavigationSession
import br.com.saqz.navigation.ProductRoute
import br.com.saqz.navigation.ProductTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * GROUPNAV-01..04, BACK-01..03: entry inventory, chrome classification, TopBar back
 * delegation, and the actual-predecessor back chain for the Groups host. Entries are
 * inventoried without composing content, so no Koin/screen bindings are needed.
 */
@OptIn(ExperimentalTestApi::class)
class GroupsNavigationHostTest {

    private val allKeys: List<NavKey> = listOf(
        ProductRoute.AppHome,
        GroupsRoute.Setup,
        GroupsRoute.Selector,
        GroupsRoute.Loading,
        GroupsRoute.LoadError,
        GroupsRoute.GroupHome,
        GroupsRoute.ProfileCompletion,
        GroupsRoute.People,
        GroupsRoute.Games,
        GroupsRoute.GameDetail("game-1"),
        GroupsRoute.GameEditor,
        GroupsRoute.Notices,
        GroupsRoute.More,
        GroupsRoute.Settings,
        GroupsRoute.Memberships,
        GroupsRoute.Invite,
        GroupsRoute.CreateGroup,
    )

    private fun session(groups: MutableList<NavKey>) = NavigationSession(
        stacks = mapOf(
            ProductTab.HOME to mutableListOf<NavKey>(ProductRoute.AppHome),
            ProductTab.GROUPS to groups,
            ProductTab.NOTICES to mutableListOf<NavKey>(GroupsRoute.Notices),
            ProductTab.MORE to mutableListOf<NavKey>(GroupsRoute.More),
        ),
        initialTab = ProductTab.GROUPS,
    )

    @Test
    fun `installs app home and all sixteen groups routes as distinct entries`() {
        val provider = entryProvider<NavKey> {
            installGroupsEntries(
                session = session(mutableListOf(GroupsRoute.GroupHome)),
                titleFor = { "" },
                bottomBar = {},
                content = {},
            )
        }

        val entries: List<NavEntry<NavKey>> = allKeys.map(provider)

        assertEquals(17, allKeys.size)
        assertEquals(17, entries.map { it.contentKey }.toSet().size, "every route maps to a distinct entry")
    }

    @Test
    fun `chrome classification matches the design`() {
        assertEquals(GroupsChrome.SELECTOR, chromeFor(ProductRoute.AppHome))
        assertEquals(GroupsChrome.SELECTOR, chromeFor(GroupsRoute.Selector))
        listOf(GroupsRoute.Setup, GroupsRoute.Loading, GroupsRoute.LoadError, GroupsRoute.CreateGroup)
            .forEach { assertEquals(GroupsChrome.BARE, chromeFor(it), "$it must be chrome-free") }
        listOf(
            GroupsRoute.GroupHome,
            GroupsRoute.ProfileCompletion,
            GroupsRoute.People,
            GroupsRoute.Games,
            GroupsRoute.GameDetail("g"),
            GroupsRoute.GameEditor,
            GroupsRoute.Notices,
            GroupsRoute.More,
            GroupsRoute.Settings,
            GroupsRoute.Memberships,
            GroupsRoute.Invite,
        ).forEach { assertEquals(GroupsChrome.SCOPED, chromeFor(it), "$it must be group-scoped") }
    }

    @Test
    fun `single membership group home is a root with no top bar back`() {
        val stack = mutableListOf<NavKey>(GroupsRoute.Loading)

        canonicalizeSelectedGroup(stack, multipleMemberships = false)

        assertEquals(listOf<NavKey>(GroupsRoute.GroupHome), stack)
        assertFalse(groupsBackVisible(stack.size))
    }

    @Test
    fun `multiple membership group home returns to selector`() {
        val stack = mutableListOf<NavKey>(GroupsRoute.Loading)

        canonicalizeSelectedGroup(stack, multipleMemberships = true)

        assertEquals(listOf<NavKey>(GroupsRoute.Selector, GroupsRoute.GroupHome), stack)
        assertTrue(groupsBackVisible(stack.size))
        val s = session(stack)
        assertTrue(s.goBack())
        assertEquals(listOf<NavKey>(GroupsRoute.Selector), s.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `canonicalize is idempotent when the stack already matches`() {
        val stack = mutableListOf<NavKey>(GroupsRoute.Selector, GroupsRoute.GroupHome)
        val reference = stack

        canonicalizeSelectedGroup(stack, multipleMemberships = true)

        assertTrue(reference === stack, "no-op must not replace the stack instance")
        assertEquals(listOf<NavKey>(GroupsRoute.Selector, GroupsRoute.GroupHome), stack)
    }

    @Test
    fun `game detail back returns games then the actual predecessor`() {
        val groups = mutableListOf<NavKey>(GroupsRoute.Selector, GroupsRoute.GroupHome)
        val s = session(groups)
        s.push(GroupsRoute.Games)
        s.push(GroupsRoute.GameDetail("game-1"))

        assertTrue(s.goBack())
        assertEquals(GroupsRoute.Games, s.stackFor(ProductTab.GROUPS).last())
        assertTrue(s.goBack())
        assertEquals(GroupsRoute.GroupHome, s.stackFor(ProductTab.GROUPS).last())
        assertTrue(s.goBack())
        assertEquals(GroupsRoute.Selector, s.stackFor(ProductTab.GROUPS).last())
    }

    @Test
    fun `game editor back returns to the games list that opened it`() {
        val groups = mutableListOf<NavKey>(GroupsRoute.Selector, GroupsRoute.GroupHome, GroupsRoute.Games)
        val s = session(groups)
        s.push(GroupsRoute.GameEditor)

        assertTrue(groupsBackVisible(s.stackFor(ProductTab.GROUPS).size))
        assertTrue(s.goBack())
        assertEquals(GroupsRoute.Games, s.stackFor(ProductTab.GROUPS).last())
    }

    @Test
    fun `scoped scaffold shows top bar back and delegates to the shared callback`() = runComposeUiTest {
        var backs = 0
        setContent {
            SaqzTheme {
                GroupsScopedScaffold(title = "Jogos", canGoBack = true, onBack = { backs++ }) {
                    Text("games-body")
                }
            }
        }

        onNodeWithTag(SaqzTopBarTag).assertIsDisplayed()
        onNodeWithTag(SaqzTopBarBackTag).performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `scoped scaffold hides back when it cannot go back`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupsScopedScaffold(title = "Grupo", canGoBack = false, onBack = {}) {
                    Text("home-body")
                }
            }
        }

        onNodeWithTag(SaqzTopBarTag).assertIsDisplayed()
        onNodeWithTag(SaqzTopBarBackTag).assertDoesNotExist()
    }

    @Test
    fun `selector scaffold renders the bottom bar and content without a top bar`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupsSelectorScaffold(
                    bottomBar = { Box(Modifier.fillMaxWidth().height(56.dp).testTag("fake-bottom-nav")) },
                    content = { Text("selector-body") },
                )
            }
        }

        onNodeWithTag("fake-bottom-nav").assertIsDisplayed()
        onNodeWithTag(SaqzTopBarTag).assertDoesNotExist()
    }
}
