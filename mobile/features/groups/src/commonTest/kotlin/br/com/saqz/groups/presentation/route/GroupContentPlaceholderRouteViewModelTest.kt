package br.com.saqz.groups.presentation.route

import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.CreateGroupCommand
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.UpdateGroupSettingsCommand
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.RedeemedMembership
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupAdministrationStateMachine
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Access-projection/inertness matrix for [GroupContentPlaceholderRouteViewModel]
 * (T13). Derived from GROUPNAV-01, LIFE-01, LIFE-03, LIFE-05, REG-01.
 */
class GroupContentPlaceholderRouteViewModelTest {

    @Test
    fun `PEOPLE mode projects peopleVisible for an organizer`() = runTest {
        val administration = machine(this)
        setGroup(administration, GroupRole.OWNER)

        val viewModel = GroupContentPlaceholderRouteViewModel(GroupContentPlaceholderMode.PEOPLE, administration)

        assertTrue(viewModel.state.value.access.peopleVisible)
        assertEquals("Team", viewModel.state.value.groupName)
    }

    @Test
    fun `PEOPLE mode projects peopleVisible false for an athlete`() = runTest {
        val administration = machine(this)
        setGroup(administration, GroupRole.ATHLETE)

        val viewModel = GroupContentPlaceholderRouteViewModel(GroupContentPlaceholderMode.PEOPLE, administration)

        assertTrue(!viewModel.state.value.access.peopleVisible)
    }

    @Test
    fun `non-MORE modes ignore intents and stay inert placeholders`() = runTest {
        val administration = machine(this)
        setGroup(administration, GroupRole.OWNER)
        val viewModel = GroupContentPlaceholderRouteViewModel(GroupContentPlaceholderMode.GAMES, administration)
        val delivered = mutableListOf<GroupContentPlaceholderEffect>()
        val collector = launch { viewModel.effects.toList(delivered) }

        viewModel.onIntent(GroupContentPlaceholderIntent.OpenPeople)
        runCurrent()
        collector.cancel()

        assertTrue(delivered.isEmpty())
    }

    @Test
    fun `MORE mode emits OpenPeople effect`() = runTest {
        val administration = machine(this)
        setGroup(administration, GroupRole.OWNER)
        val viewModel = GroupContentPlaceholderRouteViewModel(GroupContentPlaceholderMode.MORE, administration)

        viewModel.onIntent(GroupContentPlaceholderIntent.OpenPeople)
        val delivered = viewModel.effects.take(1).toList()

        assertEquals(listOf(GroupContentPlaceholderEffect.OpenPeople), delivered)
    }

    @Test
    fun `MORE mode emits OpenFinance effect`() = runTest {
        val administration = machine(this)
        setGroup(administration, GroupRole.ATHLETE)
        val viewModel = GroupContentPlaceholderRouteViewModel(GroupContentPlaceholderMode.MORE, administration)

        viewModel.onIntent(GroupContentPlaceholderIntent.OpenFinance)
        val delivered = viewModel.effects.take(1).toList()

        assertEquals(listOf(GroupContentPlaceholderEffect.OpenFinance), delivered)
    }

    @Test
    fun `state updates as the shared administration machine changes`() = runTest {
        val administration = machine(this)
        val viewModel = GroupContentPlaceholderRouteViewModel(GroupContentPlaceholderMode.GAMES, administration)

        setGroup(administration, GroupRole.OWNER)
        runCurrent()

        assertEquals("Team", viewModel.state.value.groupName)
    }

    private fun machine(scope: kotlinx.coroutines.CoroutineScope) =
        GroupAdministrationStateMachine(FakeGroupGateway(), FakeMembershipGateway(), scope) {}

    private fun setGroup(machine: GroupAdministrationStateMachine, role: GroupRole) {
        machine.onIntent(
            GroupAdministrationIntent.SetGroup(
                VersionedGroup(Group("g1", "Team", "UTC", 1, role), GroupVersionToken("\"1\"")),
            ),
        )
    }

    private class FakeGroupGateway : GroupGateway {
        override suspend fun read(groupId: GroupId) = error("unused")
        override suspend fun create(command: CreateGroupCommand) = error("unused")
        override suspend fun update(command: UpdateGroupSettingsCommand) = error("unused")
    }

    private class FakeMembershipGateway : GroupMembershipGateway {
        override suspend fun listMemberships(groupId: GroupId): SaqzResult<List<GroupMembership>, GroupMembershipError> =
            SaqzResult.Success(emptyList())
        override suspend fun changeRole(command: ChangeMembershipRoleCommand) = error("unused")
        override suspend fun rotateInvite(groupId: GroupId) = error("unused")
        override suspend fun expireInvite(groupId: GroupId): EmptyResult<GroupMembershipError> = SaqzResult.Success(Unit)
        override suspend fun redeem(code: InviteCode): SaqzResult<RedeemedMembership, GroupMembershipError> = error("unused")
    }
}
