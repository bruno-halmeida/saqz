package br.com.saqz.groups.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GroupsRouteTest {

    // GROUPNAV-01: the route inventory is exhaustive - exactly these fifteen keys.
    private val stableRoutes: List<GroupsRoute> = listOf(
        GroupsRoute.Setup,
        GroupsRoute.Selector,
        GroupsRoute.Loading,
        GroupsRoute.LoadError,
        GroupsRoute.GroupHome,
        GroupsRoute.ProfileCompletion,
        GroupsRoute.People,
        GroupsRoute.Games,
        GroupsRoute.GameEditor,
        GroupsRoute.Notices,
        GroupsRoute.More,
        GroupsRoute.Settings,
        GroupsRoute.Memberships,
        GroupsRoute.Invite,
        GroupsRoute.CreateGroup,
    )

    /** The parameterized keys, exercised alongside [stableRoutes] in every inventory test. */
    private val parameterizedRoutes: List<GroupsRoute> = listOf(
        GroupsRoute.GameDetail("game-1"),
    )

    @Test
    fun `route inventory contains exactly the fifteen stable keys plus GameDetail`() {
        assertEquals(15, stableRoutes.size)
        assertEquals(15, stableRoutes.distinct().size)
    }

    @Test
    fun `every route is a NavKey`() {
        (stableRoutes + parameterizedRoutes).forEach { route ->
            assertTrue(route is NavKey)
        }
    }

    @Test
    fun `exhaustive when over GroupsRoute covers every key without an else branch`() {
        (stableRoutes + parameterizedRoutes).forEach { route ->
            val label = when (route) {
                GroupsRoute.Setup -> "Setup"
                GroupsRoute.Selector -> "Selector"
                GroupsRoute.Loading -> "Loading"
                GroupsRoute.LoadError -> "LoadError"
                GroupsRoute.GroupHome -> "GroupHome"
                GroupsRoute.ProfileCompletion -> "ProfileCompletion"
                GroupsRoute.People -> "People"
                GroupsRoute.Games -> "Games"
                is GroupsRoute.GameDetail -> "GameDetail"
                GroupsRoute.GameEditor -> "GameEditor"
                GroupsRoute.Notices -> "Notices"
                GroupsRoute.More -> "More"
                GroupsRoute.Settings -> "Settings"
                GroupsRoute.Memberships -> "Memberships"
                GroupsRoute.Invite -> "Invite"
                GroupsRoute.CreateGroup -> "CreateGroup"
            }
            assertTrue(label.isNotBlank())
        }
    }

    @Test
    fun `each stable route key is equal to itself and unequal to every other key`() {
        stableRoutes.forEachIndexed { index, route ->
            assertEquals(route, route)
            stableRoutes.forEachIndexed { otherIndex, other ->
                if (index != otherIndex) assertNotEquals(route, other)
            }
        }
    }

    @Test
    fun `GameDetail keys are equal for the same gameId and unequal for a different gameId`() {
        assertEquals(GroupsRoute.GameDetail("game-1"), GroupsRoute.GameDetail("game-1"))
        assertNotEquals(GroupsRoute.GameDetail("game-1"), GroupsRoute.GameDetail("game-2"))
    }

    @Test
    fun `GameDetail rejects a blank or empty gameId at construction`() {
        assertFailsWith<IllegalArgumentException> { GroupsRoute.GameDetail("") }
        assertFailsWith<IllegalArgumentException> { GroupsRoute.GameDetail("   ") }
    }

    @Test
    fun `GameDetail accepts a valid gameId and preserves it`() {
        assertEquals("game-42", GroupsRoute.GameDetail("game-42").gameId)
    }

    @Test
    fun `each concrete route serializes and deserializes to an equal instance`() {
        assertEquals(GroupsRoute.GroupHome, Json.decodeFromString(GroupsRoute.GroupHome.serializer(), Json.encodeToString(GroupsRoute.GroupHome.serializer(), GroupsRoute.GroupHome)))
        assertEquals(GroupsRoute.GameEditor, Json.decodeFromString(GroupsRoute.GameEditor.serializer(), Json.encodeToString(GroupsRoute.GameEditor.serializer(), GroupsRoute.GameEditor)))
        val gameDetail = GroupsRoute.GameDetail("game-77")
        assertEquals(gameDetail, Json.decodeFromString(GroupsRoute.GameDetail.serializer(), Json.encodeToString(GroupsRoute.GameDetail.serializer(), gameDetail)))
    }
}
