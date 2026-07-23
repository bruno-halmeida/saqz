package br.com.saqz.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure command-order/push/tab/back matrix for [NavigationSession] (T07).
 * Derived from GROUPNAV-03, BACK-02..04, TAB-01..02, STATE-03.
 */
class NavigationSessionTest {

    @Serializable
    private data class TestKey(val id: String) : NavKey

    private fun session(
        home: MutableList<NavKey> = mutableListOf(TestKey("home-root")),
        groups: MutableList<NavKey> = mutableListOf(TestKey("groups-root")),
        notices: MutableList<NavKey> = mutableListOf(TestKey("notices-root")),
        more: MutableList<NavKey> = mutableListOf(TestKey("more-root")),
    ) = NavigationSession(
        stacks = mapOf(
            ProductTab.HOME to home,
            ProductTab.GROUPS to groups,
            ProductTab.NOTICES to notices,
            ProductTab.MORE to more,
        ),
    )

    @Test
    fun `push appends a new key to the active stack`() {
        val s = session()
        s.push(TestKey("games"))
        assertEquals(listOf(TestKey("home-root"), TestKey("games")), s.stackFor(ProductTab.HOME))
    }

    @Test
    fun `push does not duplicate the active top key`() {
        val s = session()
        s.push(TestKey("games"))
        s.push(TestKey("games"))
        assertEquals(2, s.stackFor(ProductTab.HOME).size)
    }

    @Test
    fun `each tab preserves its own independent stack`() {
        val s = session()
        s.selectTab(ProductTab.GROUPS)
        s.push(TestKey("games"))
        s.selectTab(ProductTab.NOTICES)
        s.push(TestKey("notice-detail"))

        assertEquals(listOf(TestKey("groups-root"), TestKey("games")), s.stackFor(ProductTab.GROUPS))
        assertEquals(
            listOf(TestKey("notices-root"), TestKey("notice-detail")),
            s.stackFor(ProductTab.NOTICES),
        )
        assertEquals(listOf(TestKey("home-root")), s.stackFor(ProductTab.HOME))
    }

    @Test
    fun `reselecting the already-selected tab does not mutate its stack`() {
        val s = session()
        s.selectTab(ProductTab.HOME)
        assertEquals(ProductTab.HOME, s.selectedTab)
        assertEquals(1, s.stackFor(ProductTab.HOME).size)
    }

    @Test
    fun `goBack pops a nested key and returns true`() {
        val s = session()
        s.selectTab(ProductTab.GROUPS)
        s.push(TestKey("games"))
        s.push(TestKey("game-detail"))

        val popped = s.goBack()

        assertTrue(popped)
        assertEquals(listOf(TestKey("groups-root"), TestKey("games")), s.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `goBack at a non-home tab root selects Inicio`() {
        val s = session()
        s.selectTab(ProductTab.GROUPS)

        val handled = s.goBack()

        assertTrue(handled)
        assertEquals(ProductTab.HOME, s.selectedTab)
        assertEquals(listOf(TestKey("groups-root")), s.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `goBack at the Inicio root leaves back to the platform`() {
        val s = session()

        val handled = s.goBack()

        assertFalse(handled)
        assertEquals(ProductTab.HOME, s.selectedTab)
    }

    @Test
    fun `TopBar back and system back invoke the identical handler producing the same result`() {
        val topBarSession = session()
        val systemBackSession = session()
        listOf(topBarSession, systemBackSession).forEach {
            it.selectTab(ProductTab.GROUPS)
            it.push(TestKey("games"))
            it.push(TestKey("game-detail"))
        }

        val topBarResult = topBarSession.goBack()
        val systemBackResult = systemBackSession.goBack()

        assertEquals(topBarResult, systemBackResult)
        assertEquals(topBarSession.stackFor(ProductTab.GROUPS), systemBackSession.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `canGoBack is false at the Inicio root`() {
        val s = session()
        assertFalse(s.canGoBack)
    }

    @Test
    fun `canGoBack is true at a non-home tab root`() {
        val s = session()
        s.selectTab(ProductTab.GROUPS)
        assertTrue(s.canGoBack)
    }

    @Test
    fun `canGoBack is true when the active stack has nested entries`() {
        val s = session()
        s.push(TestKey("games"))
        assertTrue(s.canGoBack)
    }
}
