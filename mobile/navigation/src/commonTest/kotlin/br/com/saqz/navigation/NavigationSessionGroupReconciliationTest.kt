package br.com.saqz.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.navigation.GroupsRoute
import br.com.saqz.groups.presentation.GroupSelectionMembership
import br.com.saqz.groups.presentation.GroupSelectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Transient/stable-root reconciliation matrix for [NavigationSession.reconcileGroupSelection]
 * (T08). Derived from GROUPNAV-06 and STATE-01..03.
 */
class NavigationSessionGroupReconciliationTest {

    private fun session(groupsRoot: NavKey = GroupsRoute.Setup) = NavigationSession(
        stacks = mapOf(
            ProductTab.HOME to mutableListOf<NavKey>(TestHomeKey),
            ProductTab.GROUPS to mutableListOf(groupsRoot),
            ProductTab.NOTICES to mutableListOf<NavKey>(TestHomeKey),
            ProductTab.MORE to mutableListOf<NavKey>(TestHomeKey),
        ),
    )

    private data object TestHomeKey : NavKey

    private val membership = GroupSelectionMembership(
        groupId = "g1",
        groupName = "Team",
        role = GroupRole.OWNER,
    )

    private val selectedGroup = VersionedGroup(
        group = Group(
            id = GroupId("g1"),
            name = "Team",
            timeZone = GroupTimeZone("America/Sao_Paulo"),
            version = 1,
            role = GroupRole.OWNER,
        ),
        versionToken = GroupVersionToken("v1"),
    )

    @Test
    fun `NoGroup reconciles the GROUPS root to Setup`() {
        val s = session(groupsRoot = GroupsRoute.Selector)
        s.reconcileGroupSelection(GroupSelectionState.NoGroup)
        assertEquals(listOf(GroupsRoute.Setup), s.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `Selector reconciles the GROUPS root to Selector`() {
        val s = session()
        s.reconcileGroupSelection(GroupSelectionState.Selector(listOf(membership)))
        assertEquals(listOf(GroupsRoute.Selector), s.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `Loading reconciles the GROUPS root to Loading`() {
        val s = session()
        s.reconcileGroupSelection(GroupSelectionState.Loading("g1"))
        assertEquals(listOf(GroupsRoute.Loading), s.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `LoadError reconciles the GROUPS root to LoadError`() {
        val s = session()
        s.reconcileGroupSelection(GroupSelectionState.LoadError("g1"))
        assertEquals(listOf(GroupsRoute.LoadError), s.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `Selected reconciles the GROUPS root to GroupHome`() {
        val s = session()
        s.reconcileGroupSelection(GroupSelectionState.Selected(selectedGroup))
        assertEquals(listOf(GroupsRoute.GroupHome), s.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `rapid Loading LoadError Loading flapping never leaves more than one root key`() {
        val s = session()
        s.reconcileGroupSelection(GroupSelectionState.Loading("g1"))
        s.reconcileGroupSelection(GroupSelectionState.LoadError("g1"))
        s.reconcileGroupSelection(GroupSelectionState.Loading("g1"))

        assertEquals(listOf(GroupsRoute.Loading), s.stackFor(ProductTab.GROUPS))
    }

    @Test
    fun `reconciling to the same state twice is idempotent and does not duplicate the root`() {
        val s = session()
        s.reconcileGroupSelection(GroupSelectionState.Loading("g1"))
        s.reconcileGroupSelection(GroupSelectionState.Loading("g1"))

        assertEquals(1, s.stackFor(ProductTab.GROUPS).size)
    }

    @Test
    fun `back can never reveal an obsolete Loading or LoadError key`() {
        val s = session()
        s.selectTab(ProductTab.GROUPS)
        s.reconcileGroupSelection(GroupSelectionState.Loading("g1"))
        s.reconcileGroupSelection(GroupSelectionState.LoadError("g1"))
        s.reconcileGroupSelection(GroupSelectionState.Selected(selectedGroup))

        // The GROUPS root was replaced in place at every step, so back at this
        // root can only leave the GROUPS tab (selecting Inicio) -- it never pops
        // into a prior Loading/LoadError entry because none were ever pushed.
        assertEquals(listOf(GroupsRoute.GroupHome), s.stackFor(ProductTab.GROUPS))
        val handled = s.goBack()
        assertEquals(ProductTab.HOME, s.selectedTab)
        assertFalse(s.stackFor(ProductTab.GROUPS).contains(GroupsRoute.Loading))
        assertFalse(s.stackFor(ProductTab.GROUPS).contains(GroupsRoute.LoadError))
        assertTrue(handled)
    }

    @Test
    fun `reconciliation is a no-op once the GROUPS stack has grown past its root`() {
        val s = session(groupsRoot = GroupsRoute.GroupHome)
        s.selectTab(ProductTab.GROUPS)
        s.push(GroupsRoute.People)

        s.reconcileGroupSelection(GroupSelectionState.Loading("g1"))

        assertEquals(listOf(GroupsRoute.GroupHome, GroupsRoute.People), s.stackFor(ProductTab.GROUPS))
    }
}
