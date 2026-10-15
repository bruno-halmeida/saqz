package br.com.saqz.androidapp.access

import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.port.ValueResult
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A regra do convite guardado depende de acesso (logout) e grupos (coordinator) enxergarem o mesmo
 * convite: na composição os dois adapters recebem o mesmo store, e a chave é uma só
 * (`pending_invite_ciphertext`). Aqui os dois adapters reais conversam por um store único.
 */
class AndroidPendingInviteSharedKeyTest {
    private val store = SingleSlotStore()
    private val access = AndroidLocalAccessStateAdapter(store)
    private val groups = AndroidLocalGroupStateAdapter(store)

    @Test
    fun writtenByAccessIsReadByGroups() {
        access.writePendingInvite("convite", accessIgnore)
        assertEquals(GroupValueResult.Success("convite"), readGroups())
    }

    @Test
    fun erasedByAccessIsNullForGroups() {
        groups.writePendingInvite("convite", groupIgnore)
        access.writePendingInvite(null, accessIgnore)
        assertEquals(GroupValueResult.Success(null), readGroups())
    }

    @Test
    fun writtenByGroupsIsReadByAccess() {
        groups.writePendingInvite("convite", groupIgnore)
        assertEquals(ValueResult.Success("convite"), readAccess())
    }

    @Test
    fun erasedByGroupsIsNullForAccess() {
        access.writePendingInvite("convite", accessIgnore)
        groups.writePendingInvite(null, groupIgnore)
        assertEquals(ValueResult.Success(null), readAccess())
    }

    private fun readGroups(): GroupValueResult {
        var result: GroupValueResult? = null
        groups.readPendingInvite(object : GroupValueCallback {
            override fun complete(result2: GroupValueResult) { result = result2 }
        })
        return checkNotNull(result)
    }

    private fun readAccess(): ValueResult {
        var result: ValueResult? = null
        access.readPendingInvite(object : ValueCallback {
            override fun complete(result2: ValueResult) { result = result2 }
        })
        return checkNotNull(result)
    }

    private val accessIgnore = object : ResultCallback {
        override fun complete(result: OperationResult) = Unit
    }
    private val groupIgnore = object : GroupResultCallback {
        override fun complete(result: GroupOperationResult) = Unit
    }

    private class SingleSlotStore : AndroidAccessStateStore {
        private var pending: String? = null
        override fun readSelectedGroupId(): String? = null
        override fun writeSelectedGroupId(value: String?) = Unit
        override fun readPendingInvite(): String? = pending
        override fun writePendingInvite(value: String?) { pending = value }
        override fun readPendingAttendanceLink(): String? = null
        override fun writePendingAttendanceLink(value: String?) = Unit
    }
}
