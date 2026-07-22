package br.com.saqz.androidapp.access

import br.com.saqz.access.port.NativeLinkPort
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort

internal class AndroidGroupLinkAdapter(private val delegate: NativeLinkPort) : NativeGroupLinkPort {
    override fun start(listener: GroupLinkEventListener): GroupCancelable {
        val cancelable = delegate.start(object : br.com.saqz.access.port.InviteCodeListener {
            override fun onInviteCode(code: String) = listener.onEvent(GroupLinkEvent.Invite(code))
        })
        return object : GroupCancelable { override fun cancel() = cancelable.cancel() }
    }
}

internal class AndroidLocalGroupStateAdapter(
    private val store: AndroidAccessStateStore,
) : LocalGroupStatePort {
    override fun readSelectedGroupId(done: GroupValueCallback) = read(done, store::readSelectedGroupId)
    override fun writeSelectedGroupId(value: String?, done: GroupResultCallback) = write(done) { store.writeSelectedGroupId(value) }
    override fun readPendingInvite(done: GroupValueCallback) = read(done, store::readPendingInvite)
    override fun writePendingInvite(value: String?, done: GroupResultCallback) = write(done) { store.writePendingInvite(value) }
    override fun readPendingAttendanceLink(done: GroupValueCallback) = read(done, store::readPendingAttendanceLink)
    override fun writePendingAttendanceLink(value: String?, done: GroupResultCallback) = write(done) { store.writePendingAttendanceLink(value) }

    private fun read(done: GroupValueCallback, block: () -> String?) {
        try {
            done.complete(GroupValueResult.Success(block()))
        } catch (_: Exception) {
            done.complete(GroupValueResult.Failure(br.com.saqz.groups.port.GroupNativeFailureCode.UNKNOWN))
        }
    }

    private fun write(done: GroupResultCallback, block: () -> Unit) {
        try {
            block()
            done.complete(GroupOperationResult.Success)
        } catch (_: Exception) {
            done.complete(GroupOperationResult.Failure(br.com.saqz.groups.port.GroupNativeFailureCode.UNKNOWN))
        }
    }
}
