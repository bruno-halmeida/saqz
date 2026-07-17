package br.com.saqz.access.port

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NativeAccessPortsTest {
    @Test
    fun `cancelable delegates cancellation without provider type`() {
        var cancellations = 0
        val cancelable = object : Cancelable {
            override fun cancel() { cancellations += 1 }
        }

        cancelable.cancel()

        assertEquals(1, cancellations)
    }

    @Test
    fun `auth observer receives provider neutral user`() {
        var received: AuthState? = null
        val listener = object : AuthStateListener {
            override fun onStateChanged(state: AuthState) { received = state }
        }
        val user = NativeUser("subject", "person@example.test", true, "Person Name")

        listener.onStateChanged(AuthState.SignedIn(user))

        assertEquals(user, assertIs<AuthState.SignedIn>(received).user)
    }

    @Test
    fun `account callback receives successful neutral auth result`() {
        var received: AuthResult? = null
        val callback = object : AuthCallback {
            override fun complete(result: AuthResult) { received = result }
        }
        val user = NativeUser("subject", null, false, "Person Name")

        callback.complete(AuthResult.Success(user))

        assertEquals(user, assertIs<AuthResult.Success>(received).user)
    }

    @Test
    fun `google cancellation is a distinct non failure result`() {
        var received: AuthResult? = null
        val callback = object : AuthCallback {
            override fun complete(result: AuthResult) { received = result }
        }

        callback.complete(AuthResult.Cancelled)

        assertSame(AuthResult.Cancelled, received)
    }

    @Test
    fun `token callback carries token only on success`() {
        var received: TokenResult? = null
        val callback = object : TokenCallback {
            override fun complete(result: TokenResult) { received = result }
        }

        callback.complete(TokenResult.Success("fixture-token"))

        assertEquals("fixture-token", assertIs<TokenResult.Success>(received).token)
    }

    @Test
    fun `native link listener receives opaque code and supports cancellation`() {
        val adapter = RecordingLinkAdapter()
        var received: String? = null

        val cancelable = adapter.start(object : InviteCodeListener {
            override fun onInviteCode(code: String) { received = code }
        })
        adapter.emit("opaque-code")
        cancelable.cancel()

        assertEquals("opaque-code", received)
        assertTrue(adapter.cancelled)
    }

    @Test
    fun `local state callbacks preserve nullable selected and pending values`() {
        val adapter = InMemoryLocalStateAdapter()
        var selected: ValueResult? = null
        var pending: ValueResult? = null

        adapter.writeSelectedGroupId("group-id", ignoringResult())
        adapter.writePendingInvite(null, ignoringResult())
        adapter.readSelectedGroupId(valueCallback { selected = it })
        adapter.readPendingInvite(valueCallback { pending = it })

        assertEquals("group-id", assertIs<ValueResult.Success>(selected).value)
        assertEquals(null, assertIs<ValueResult.Success>(pending).value)
    }

    @Test
    fun `external adapters implement all exported ports without native SDK types`() {
        val adapters = ExternalAdapters()
        var shared: OperationResult? = null

        adapters.share("invite", object : ResultCallback {
            override fun complete(result: OperationResult) { shared = result }
        })

        assertSame(OperationResult.Success, shared)
    }

    private fun ignoringResult() = object : ResultCallback {
        override fun complete(result: OperationResult) = Unit
    }

    private fun valueCallback(block: (ValueResult) -> Unit) = object : ValueCallback {
        override fun complete(result: ValueResult) = block(result)
    }

    private class RecordingLinkAdapter : NativeLinkPort {
        private var listener: InviteCodeListener? = null
        var cancelled = false

        override fun start(listener: InviteCodeListener): Cancelable {
            this.listener = listener
            return object : Cancelable {
                override fun cancel() {
                    cancelled = true
                    this@RecordingLinkAdapter.listener = null
                }
            }
        }

        fun emit(code: String) = listener?.onInviteCode(code)
    }

    private class InMemoryLocalStateAdapter : LocalAccessStatePort {
        private var selected: String? = null
        private var pending: String? = null
        override fun readSelectedGroupId(done: ValueCallback) = done.complete(ValueResult.Success(selected))
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) {
            selected = value
            done.complete(OperationResult.Success)
        }
        override fun readPendingInvite(done: ValueCallback) = done.complete(ValueResult.Success(pending))
        override fun writePendingInvite(value: String?, done: ResultCallback) {
            pending = value
            done.complete(OperationResult.Success)
        }
    }

    private class ExternalAdapters : NativeAuthPort, NativeLinkPort, LocalAccessStatePort, NativeSharePort {
        override fun observe(listener: AuthStateListener) = noOpCancelable()
        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = done.complete(AuthResult.Cancelled)
        override fun sendVerification(done: ResultCallback) = done.complete(OperationResult.Success)
        override fun reloadUser(done: AuthCallback) = Unit
        override fun sendPasswordReset(email: String, done: ResultCallback) = done.complete(OperationResult.Success)
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit
        override fun signOut(done: ResultCallback) = done.complete(OperationResult.Success)
        override fun start(listener: InviteCodeListener) = noOpCancelable()
        override fun readSelectedGroupId(done: ValueCallback) = done.complete(ValueResult.Success(null))
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
        override fun readPendingInvite(done: ValueCallback) = done.complete(ValueResult.Success(null))
        override fun writePendingInvite(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
        override fun share(text: String, done: ResultCallback) = done.complete(OperationResult.Success)

        private fun noOpCancelable() = object : Cancelable {
            override fun cancel() = Unit
        }
    }
}
