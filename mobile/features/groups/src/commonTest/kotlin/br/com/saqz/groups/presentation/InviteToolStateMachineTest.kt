package br.com.saqz.groups.presentation

import br.com.saqz.groups.data.InviteUrlDto
import br.com.saqz.groups.data.MembershipDto
import br.com.saqz.groups.data.PersistedRoleDto
import br.com.saqz.groups.data.RedeemedInviteDto
import br.com.saqz.groups.data.RolesInvitesGateway
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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
        fixture.roles.rotateResult = NetworkResult.Failure(NetworkError.Unavailable)

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
        fixture.roles.expireResult = NetworkResult.Failure(NetworkError.Unavailable)

        fixture.machine.expire()
        advanceUntilIdle()

        assertEquals(INVITE_URL, fixture.machine.state.value.inviteUrl)
        assertEquals(InviteUiError.UNAVAILABLE, fixture.machine.state.value.error)
    }

    @Test fun `unsuccessful share reports unavailable`() = runTest {
        val fixture = fixture(this)

        fixture.machine.shareFinished(false)

        assertEquals(InviteUiError.UNAVAILABLE, fixture.machine.state.value.error)
    }

    @Test fun `loading invite ignores concurrent command`() = runTest {
        val fixture = fixture(this)
        fixture.roles.pendingRotation = CompletableDeferred()

        fixture.machine.rotate()
        advanceUntilIdle()
        fixture.machine.expire()

        assertTrue(fixture.machine.state.value.isLoading)
        assertEquals(listOf(GROUP_ID), fixture.roles.rotations)
        assertTrue(fixture.roles.expirations.isEmpty())
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

    private class FakeRoles : RolesInvitesGateway {
        val rotations = mutableListOf<String>()
        val expirations = mutableListOf<String>()
        var rotateResult: NetworkResult<InviteUrlDto> = NetworkResult.Success(InviteUrlDto(INVITE_URL))
        var expireResult: NetworkResult<Unit> = NetworkResult.Success(Unit)
        var pendingRotation: CompletableDeferred<NetworkResult<InviteUrlDto>>? = null

        override suspend fun rotateInvite(groupId: String): NetworkResult<InviteUrlDto> {
            rotations += groupId
            return pendingRotation?.await() ?: rotateResult
        }

        override suspend fun expireInvite(groupId: String): NetworkResult<Unit> {
            expirations += groupId
            return expireResult
        }

        override suspend fun listMemberships(groupId: String): NetworkResult<List<MembershipDto>> = error("unused")
        override suspend fun changeRole(groupId: String, userId: String, role: PersistedRoleDto): NetworkResult<MembershipDto> = error("unused")
        override suspend fun redeem(code: String): NetworkResult<RedeemedInviteDto> = error("unused")
    }

    private data class Fixture(val machine: InviteToolStateMachine, val roles: FakeRoles)

    private companion object {
        const val GROUP_ID = "group-id"
        const val INVITE_URL = "https://saqz.app/i/code"
    }
}
