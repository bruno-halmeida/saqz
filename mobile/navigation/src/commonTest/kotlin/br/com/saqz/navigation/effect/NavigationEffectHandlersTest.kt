package br.com.saqz.navigation.effect

import androidx.navigation3.runtime.NavKey
import br.com.saqz.groups.navigation.FinanceRoute
import br.com.saqz.groups.navigation.GroupsRoute
import br.com.saqz.groups.presentation.games.editor.GameEditorEffect
import br.com.saqz.groups.presentation.games.list.GamesEffect
import br.com.saqz.groups.presentation.route.GroupContentPlaceholderEffect
import br.com.saqz.groups.presentation.route.GroupHomeRouteEffect
import br.com.saqz.groups.presentation.route.GroupSelectionRouteEffect
import br.com.saqz.navigation.NavigationSession
import br.com.saqz.navigation.ProductRoute
import br.com.saqz.navigation.ProductTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * LIFE-04, GROUPNAV-02, BACK-01: exhaustive, duplicate-safe translation of feature
 * effects into [NavigationSession] mutations, including the deferred attendance deep
 * link where `Games` is the predecessor of the opened `GameDetail`.
 */
class NavigationEffectHandlersTest {

    private fun session(
        groups: MutableList<NavKey> = mutableListOf(GroupsRoute.GroupHome),
    ) = NavigationSession(
        stacks = mapOf(
            ProductTab.HOME to mutableListOf<NavKey>(ProductRoute.AppHome),
            ProductTab.GROUPS to groups,
            ProductTab.NOTICES to mutableListOf<NavKey>(GroupsRoute.Notices),
            ProductTab.MORE to mutableListOf<NavKey>(GroupsRoute.More),
        ),
        initialTab = ProductTab.GROUPS,
    )

    @Test
    fun `attendance deep link places games before game detail so back returns to games`() {
        val s = session()

        handleOpenAttendanceGame(s, "game-42")

        assertEquals(
            listOf<NavKey>(GroupsRoute.GroupHome, GroupsRoute.Games, GroupsRoute.GameDetail("game-42")),
            s.stackFor(ProductTab.GROUPS),
        )
        assertTrue(s.goBack())
        assertEquals(GroupsRoute.Games, s.stackFor(ProductTab.GROUPS).last())
    }

    @Test
    fun `attendance deep link is duplicate safe`() {
        val s = session(mutableListOf(GroupsRoute.GroupHome, GroupsRoute.Games, GroupsRoute.GameDetail("game-42")))

        handleOpenAttendanceGame(s, "game-42")

        assertEquals(
            listOf<NavKey>(GroupsRoute.GroupHome, GroupsRoute.Games, GroupsRoute.GameDetail("game-42")),
            s.stackFor(ProductTab.GROUPS),
        )
    }

    @Test
    fun `group selection open create group pushes create group`() {
        val s = session(mutableListOf(GroupsRoute.Selector))

        handleGroupSelectionEffect(s, GroupSelectionRouteEffect.OpenCreateGroup)

        assertEquals(GroupsRoute.CreateGroup, s.stackFor(ProductTab.GROUPS).last())
    }

    @Test
    fun `content open people pushes people`() {
        val s = session()

        handleGroupContentEffect(s, GroupContentPlaceholderEffect.OpenPeople, canManageFinance = true)

        assertEquals(GroupsRoute.People, s.stackFor(ProductTab.GROUPS).last())
    }

    @Test
    fun `content open finance resolves the route by finance capability`() {
        val organizer = session()
        handleGroupContentEffect(organizer, GroupContentPlaceholderEffect.OpenFinance, canManageFinance = true)
        assertEquals(FinanceRoute.Finance, organizer.stackFor(ProductTab.GROUPS).last())

        val athlete = session()
        handleGroupContentEffect(athlete, GroupContentPlaceholderEffect.OpenFinance, canManageFinance = false)
        assertEquals(FinanceRoute.OwnCharges, athlete.stackFor(ProductTab.GROUPS).last())
    }

    @Test
    fun `group home panel effects push their route and report navigation consumed`() {
        val settings = session()
        assertTrue(handleGroupHomeEffect(settings, GroupHomeRouteEffect.OpenSettings))
        assertEquals(GroupsRoute.Settings, settings.stackFor(ProductTab.GROUPS).last())

        val memberships = session()
        assertTrue(handleGroupHomeEffect(memberships, GroupHomeRouteEffect.OpenMemberships))
        assertEquals(GroupsRoute.Memberships, memberships.stackFor(ProductTab.GROUPS).last())

        val invite = session()
        assertTrue(handleGroupHomeEffect(invite, GroupHomeRouteEffect.OpenInvite))
        assertEquals(GroupsRoute.Invite, invite.stackFor(ProductTab.GROUPS).last())
    }

    @Test
    fun `games list open game pushes the detail so back returns to the list`() {
        val s = session(mutableListOf(GroupsRoute.GroupHome, GroupsRoute.Games))

        handleGamesEffect(s, GamesEffect.OpenGame("alpha", "game-42"))

        assertEquals(GroupsRoute.GameDetail("game-42"), s.stackFor(ProductTab.GROUPS).last())
        assertTrue(s.goBack())
        assertEquals(GroupsRoute.Games, s.stackFor(ProductTab.GROUPS).last())
    }

    @Test
    fun `games list open create pushes the editor and back returns to the list`() {
        val s = session(mutableListOf(GroupsRoute.GroupHome, GroupsRoute.Games))

        handleGamesEffect(s, GamesEffect.OpenCreate("alpha"))

        assertEquals(GroupsRoute.GameEditor, s.stackFor(ProductTab.GROUPS).last())
        assertTrue(s.goBack())
        assertEquals(GroupsRoute.Games, s.stackFor(ProductTab.GROUPS).last())
    }

    @Test
    fun `game editor saved pops back to the games list that opened it`() {
        val s = session(mutableListOf(GroupsRoute.GroupHome, GroupsRoute.Games, GroupsRoute.GameEditor))

        assertTrue(handleGameEditorEffect(s, GameEditorEffect.Saved("game-42")))

        assertEquals(GroupsRoute.Games, s.stackFor(ProductTab.GROUPS).last())
    }

    @Test
    fun `game editor reload is a re-read rather than a back-stack mutation`() {
        val s = session(mutableListOf(GroupsRoute.GroupHome, GroupsRoute.GameEditor))

        assertFalse(handleGameEditorEffect(s, GameEditorEffect.Reload("alpha", "game-42")))

        assertEquals(
            listOf<NavKey>(GroupsRoute.GroupHome, GroupsRoute.GameEditor),
            s.stackFor(ProductTab.GROUPS),
        )
    }

    @Test
    fun `group home domain effects are not back-stack navigation`() {
        val switch = session()
        assertFalse(handleGroupHomeEffect(switch, GroupHomeRouteEffect.SwitchGroup))
        assertEquals(listOf<NavKey>(GroupsRoute.GroupHome), switch.stackFor(ProductTab.GROUPS))

        val logout = session()
        assertFalse(handleGroupHomeEffect(logout, GroupHomeRouteEffect.ConfirmLogout))
        assertEquals(listOf<NavKey>(GroupsRoute.GroupHome), logout.stackFor(ProductTab.GROUPS))
    }
}
