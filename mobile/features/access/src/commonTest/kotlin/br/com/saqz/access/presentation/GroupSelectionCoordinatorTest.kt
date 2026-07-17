package br.com.saqz.access.presentation

import br.com.saqz.access.data.GroupDto
import br.com.saqz.access.data.GroupGateway
import br.com.saqz.access.data.GroupRoleDto
import br.com.saqz.access.data.VersionedGroupDto
import br.com.saqz.access.port.LocalAccessStatePort
import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.ValueCallback
import br.com.saqz.access.port.ValueResult
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
class GroupSelectionCoordinatorTest {
    @Test
    fun `zero memberships enters no group state`() = runTest {
        val fixture = fixture(this, stored = null)

        fixture.coordinator.reconcile(session())

        assertIs<GroupSelectionState.NoGroup>(fixture.coordinator.state.value)
        assertEquals(0, fixture.groups.reads.size)
    }

    @Test
    fun `zero memberships clears stale local selection`() = runTest {
        val fixture = fixture(this, stored = GROUP_A)

        fixture.coordinator.reconcile(session())

        assertEquals(listOf<String?>(null), fixture.local.writes)
    }

    @Test
    fun `one membership auto selects and loads group`() = runTest {
        val fixture = fixture(this)

        fixture.coordinator.reconcile(session(memberA))
        runCurrent()

        assertEquals(listOf(GROUP_A), fixture.groups.reads)
        assertEquals(GROUP_A, assertIs<GroupSelectionState.Selected>(fixture.coordinator.state.value).group.group.id)
    }

    @Test
    fun `one membership persists its automatic selection`() = runTest {
        val fixture = fixture(this, stored = GROUP_B)

        fixture.coordinator.reconcile(session(memberA))
        runCurrent()

        assertEquals(listOf<String?>(GROUP_A), fixture.local.writes)
    }

    @Test
    fun `multiple memberships without stored choice shows selector`() = runTest {
        val fixture = fixture(this)

        fixture.coordinator.reconcile(session(memberA, memberB))

        val choices = assertIs<GroupSelectionState.Selector>(fixture.coordinator.state.value).memberships
        assertEquals(listOf(GROUP_A, GROUP_B), choices.map { it.groupId })
        assertTrue(fixture.groups.reads.isEmpty())
    }

    @Test
    fun `multiple memberships restore a current stored choice`() = runTest {
        val fixture = fixture(this, stored = GROUP_B)

        fixture.coordinator.reconcile(session(memberA, memberB))
        runCurrent()

        assertEquals(listOf(GROUP_B), fixture.groups.reads)
        assertEquals(GROUP_B, assertIs<GroupSelectionState.Selected>(fixture.coordinator.state.value).group.group.id)
    }

    @Test
    fun `stale stored choice is erased and selector is shown`() = runTest {
        val fixture = fixture(this, stored = "stale-group")

        fixture.coordinator.reconcile(session(memberA, memberB))

        assertEquals(listOf<String?>(null), fixture.local.writes)
        assertIs<GroupSelectionState.Selector>(fixture.coordinator.state.value)
        assertTrue(fixture.groups.reads.isEmpty())
    }

    @Test
    fun `explicit selection clears previous content before network returns`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.reconcile(session(memberA, memberB))
        fixture.coordinator.select(GROUP_A)
        runCurrent()
        fixture.groups.pending = CompletableDeferred()

        fixture.coordinator.select(GROUP_B)
        runCurrent()

        val loading = assertIs<GroupSelectionState.Loading>(fixture.coordinator.state.value)
        assertEquals(GROUP_B, loading.groupId)
        fixture.groups.pending!!.complete(success(GROUP_B, GroupRoleDto.ATHLETE))
        runCurrent()
    }

    @Test
    fun `explicit current membership selection persists and loads`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.reconcile(session(memberA, memberB))

        fixture.coordinator.select(GROUP_B)
        runCurrent()

        assertEquals(listOf<String?>(GROUP_B), fixture.local.writes)
        assertEquals(listOf(GROUP_B), fixture.groups.reads)
    }

    @Test
    fun `successful switch publishes fresh group and role`() = runTest {
        val fixture = fixture(this)
        fixture.groups.results[GROUP_B] = success(GROUP_B, GroupRoleDto.ADMIN)
        fixture.coordinator.reconcile(session(memberA, memberB))

        fixture.coordinator.select(GROUP_B)
        runCurrent()

        val selected = assertIs<GroupSelectionState.Selected>(fixture.coordinator.state.value)
        assertEquals(GROUP_B, selected.group.group.id)
        assertEquals(GroupRoleDto.ADMIN, selected.group.group.role)
    }

    @Test
    fun `failed switch exposes error without protected previous group`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.reconcile(session(memberA, memberB))
        fixture.coordinator.select(GROUP_A)
        runCurrent()
        fixture.groups.results[GROUP_B] = NetworkResult.Failure(NetworkError.Unavailable)

        fixture.coordinator.select(GROUP_B)
        runCurrent()

        assertEquals(GROUP_B, assertIs<GroupSelectionState.LoadError>(fixture.coordinator.state.value).groupId)
    }

    @Test
    fun `selection outside current memberships is ignored`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.reconcile(session(memberA, memberB))

        fixture.coordinator.select("unknown-group")

        assertIs<GroupSelectionState.Selector>(fixture.coordinator.state.value)
        assertTrue(fixture.groups.reads.isEmpty())
        assertTrue(fixture.local.writes.isEmpty())
    }

    @Test
    fun `duplicate selection while loading is single flight`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.reconcile(session(memberA, memberB))
        fixture.groups.pending = CompletableDeferred()

        fixture.coordinator.select(GROUP_A)
        runCurrent()
        fixture.coordinator.select(GROUP_A)

        assertEquals(listOf(GROUP_A), fixture.groups.reads)
        fixture.groups.pending!!.complete(success(GROUP_A, GroupRoleDto.OWNER))
        runCurrent()
    }

    @Test
    fun `reconciliation refreshes role from group response not stored membership`() = runTest {
        val fixture = fixture(this)
        fixture.groups.results[GROUP_A] = success(GROUP_A, GroupRoleDto.ATHLETE)

        fixture.coordinator.reconcile(session(memberA.copy(role = "ADMIN")))
        runCurrent()

        assertEquals(
            GroupRoleDto.ATHLETE,
            assertIs<GroupSelectionState.Selected>(fixture.coordinator.state.value).group.group.role,
        )
    }

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope, stored: String? = null): Fixture {
        val local = FakeLocalState(stored)
        val groups = FakeGroupGateway()
        return Fixture(GroupSelectionCoordinator(local, groups, scope), local, groups)
    }

    private class FakeLocalState(private val stored: String?) : LocalAccessStatePort {
        val writes = mutableListOf<String?>()
        override fun readSelectedGroupId(done: ValueCallback) = done.complete(ValueResult.Success(stored))
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) { writes += value; done.complete(OperationResult.Success) }
        override fun readPendingInvite(done: ValueCallback) = done.complete(ValueResult.Success(null))
        override fun writePendingInvite(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
    }

    private class FakeGroupGateway : GroupGateway {
        val reads = mutableListOf<String>()
        val results = mutableMapOf<String, NetworkResult<VersionedGroupDto>>()
        var pending: CompletableDeferred<NetworkResult<VersionedGroupDto>>? = null
        override suspend fun read(groupId: String): NetworkResult<VersionedGroupDto> {
            reads += groupId
            return pending?.await() ?: results[groupId] ?: success(groupId, GroupRoleDto.OWNER)
        }
        override suspend fun create(requestId: String, name: String, timeZone: String) = error("unused")
        override suspend fun update(groupId: String, etag: String, name: String, timeZone: String) = error("unused")
    }

    private data class Fixture(
        val coordinator: GroupSelectionCoordinator,
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
