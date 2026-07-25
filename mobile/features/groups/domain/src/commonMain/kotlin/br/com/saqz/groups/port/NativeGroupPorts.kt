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

sealed interface GroupLinkEvent {
    data class Invite(val code: String) : GroupLinkEvent
    data class Attendance(val code: String) : GroupLinkEvent
}

interface GroupLinkEventListener { fun onEvent(event: GroupLinkEvent) }

interface NativeGroupLinkPort { fun start(listener: GroupLinkEventListener): GroupCancelable }

interface LocalGroupStatePort {
    fun readSelectedGroupId(done: GroupValueCallback)
    fun writeSelectedGroupId(value: String?, done: GroupResultCallback)
    fun readPendingInvite(done: GroupValueCallback)
    fun writePendingInvite(value: String?, done: GroupResultCallback)
    fun readPendingAttendanceLink(done: GroupValueCallback)
    fun writePendingAttendanceLink(value: String?, done: GroupResultCallback)
}

typealias Cancelable = GroupCancelable
typealias NativeFailureCode = GroupNativeFailureCode
typealias OperationResult = GroupOperationResult
typealias ResultCallback = GroupResultCallback
typealias ValueCallback = GroupValueCallback
typealias ValueResult = GroupValueResult
typealias LinkEvent = GroupLinkEvent
typealias LinkEventListener = GroupLinkEventListener
typealias NativeLinkPort = NativeGroupLinkPort
typealias LocalAccessStatePort = LocalGroupStatePort
