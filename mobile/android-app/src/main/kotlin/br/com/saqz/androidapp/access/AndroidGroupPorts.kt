package br.com.saqz.androidapp.access

import br.com.saqz.access.port.LocalAccessStatePort
import br.com.saqz.access.port.NativeLinkPort
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.ValueCallback
import br.com.saqz.access.port.ValueResult
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupInviteCodeListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort

internal class AndroidGroupLinkAdapter(private val delegate: NativeLinkPort) : NativeGroupLinkPort {
    override fun start(listener: GroupInviteCodeListener): GroupCancelable {
        val cancelable = delegate.start(object : br.com.saqz.access.port.InviteCodeListener { override fun onInviteCode(code: String) = listener.onInviteCode(code) })
        return object : GroupCancelable { override fun cancel() = cancelable.cancel() }
    }
}

internal class AndroidGroupStateAdapter(private val delegate: LocalAccessStatePort) : LocalGroupStatePort {
    override fun readSelectedGroupId(done: GroupValueCallback) = delegate.readSelectedGroupId(value(done))
    override fun writeSelectedGroupId(value: String?, done: GroupResultCallback) = delegate.writeSelectedGroupId(value, result(done))
    override fun readPendingInvite(done: GroupValueCallback) = delegate.readPendingInvite(value(done))
    override fun writePendingInvite(value: String?, done: GroupResultCallback) = delegate.writePendingInvite(value, result(done))
    private fun value(done: GroupValueCallback) = object : ValueCallback { override fun complete(result: ValueResult) = done.complete(if (result is ValueResult.Success) GroupValueResult.Success(result.value) else GroupValueResult.Failure(br.com.saqz.groups.port.GroupNativeFailureCode.UNKNOWN)) }
    private fun result(done: GroupResultCallback) = object : ResultCallback { override fun complete(result: OperationResult) = done.complete(if (result is OperationResult.Success) GroupOperationResult.Success else GroupOperationResult.Failure(br.com.saqz.groups.port.GroupNativeFailureCode.UNKNOWN)) }
}
