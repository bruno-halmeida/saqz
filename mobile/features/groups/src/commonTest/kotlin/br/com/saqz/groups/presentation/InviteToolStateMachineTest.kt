package br.com.saqz.groups.presentation

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.GroupInviteUrl
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.RedeemedMembership
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InviteToolStateMachineTest {
    @Test fun `rotate publishes returned invite url`() = runTest {
        val fixture = fixture(this)

        fixture.machine.rotate()
        advanceUntilIdle()

        assertEquals(listOf(GROUP_ID), fixture.roles.rotations)
        assertEquals(INVITE_URL, fixture.machine.state.value.inviteUrl)
        assertFalse(fixture.machine.state.value.isLoading)
        assertNull(fixture.machine.state.value.error)
    }

    @Test fun `rotate failure preserves prior url and reports unavailable`() = runTest {
        val fixture = fixture(this)
        fixture.machine.rotate()
        advanceUntilIdle()
        fixture.roles.rotateResult = unavailable()

        fixture.machine.rotate()
        advanceUntilIdle()

        assertEquals(INVITE_URL, fixture.machine.state.value.inviteUrl)
        assertEquals(InviteUiError.UNAVAILABLE, fixture.machine.state.value.error)
        assertFalse(fixture.machine.state.value.isLoading)
    }

    @Test fun `expire clears invite on success`() = runTest {
        val fixture = fixture(this)

        fixture.machine.expire()
        advanceUntilIdle()

        assertEquals(listOf(GROUP_ID), fixture.roles.expirations)
        assertEquals(InviteToolState(), fixture.machine.state.value)
    }

    @Test fun `expire failure reports unavailable`() = runTest {
        val fixture = fixture(this)
        fixture.machine.rotate()
        advanceUntilIdle()
        fixture.roles.expireResult = unavailable()

        fixture.machine.expire()
        advanceUntilIdle()

        assertEquals(INVITE_URL, fixture.machine.state.value.inviteUrl)
        assertEquals(InviteUiError.UNAVAILABLE, fixture.machine.state.value.error)
    }

    @Test
    fun `attempt limit preserves exact retry delay`() = runTest {
        val fixture = fixture(this)
        fixture.roles.rotateResult = SaqzResult.Failure(GroupMembershipError.AttemptLimit(42))

        fixture.machine.rotate()
        advanceUntilIdle()

        assertEquals(InviteUiError.ATTEMPT_LIMIT, fixture.machine.state.value.error)
        assertEquals(42, fixture.machine.state.value.retryAfterSeconds)
    }

    @Test
    fun `validation without global message uses localized unavailable state`() = runTest {
        val fixture = fixture(this)
        fixture.roles.rotateResult = SaqzResult.Failure(
            GroupMembershipError.Validation(
                ValidationDetails(globalMessages = emptyList(), fieldMessages = emptyMap()),
            ),
        )

        fixture.machine.rotate()
        advanceUntilIdle()

        assertEquals(InviteUiError.UNAVAILABLE, fixture.machine.state.value.error)
    }

    @Test fun `unsuccessful share reports unavailable`() = runTest {
        val fixture = fixture(this)

        fixture.machine.shareFinished(false)

        assertEquals(InviteUiError.UNAVAILABLE, fixture.machine.state.value.error)
    }

    @Test fun `loading invite ignores concurrent command`() = runTest {
        val fixture = fixture(this)
        val pendingRotation = CompletableDeferred<SaqzResult<GroupInviteUrl, GroupMembershipError>>()
        fixture.roles.pendingRotation = pendingRotation

        fixture.machine.rotate()
        runCurrent()
        fixture.machine.expire()

        assertTrue(fixture.machine.state.value.isLoading)
        assertEquals(listOf(GROUP_ID), fixture.roles.rotations)
        assertTrue(fixture.roles.expirations.isEmpty())

        pendingRotation.complete(fixture.roles.rotateResult)
        runCurrent()
    }

    @Test fun `missing group does not issue invite request`() = runTest {
        val fixture = fixture(this, groupId = null)

        fixture.machine.rotate()
        fixture.machine.expire()

        assertTrue(fixture.roles.rotations.isEmpty())
        assertTrue(fixture.roles.expirations.isEmpty())
        assertEquals(InviteToolState(), fixture.machine.state.value)
    }

    private fun fixture(
        scope: kotlinx.coroutines.CoroutineScope,
        groupId: String? = GROUP_ID,
    ): Fixture {
        val roles = FakeRoles()
        val machine = InviteToolStateMachine(roles, { groupId }, scope)
        return Fixture(machine, roles)
    }

    private class FakeRoles : GroupMembershipGateway {
        val rotations = mutableListOf<String>()
        val expirations = mutableListOf<String>()
        var rotateResult: SaqzResult<GroupInviteUrl, GroupMembershipError> =
            SaqzResult.Success(GroupInviteUrl(INVITE_URL))
        var expireResult: SaqzResult<Unit, GroupMembershipError> = SaqzResult.Success(Unit)
        var pendingRotation: CompletableDeferred<SaqzResult<GroupInviteUrl, GroupMembershipError>>? = null

        override suspend fun rotateInvite(groupId: GroupId): SaqzResult<GroupInviteUrl, GroupMembershipError> {
            rotations += groupId.value
            return pendingRotation?.await() ?: rotateResult
        }

        override suspend fun expireInvite(groupId: GroupId): SaqzResult<Unit, GroupMembershipError> {
            expirations += groupId.value
            return expireResult
        }

        override suspend fun listMemberships(
            groupId: GroupId,
        ): SaqzResult<List<GroupMembership>, GroupMembershipError> = error("unused")

        override suspend fun changeRole(
            command: ChangeMembershipRoleCommand,
        ): SaqzResult<GroupMembership, GroupMembershipError> = error("unused")

        override suspend fun redeem(
            code: InviteCode,
        ): SaqzResult<RedeemedMembership, GroupMembershipError> = error("unused")
    }

    private data class Fixture(val machine: InviteToolStateMachine, val roles: FakeRoles)

    private companion object {
        const val GROUP_ID = "group-id"
        const val INVITE_URL = "https://saqz.app/i/code"
        fun unavailable(): SaqzResult.Failure<GroupMembershipError> =
            SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.Server))
    }
}
