package br.com.saqz.groups.port

interface GroupCancelable { fun cancel() }

enum class GroupNativeFailureCode { UNKNOWN }

sealed interface GroupOperationResult {
    data object Success : GroupOperationResult
    data class Failure(val code: GroupNativeFailureCode) : GroupOperationResult
}

sealed interface GroupValueResult {
    data class Success(val value: String?) : GroupValueResult
    data class Failure(val code: GroupNativeFailureCode) : GroupValueResult
}

interface GroupResultCallback { fun complete(result: GroupOperationResult) }

interface GroupValueCallback { fun complete(result: GroupValueResult) }

interface GroupInviteCodeListener { fun onInviteCode(code: String) }

interface NativeGroupLinkPort { fun start(listener: GroupInviteCodeListener): GroupCancelable }

interface LocalGroupStatePort {
    fun readSelectedGroupId(done: GroupValueCallback)
    fun writeSelectedGroupId(value: String?, done: GroupResultCallback)
    fun readPendingInvite(done: GroupValueCallback)
    fun writePendingInvite(value: String?, done: GroupResultCallback)
}

typealias Cancelable = GroupCancelable
typealias NativeFailureCode = GroupNativeFailureCode
typealias OperationResult = GroupOperationResult
typealias ResultCallback = GroupResultCallback
typealias ValueCallback = GroupValueCallback
typealias ValueResult = GroupValueResult
typealias InviteCodeListener = GroupInviteCodeListener
typealias NativeLinkPort = NativeGroupLinkPort
typealias LocalAccessStatePort = LocalGroupStatePort
