package br.com.saqz.access.presentation.appaccess

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerializedNativeAuthPortTest {
    @Test
    fun `new login waits for stale custom sign in then cleanup before mutating Firebase`() {
        val firebase = FakeAuth()
        val auth = SerializedNativeAuthPort(firebase)
        val observed = mutableListOf<AuthState>()
        auth.observe(object : AuthStateListener {
            override fun onStateChanged(state: AuthState) { observed += state }
        })
        var oldCallback = 0
        var newCallback = 0

        auth.signInWithCustomToken("token-a", callback { oldCallback++ })
        auth.signInWithPassword("b@example.test", "password", callback { newCallback++ })
        assertEquals(listOf("custom:token-a"), firebase.operations)

        firebase.emit(AuthState.SignedIn(user("firebase-a")))
        firebase.completeCustom(AuthResult.Success(user("firebase-a")))
        assertEquals(listOf("custom:token-a", "signOut"), firebase.operations)
        assertEquals(0, oldCallback)
        assertEquals(0, newCallback)
        firebase.completeSignOut()
        assertEquals(listOf("custom:token-a", "signOut", "password:b@example.test"), firebase.operations)
        firebase.emit(AuthState.SignedIn(user("firebase-b")))
        firebase.completePassword(AuthResult.Success(user("firebase-b")))
        firebase.emit(AuthState.SignedIn(user("firebase-a")))

        assertEquals(0, oldCallback)
        assertEquals(1, newCallback)
        assertTrue(observed.none { it is AuthState.SignedIn && it.user.subject == "firebase-a" })
        assertTrue(observed.any { it is AuthState.SignedIn && it.user.subject == "firebase-b" })
    }

    @Test
    fun `logout during custom sign in suppresses callback and publishes signed out after cleanup`() {
        val firebase = FakeAuth()
        val auth = SerializedNativeAuthPort(firebase)
        val observed = mutableListOf<AuthState>()
        auth.observe(object : AuthStateListener {
            override fun onStateChanged(state: AuthState) { observed += state }
        })
        var logoutResult: OperationResult? = null
        auth.signInWithCustomToken("token-a", callback {})
        auth.signOut(object : ResultCallback {
            override fun complete(result: OperationResult) { logoutResult = result }
        })

        firebase.completeCustom(AuthResult.Success(user("firebase-a")))
        assertEquals(listOf("custom:token-a", "signOut"), firebase.operations)
        firebase.completeSignOut()

        assertEquals(OperationResult.Success, logoutResult)
        assertEquals(null, firebase.currentUser)
        assertEquals(listOf<AuthState>(AuthState.SignedOut), observed)
    }

    @Test
    fun `callback before stale observer is also quarantined`() {
        val firebase = FakeAuth()
        val auth = SerializedNativeAuthPort(firebase)
        val observed = mutableListOf<AuthState>()
        auth.observe(object : AuthStateListener {
            override fun onStateChanged(state: AuthState) { observed += state }
        })
        var newCallback = 0

        auth.signInWithCustomToken("token-a", callback {})
        auth.signInWithPassword("b@example.test", "password", callback { newCallback++ })
        firebase.completeCustomThenObserver(AuthResult.Success(user("firebase-a")))
        assertEquals(listOf("custom:token-a", "signOut"), firebase.operations)
        assertTrue(observed.none { it is AuthState.SignedIn && it.user.subject == "firebase-a" })

        firebase.completeSignOut(observerBeforeCallback = false)
        firebase.emit(AuthState.SignedIn(user("firebase-b")))
        firebase.completePassword(AuthResult.Success(user("firebase-b")))
        assertEquals(1, newCallback)
        assertEquals("firebase-b", firebase.currentUser?.subject)
        assertTrue(observed.any { it is AuthState.SignedIn && it.user.subject == "firebase-b" })
        assertTrue(observed.none { it is AuthState.SignedIn && it.user.subject == "firebase-a" })
    }

    @Test
    fun `multiple consumers share one provider observer`() {
        val firebase = FakeAuth()
        val auth = SerializedNativeAuthPort(firebase)
        val first = mutableListOf<AuthState>()
        val second = mutableListOf<AuthState>()
        auth.observe(object : AuthStateListener {
            override fun onStateChanged(state: AuthState) { first += state }
        })
        auth.observe(object : AuthStateListener {
            override fun onStateChanged(state: AuthState) { second += state }
        })

        assertEquals(1, firebase.observeCalls)
        firebase.emit(AuthState.SignedOut)
        assertEquals(listOf<AuthState>(AuthState.SignedOut), first)
        assertEquals(listOf<AuthState>(AuthState.SignedOut), second)
    }

    private fun callback(block: () -> Unit) = object : AuthCallback {
        override fun complete(result: AuthResult) = block()
    }

    private fun user(subject: String) = NativeUser(subject, "$subject@example.test", true, subject)

    private class FakeAuth : NativeAuthPort {
        val operations = mutableListOf<String>()
        private var observer: AuthStateListener? = null
        private var custom: AuthCallback? = null
        private var password: AuthCallback? = null
        private var signOut: ResultCallback? = null
        var currentUser: NativeUser? = null
        var observeCalls = 0

        override fun observe(listener: AuthStateListener): Cancelable {
            observeCalls++
            observer = listener
            return object : Cancelable { override fun cancel() { observer = null } }
        }

        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit

        override fun signInWithPassword(email: String, password: String, done: AuthCallback) {
            operations += "password:$email"
            this.password = done
        }

        override fun signInWithGoogle(done: AuthCallback) = Unit

        override fun signInWithCustomToken(customToken: String, done: AuthCallback) {
            operations += "custom:$customToken"
            custom = done
        }

        override fun sendVerification(done: ResultCallback) = Unit
        override fun reloadUser(done: AuthCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit

        override fun signOut(done: ResultCallback) {
            operations += "signOut"
            signOut = done
        }

        fun completeCustom(result: AuthResult) {
            val callback = custom ?: error("custom sign in was not started")
            custom = null
            if (result is AuthResult.Success) currentUser = result.user
            callback.complete(result)
        }

        fun completeCustomThenObserver(result: AuthResult) {
            val callback = custom ?: error("custom sign in was not started")
            custom = null
            if (result is AuthResult.Success) currentUser = result.user
            callback.complete(result)
            val user = (result as? AuthResult.Success)?.user ?: return
            observer?.onStateChanged(AuthState.SignedIn(user))
        }

        fun completePassword(result: AuthResult) {
            val callback = password ?: error("password sign in was not started")
            password = null
            if (result is AuthResult.Success) currentUser = result.user
            callback.complete(result)
        }

        fun completeSignOut(observerBeforeCallback: Boolean = true) {
            val callback = signOut ?: error("sign out was not started")
            signOut = null
            currentUser = null
            if (observerBeforeCallback) observer?.onStateChanged(AuthState.SignedOut)
            callback.complete(OperationResult.Success)
            if (!observerBeforeCallback) observer?.onStateChanged(AuthState.SignedOut)
        }

        fun emit(state: AuthState) = observer?.onStateChanged(state)
    }
}
