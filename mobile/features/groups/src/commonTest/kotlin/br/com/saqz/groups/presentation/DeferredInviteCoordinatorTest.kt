package br.com.saqz.groups.presentation

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.GroupInviteUrl
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.RedeemedMembership
import br.com.saqz.groups.port.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeferredInviteStateMachineTest {
    @Test fun `start subscribes once to native links`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(DeferredInviteIntent.Start); fixture.machine.onIntent(DeferredInviteIntent.Start)
        assertEquals(1, fixture.links.starts)
    }

    @Test fun `link event persists pending code`() = runTest {
        val fixture = fixture(this); fixture.machine.onIntent(DeferredInviteIntent.Start)
        fixture.links.emit(CODE_A)
        assertEquals(listOf<String?>(CODE_A), fixture.local.writes)
        assertTrue(fixture.machine.state.value.hasPending)
    }

    @Test fun `last preauthentication link replaces prior code`() = runTest {
        val fixture = fixture(this); fixture.machine.onIntent(DeferredInviteIntent.Start)
        fixture.links.emit(CODE_A); fixture.links.emit(CODE_B)
        assertEquals(listOf<String?>(CODE_A, CODE_B), fixture.local.writes)
    }

    @Test fun `pending link is not redeemed before session ready`() = runTest {
        val fixture = fixture(this); fixture.machine.onIntent(DeferredInviteIntent.Start); fixture.links.emit(CODE_A)
        runCurrent()
        assertTrue(fixture.roles.redeems.isEmpty())
    }

    @Test fun `session readiness redeems only latest pending code`() = runTest {
        val fixture = fixture(this); fixture.machine.onIntent(DeferredInviteIntent.Start)
        fixture.links.emit(CODE_A); fixture.links.emit(CODE_B)
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true)); runCurrent()
        assertEquals(listOf(CODE_B), fixture.roles.redeems)
    }

    @Test fun `restart restores pending code without exposing it in state`() = runTest {
        val fixture = fixture(this, stored = CODE_A)
        fixture.machine.onIntent(DeferredInviteIntent.Restore)
        assertTrue(fixture.machine.state.value.hasPending)
        assertFalse(fixture.machine.state.value.toString().contains(CODE_A))
    }

    @Test fun `restored code waits for verified bootstrap`() = runTest {
        val fixture = fixture(this, stored = CODE_A)
        fixture.machine.onIntent(DeferredInviteIntent.Restore); runCurrent()
        assertTrue(fixture.roles.redeems.isEmpty())
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true)); runCurrent()
        assertEquals(listOf(CODE_A), fixture.roles.redeems)
    }

    @Test fun `successful redeem selects returned group`() = runTest {
        val fixture = fixture(this, stored = CODE_A); fixture.machine.onIntent(DeferredInviteIntent.Restore)
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true)); runCurrent()
        assertEquals(listOf(GROUP_ID), fixture.selected)
    }

    @Test fun `successful redeem clears pending capability`() = runTest {
        val fixture = fixture(this, stored = CODE_A); fixture.machine.onIntent(DeferredInviteIntent.Restore)
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true)); runCurrent()
        assertEquals(null, fixture.local.writes.last())
        assertFalse(fixture.machine.state.value.hasPending)
    }

    @Test fun `successful redeem preserves authoritative admin role`() = runTest {
        val fixture = fixture(this, stored = CODE_A)
        fixture.roles.result = SaqzResult.Success(redeemed(GroupRole.ADMIN))
        fixture.machine.onIntent(DeferredInviteIntent.Restore)
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true)); runCurrent()
        assertEquals(GroupRole.ADMIN, fixture.machine.state.value.redeemedRole)
    }

    @Test fun `duplicate events during redeem produce one request`() = runTest {
        val fixture = fixture(this); fixture.roles.pending = CompletableDeferred()
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true)); fixture.machine.onIntent(DeferredInviteIntent.Start)
        fixture.links.emit(CODE_A); runCurrent(); fixture.links.emit(CODE_A)
        assertEquals(listOf(CODE_A), fixture.roles.redeems)
        fixture.roles.pending!!.complete(SaqzResult.Success(redeemed(GroupRole.ATHLETE))); runCurrent()
    }

    @Test fun `invalid invite clears pending capability`() = runTest {
        val fixture = fixture(this, stored = CODE_A)
        fixture.roles.result = problem(404, "INVITE_INVALID_OR_EXPIRED")
        fixture.machine.onIntent(DeferredInviteIntent.Restore)
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true)); runCurrent()
        assertEquals(null, fixture.local.writes.last())
        assertEquals(InviteUiError.INVALID_OR_EXPIRED, fixture.machine.state.value.error)
    }

    @Test fun `invalid invite never selects a group`() = runTest {
        val fixture = fixture(this, stored = CODE_A)
        fixture.roles.result = problem(404, "INVITE_INVALID_OR_EXPIRED")
        fixture.machine.onIntent(DeferredInviteIntent.Restore)
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true)); runCurrent()
        assertTrue(fixture.selected.isEmpty())
        assertNull(fixture.machine.state.value.redeemedRole)
    }

    @Test fun `rate limit preserves pending capability`() = runTest {
        val fixture = fixture(this, stored = CODE_A)
        fixture.roles.result = limited(37)
        fixture.machine.onIntent(DeferredInviteIntent.Restore)
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true)); runCurrent()
        assertTrue(fixture.machine.state.value.hasPending)
        assertFalse(fixture.local.writes.contains(null))
    }

    @Test fun `rate limit exposes exact retry seconds`() = runTest {
        val fixture = fixture(this, stored = CODE_A)
        fixture.roles.result = limited(37)
        fixture.machine.onIntent(DeferredInviteIntent.Restore)
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true)); runCurrent()
        assertEquals(InviteUiError.ATTEMPT_LIMIT, fixture.machine.state.value.error)
        assertEquals(37, fixture.machine.state.value.retryAfterSeconds)
    }

    @Test fun `retry remains blocked while rate limit is active`() = runTest {
        val fixture = fixture(this, stored = CODE_A)
        fixture.roles.result = limited(37)
        fixture.machine.onIntent(DeferredInviteIntent.Restore)
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true)); runCurrent()
        fixture.machine.onIntent(DeferredInviteIntent.Retry); runCurrent()
        assertEquals(1, fixture.roles.redeems.size)
    }

    @Test fun `logout clears pending capability and session readiness`() = runTest {
        val fixture = fixture(this, stored = CODE_A)
        fixture.machine.onIntent(DeferredInviteIntent.Restore)
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true)); runCurrent()
        fixture.machine.onIntent(DeferredInviteIntent.Logout)
        assertEquals(null, fixture.local.writes.last())
        assertEquals(InviteState(), fixture.machine.state.value)
    }

    @Test fun `explicit discard clears pending without redeem`() = runTest {
        val fixture = fixture(this, stored = CODE_A); fixture.machine.onIntent(DeferredInviteIntent.Restore)
        fixture.machine.onIntent(DeferredInviteIntent.Discard)
        assertEquals(null, fixture.local.writes.last())
        assertFalse(fixture.machine.state.value.hasPending)
        assertTrue(fixture.roles.redeems.isEmpty())
    }

    @Test fun `ready session without pending code is a no op`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true))
        fixture.machine.onIntent(DeferredInviteIntent.Retry); runCurrent()
        assertTrue(fixture.roles.redeems.isEmpty())
    }

    @Test fun `already authenticated warm link redeems and selects once`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true))
        fixture.machine.onIntent(DeferredInviteIntent.Start)
        fixture.links.emit(CODE_A); runCurrent()
        fixture.machine.onIntent(DeferredInviteIntent.Retry); runCurrent()
        assertEquals(listOf(CODE_A), fixture.roles.redeems)
        assertEquals(listOf(GROUP_ID), fixture.selected)
    }

    @Test fun `temporary server error keeps pending for retry`() = runTest {
        val fixture = fixture(this)
        fixture.roles.result = unavailable()
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true))
        fixture.machine.onIntent(DeferredInviteIntent.Start)
        fixture.links.emit(CODE_A); runCurrent()
        assertTrue(fixture.machine.state.value.hasPending)
        assertFalse(fixture.local.writes.contains(null))
        assertEquals(InviteUiError.UNAVAILABLE, fixture.machine.state.value.error)
    }

    @Test fun `temporary server error retry redeems same code exactly once more`() = runTest {
        val fixture = fixture(this)
        fixture.roles.result = unavailable()
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true))
        fixture.machine.onIntent(DeferredInviteIntent.Start)
        fixture.links.emit(CODE_A); runCurrent()
        fixture.roles.result = SaqzResult.Success(redeemed(GroupRole.ATHLETE))
        fixture.machine.onIntent(DeferredInviteIntent.Retry); runCurrent()
        assertEquals(listOf(CODE_A, CODE_A), fixture.roles.redeems)
        assertEquals(listOf(GROUP_ID), fixture.selected)
        assertFalse(fixture.machine.state.value.hasPending)
    }

    @Test fun `new link replaces retryable pending invite and redeems latest only`() = runTest {
        val fixture = fixture(this)
        fixture.roles.result = unavailable()
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true))
        fixture.machine.onIntent(DeferredInviteIntent.Start)
        fixture.links.emit(CODE_A); runCurrent()
        fixture.roles.result = SaqzResult.Success(redeemed(GroupRole.ATHLETE))
        fixture.links.emit(CODE_B); runCurrent()
        assertEquals(listOf(CODE_A, CODE_B), fixture.roles.redeems)
        assertEquals(listOf<String?>(CODE_A, CODE_B, null), fixture.local.writes)
        assertEquals(listOf(GROUP_ID), fixture.selected)
    }

    @Test fun `terminal invalid clears state so retry cannot reselect`() = runTest {
        val fixture = fixture(this, stored = CODE_A)
        fixture.roles.result = problem(404, "INVITE_INVALID_OR_EXPIRED")
        fixture.machine.onIntent(DeferredInviteIntent.Restore)
        fixture.machine.onIntent(DeferredInviteIntent.SetSessionReady(true)); runCurrent()
        fixture.roles.result = SaqzResult.Success(redeemed(GroupRole.ATHLETE))
        fixture.machine.onIntent(DeferredInviteIntent.Retry); runCurrent()
        assertEquals(listOf(CODE_A), fixture.roles.redeems)
        assertTrue(fixture.selected.isEmpty())
    }

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope, stored: String? = null): Fixture {
        val links = FakeLinks(); val local = FakeLocal(stored); val roles = FakeRoles(); val selected = mutableListOf<String>()
        return Fixture(DeferredInviteStateMachine(links, local, roles, scope, selected::add), links, local, roles, selected)
    }

    private class FakeLinks : NativeLinkPort {
        var starts = 0; private var listener: LinkEventListener? = null
        override fun start(listener: LinkEventListener): Cancelable { starts += 1; this.listener = listener; return object : Cancelable { override fun cancel() { this@FakeLinks.listener = null } } }
        fun emit(code: String) = listener!!.onEvent(GroupLinkEvent.Invite(code))
    }

    private class FakeLocal(private val stored: String?) : LocalAccessStatePort {
        val writes = mutableListOf<String?>()
        override fun readPendingInvite(done: ValueCallback) = done.complete(GroupValueResult.Success(stored))
        override fun writePendingInvite(value: String?, done: ResultCallback) { writes += value; done.complete(GroupOperationResult.Success) }
        override fun readPendingAttendanceLink(done: ValueCallback) = done.complete(GroupValueResult.Success(null))
        override fun writePendingAttendanceLink(value: String?, done: ResultCallback) = done.complete(GroupOperationResult.Success)
        override fun readSelectedGroupId(done: ValueCallback) = done.complete(GroupValueResult.Success(null))
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) = done.complete(GroupOperationResult.Success)
    }

    private class FakeRoles : GroupMembershipGateway {
        val redeems = mutableListOf<String>()
        var result: SaqzResult<RedeemedMembership, GroupMembershipError> = SaqzResult.Success(redeemed(GroupRole.ATHLETE))
        var pending: CompletableDeferred<SaqzResult<RedeemedMembership, GroupMembershipError>>? = null
        override suspend fun redeem(code: InviteCode): SaqzResult<RedeemedMembership, GroupMembershipError> {
            redeems += code.value
            return pending?.await() ?: result
        }
        override suspend fun listMemberships(groupId: GroupId): SaqzResult<List<GroupMembership>, GroupMembershipError> = error("unused")
        override suspend fun changeRole(command: ChangeMembershipRoleCommand): SaqzResult<GroupMembership, GroupMembershipError> = error("unused")
        override suspend fun rotateInvite(groupId: GroupId): SaqzResult<GroupInviteUrl, GroupMembershipError> = error("unused")
        override suspend fun expireInvite(groupId: GroupId): SaqzResult<Unit, GroupMembershipError> = error("unused")
    }

    private data class Fixture(val machine: DeferredInviteStateMachine, val links: FakeLinks, val local: FakeLocal, val roles: FakeRoles, val selected: MutableList<String>)

    private companion object {
        const val CODE_A = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val CODE_B = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
        const val GROUP_ID = "group-id"
        fun redeemed(role: GroupRole) = RedeemedMembership(GroupId(GROUP_ID), role)
        fun problem(status: Int, code: String): SaqzResult.Failure<GroupMembershipError> = SaqzResult.Failure(
            if (code == "INVITE_INVALID_OR_EXPIRED") {
                GroupMembershipError.InvalidOrExpired
            } else {
                GroupMembershipError.DataFailure(if (status == 404) DataError.NotFound else DataError.Unknown)
            },
        )
        fun limited(seconds: Int): SaqzResult.Failure<GroupMembershipError> =
            SaqzResult.Failure(GroupMembershipError.AttemptLimit(seconds))
        fun unavailable(): SaqzResult.Failure<GroupMembershipError> =
            SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.Server))
    }
}
