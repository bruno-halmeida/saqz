package br.com.saqz.groups.presentation.route

import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.GroupInviteUrl
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.RedeemedMembership
import br.com.saqz.groups.presentation.InviteToolStateMachine
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * State/intent-delegation matrix for [GroupInviteRouteViewModel] (T13).
 * Derived from GROUPNAV-01, LIFE-01, LIFE-03, LIFE-05, REG-01.
 */
class GroupInviteRouteViewModelTest {

    @Test
    fun `Rotate forwards to the shared invite machine and produces the url`() = runTest {
        val gateway = FakeMembershipGateway()
        gateway.rotateResult = SaqzResult.Success(GroupInviteUrl("https://saqz.example/invite/abc"))
        val invite = InviteToolStateMachine(gateway, { "g1" }, this)
        val viewModel = GroupInviteRouteViewModel(invite)

        viewModel.onIntent(GroupInviteRouteIntent.Rotate)
        runCurrent()

        assertEquals("https://saqz.example/invite/abc", viewModel.state.value.invite.inviteUrl)
    }

    @Test
    fun `ShareInvite emits a typed share request effect`() = runTest {
        val invite = InviteToolStateMachine(FakeMembershipGateway(), { "g1" }, this)
        val viewModel = GroupInviteRouteViewModel(invite)

        viewModel.onIntent(GroupInviteRouteIntent.ShareInvite("https://saqz.example/invite/abc"))
        val delivered = viewModel.effects.take(1).toList()

        assertEquals(
            listOf(GroupInviteRouteEffect.RequestShare("https://saqz.example/invite/abc")),
            delivered,
        )
    }

    @Test
    fun `expire requires confirmation before forwarding to the shared invite machine`() = runTest {
        val gateway = FakeMembershipGateway()
        val invite = InviteToolStateMachine(gateway, { "g1" }, this)
        val viewModel = GroupInviteRouteViewModel(invite)

        viewModel.onIntent(GroupInviteRouteIntent.RequestExpire)
        assertTrue(viewModel.state.value.showExpireConfirmation)
        assertEquals(0, gateway.expireCalls)

        viewModel.onIntent(GroupInviteRouteIntent.ConfirmExpire)
        runCurrent()

        assertTrue(!viewModel.state.value.showExpireConfirmation)
        assertEquals(1, gateway.expireCalls)
    }

    @Test
    fun `CancelExpire dismisses the dialog without forwarding to the shared invite machine`() = runTest {
        val gateway = FakeMembershipGateway()
        val invite = InviteToolStateMachine(gateway, { "g1" }, this)
        val viewModel = GroupInviteRouteViewModel(invite)
        viewModel.onIntent(GroupInviteRouteIntent.RequestExpire)

        viewModel.onIntent(GroupInviteRouteIntent.CancelExpire)

        assertTrue(!viewModel.state.value.showExpireConfirmation)
        assertEquals(0, gateway.expireCalls)
    }

    private class FakeMembershipGateway : GroupMembershipGateway {
        var rotateResult: SaqzResult<GroupInviteUrl, GroupMembershipError> =
            SaqzResult.Success(GroupInviteUrl("https://saqz.example/invite/default"))
        var expireCalls = 0

        override suspend fun listMemberships(groupId: GroupId): SaqzResult<List<GroupMembership>, GroupMembershipError> =
            SaqzResult.Success(emptyList())
        override suspend fun changeRole(command: ChangeMembershipRoleCommand) = error("unused")
        override suspend fun rotateInvite(groupId: GroupId): SaqzResult<GroupInviteUrl, GroupMembershipError> = rotateResult
        override suspend fun expireInvite(groupId: GroupId): EmptyResult<GroupMembershipError> {
            expireCalls += 1
            return SaqzResult.Success(Unit)
        }
        override suspend fun redeem(code: InviteCode): SaqzResult<RedeemedMembership, GroupMembershipError> = error("unused")
    }
}
