package br.com.saqz.groups.presentation.attendance.share

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.share.*
import br.com.saqz.groups.port.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeferredAttendanceLinkStateMachineTest {
    @Test fun `start subscribes once to group links`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.Start)
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.Start)
        assertEquals(1, fixture.links.starts)
    }

    @Test fun `attendance event persists pending code`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.Start)
        fixture.links.emitAttendance(CODE_A)
        assertEquals(listOf<String?>(CODE_A), fixture.local.attendanceWrites)
        assertTrue(fixture.machine.state.value.hasPending)
    }

    @Test fun `invite event never enters attendance pending state`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.Start)
        fixture.links.emitInvite(CODE_A)
        assertTrue(fixture.gateway.resolves.isEmpty())
        assertTrue(fixture.local.attendanceWrites.isEmpty())
    }

    @Test fun `session readiness resolves only latest attendance code`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.Start)
        fixture.links.emitAttendance(CODE_A)
        fixture.links.emitAttendance(CODE_B)
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.SetSessionReady(true))
        runCurrent()
        assertEquals(listOf(CODE_B), fixture.gateway.resolves)
    }

    @Test fun `restored code waits for verified session`() = runTest {
        val fixture = fixture(this, storedAttendance = CODE_A)
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.Restore)
        runCurrent()
        assertTrue(fixture.gateway.resolves.isEmpty())
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.SetSessionReady(true))
        runCurrent()
        assertEquals(listOf(CODE_A), fixture.gateway.resolves)
    }

    @Test fun `successful resolution clears pending and emits destination`() = runTest {
        val fixture = fixture(this, storedAttendance = CODE_A)
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.Restore)
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.SetSessionReady(true))
        runCurrent()
        assertEquals(listOf(AttendanceLinkDestination(GROUP_ID, GAME_ID)), fixture.destinations)
        assertEquals(null, fixture.local.attendanceWrites.last())
        assertFalse(fixture.machine.state.value.hasPending)
    }

    @Test fun `duplicate attendance events during resolve produce one request`() = runTest {
        val fixture = fixture(this)
        fixture.gateway.pending = CompletableDeferred()
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.SetSessionReady(true))
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.Start)
        fixture.links.emitAttendance(CODE_A)
        runCurrent()
        fixture.links.emitAttendance(CODE_A)
        assertEquals(listOf(CODE_A), fixture.gateway.resolves)
        fixture.gateway.pending!!.complete(SaqzResult.Success(br.com.saqz.groups.domain.attendance.share.AttendanceLinkDestination(GroupId(GROUP_ID), GAME_ID)))
        runCurrent()
    }

    @Test fun `invalid attendance link clears pending`() = runTest {
        val fixture = fixture(this, storedAttendance = CODE_A)
        fixture.gateway.result = SaqzResult.Failure(AttendanceSharingError.InvalidOrExpired)
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.Restore)
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.SetSessionReady(true))
        runCurrent()
        assertEquals(null, fixture.local.attendanceWrites.last())
        assertEquals(AttendanceLinkUiError.INVALID_OR_EXPIRED, fixture.machine.state.value.error)
    }

    @Test fun `rate limit preserves pending attendance link and retry seconds`() = runTest {
        val fixture = fixture(this, storedAttendance = CODE_A)
        fixture.gateway.result = SaqzResult.Failure(AttendanceSharingError.AttemptLimit(37))
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.Restore)
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.SetSessionReady(true))
        runCurrent()
        assertTrue(fixture.machine.state.value.hasPending)
        assertEquals(AttendanceLinkUiError.ATTEMPT_LIMIT, fixture.machine.state.value.error)
        assertEquals(37, fixture.machine.state.value.retryAfterSeconds)
    }

    @Test fun `temporary failure keeps pending for retry`() = runTest {
        val fixture = fixture(this)
        fixture.gateway.result = SaqzResult.Failure(AttendanceSharingError.DataFailure(DataError.Unknown))
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.SetSessionReady(true))
        fixture.machine.onIntent(DeferredAttendanceLinkIntent.Start)
        fixture.links.emitAttendance(CODE_A)
        runCurrent()
        assertTrue(fixture.machine.state.value.hasPending)
        assertEquals(AttendanceLinkUiError.UNAVAILABLE, fixture.machine.state.value.error)
    }

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope, storedAttendance: String? = null): Fixture {
        val links = FakeLinks()
        val local = FakeLocal(storedAttendance)
        val gateway = FakeGateway()
        val destinations = mutableListOf<AttendanceLinkDestination>()
        return Fixture(
            DeferredAttendanceLinkStateMachine(links, local, gateway, scope, destinations::add),
            links,
            local,
            gateway,
            destinations,
        )
    }

    private class FakeLinks : NativeLinkPort {
        var starts = 0
        private var listener: LinkEventListener? = null
        override fun start(listener: LinkEventListener): Cancelable {
            starts += 1
            this.listener = listener
            return object : Cancelable { override fun cancel() { this@FakeLinks.listener = null } }
        }
        fun emitInvite(code: String) = listener!!.onEvent(GroupLinkEvent.Invite(code))
        fun emitAttendance(code: String) = listener!!.onEvent(GroupLinkEvent.Attendance(code))
    }

    private class FakeLocal(private val storedAttendance: String?) : LocalAccessStatePort {
        val attendanceWrites = mutableListOf<String?>()
        override fun readPendingAttendanceLink(done: ValueCallback) = done.complete(GroupValueResult.Success(storedAttendance))
        override fun writePendingAttendanceLink(value: String?, done: ResultCallback) {
            attendanceWrites += value
            done.complete(GroupOperationResult.Success)
        }
        override fun readSelectedGroupId(done: ValueCallback) = done.complete(GroupValueResult.Success(null))
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) = done.complete(GroupOperationResult.Success)
        override fun readPendingInvite(done: ValueCallback) = done.complete(GroupValueResult.Success(null))
        override fun writePendingInvite(value: String?, done: ResultCallback) = done.complete(GroupOperationResult.Success)
    }

    private class FakeGateway : AttendanceSharingGateway {
        val resolves = mutableListOf<String>()
        var result: SaqzResult<br.com.saqz.groups.domain.attendance.share.AttendanceLinkDestination, AttendanceSharingError> = SaqzResult.Success(br.com.saqz.groups.domain.attendance.share.AttendanceLinkDestination(GroupId(GROUP_ID), GAME_ID))
        var pending: CompletableDeferred<SaqzResult<br.com.saqz.groups.domain.attendance.share.AttendanceLinkDestination, AttendanceSharingError>>? = null
        override suspend fun rotateLink(groupId: GroupId, gameId: String): SaqzResult<AttendanceLinkUrl, AttendanceSharingError> = error("unused")
        override suspend fun resolveLink(code: AttendanceLinkCode): SaqzResult<br.com.saqz.groups.domain.attendance.share.AttendanceLinkDestination, AttendanceSharingError> {
            resolves += code.value
            return pending?.await() ?: result
        }
        override suspend fun readSnapshot(groupId: GroupId, gameId: String): SaqzResult<AttendanceShareSnapshot, AttendanceSharingError> = error("unused")
    }

    private data class Fixture(
        val machine: DeferredAttendanceLinkStateMachine,
        val links: FakeLinks,
        val local: FakeLocal,
        val gateway: FakeGateway,
        val destinations: MutableList<AttendanceLinkDestination>,
    )

    private companion object {
        const val CODE_A = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val CODE_B = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
        const val GROUP_ID = "group-id"
        const val GAME_ID = "game-id"
    }
}
