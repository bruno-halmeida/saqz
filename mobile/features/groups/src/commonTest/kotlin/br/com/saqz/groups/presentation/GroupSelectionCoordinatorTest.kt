package br.com.saqz.groups.presentation

import br.com.saqz.groups.data.*
import br.com.saqz.groups.port.*
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.SessionDto
import br.com.saqz.network.SessionMembershipDto
import br.com.saqz.network.SessionUserDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSelectionStateMachineTest {
    @Test
    fun `zero memberships enters no group state`() = runTest {
        val fixture = fixture(this, stored = null)

        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session()))

        assertIs<GroupSelectionState.NoGroup>(fixture.machine.state.value)
        assertEquals(0, fixture.groups.reads.size)
    }

    @Test
    fun `zero memberships clears stale local selection`() = runTest {
        val fixture = fixture(this, stored = GROUP_A)

        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session()))

        assertEquals(listOf<String?>(null), fixture.local.writes)
    }

    @Test
    fun `one membership auto selects and loads group`() = runTest {
        val fixture = fixture(this)

        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberA)))
        runCurrent()

        assertEquals(listOf(GROUP_A), fixture.groups.reads)
        assertEquals(GROUP_A, assertIs<GroupSelectionState.Selected>(fixture.machine.state.value).group.group.id)
    }

    @Test
    fun `one membership persists its automatic selection`() = runTest {
        val fixture = fixture(this, stored = GROUP_B)

        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberA)))
        runCurrent()

        assertEquals(listOf<String?>(GROUP_A), fixture.local.writes)
    }

    @Test
    fun `multiple memberships without stored choice shows selector`() = runTest {
        val fixture = fixture(this)

        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberA, memberB)))

        val choices = assertIs<GroupSelectionState.Selector>(fixture.machine.state.value).memberships
        assertEquals(listOf(GROUP_A, GROUP_B), choices.map { it.groupId })
        assertTrue(fixture.groups.reads.isEmpty())
    }

    @Test
    fun `multiple memberships restore a current stored choice`() = runTest {
        val fixture = fixture(this, stored = GROUP_B)

        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberA, memberB)))
        runCurrent()

        assertEquals(listOf(GROUP_B), fixture.groups.reads)
        assertEquals(GROUP_B, assertIs<GroupSelectionState.Selected>(fixture.machine.state.value).group.group.id)
    }

    @Test
    fun `stale stored choice is erased and selector is shown`() = runTest {
        val fixture = fixture(this, stored = "stale-group")

        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberA, memberB)))

        assertEquals(listOf<String?>(null), fixture.local.writes)
        assertIs<GroupSelectionState.Selector>(fixture.machine.state.value)
        assertTrue(fixture.groups.reads.isEmpty())
    }

    @Test
    fun `explicit selection clears previous content before network returns`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberA, memberB)))
        fixture.machine.onIntent(GroupSelectionIntent.Select(GROUP_A))
        runCurrent()
        fixture.groups.pending[GROUP_B] = CompletableDeferred()

        fixture.machine.onIntent(GroupSelectionIntent.Select(GROUP_B))
        runCurrent()

        val loading = assertIs<GroupSelectionState.Loading>(fixture.machine.state.value)
        assertEquals(GROUP_B, loading.groupId)
        fixture.groups.pending.getValue(GROUP_B).complete(success(GROUP_B, GroupRoleDto.ATHLETE))
        runCurrent()
    }

    @Test
    fun `explicit current membership selection persists and loads`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberA, memberB)))

        fixture.machine.onIntent(GroupSelectionIntent.Select(GROUP_B))
        runCurrent()

        assertEquals(listOf<String?>(GROUP_B), fixture.local.writes)
        assertEquals(listOf(GROUP_B), fixture.groups.reads)
    }

    @Test
    fun `successful switch publishes fresh group and role`() = runTest {
        val fixture = fixture(this)
        fixture.groups.results[GROUP_B] = success(GROUP_B, GroupRoleDto.ADMIN)
        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberA, memberB)))

        fixture.machine.onIntent(GroupSelectionIntent.Select(GROUP_B))
        runCurrent()

        val selected = assertIs<GroupSelectionState.Selected>(fixture.machine.state.value)
        assertEquals(GROUP_B, selected.group.group.id)
        assertEquals(GroupRoleDto.ADMIN, selected.group.group.role)
    }

    @Test
    fun `failed switch exposes error without protected previous group`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberA, memberB)))
        fixture.machine.onIntent(GroupSelectionIntent.Select(GROUP_A))
        runCurrent()
        fixture.groups.results[GROUP_B] = NetworkResult.Failure(NetworkError.Unavailable)

        fixture.machine.onIntent(GroupSelectionIntent.Select(GROUP_B))
        runCurrent()

        assertEquals(GROUP_B, assertIs<GroupSelectionState.LoadError>(fixture.machine.state.value).groupId)
    }

    @Test
    fun `selection outside current memberships is ignored`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberA, memberB)))

        fixture.machine.onIntent(GroupSelectionIntent.Select("unknown-group"))

        assertIs<GroupSelectionState.Selector>(fixture.machine.state.value)
        assertTrue(fixture.groups.reads.isEmpty())
        assertTrue(fixture.local.writes.isEmpty())
    }

    @Test
    fun `duplicate selection while loading is single flight`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberA, memberB)))
        fixture.groups.pending[GROUP_A] = CompletableDeferred()

        fixture.machine.onIntent(GroupSelectionIntent.Select(GROUP_A))
        runCurrent()
        fixture.machine.onIntent(GroupSelectionIntent.Select(GROUP_A))

        assertEquals(listOf(GROUP_A), fixture.groups.reads)
        fixture.groups.pending.getValue(GROUP_A).complete(success(GROUP_A, GroupRoleDto.OWNER))
        runCurrent()
    }

    @Test
    fun `reconciliation refreshes role from group response not stored membership`() = runTest {
        val fixture = fixture(this)
        fixture.groups.results[GROUP_A] = success(GROUP_A, GroupRoleDto.ATHLETE)

        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberA.copy(role = "ADMIN"))))
        runCurrent()

        assertEquals(
            GroupRoleDto.ATHLETE,
            assertIs<GroupSelectionState.Selected>(fixture.machine.state.value).group.group.role,
        )
    }

    @Test
    fun `stale group completion cannot replace a newer reconciliation`() = runTest {
        val fixture = fixture(this)
        fixture.groups.pending[GROUP_A] = CompletableDeferred()
        fixture.groups.pending[GROUP_B] = CompletableDeferred()

        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberA)))
        runCurrent()
        fixture.machine.onIntent(GroupSelectionIntent.Reconcile(session(memberB)))
        runCurrent()
        fixture.groups.pending.getValue(GROUP_B).complete(success(GROUP_B, GroupRoleDto.ADMIN))
        runCurrent()
        fixture.groups.pending.getValue(GROUP_A).complete(success(GROUP_A, GroupRoleDto.OWNER))
        runCurrent()

        val selected = assertIs<GroupSelectionState.Selected>(fixture.machine.state.value)
        assertEquals(GROUP_B, selected.group.group.id)
        assertEquals(GroupRoleDto.ADMIN, selected.group.group.role)
    }

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope, stored: String? = null): Fixture {
        val local = FakeLocalState(stored)
        val groups = FakeGroupGateway()
        return Fixture(GroupSelectionStateMachine(local, groups, scope), local, groups)
    }

    private class FakeLocalState(private val stored: String?) : LocalAccessStatePort {
        val writes = mutableListOf<String?>()
        override fun readSelectedGroupId(done: ValueCallback) = done.complete(GroupValueResult.Success(stored))
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) { writes += value; done.complete(GroupOperationResult.Success) }
        override fun readPendingInvite(done: ValueCallback) = done.complete(GroupValueResult.Success(null))
        override fun writePendingInvite(value: String?, done: ResultCallback) = done.complete(GroupOperationResult.Success)
    }

    private class FakeGroupGateway : GroupGateway {
        val reads = mutableListOf<String>()
        val results = mutableMapOf<String, NetworkResult<VersionedGroupDto>>()
        val pending = mutableMapOf<String, CompletableDeferred<NetworkResult<VersionedGroupDto>>>()
        override suspend fun read(groupId: String): NetworkResult<VersionedGroupDto> {
            reads += groupId
            return pending[groupId]?.await() ?: results[groupId] ?: success(groupId, GroupRoleDto.OWNER)
        }
        override suspend fun create(requestId: String, name: String, timeZone: String) = error("unused")
        override suspend fun update(groupId: String, etag: String, name: String, timeZone: String) = error("unused")
    }

    private data class Fixture(
        val machine: GroupSelectionStateMachine,
        val local: FakeLocalState,
        val groups: FakeGroupGateway,
    )

    private companion object {
        const val GROUP_A = "group-a"
        const val GROUP_B = "group-b"
        val memberA = SessionMembershipDto(GROUP_A, "Group A", "OWNER")
        val memberB = SessionMembershipDto(GROUP_B, "Group B", "ATHLETE")
        fun session(vararg memberships: SessionMembershipDto) =
            SessionDto(SessionUserDto("user", null, "Person"), memberships.toList())
        fun success(groupId: String, role: GroupRoleDto) = NetworkResult.Success(
            VersionedGroupDto(GroupDto(groupId, "Group $groupId", "UTC", 1, role), "\"1\""),
        )
    }
}
