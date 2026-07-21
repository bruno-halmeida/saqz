package br.com.saqz.androidapp.access

import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.ValueCallback
import br.com.saqz.access.port.ValueResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AndroidLocalAccessAdaptersTest {
    @Test
    fun selectedGroupRoundTripsAsNonSecretValue() {
        val fixture = Fixture()
        var result: ValueResult? = null

        fixture.adapter.writeSelectedGroupId("group-42", ignoringResult())
        fixture.adapter.readSelectedGroupId(valueCallback { result = it })

        assertEquals("group-42", fixture.store.selected)
        assertEquals(ValueResult.Success("group-42"), result)
    }

    @Test
    fun nullSelectedGroupDeletesValue() {
        val fixture = Fixture(selected = "group-42")

        fixture.adapter.writeSelectedGroupId(null, ignoringResult())

        assertNull(fixture.store.selected)
    }

    @Test
    fun pendingInviteRoundTripsThroughEncryptedStoreBoundary() {
        val fixture = Fixture()
        var result: ValueResult? = null

        fixture.adapter.writePendingInvite("opaque-code", ignoringResult())
        fixture.adapter.readPendingInvite(valueCallback { result = it })

        assertEquals("opaque-code", fixture.store.pending)
        assertEquals(ValueResult.Success("opaque-code"), result)
    }

    @Test
    fun nullPendingInviteDeletesCiphertext() {
        val fixture = Fixture(pending = "opaque-code")

        fixture.adapter.writePendingInvite(null, ignoringResult())

        assertNull(fixture.store.pending)
    }

    @Test
    fun storageReadFailureBecomesRecoverableProviderNeutralError() {
        val fixture = Fixture().also { it.store.failReads = true }
        var selected: ValueResult? = null
        var pending: ValueResult? = null

        fixture.adapter.readSelectedGroupId(valueCallback { selected = it })
        fixture.adapter.readPendingInvite(valueCallback { pending = it })

        assertEquals(ValueResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE), selected)
        assertEquals(ValueResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE), pending)
    }

    @Test
    fun storageWriteFailureBecomesRecoverableProviderNeutralError() {
        val fixture = Fixture().also { it.store.failWrites = true }
        var result: OperationResult? = null

        fixture.adapter.writePendingInvite("opaque-code", resultCallback { result = it })

        assertEquals(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE), result)
    }

    @Test
    fun sharePassesTheCompleteUrlUnchanged() {
        val launcher = FakeShareLauncher()
        val adapter = AndroidShareAdapter(launcher)
        var result: OperationResult? = null
        val url = "https://saqz.test-app.link/invite?saqz_invite=opaque_code"

        adapter.share(url, resultCallback { result = it })

        assertEquals(listOf(url), launcher.shared)
        assertSame(OperationResult.Success, result)
    }

    @Test
    fun shareFailureIsRecoverableAndDoesNotEchoSensitiveText() {
        val launcher = FakeShareLauncher(fail = true)
        val adapter = AndroidShareAdapter(launcher)
        var result: OperationResult? = null

        adapter.share("https://example.test/?saqz_invite=sensitive", resultCallback { result = it })

        assertEquals(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE), result)
        assertEquals("AndroidShareAdapter", adapter.toString().substringBefore('@'))
    }

    private class Fixture(selected: String? = null, pending: String? = null) {
        val store = FakeAccessStateStore(selected, pending)
        val adapter = AndroidLocalAccessStateAdapter(store)
    }

    private class FakeAccessStateStore(
        var selected: String?,
        var pending: String?,
    ) : AndroidAccessStateStore {
        var pendingAttendance: String? = null
        var failReads = false
        var failWrites = false

        override fun readSelectedGroupId(): String? = selected.also { if (failReads) error("read failed") }
        override fun writeSelectedGroupId(value: String?) {
            if (failWrites) error("write failed")
            selected = value
        }
        override fun readPendingInvite(): String? = pending.also { if (failReads) error("read failed") }
        override fun writePendingInvite(value: String?) {
            if (failWrites) error("write failed")
            pending = value
        }
        override fun readPendingAttendanceLink(): String? = pendingAttendance.also { if (failReads) error("read failed") }
        override fun writePendingAttendanceLink(value: String?) {
            if (failWrites) error("write failed")
            pendingAttendance = value
        }
    }

    private class FakeShareLauncher(private val fail: Boolean = false) : AndroidShareLauncher {
        val shared = mutableListOf<String>()
        override fun launch(text: String) {
            if (fail) error("share failed")
            shared += text
        }
    }

    private companion object {
        fun ignoringResult() = object : ResultCallback {
            override fun complete(result: OperationResult) = Unit
        }
        fun resultCallback(block: (OperationResult) -> Unit) = object : ResultCallback {
            override fun complete(result: OperationResult) = block(result)
        }
        fun valueCallback(block: (ValueResult) -> Unit) = object : ValueCallback {
            override fun complete(result: ValueResult) = block(result)
        }
    }
}
