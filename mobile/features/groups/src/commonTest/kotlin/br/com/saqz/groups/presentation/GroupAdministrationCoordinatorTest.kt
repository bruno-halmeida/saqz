package br.com.saqz.groups.presentation

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.group.CreateGroupCommand
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.UpdateGroupSettingsCommand
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.domain.membership.AssignableGroupRole
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.GroupInviteUrl
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.RedeemedMembership
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupAdministrationStateMachineTest {
    @Test fun `owner receives edit roles and invite actions`() = runTest {
        assertEquals(GroupActions(true, true, true), fixture(this).withGroup(GroupRole.OWNER).machine.state.value.actions)
    }

    @Test fun `admin receives edit and invite without role administration`() = runTest {
        assertEquals(GroupActions(true, false, true), fixture(this).withGroup(GroupRole.ADMIN).machine.state.value.actions)
    }

    @Test fun `athlete receives read only actions`() = runTest {
        assertEquals(GroupActions(false, false, false), fixture(this).withGroup(GroupRole.ATHLETE).machine.state.value.actions)
    }

    @Test fun `create sends exact stable request values`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(GroupAdministrationIntent.CreateGroup(REQUEST_ID, "New Group", "Europe/Lisbon"))
        runCurrent()
        assertEquals(listOf(CreateCall(REQUEST_ID, "New Group", "Europe/Lisbon")), fixture.groups.creates)
    }

    @Test fun `created group is always selected`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(GroupAdministrationIntent.CreateGroup(REQUEST_ID, "New Group", "UTC"))
        runCurrent()
        assertEquals(listOf(GROUP_ID), fixture.selected)
    }

    @Test fun `create validation exposes exact field errors`() = runTest {
        val fixture = fixture(this)
        fixture.groups.createResult = validation("name")
        fixture.machine.onIntent(GroupAdministrationIntent.CreateGroup(REQUEST_ID, " ", "UTC"))
        runCurrent()
        assertEquals(setOf("name"), fixture.machine.state.value.fieldErrors.keys)
        assertTrue(fixture.selected.isEmpty())
    }

    @Test fun `duplicate create while pending is single flight`() = runTest {
        val fixture = fixture(this)
        fixture.groups.pendingCreate = CompletableDeferred()
        fixture.machine.onIntent(GroupAdministrationIntent.CreateGroup(REQUEST_ID, "New Group", "UTC"))
        runCurrent()
        fixture.machine.onIntent(GroupAdministrationIntent.CreateGroup(REQUEST_ID, "New Group", "UTC"))
        assertEquals(1, fixture.groups.creates.size)
        fixture.groups.pendingCreate!!.complete(SaqzResult.Success(group(GroupRole.OWNER)))
        runCurrent()
    }

    @Test fun `settings send current etag and exact values`() = runTest {
        val fixture = fixture(this).withGroup(GroupRole.OWNER)
        fixture.machine.onIntent(GroupAdministrationIntent.UpdateSettings("Renamed", "America/Sao_Paulo"))
        runCurrent()
        assertEquals(listOf(UpdateCall(GROUP_ID, "\"7\"", "Renamed", "America/Sao_Paulo")), fixture.groups.updates)
    }

    @Test fun `successful settings replace group and etag`() = runTest {
        val fixture = fixture(this).withGroup(GroupRole.ADMIN)
        fixture.groups.updateResult = SaqzResult.Success(versioned(GroupRole.ADMIN, 8))
        fixture.machine.onIntent(GroupAdministrationIntent.UpdateSettings("Renamed", "UTC"))
        runCurrent()
        assertEquals(8, fixture.machine.state.value.group!!.group.version)
        assertEquals("\"8\"", fixture.machine.state.value.group!!.versionToken.value)
    }

    @Test fun `athlete settings intent never reaches API`() = runTest {
        val fixture = fixture(this).withGroup(GroupRole.ATHLETE)
        fixture.machine.onIntent(GroupAdministrationIntent.UpdateSettings("Denied", "UTC"))
        runCurrent()
        assertTrue(fixture.groups.updates.isEmpty())
    }

    @Test fun `version conflict reloads authoritative settings`() = runTest {
        val fixture = fixture(this).withGroup(GroupRole.OWNER)
        fixture.groups.updateResult = conflict()
        fixture.groups.readResult = SaqzResult.Success(versioned(GroupRole.OWNER, 9))
        fixture.machine.onIntent(GroupAdministrationIntent.UpdateSettings("Stale", "UTC"))
        runCurrent()
        assertEquals(listOf(GROUP_ID), fixture.groups.reads)
        assertEquals(9, fixture.machine.state.value.group!!.group.version)
    }

    @Test fun `version conflict feedback remains after successful reload`() = runTest {
        val fixture = fixture(this).withGroup(GroupRole.OWNER)
        fixture.groups.updateResult = conflict()
        fixture.machine.onIntent(GroupAdministrationIntent.UpdateSettings("Stale", "UTC"))
        runCurrent()
        assertTrue(fixture.machine.state.value.versionConflict)
        assertFalse(fixture.machine.state.value.isLoading)
    }

    @Test fun `settings validation associates every returned field`() = runTest {
        val fixture = fixture(this).withGroup(GroupRole.OWNER)
        fixture.groups.updateResult = validation("name", "timeZone")
        fixture.machine.onIntent(GroupAdministrationIntent.UpdateSettings(" ", "Mars/Olympus"))
        runCurrent()
        assertEquals(setOf("name", "timeZone"), fixture.machine.state.value.fieldErrors.keys)
    }

    @Test fun `owner lists memberships`() = runTest {
        val fixture = fixture(this).withGroup(GroupRole.OWNER)
        fixture.machine.onIntent(GroupAdministrationIntent.LoadMemberships)
        runCurrent()
        assertEquals(listOf(GROUP_ID), fixture.roles.lists)
        assertEquals(2, fixture.machine.state.value.memberships.size)
    }

    @Test fun `admin cannot issue owner-only membership list`() = runTest {
        val fixture = fixture(this).withGroup(GroupRole.ADMIN)
        fixture.machine.onIntent(GroupAdministrationIntent.LoadMemberships)
        runCurrent()
        assertTrue(fixture.roles.lists.isEmpty())
    }

    @Test fun `owner changes exact target role`() = runTest {
        val fixture = fixture(this).withGroup(GroupRole.OWNER)
        fixture.machine.onIntent(GroupAdministrationIntent.LoadMemberships); runCurrent()
        fixture.machine.onIntent(GroupAdministrationIntent.ChangeRole(USER_ID, AssignableGroupRole.ADMIN)); runCurrent()
        assertEquals(listOf(RoleCall(GROUP_ID, USER_ID, AssignableGroupRole.ADMIN)), fixture.roles.changes)
    }

    @Test fun `role response updates only target membership`() = runTest {
        val fixture = fixture(this).withGroup(GroupRole.OWNER)
        fixture.machine.onIntent(GroupAdministrationIntent.LoadMemberships); runCurrent()
        fixture.machine.onIntent(GroupAdministrationIntent.ChangeRole(USER_ID, AssignableGroupRole.ADMIN)); runCurrent()
        val memberships = fixture.machine.state.value.memberships
        assertEquals(GroupRole.ADMIN, memberships.single { it.userId == USER_ID }.role)
        assertEquals(GroupRole.ADMIN, memberships.single { it.userId == OTHER_ADMIN }.role)
    }

    @Test fun `role refresh immediately governs available actions`() = runTest {
        val fixture = fixture(this).withGroup(GroupRole.OWNER)
        fixture.machine.onIntent(GroupAdministrationIntent.SetGroup(versioned(GroupRole.ATHLETE)))
        assertEquals(GroupActions(false, false, false), fixture.machine.state.value.actions)
    }

    @Test fun `duplicate role change is single flight`() = runTest {
        val fixture = fixture(this).withGroup(GroupRole.OWNER)
        fixture.roles.pendingChange = CompletableDeferred()
        fixture.machine.onIntent(GroupAdministrationIntent.ChangeRole(USER_ID, AssignableGroupRole.ADMIN)); runCurrent()
        fixture.machine.onIntent(GroupAdministrationIntent.ChangeRole(USER_ID, AssignableGroupRole.ADMIN))
        assertEquals(1, fixture.roles.changes.size)
        fixture.roles.pendingChange!!.complete(SaqzResult.Success(member(USER_ID, GroupRole.ADMIN)))
        runCurrent()
    }

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope): Fixture {
        val groups = FakeGroups()
        val roles = FakeRoles()
        val selected = mutableListOf<String>()
        return Fixture(GroupAdministrationStateMachine(groups, roles, scope, selected::add), groups, roles, selected)
    }

    private fun Fixture.withGroup(role: GroupRole) = apply {
        machine.onIntent(GroupAdministrationIntent.SetGroup(versioned(role)))
    }

    private class FakeGroups : GroupGateway {
        val creates = mutableListOf<CreateCall>(); val updates = mutableListOf<UpdateCall>(); val reads = mutableListOf<String>()
        var createResult: SaqzResult<Group, GroupProfileError> = SaqzResult.Success(group(GroupRole.OWNER))
        var updateResult: SaqzResult<VersionedGroup, GroupProfileError> = SaqzResult.Success(versioned(GroupRole.OWNER, 8))
        var readResult: SaqzResult<VersionedGroup, GroupProfileError> = SaqzResult.Success(versioned(GroupRole.OWNER, 9))
        var pendingCreate: CompletableDeferred<SaqzResult<Group, GroupProfileError>>? = null
        override suspend fun create(command: CreateGroupCommand): SaqzResult<Group, GroupProfileError> {
            creates += CreateCall(command.commandKey, command.name, command.timeZone.id)
            return pendingCreate?.await() ?: createResult
        }
        override suspend fun read(groupId: GroupId): SaqzResult<VersionedGroup, GroupProfileError> {
            reads += groupId.value
            return readResult
        }
        override suspend fun update(command: UpdateGroupSettingsCommand): SaqzResult<VersionedGroup, GroupProfileError> {
            updates += UpdateCall(command.groupId.value, command.versionToken.value, command.name, command.timeZone.id)
            return updateResult
        }
    }

    private class FakeRoles : GroupMembershipGateway {
        val lists = mutableListOf<String>(); val changes = mutableListOf<RoleCall>()
        var pendingChange: CompletableDeferred<SaqzResult<GroupMembership, GroupMembershipError>>? = null
        override suspend fun listMemberships(
            groupId: GroupId,
        ): SaqzResult<List<GroupMembership>, GroupMembershipError> {
            lists += groupId.value
            return SaqzResult.Success(
                listOf(member(USER_ID, GroupRole.ATHLETE), member(OTHER_ADMIN, GroupRole.ADMIN)),
            )
        }
        override suspend fun changeRole(
            command: ChangeMembershipRoleCommand,
        ): SaqzResult<GroupMembership, GroupMembershipError> {
            changes += RoleCall(command.groupId.value, command.userId, command.role)
            return pendingChange?.await()
                ?: SaqzResult.Success(member(command.userId, GroupRole.valueOf(command.role.name)))
        }
        override suspend fun rotateInvite(groupId: GroupId): SaqzResult<GroupInviteUrl, GroupMembershipError> = error("unused")
        override suspend fun expireInvite(groupId: GroupId): SaqzResult<Unit, GroupMembershipError> = error("unused")
        override suspend fun redeem(code: InviteCode): SaqzResult<RedeemedMembership, GroupMembershipError> = error("unused")
    }

    private data class Fixture(val machine: GroupAdministrationStateMachine, val groups: FakeGroups, val roles: FakeRoles, val selected: MutableList<String>)
    private data class CreateCall(val requestId: String, val name: String, val timeZone: String)
    private data class UpdateCall(val groupId: String, val etag: String, val name: String, val timeZone: String)
    private data class RoleCall(val groupId: String, val userId: String, val role: AssignableGroupRole)

    private companion object {
        const val GROUP_ID = "group-id"; const val USER_ID = "user-id"; const val OTHER_ADMIN = "other-admin"; const val REQUEST_ID = "request-id"
        fun group(role: GroupRole, version: Long = 7) = Group(GROUP_ID, "Group", "UTC", version, role)
        fun versioned(role: GroupRole, version: Long = 7) = VersionedGroup(
            group(role, version),
            GroupVersionToken("\"$version\""),
        )
        fun member(id: String, role: GroupRole) = GroupMembership(id, id, role)
        fun conflict(): SaqzResult<VersionedGroup, GroupProfileError> = SaqzResult.Failure(GroupProfileError.Conflict())
        fun validation(vararg fields: String): SaqzResult.Failure<GroupProfileError> = SaqzResult.Failure(
            GroupProfileError.Validation(
                ValidationDetails(
                    globalMessages = emptyList(),
                    fieldMessages = fields.associateWith { listOf("invalid") },
                ),
            ),
        )
    }
}
