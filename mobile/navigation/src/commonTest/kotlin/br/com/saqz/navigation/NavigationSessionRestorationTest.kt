package br.com.saqz.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Restoration, selected-tab, logout, and group-scope matrix (T10). Derived from
 * TAB-03 and RESTORE-01..04.
 */
class NavigationSessionRestorationTest {

    private data class Key(val name: String) : NavKey

    private val groupHome = Key("GroupHome")
    private val people = Key("People")

    private fun session(
        home: MutableList<NavKey> = mutableListOf(Key("AppHome")),
        groups: MutableList<NavKey> = mutableListOf(groupHome),
        notices: MutableList<NavKey> = mutableListOf(Key("Notices")),
        more: MutableList<NavKey> = mutableListOf(Key("More")),
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
    fun `RESTORE-01 and TAB-03 restore the selected tab together with every stack's saved content`() {
        val restoredGroups = mutableListOf<NavKey>(groupHome, people)
        val s = session(groups = restoredGroups, initialTab = ProductTab.GROUPS)

        assertEquals(ProductTab.GROUPS, s.selectedTab)
        assertEquals(listOf(groupHome, people), s.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `RESTORE-04 prunes a restored no-longer-authorized route the same as authorization pruning`() {
        val restoredGroups = mutableListOf<NavKey>(groupHome, people)
        val s = session(groups = restoredGroups, initialTab = ProductTab.GROUPS)

        s.pruneDisallowed(
            isAllowed = { it != people },
            membershipActive = true,
            fallback = groupHome,
            membershipLostFallback = Key("Selector"),
        )

        assertEquals(listOf(groupHome), s.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `RESTORE-02 clearAuthenticated resets every stack to its initial root and selects Inicio`() {
        val s = session(groups = mutableListOf(groupHome, people), initialTab = ProductTab.GROUPS)
        s.selectTab(ProductTab.GROUPS)
        s.push(Key("GameDetail"))

        s.clearAuthenticated()

        assertEquals(ProductTab.HOME, s.selectedTab)
        assertEquals(listOf(groupHome), s.stackFor(ProductTab.GROUPS))
        assertEquals(listOf(Key("AppHome")), s.stackFor(ProductTab.HOME))
        assertEquals(listOf(Key("Notices")), s.stackFor(ProductTab.NOTICES))
        assertEquals(listOf(Key("More")), s.stackFor(ProductTab.MORE))
    }

    @Test
    fun `RESTORE-03 clearGroupScope resets GROUPS NOTICES and MORE but leaves Inicio untouched`() {
        val s = session(groups = mutableListOf(groupHome, people))
        s.selectTab(ProductTab.NOTICES)
        s.push(Key("NoticeDetail"))

        s.clearGroupScope("g2")

        assertEquals(listOf(groupHome), s.stackFor(ProductTab.GROUPS))
        assertEquals(listOf(Key("Notices")), s.stackFor(ProductTab.NOTICES))
        assertEquals(listOf(Key("AppHome")), s.stackFor(ProductTab.HOME))
    }

    @Test
    fun `clearGroupScope called twice with the same groupId is idempotent`() {
        val s = session(groups = mutableListOf(groupHome, people))
        s.selectTab(ProductTab.GROUPS)

        s.clearGroupScope("g1")
        s.push(Key("PushedAfterFirstClear"))
        s.clearGroupScope("g1")

        assertEquals(
            listOf(groupHome, Key("PushedAfterFirstClear")),
            s.stackFor(ProductTab.GROUPS),
        )
    }

    @Test
    fun `clearGroupScope with a different groupId clears the stack again`() {
        val s = session(groups = mutableListOf(groupHome, people))
        s.selectTab(ProductTab.GROUPS)

        s.clearGroupScope("g1")
        s.push(Key("PushedForG1"))
        s.clearGroupScope("g2")

        assertEquals(listOf(groupHome), s.stackFor(ProductTab.GROUPS))
    }
}
