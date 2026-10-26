package br.com.saqz.groups.port

import br.com.saqz.groups.domain.attendance.AttendanceIntent

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
    /** [intent] viaja no endereço: `saqz_intent=decline` é o "não vou"; sem ele, confirma. */
    data class Attendance(
        val code: String,
        val intent: AttendanceIntent = AttendanceIntent.Confirm,
    ) : GroupLinkEvent
    /**
     * Toque em push (ou no card da Live Activity): com [gameId] abre o jogo; sem, abre a central. [pickPosition] é o
     * "Vou" do push de jogo com posições (DEB-107): abre a tela de escolher posição, sem confirmar nada por baixo.
     */
    /**
     * Botão da hora do acerto (FIN-013): `https://links.saqz.app/acerto/<código>` ou `?saqz_settle=<código>` (o
     * referrer do Play). O app resolve o código no servidor e abre o pagamento daquele jogo ou mês.
     */
    data class Settle(val code: String) : GroupLinkEvent

    data class NotificationOpen(
        val groupId: String?,
        val gameId: String? = null,
        val channel: String? = null,
        val pickPosition: Boolean = false,
    ) : GroupLinkEvent
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
