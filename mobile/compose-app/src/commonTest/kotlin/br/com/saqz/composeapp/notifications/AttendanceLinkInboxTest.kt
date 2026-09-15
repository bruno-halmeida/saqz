package br.com.saqz.composeapp.notifications

import br.com.saqz.groups.port.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AttendanceLinkInboxTest {
    @Test fun preservesLinkAcrossLoginAndRestartUntilNavigationConsumesIt() = runTest {
        val ports = Ports()
        val inbox = AttendanceLinkInbox(ports, ports, backgroundScope)
        inbox.start()
        ports.listener?.onEvent(GroupLinkEvent.Attendance("first"))
        runCurrent()
        assertEquals("first", ports.stored)
        assertEquals("first", inbox.pending.value)
        inbox.stop()
        val restored = AttendanceLinkInbox(ports, ports, backgroundScope)
        restored.start()
        assertEquals("first", restored.pending.value)
        restored.consume("other")
        assertEquals("first", ports.stored)
        restored.consume("first")
        assertNull(ports.stored)
        assertNull(restored.pending.value)
    }

    @Test fun staleStorageReadCannotReplaceNewLinkAndFailedClearRetainsPending() = runTest {
        val ports = Ports().apply { deferRead = true }
        val inbox = AttendanceLinkInbox(ports, ports, backgroundScope)
        inbox.start()
        ports.listener?.onEvent(GroupLinkEvent.Attendance("new"))
        ports.read?.complete(GroupValueResult.Success("old"))
        runCurrent()
        assertEquals("new", inbox.pending.value)
        ports.failWrite = true
        inbox.consume("new")
        assertEquals("new", inbox.pending.value)
        assertEquals("new", ports.stored)
    }

    private class Ports : NativeGroupLinkPort, LocalGroupStatePort {
        var listener: GroupLinkEventListener? = null
        var stored: String? = null
        var deferRead = false
        var failWrite = false
        var read: GroupValueCallback? = null
        override fun start(listener: GroupLinkEventListener): GroupCancelable {
            this.listener = listener
            return object : GroupCancelable { override fun cancel() { this@Ports.listener = null } }
        }
        override fun readPendingAttendanceLink(done: GroupValueCallback) {
            read = done
            if (!deferRead) done.complete(GroupValueResult.Success(stored))
        }
        override fun writePendingAttendanceLink(value: String?, done: GroupResultCallback) {
            if (!failWrite) stored = value
            done.complete(if (failWrite) GroupOperationResult.Failure(GroupNativeFailureCode.UNKNOWN) else GroupOperationResult.Success)
        }
        override fun readSelectedGroupId(done: GroupValueCallback) = error("unused")
        override fun writeSelectedGroupId(value: String?, done: GroupResultCallback) = error("unused")
        override fun readPendingInvite(done: GroupValueCallback) = error("unused")
        override fun writePendingInvite(value: String?, done: GroupResultCallback) = error("unused")
    }
}
