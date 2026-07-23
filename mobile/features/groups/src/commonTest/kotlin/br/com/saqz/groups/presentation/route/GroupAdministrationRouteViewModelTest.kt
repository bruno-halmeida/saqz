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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Projection/intent-delegation matrix for [GroupAdministrationRouteViewModel]
 * (T13). Derived from GROUPNAV-01, LIFE-01, LIFE-03, LIFE-05.
 */
class GroupAdministrationRouteViewModelTest {

    @Test
    fun `SETTINGS mode projects the administration state directly`() = runTest {
        val administration = GroupAdministrationStateMachine(FakeGroupGateway(), FakeMembershipGateway(), this) {}
        val group = VersionedGroup(Group("g1", "Team", "UTC", 1, GroupRole.OWNER), GroupVersionToken("\"1\""))
        administration.onIntent(GroupAdministrationIntent.SetGroup(group))

        val viewModel = GroupAdministrationRouteViewModel(GroupAdministrationRouteMode.SETTINGS, administration)

        assertEquals(group, viewModel.state.value.group)
    }

    @Test
    fun `MEMBERSHIPS mode triggers LoadMemberships on construction`() = runTest {
        val membershipGateway = FakeMembershipGateway()
        val administration = GroupAdministrationStateMachine(FakeGroupGateway(), membershipGateway, this) {}
        val group = VersionedGroup(Group("g1", "Team", "UTC", 1, GroupRole.OWNER), GroupVersionToken("\"1\""))
        administration.onIntent(GroupAdministrationIntent.SetGroup(group))

        GroupAdministrationRouteViewModel(GroupAdministrationRouteMode.MEMBERSHIPS, administration)
        runCurrent()

        assertEquals(1, membershipGateway.listCalls)
    }

    @Test
    fun `SETTINGS mode does not trigger LoadMemberships`() = runTest {
        val membershipGateway = FakeMembershipGateway()
        val administration = GroupAdministrationStateMachine(FakeGroupGateway(), membershipGateway, this) {}

        GroupAdministrationRouteViewModel(GroupAdministrationRouteMode.SETTINGS, administration)
        runCurrent()

        assertEquals(0, membershipGateway.listCalls)
    }

    @Test
    fun `onIntent forwards directly to the shared administration machine`() = runTest {
        val administration = GroupAdministrationStateMachine(FakeGroupGateway(), FakeMembershipGateway(), this) {}
        val group = VersionedGroup(Group("g1", "Team", "UTC", 1, GroupRole.OWNER), GroupVersionToken("\"1\""))
        administration.onIntent(GroupAdministrationIntent.SetGroup(group))
        val viewModel = GroupAdministrationRouteViewModel(GroupAdministrationRouteMode.SETTINGS, administration)

        viewModel.onIntent(GroupAdministrationIntent.UpdateSettings("New name", "America/Sao_Paulo"))
        runCurrent()

        assertEquals("New name", viewModel.state.value.group?.group?.name)
    }

    private class FakeGroupGateway : GroupGateway {
        override suspend fun read(groupId: GroupId) = error("unused")
        override suspend fun create(command: CreateGroupCommand) = error("unused")
        override suspend fun update(command: UpdateGroupSettingsCommand): SaqzResult<VersionedGroup, br.com.saqz.groups.domain.group.GroupProfileError> =
            SaqzResult.Success(
                VersionedGroup(
                    Group(command.groupId.value, command.name, command.timeZone.id, 2, GroupRole.OWNER),
                    GroupVersionToken("\"2\""),
                ),
            )
    }

    private class FakeMembershipGateway : GroupMembershipGateway {
        var listCalls = 0
        override suspend fun listMemberships(groupId: GroupId): SaqzResult<List<GroupMembership>, GroupMembershipError> {
            listCalls += 1
            return SaqzResult.Success(emptyList())
        }
        override suspend fun changeRole(command: ChangeMembershipRoleCommand) = error("unused")
        override suspend fun rotateInvite(groupId: GroupId) = error("unused")
        override suspend fun expireInvite(groupId: GroupId): EmptyResult<GroupMembershipError> = SaqzResult.Success(Unit)
        override suspend fun redeem(code: InviteCode): SaqzResult<RedeemedMembership, GroupMembershipError> = error("unused")
    }
}
