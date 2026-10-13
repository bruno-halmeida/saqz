package br.com.saqz.webapp.access

import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.groups.port.GroupNativeFailureCode
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.webapp.interop.WebStorage
import br.com.saqz.access.domain.port.NativeFailureCode as AccessFailureCode
import br.com.saqz.access.domain.port.OperationResult as AccessOperationResult
import br.com.saqz.access.domain.port.ResultCallback as AccessResultCallback
import br.com.saqz.access.domain.port.ValueCallback as AccessValueCallback
import br.com.saqz.access.domain.port.ValueResult as AccessValueResult

/**
 * Estado local de acesso no `localStorage`: grupo selecionado e links pendentes (convite e
 * presença). No Android fica cifrado no Keystore; no navegador o `localStorage` já é por origin
 * e por perfil, e o que guarda são códigos de uso único que o backend valida de novo.
 */
internal class WebAccessStateStore {
    fun read(key: String): String? = WebStorage.read(key)
    fun write(key: String, value: String?): Boolean = WebStorage.write(key, value)

    companion object {
        const val SELECTED_GROUP = "saqz.access.selectedGroupId"
        const val PENDING_INVITE = "saqz.access.pendingInvite"
        const val PENDING_ATTENDANCE_LINK = "saqz.access.pendingAttendanceLink"
    }
}

internal class WebLocalAccessStateAdapter(private val store: WebAccessStateStore) : LocalAccessStatePort {
    override fun readSelectedGroupId(done: AccessValueCallback) =
        done.complete(AccessValueResult.Success(store.read(WebAccessStateStore.SELECTED_GROUP)))

    override fun writeSelectedGroupId(value: String?, done: AccessResultCallback) =
        done.complete(result(store.write(WebAccessStateStore.SELECTED_GROUP, value)))

    override fun readPendingInvite(done: AccessValueCallback) =
        done.complete(AccessValueResult.Success(store.read(WebAccessStateStore.PENDING_INVITE)))

    override fun writePendingInvite(value: String?, done: AccessResultCallback) =
        done.complete(result(store.write(WebAccessStateStore.PENDING_INVITE, value)))

    private fun result(ok: Boolean): AccessOperationResult =
        if (ok) AccessOperationResult.Success else AccessOperationResult.Failure(AccessFailureCode.UNKNOWN)
}

internal class WebLocalGroupStateAdapter(private val store: WebAccessStateStore) : LocalGroupStatePort {
    override fun readSelectedGroupId(done: GroupValueCallback) =
        done.complete(GroupValueResult.Success(store.read(WebAccessStateStore.SELECTED_GROUP)))

    override fun writeSelectedGroupId(value: String?, done: GroupResultCallback) =
        done.complete(result(store.write(WebAccessStateStore.SELECTED_GROUP, value)))

    override fun readPendingInvite(done: GroupValueCallback) =
        done.complete(GroupValueResult.Success(store.read(WebAccessStateStore.PENDING_INVITE)))

    override fun writePendingInvite(value: String?, done: GroupResultCallback) =
        done.complete(result(store.write(WebAccessStateStore.PENDING_INVITE, value)))

    override fun readPendingAttendanceLink(done: GroupValueCallback) =
        done.complete(GroupValueResult.Success(store.read(WebAccessStateStore.PENDING_ATTENDANCE_LINK)))

    override fun writePendingAttendanceLink(value: String?, done: GroupResultCallback) =
        done.complete(result(store.write(WebAccessStateStore.PENDING_ATTENDANCE_LINK, value)))

    private fun result(ok: Boolean): GroupOperationResult =
        if (ok) GroupOperationResult.Success else GroupOperationResult.Failure(GroupNativeFailureCode.UNKNOWN)
}
