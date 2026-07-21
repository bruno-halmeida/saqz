package br.com.saqz.androidapp.access

import br.com.saqz.access.port.LocalAccessStatePort
import br.com.saqz.access.port.NativeLinkPort
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.ValueCallback
import br.com.saqz.access.port.ValueResult
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

internal class AndroidGroupStateAdapter(private val delegate: LocalAccessStatePort) : LocalGroupStatePort {
    override fun readSelectedGroupId(done: GroupValueCallback) = delegate.readSelectedGroupId(value(done))
    override fun writeSelectedGroupId(value: String?, done: GroupResultCallback) = delegate.writeSelectedGroupId(value, result(done))
    override fun readPendingInvite(done: GroupValueCallback) = delegate.readPendingInvite(value(done))
    override fun writePendingInvite(value: String?, done: GroupResultCallback) = delegate.writePendingInvite(value, result(done))
    override fun readPendingAttendanceLink(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))
    override fun writePendingAttendanceLink(value: String?, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
    private fun value(done: GroupValueCallback) = object : ValueCallback { override fun complete(result: ValueResult) = done.complete(if (result is ValueResult.Success) GroupValueResult.Success(result.value) else GroupValueResult.Failure(br.com.saqz.groups.port.GroupNativeFailureCode.UNKNOWN)) }
    private fun result(done: GroupResultCallback) = object : ResultCallback { override fun complete(result: OperationResult) = done.complete(if (result is OperationResult.Success) GroupOperationResult.Success else GroupOperationResult.Failure(br.com.saqz.groups.port.GroupNativeFailureCode.UNKNOWN)) }
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
