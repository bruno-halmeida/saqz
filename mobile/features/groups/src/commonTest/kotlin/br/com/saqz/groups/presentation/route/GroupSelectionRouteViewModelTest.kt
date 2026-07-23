package br.com.saqz.groups.presentation.route

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.group.CreateGroupCommand
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.UpdateGroupSettingsCommand
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.port.*
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.GroupSelectionMembership
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.GroupSelectionStateMachine
import br.com.saqz.groups.ui.GroupOnboardingIntent
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Projection/intent-delegation matrix for [GroupSelectionRouteViewModel] (T12).
 * Derived from GROUPNAV-01, LIFE-01, LIFE-03, LIFE-05.
 */
class GroupSelectionRouteViewModelTest {

    @Test
    fun `initial state projects the coordinator's current state`() = runTest {
        val coordinator = GroupSelectionStateMachine(FakeLocalState(), FakeGroupGateway(), this)

        val viewModel = GroupSelectionRouteViewModel(coordinator)

        assertIs<GroupSelectionState.NoGroup>(viewModel.state.value)
    }

    @Test
    fun `state updates when the coordinator reconciles to Selector`() = runTest {
        val coordinator = GroupSelectionStateMachine(FakeLocalState(), FakeGroupGateway(), this)
        val viewModel = GroupSelectionRouteViewModel(coordinator)

        coordinator.onIntent(
            GroupSelectionIntent.Reconcile(
                listOf(GroupSelectionMembership("g1", "Team", GroupRole.OWNER), GroupSelectionMembership("g2", "Team 2", GroupRole.ATHLETE)),
            ),
        )
        runCurrent()

        val selector = assertIs<GroupSelectionState.Selector>(viewModel.state.value)
        assertEquals(listOf("g1", "g2"), selector.memberships.map { it.groupId })
    }

    @Test
    fun `Select forwards to the coordinator and business logic stays in the machine`() = runTest {
        val gateway = FakeGroupGateway()
        val coordinator = GroupSelectionStateMachine(FakeLocalState(), gateway, this)
        coordinator.onIntent(
            GroupSelectionIntent.Reconcile(
                listOf(GroupSelectionMembership("g1", "Team", GroupRole.OWNER), GroupSelectionMembership("g2", "Team 2", GroupRole.ATHLETE)),
            ),
        )
        val viewModel = GroupSelectionRouteViewModel(coordinator)

        viewModel.onIntent(GroupOnboardingIntent.Select("g2"))
        runCurrent()

        assertEquals(listOf("g2"), gateway.reads)
        assertIs<GroupSelectionState.Selected>(viewModel.state.value)
    }

    @Test
    fun `Retry forwards to the coordinator`() = runTest {
        val gateway = FakeGroupGateway()
        gateway.results["g1"] = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Unknown))
        val coordinator = GroupSelectionStateMachine(FakeLocalState(), gateway, this)
        coordinator.onIntent(
            GroupSelectionIntent.Reconcile(listOf(GroupSelectionMembership("g1", "Team", GroupRole.OWNER))),
        )
        runCurrent()
        val viewModel = GroupSelectionRouteViewModel(coordinator)
        assertIs<GroupSelectionState.LoadError>(viewModel.state.value)
        gateway.results["g1"] = SaqzResult.Success(
            VersionedGroup(Group("g1", "Team", "UTC", 1, GroupRole.OWNER), GroupVersionToken("\"1\"")),
        )

        viewModel.onIntent(GroupOnboardingIntent.Retry)
        runCurrent()

        assertIs<GroupSelectionState.Selected>(viewModel.state.value)
    }

    @Test
    fun `OpenCreateGroup emits a typed effect without touching the coordinator`() = runTest {
        val gateway = FakeGroupGateway()
        val coordinator = GroupSelectionStateMachine(FakeLocalState(), gateway, this)
        val viewModel = GroupSelectionRouteViewModel(coordinator)

        viewModel.onIntent(GroupOnboardingIntent.OpenCreateGroup)
        val delivered = viewModel.effects.take(1).toList()

        assertEquals(listOf(GroupSelectionRouteEffect.OpenCreateGroup), delivered)
        assertTrue(gateway.reads.isEmpty())
    }

    private class FakeLocalState : LocalGroupStatePort {
        override fun readSelectedGroupId(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
        override fun writeSelectedGroupId(value: String?, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
        override fun readPendingInvite(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
        override fun writePendingInvite(value: String?, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
        override fun readPendingAttendanceLink(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
        override fun writePendingAttendanceLink(value: String?, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
    }

    private class FakeGroupGateway : GroupGateway {
        val reads = mutableListOf<String>()
        val results = mutableMapOf<String, SaqzResult<VersionedGroup, GroupProfileError>>()

        override suspend fun read(groupId: GroupId): SaqzResult<VersionedGroup, GroupProfileError> {
            reads += groupId.value
            return results[groupId.value] ?: SaqzResult.Success(
                VersionedGroup(Group(groupId.value, "Group ${groupId.value}", "UTC", 1, GroupRole.OWNER), GroupVersionToken("\"1\"")),
            )
        }

        override suspend fun create(command: CreateGroupCommand) = error("unused")
        override suspend fun update(command: UpdateGroupSettingsCommand) = error("unused")
    }
}
