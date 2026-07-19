package br.com.saqz.groups.presentation

import br.com.saqz.groups.data.*
import br.com.saqz.network.ApiProblem
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
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
        assertEquals(GroupActions(true, true, true), fixture(this).withGroup(GroupRoleDto.OWNER).machine.state.value.actions)
    }

    @Test fun `admin receives edit and invite without role administration`() = runTest {
        assertEquals(GroupActions(true, false, true), fixture(this).withGroup(GroupRoleDto.ADMIN).machine.state.value.actions)
    }

    @Test fun `athlete receives read only actions`() = runTest {
        assertEquals(GroupActions(false, false, false), fixture(this).withGroup(GroupRoleDto.ATHLETE).machine.state.value.actions)
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
        fixture.groups.pendingCreate!!.complete(NetworkResult.Success(group(GroupRoleDto.OWNER)))
        runCurrent()
    }

    @Test fun `settings send current etag and exact values`() = runTest {
        val fixture = fixture(this).withGroup(GroupRoleDto.OWNER)
        fixture.machine.onIntent(GroupAdministrationIntent.UpdateSettings("Renamed", "America/Sao_Paulo"))
        runCurrent()
        assertEquals(listOf(UpdateCall(GROUP_ID, "\"7\"", "Renamed", "America/Sao_Paulo")), fixture.groups.updates)
    }

    @Test fun `successful settings replace group and etag`() = runTest {
        val fixture = fixture(this).withGroup(GroupRoleDto.ADMIN)
        fixture.groups.updateResult = NetworkResult.Success(versioned(GroupRoleDto.ADMIN, 8))
        fixture.machine.onIntent(GroupAdministrationIntent.UpdateSettings("Renamed", "UTC"))
        runCurrent()
        assertEquals(8, fixture.machine.state.value.group!!.group.version)
        assertEquals("\"8\"", fixture.machine.state.value.group!!.etag)
    }

    @Test fun `athlete settings intent never reaches API`() = runTest {
        val fixture = fixture(this).withGroup(GroupRoleDto.ATHLETE)
        fixture.machine.onIntent(GroupAdministrationIntent.UpdateSettings("Denied", "UTC"))
        runCurrent()
        assertTrue(fixture.groups.updates.isEmpty())
    }

    @Test fun `version conflict reloads authoritative settings`() = runTest {
        val fixture = fixture(this).withGroup(GroupRoleDto.OWNER)
        fixture.groups.updateResult = problem(409, "VERSION_CONFLICT")
        fixture.groups.readResult = NetworkResult.Success(versioned(GroupRoleDto.OWNER, 9))
        fixture.machine.onIntent(GroupAdministrationIntent.UpdateSettings("Stale", "UTC"))
        runCurrent()
        assertEquals(listOf(GROUP_ID), fixture.groups.reads)
        assertEquals(9, fixture.machine.state.value.group!!.group.version)
    }

    @Test fun `version conflict feedback remains after successful reload`() = runTest {
        val fixture = fixture(this).withGroup(GroupRoleDto.OWNER)
        fixture.groups.updateResult = problem(409, "VERSION_CONFLICT")
        fixture.machine.onIntent(GroupAdministrationIntent.UpdateSettings("Stale", "UTC"))
        runCurrent()
        assertTrue(fixture.machine.state.value.versionConflict)
        assertFalse(fixture.machine.state.value.isLoading)
    }

    @Test fun `settings validation associates every returned field`() = runTest {
        val fixture = fixture(this).withGroup(GroupRoleDto.OWNER)
        fixture.groups.updateResult = validation("name", "timeZone")
        fixture.machine.onIntent(GroupAdministrationIntent.UpdateSettings(" ", "Mars/Olympus"))
        runCurrent()
        assertEquals(setOf("name", "timeZone"), fixture.machine.state.value.fieldErrors.keys)
    }

    @Test fun `owner lists memberships`() = runTest {
        val fixture = fixture(this).withGroup(GroupRoleDto.OWNER)
        fixture.machine.onIntent(GroupAdministrationIntent.LoadMemberships)
        runCurrent()
        assertEquals(listOf(GROUP_ID), fixture.roles.lists)
        assertEquals(2, fixture.machine.state.value.memberships.size)
    }

    @Test fun `admin cannot issue owner-only membership list`() = runTest {
        val fixture = fixture(this).withGroup(GroupRoleDto.ADMIN)
        fixture.machine.onIntent(GroupAdministrationIntent.LoadMemberships)
        runCurrent()
        assertTrue(fixture.roles.lists.isEmpty())
    }

    @Test fun `owner changes exact target role`() = runTest {
        val fixture = fixture(this).withGroup(GroupRoleDto.OWNER)
        fixture.machine.onIntent(GroupAdministrationIntent.LoadMemberships); runCurrent()
        fixture.machine.onIntent(GroupAdministrationIntent.ChangeRole(USER_ID, PersistedRoleDto.ADMIN)); runCurrent()
        assertEquals(listOf(RoleCall(GROUP_ID, USER_ID, PersistedRoleDto.ADMIN)), fixture.roles.changes)
    }

    @Test fun `role response updates only target membership`() = runTest {
        val fixture = fixture(this).withGroup(GroupRoleDto.OWNER)
        fixture.machine.onIntent(GroupAdministrationIntent.LoadMemberships); runCurrent()
        fixture.machine.onIntent(GroupAdministrationIntent.ChangeRole(USER_ID, PersistedRoleDto.ADMIN)); runCurrent()
        val memberships = fixture.machine.state.value.memberships
        assertEquals(GroupRoleDto.ADMIN, memberships.single { it.userId == USER_ID }.role)
        assertEquals(GroupRoleDto.ADMIN, memberships.single { it.userId == OTHER_ADMIN }.role)
    }

    @Test fun `role refresh immediately governs available actions`() = runTest {
        val fixture = fixture(this).withGroup(GroupRoleDto.OWNER)
        fixture.machine.onIntent(GroupAdministrationIntent.SetGroup(versioned(GroupRoleDto.ATHLETE)))
        assertEquals(GroupActions(false, false, false), fixture.machine.state.value.actions)
    }

    @Test fun `duplicate role change is single flight`() = runTest {
        val fixture = fixture(this).withGroup(GroupRoleDto.OWNER)
        fixture.roles.pendingChange = CompletableDeferred()
        fixture.machine.onIntent(GroupAdministrationIntent.ChangeRole(USER_ID, PersistedRoleDto.ADMIN)); runCurrent()
        fixture.machine.onIntent(GroupAdministrationIntent.ChangeRole(USER_ID, PersistedRoleDto.ADMIN))
        assertEquals(1, fixture.roles.changes.size)
        fixture.roles.pendingChange!!.complete(NetworkResult.Success(member(USER_ID, GroupRoleDto.ADMIN)))
        runCurrent()
    }

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope): Fixture {
        val groups = FakeGroups()
        val roles = FakeRoles()
        val selected = mutableListOf<String>()
        return Fixture(GroupAdministrationStateMachine(groups, roles, scope, selected::add), groups, roles, selected)
    }

    private fun Fixture.withGroup(role: GroupRoleDto) = apply {
        machine.onIntent(GroupAdministrationIntent.SetGroup(versioned(role)))
    }

    private class FakeGroups : GroupGateway {
        val creates = mutableListOf<CreateCall>(); val updates = mutableListOf<UpdateCall>(); val reads = mutableListOf<String>()
        var createResult: NetworkResult<GroupDto> = NetworkResult.Success(group(GroupRoleDto.OWNER))
        var updateResult: NetworkResult<VersionedGroupDto> = NetworkResult.Success(versioned(GroupRoleDto.OWNER, 8))
        var readResult: NetworkResult<VersionedGroupDto> = NetworkResult.Success(versioned(GroupRoleDto.OWNER, 9))
        var pendingCreate: CompletableDeferred<NetworkResult<GroupDto>>? = null
        override suspend fun create(requestId: String, name: String, timeZone: String): NetworkResult<GroupDto> {
            creates += CreateCall(requestId, name, timeZone); return pendingCreate?.await() ?: createResult
        }
        override suspend fun read(groupId: String): NetworkResult<VersionedGroupDto> { reads += groupId; return readResult }
        override suspend fun update(groupId: String, etag: String, name: String, timeZone: String): NetworkResult<VersionedGroupDto> {
            updates += UpdateCall(groupId, etag, name, timeZone); return updateResult
        }
    }

    private class FakeRoles : RolesInvitesGateway {
        val lists = mutableListOf<String>(); val changes = mutableListOf<RoleCall>()
        var pendingChange: CompletableDeferred<NetworkResult<MembershipDto>>? = null
        override suspend fun listMemberships(groupId: String): NetworkResult<List<MembershipDto>> { lists += groupId; return NetworkResult.Success(listOf(member(USER_ID, GroupRoleDto.ATHLETE), member(OTHER_ADMIN, GroupRoleDto.ADMIN))) }
        override suspend fun changeRole(groupId: String, userId: String, role: PersistedRoleDto): NetworkResult<MembershipDto> {
            changes += RoleCall(groupId, userId, role); return pendingChange?.await() ?: NetworkResult.Success(member(userId, GroupRoleDto.valueOf(role.name)))
        }
        override suspend fun rotateInvite(groupId: String): NetworkResult<InviteUrlDto> = error("unused")
        override suspend fun expireInvite(groupId: String): NetworkResult<Unit> = error("unused")
        override suspend fun redeem(code: String): NetworkResult<RedeemedInviteDto> = error("unused")
    }

    private data class Fixture(val machine: GroupAdministrationStateMachine, val groups: FakeGroups, val roles: FakeRoles, val selected: MutableList<String>)
    private data class CreateCall(val requestId: String, val name: String, val timeZone: String)
    private data class UpdateCall(val groupId: String, val etag: String, val name: String, val timeZone: String)
    private data class RoleCall(val groupId: String, val userId: String, val role: PersistedRoleDto)

    private companion object {
        const val GROUP_ID = "group-id"; const val USER_ID = "user-id"; const val OTHER_ADMIN = "other-admin"; const val REQUEST_ID = "request-id"
        fun group(role: GroupRoleDto, version: Long = 7) = GroupDto(GROUP_ID, "Group", "UTC", version, role)
        fun versioned(role: GroupRoleDto, version: Long = 7) = VersionedGroupDto(group(role, version), "\"$version\"")
        fun member(id: String, role: GroupRoleDto) = MembershipDto(id, id, role)
        fun problem(status: Int, code: String) = NetworkResult.Failure(NetworkError.ApiProblemError(ApiProblem(status, code, "corr-$status")))
        fun validation(vararg fields: String) = NetworkResult.Failure(NetworkError.ApiProblemError(ApiProblem(400, "VALIDATION_FAILED", "corr-400", fields.associateWith { listOf("invalid") })))
    }
}
