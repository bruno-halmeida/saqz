package br.com.saqz.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [NavigationSession.pruneDisallowed] matrix (T09). Derived from AUTHZ-01..02
 * and RESTORE-04 (a restored, no-longer-authorized stack reconciles through the
 * same pruning call).
 */
class NavigationSessionPruningTest {

    private data class Key(val name: String) : NavKey

    private val groupHome = Key("GroupHome")
    private val people = Key("People")
    private val gameDetail = Key("GameDetail")
    private val selector = Key("Selector")

    private fun session(
        groups: MutableList<NavKey> = mutableListOf(groupHome),
        notices: MutableList<NavKey> = mutableListOf(Key("Notices")),
        more: MutableList<NavKey> = mutableListOf(Key("More")),
    ) = NavigationSession(
        stacks = mapOf(
            ProductTab.HOME to mutableListOf<NavKey>(Key("AppHome")),
            ProductTab.GROUPS to groups,
            ProductTab.NOTICES to notices,
            ProductTab.MORE to more,
        ),
    )

    @Test
    fun `AUTHZ-01 pops the active stack to its previous allowed route when the top is no longer allowed`() {
        val s = session(groups = mutableListOf(groupHome, people))
        s.selectTab(ProductTab.GROUPS)

        s.pruneDisallowed(
            isAllowed = { it != people },
            membershipActive = true,
            fallback = groupHome,
            membershipLostFallback = selector,
        )

        assertEquals(listOf(groupHome), s.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `AUTHZ-02 falls back to GroupHome when the active stack has no allowed predecessor`() {
        val s = session(groups = mutableListOf(people, gameDetail))
        s.selectTab(ProductTab.GROUPS)

        s.pruneDisallowed(
            isAllowed = { it != people && it != gameDetail },
            membershipActive = true,
            fallback = groupHome,
            membershipLostFallback = selector,
        )

        assertEquals(listOf(groupHome), s.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `an inactive stack is pruned exactly like the active stack`() {
        val s = session(groups = mutableListOf(groupHome, people))
        s.selectTab(ProductTab.NOTICES)

        s.pruneDisallowed(
            isAllowed = { it != people },
            membershipActive = true,
            fallback = groupHome,
            membershipLostFallback = selector,
        )

        assertEquals(listOf(groupHome), s.stackFor(ProductTab.GROUPS))
        assertEquals(ProductTab.NOTICES, s.selectedTab)
    }

    @Test
    fun `AUTHZ-02 clears the GROUPS stack and selects Grupos when membership is lost`() {
        val s = session(groups = mutableListOf(groupHome, people, gameDetail))
        s.selectTab(ProductTab.MORE)

        s.pruneDisallowed(
            isAllowed = { true },
            membershipActive = false,
            fallback = groupHome,
            membershipLostFallback = selector,
        )

        assertEquals(listOf(selector), s.stackFor(ProductTab.GROUPS))
        assertEquals(ProductTab.GROUPS, s.selectedTab)
    }

    @Test
    fun `RESTORE-04 reconciles a restored no-longer-authorized deep route the same as AUTHZ-01`() {
        // Simulates a stack restored from saved state whose deep entry (GameDetail)
        // is no longer authorized by the time the app resumes.
        val restoredGroups = mutableListOf<NavKey>(groupHome, Key("Games"), gameDetail)
        val s = session(groups = restoredGroups)
        s.selectTab(ProductTab.GROUPS)

        s.pruneDisallowed(
            isAllowed = { it != gameDetail },
            membershipActive = true,
            fallback = groupHome,
            membershipLostFallback = selector,
        )

        assertEquals(listOf(groupHome, Key("Games")), s.stackFor(ProductTab.GROUPS))
    }
}
