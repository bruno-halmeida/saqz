package br.com.saqz.androidapp.access

import br.com.saqz.groups.port.GroupNativeFailureCode
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidLocalGroupStateAdapterTest {
    @Test
    fun pendingAttendanceLinkRoundTripsThroughStoreBoundary() {
        val fixture = Fixture()
        var result: GroupValueResult? = null

        fixture.adapter.writePendingAttendanceLink("attendance-code", ignoringResult())
        fixture.adapter.readPendingAttendanceLink(valueCallback { result = it })

        assertEquals("attendance-code", fixture.store.pendingAttendance)
        assertEquals(GroupValueResult.Success("attendance-code"), result)
    }

    @Test
    fun nullPendingAttendanceLinkDeletesValue() {
        val fixture = Fixture(pendingAttendance = "attendance-code")

        fixture.adapter.writePendingAttendanceLink(null, ignoringResult())

        assertNull(fixture.store.pendingAttendance)
    }

    @Test
    fun storageReadFailureBecomesProviderNeutralError() {
        val fixture = Fixture().also { it.store.failReads = true }
        var result: GroupValueResult? = null

        fixture.adapter.readPendingAttendanceLink(valueCallback { result = it })

        assertEquals(GroupValueResult.Failure(GroupNativeFailureCode.UNKNOWN), result)
    }

    @Test
    fun storageWriteFailureBecomesProviderNeutralError() {
        val fixture = Fixture().also { it.store.failWrites = true }
        var result: GroupOperationResult? = null

        fixture.adapter.writePendingAttendanceLink("attendance-code", resultCallback { result = it })

        assertEquals(GroupOperationResult.Failure(GroupNativeFailureCode.UNKNOWN), result)
    }

    private class Fixture(pendingAttendance: String? = null) {
        val store = FakeGroupAccessStateStore(pendingAttendance)
        val adapter = AndroidLocalGroupStateAdapter(store)
    }

    private class FakeGroupAccessStateStore(var pendingAttendance: String?) : AndroidAccessStateStore {
        var failReads = false
        var failWrites = false

        override fun readSelectedGroupId(): String? = null
        override fun writeSelectedGroupId(value: String?) = Unit
        override fun readPendingInvite(): String? = null
        override fun writePendingInvite(value: String?) = Unit
        override fun readPendingAttendanceLink(): String? = pendingAttendance.also { if (failReads) error("read failed") }
        override fun writePendingAttendanceLink(value: String?) {
            if (failWrites) error("write failed")
            pendingAttendance = value
        }
    }

    private companion object {
        fun ignoringResult() = object : GroupResultCallback {
            override fun complete(result: GroupOperationResult) = Unit
        }
        fun resultCallback(block: (GroupOperationResult) -> Unit) = object : GroupResultCallback {
            override fun complete(result: GroupOperationResult) = block(result)
        }
        fun valueCallback(block: (GroupValueResult) -> Unit) = object : GroupValueCallback {
            override fun complete(result: GroupValueResult) = block(result)
        }
    }
}
