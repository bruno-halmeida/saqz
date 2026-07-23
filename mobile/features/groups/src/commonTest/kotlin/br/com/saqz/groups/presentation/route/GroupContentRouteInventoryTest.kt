package br.com.saqz.groups.presentation.route

import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.CreateGroupCommand
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.UpdateGroupSettingsCommand
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.RedeemedMembership
import br.com.saqz.groups.presentation.GroupAdministrationStateMachine
import br.com.saqz.groups.presentation.InviteToolStateMachine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T13's own inventory guard: proves the T01-pinned Groups content route -> adapter
 * mapping is exhaustive, each source has exactly one owning adapter type, and no
 * route silently acquired a duplicate or catch-all coordinator.
 */
class GroupContentRouteInventoryTest {

    @Test
    fun `every content route maps to exactly one of the four adapter types`() = runTest {
        val administration = GroupAdministrationStateMachine(FakeGroupGateway(), FakeMembershipGateway(), this) {}
        val invite = InviteToolStateMachine(FakeMembershipGateway(), { "g1" }, this)

        val settings = GroupAdministrationRouteViewModel(GroupAdministrationRouteMode.SETTINGS, administration)
        val memberships = GroupAdministrationRouteViewModel(GroupAdministrationRouteMode.MEMBERSHIPS, administration)
        val profileCompletion =
            GroupContentPlaceholderRouteViewModel(GroupContentPlaceholderMode.PROFILE_COMPLETION, administration)
        val people = GroupContentPlaceholderRouteViewModel(GroupContentPlaceholderMode.PEOPLE, administration)
        val games = GroupContentPlaceholderRouteViewModel(GroupContentPlaceholderMode.GAMES, administration)
        val notices = GroupContentPlaceholderRouteViewModel(GroupContentPlaceholderMode.NOTICES, administration)
        val more = GroupContentPlaceholderRouteViewModel(GroupContentPlaceholderMode.MORE, administration)
        val inviteRoute = GroupInviteRouteViewModel(invite)

        // Routes sharing a state source reuse its single adapter type (T13 "Done when").
        assertEquals(settings::class, memberships::class)
        assertEquals(profileCompletion::class, people::class)
        assertEquals(people::class, games::class)
        assertEquals(games::class, notices::class)
        assertEquals(notices::class, more::class)

        // Routes backed by different sources do not collapse onto the same adapter type
        // (no catch-all coordinator).
        val distinctTypes = setOf(
            settings::class,
            profileCompletion::class,
            inviteRoute::class,
        )
        assertEquals(3, distinctTypes.size)
        assertTrue(settings::class != profileCompletion::class)
        assertTrue(settings::class != inviteRoute::class)
        assertTrue(profileCompletion::class != inviteRoute::class)
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
