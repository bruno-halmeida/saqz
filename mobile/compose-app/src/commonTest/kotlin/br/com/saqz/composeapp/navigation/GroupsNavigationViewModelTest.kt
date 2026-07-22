package br.com.saqz.composeapp.navigation

import br.com.saqz.groups.data.GroupDto
import br.com.saqz.groups.data.GroupProfileStatusDto
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.VersionedGroupDto
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.navigation.GroupsDestination
import br.com.saqz.groups.presentation.navigation.GroupsNavigationAccess
import br.com.saqz.groups.presentation.navigation.GroupsNavigationEffect
import br.com.saqz.groups.presentation.navigation.GroupsNavigationIntent
import br.com.saqz.groups.presentation.navigation.GroupsNavigationState
import br.com.saqz.groups.presentation.GroupSelectionMembership
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsNavigationViewModelTest {
    @Test
    fun `no membership opens setup without group identity`() {
        val viewModel = selected(owner)
        viewModel.onIntent(GroupsNavigationIntent.Reconcile(GroupSelectionState.NoGroup, emptyList()))
        assertUnscoped(viewModel, GroupsDestination.SETUP)
    }

    @Test
    fun `empty session memberships clear a stale selected group`() {
        val viewModel = selected(owner)

        viewModel.onIntent(GroupsNavigationIntent.Reconcile(selectedState(owner), emptyList()))

        assertUnscoped(viewModel, GroupsDestination.SETUP)
        assertTrue(viewModel.state.value.memberships.isEmpty())
    }

    @Test
    fun `selector clears prior group before rendering choices`() {
        val viewModel = selected(owner)
        viewModel.onIntent(GroupsNavigationIntent.Reconcile(GroupSelectionState.Selector(memberships), memberships))
        assertUnscoped(viewModel, GroupsDestination.SELECTOR)
    }

    @Test
    fun `multiple memberships show complete group list despite restored selection`() {
        val viewModel = GroupsNavigationViewModel()

        viewModel.onIntent(
            GroupsNavigationIntent.Reconcile(
                selection = selectedState(owner),
                memberships = memberships,
            ),
        )

        assertUnscoped(viewModel, GroupsDestination.SELECTOR)
        assertEquals(listOf("group-1", "next"), viewModel.state.value.memberships.map { it.groupId })
    }

    @Test
    fun `exactly one membership opens its selected group detail`() {
        val viewModel = GroupsNavigationViewModel()

        viewModel.onIntent(
            GroupsNavigationIntent.Reconcile(
                selection = selectedState(owner),
                memberships = listOf(memberships.first()),
            ),
        )

        assertEquals(GroupsDestination.HOME, viewModel.state.value.destination)
        assertEquals(GROUP_ID, viewModel.state.value.groupId)
    }

    @Test
    fun `listed group opens detail only after matching group is selected`() {
        val viewModel = GroupsNavigationViewModel()
        viewModel.onIntent(
            GroupsNavigationIntent.Reconcile(
                selection = GroupSelectionState.Selector(memberships),
                memberships = memberships,
            ),
        )

        viewModel.onIntent(GroupsNavigationIntent.OpenGroup("next"))
        assertEquals(GroupsDestination.LOADING, viewModel.state.value.destination)
        assertEquals("next", viewModel.state.value.requestedGroupId)

        viewModel.onIntent(
            GroupsNavigationIntent.Reconcile(
                selection = selectedState(admin.copy(id = "next")),
                memberships = memberships,
            ),
        )

        assertEquals(GroupsDestination.HOME, viewModel.state.value.destination)
        assertEquals("next", viewModel.state.value.groupId)
        assertNull(viewModel.state.value.requestedGroupId)
    }

    @Test
    fun `returning from multi group detail restores the complete list`() {
        val viewModel = GroupsNavigationViewModel()
        viewModel.onIntent(
            GroupsNavigationIntent.Reconcile(
                selection = GroupSelectionState.Selector(memberships),
                memberships = memberships,
            ),
        )
        viewModel.onIntent(GroupsNavigationIntent.OpenGroup("next"))
        viewModel.onIntent(
            GroupsNavigationIntent.Reconcile(
                selection = selectedState(admin.copy(id = "next")),
                memberships = memberships,
            ),
        )

        viewModel.onIntent(GroupsNavigationIntent.OpenGroups)

        assertUnscoped(viewModel, GroupsDestination.SELECTOR)
        assertEquals(listOf("group-1", "next"), viewModel.state.value.memberships.map { it.groupId })
    }

    @Test
    fun `switch loading clears prior group instead of flashing it`() {
        val viewModel = selected(owner)
        viewModel.onIntent(
            GroupsNavigationIntent.Reconcile(
                GroupSelectionState.Loading("next"),
                listOf(GroupSelectionMembership("next", "Next", "ADMIN")),
            ),
        )
        assertUnscoped(viewModel, GroupsDestination.LOADING)
    }

    @Test
    fun `membership loss error clears prior group identity`() {
        val viewModel = selected(owner)
        viewModel.onIntent(
            GroupsNavigationIntent.Reconcile(
                GroupSelectionState.LoadError("removed"),
                listOf(GroupSelectionMembership("removed", "Removed", "OWNER")),
            ),
        )
        assertUnscoped(viewModel, GroupsDestination.LOAD_ERROR)
    }

    @Test
    fun `complete owner opens private home with organizer navigation`() {
        val state = selected(owner).state.value
        assertEquals(GroupsDestination.HOME, state.destination)
        assertTrue(state.access.showPeople)
        assertTrue(state.access.showGames)
        assertTrue(state.access.showFinance)
        assertTrue(state.access.canMutateOperations)
    }

    @Test
    fun `complete admin opens private home with organizer navigation`() {
        val state = selected(admin).state.value
        assertEquals(GroupsDestination.HOME, state.destination)
        assertTrue(state.access.showPeople)
        assertTrue(state.access.canMutateOperations)
        assertEquals(GroupsDestination.FINANCE, state.access.financeDestination)
    }

    @Test
    fun `athlete hides people and cannot mutate operations`() {
        val state = selected(athlete).state.value
        assertFalse(state.access.showPeople)
        assertTrue(state.access.showGames)
        assertFalse(state.access.canMutateOperations)
    }

    @Test
    fun `athlete finance resolves to own charges only`() {
        val viewModel = selected(athlete)
        viewModel.onIntent(GroupsNavigationIntent.OpenFinance)
        assertEquals(GroupsDestination.OWN_CHARGES, viewModel.state.value.destination)
    }

    @Test
    fun `owner finance resolves to organizer finance`() {
        val viewModel = selected(owner)
        viewModel.onIntent(GroupsNavigationIntent.OpenFinance)
        assertEquals(GroupsDestination.FINANCE, viewModel.state.value.destination)
    }

    @Test
    fun `any role can open notices`() {
        val viewModel = selected(athlete)
        viewModel.onIntent(GroupsNavigationIntent.OpenNotices)
        assertEquals(GroupsDestination.NOTICES, viewModel.state.value.destination)
    }

    @Test
    fun `any role can open more`() {
        val viewModel = selected(athlete)
        viewModel.onIntent(GroupsNavigationIntent.OpenMore)
        assertEquals(GroupsDestination.MORE, viewModel.state.value.destination)
    }

    @Test
    fun `single membership still opens the selector from groups tab`() {
        val viewModel = selected(owner)
        viewModel.onIntent(GroupsNavigationIntent.OpenGroups)
        assertUnscoped(viewModel, GroupsDestination.SELECTOR)
        assertEquals(listOf(GROUP_ID), viewModel.state.value.memberships.map { it.groupId })
    }

    @Test
    fun `incomplete owner is routed to profile completion readably`() {
        val state = selected(owner.copy(profileStatus = GroupProfileStatusDto.INCOMPLETE)).state.value
        assertEquals(GroupsDestination.PROFILE_COMPLETION, state.destination)
        assertTrue(state.access.canCompleteProfile)
        assertFalse(state.access.canMutateOperations)
        assertEquals(GROUP_ID, state.groupId)
    }

    @Test
    fun `incomplete admin is routed to profile completion with mutations blocked`() {
        val state = selected(admin.copy(profileStatus = GroupProfileStatusDto.INCOMPLETE)).state.value
        assertEquals(GroupsDestination.PROFILE_COMPLETION, state.destination)
        assertTrue(state.access.canCompleteProfile)
        assertFalse(state.access.canMutateOperations)
    }

    @Test
    fun `incomplete athlete stays readable without completion edit`() {
        val state = selected(athlete.copy(profileStatus = GroupProfileStatusDto.INCOMPLETE)).state.value
        assertEquals(GroupsDestination.HOME, state.destination)
        assertFalse(state.access.canCompleteProfile)
        assertFalse(state.access.canMutateOperations)
    }

    @Test
    fun `athlete cannot open people route`() {
        val viewModel = selected(athlete)
        viewModel.onIntent(GroupsNavigationIntent.OpenPeople)
        assertEquals(GroupsDestination.HOME, viewModel.state.value.destination)
    }

    @Test
    fun `all roles can open games and an exact game detail`() {
        val viewModel = selected(athlete)
        viewModel.onIntent(GroupsNavigationIntent.OpenGames)
        viewModel.onIntent(GroupsNavigationIntent.OpenGameDetail("game-7"))
        assertEquals(GroupsDestination.GAME_DETAIL, viewModel.state.value.destination)
        assertEquals("game-7", viewModel.state.value.gameId)
    }

    @Test
    fun `blank game identity never creates a detail route`() {
        val viewModel = selected(owner)
        viewModel.onIntent(GroupsNavigationIntent.OpenGameDetail("  "))
        assertEquals(GroupsDestination.HOME, viewModel.state.value.destination)
        assertNull(viewModel.state.value.gameId)
    }

    @Test
    fun `new selected group replaces destination and old game identity atomically`() {
        val viewModel = selected(owner)
        viewModel.onIntent(GroupsNavigationIntent.OpenGameDetail("old-game"))
        viewModel.onIntent(
            GroupsNavigationIntent.Reconcile(
                selectedState(admin.copy(id = "next")),
                listOf(GroupSelectionMembership("next", "Next", "ADMIN")),
            ),
        )
        assertEquals("next", viewModel.state.value.groupId)
        assertEquals(GroupsDestination.HOME, viewModel.state.value.destination)
        assertNull(viewModel.state.value.gameId)
    }

    @Test
    fun `same selected group refresh preserves allowed route without duplicate effect`() = runTest {
        val viewModel = GroupsNavigationViewModel()
        val first = async { viewModel.effects.firstEffect() }
        viewModel.onIntent(GroupsNavigationIntent.Reconcile(selectedState(owner), listOf(membership(owner))))
        runCurrent()
        assertEquals(GroupsDestination.HOME, first.await().destination)
        viewModel.onIntent(GroupsNavigationIntent.OpenGames)
        val refreshed = owner.copy(version = 2)
        viewModel.onIntent(GroupsNavigationIntent.Reconcile(selectedState(refreshed), listOf(membership(refreshed))))
        assertEquals(GroupsDestination.GAMES, viewModel.state.value.destination)
    }

    @Test
    fun `destination effects are consumed once and never replay`() = runTest {
        val viewModel = GroupsNavigationViewModel()
        val initial = async { viewModel.effects.firstEffect() }
        viewModel.onIntent(GroupsNavigationIntent.Reconcile(selectedState(owner), listOf(membership(owner))))
        runCurrent()
        assertEquals(GroupsDestination.HOME, initial.await().destination)
        val effect = async { viewModel.effects.firstEffect() }
        viewModel.onIntent(GroupsNavigationIntent.OpenGames)
        runCurrent()
        assertEquals(GroupsDestination.GAMES, effect.await().destination)
        assertEquals(GroupsDestination.GAMES, viewModel.state.value.destination)
    }

    private fun selected(group: GroupDto): GroupsNavigationViewModel = GroupsNavigationViewModel().also {
        it.onIntent(GroupsNavigationIntent.Reconcile(selectedState(group), listOf(membership(group))))
    }

    private fun selectedState(group: GroupDto) = GroupSelectionState.Selected(VersionedGroupDto(group, "etag"))

    private fun membership(group: GroupDto) = GroupSelectionMembership(group.id, group.name, group.role.name)

    private fun assertUnscoped(viewModel: GroupsNavigationViewModel, destination: GroupsDestination) {
        assertEquals(destination, viewModel.state.value.destination)
        assertNull(viewModel.state.value.groupId)
        assertNull(viewModel.state.value.gameId)
        assertEquals(GroupsNavigationAccess(), viewModel.state.value.access)
    }

    private suspend fun kotlinx.coroutines.flow.Flow<GroupsNavigationEffect>.firstEffect(): GroupsNavigationEffect.DestinationChanged =
        first() as GroupsNavigationEffect.DestinationChanged

    private companion object {
        const val GROUP_ID = "group-1"
        val owner = group(GroupRoleDto.OWNER)
        val admin = group(GroupRoleDto.ADMIN)
        val athlete = group(GroupRoleDto.ATHLETE)
        val memberships = listOf(
            GroupSelectionMembership(GROUP_ID, "Private Group", "OWNER"),
            GroupSelectionMembership("next", "Next", "ADMIN"),
        )

        fun group(role: GroupRoleDto) = GroupDto(
            id = GROUP_ID,
            name = "Private Group",
            timeZone = "America/Sao_Paulo",
            version = 1,
            role = role,
        )
    }
}
