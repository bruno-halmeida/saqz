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
        assertEquals(1, oldCallback)
        assertEquals(0, newCallback)
        firebase.completeSignOut()
        assertEquals(listOf("custom:token-a", "signOut", "password:b@example.test"), firebase.operations)
        firebase.emit(AuthState.SignedIn(user("firebase-b")))
        firebase.completePassword(AuthResult.Success(user("firebase-b")))
        firebase.emit(AuthState.SignedIn(user("firebase-a")))

        assertEquals(1, oldCallback)
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
    fun `explicit same uid relogin reauthorizes a previously canceled provider subject`() {
        val firebase = FakeAuth()
        val auth = SerializedNativeAuthPort(firebase)
        val observed = mutableListOf<AuthState>()
        auth.observe(object : AuthStateListener {
            override fun onStateChanged(state: AuthState) { observed += state }
        })
        var canceled = 0
        var relogin = 0

        auth.signInWithCustomToken("token-a", callback { canceled++ })
        auth.signInWithPassword("b@example.test", "password", callback {})
        firebase.completeCustom(AuthResult.Success(user("firebase-a")))
        firebase.completeSignOut()
        firebase.completePassword(AuthResult.Success(user("firebase-b")))
        firebase.emit(AuthState.SignedIn(user("firebase-a")))
        val staleCount = observed.count { it is AuthState.SignedIn && it.user.subject == "firebase-a" }
        if (staleCount != 0) error("stale observed=$observed")

        auth.signInWithCustomToken("token-a-reauthorize", callback { relogin++ })
        assertEquals(listOf("custom:token-a", "signOut", "password:b@example.test", "signOut"), firebase.operations)
        firebase.completeSignOut()
        assertEquals("custom:token-a-reauthorize", firebase.operations.last())
        firebase.completeCustom(AuthResult.Success(user("firebase-a")))
        firebase.emit(AuthState.SignedIn(user("firebase-a")))

        assertEquals(1, canceled)
        assertEquals(1, relogin)
        assertEquals("firebase-a", firebase.currentUser?.subject)
        assertEquals(1, observed.count { it is AuthState.SignedIn && it.user.subject == "firebase-a" })
    }

    @Test
    fun `cleanup failure settles canceled and queued callers without starting replacement`() {
        val firebase = FakeAuth()
        val auth = SerializedNativeAuthPort(firebase)
        var oldResult: AuthResult? = null
        var newResult: AuthResult? = null

        auth.signInWithCustomToken("token-a", callback { oldResult = AuthResult.Cancelled })
        auth.signInWithPassword("b@example.test", "password", callback { newResult = AuthResult.Cancelled })
        firebase.completeCustom(AuthResult.Success(user("firebase-a")))
        firebase.completeSignOut(OperationResult.Failure(br.com.saqz.access.domain.port.NativeFailureCode.UNKNOWN))

        assertEquals(AuthResult.Cancelled, oldResult)
        assertEquals(AuthResult.Cancelled, newResult)
        assertEquals(listOf("custom:token-a", "signOut"), firebase.operations)
        assertEquals("firebase-a", firebase.currentUser?.subject)
    }

    @Test
    fun `replacement arriving during cleanup starts after successful barrier`() {
        val firebase = FakeAuth()
        val auth = SerializedNativeAuthPort(firebase)
        var firstResult: AuthResult? = null
        var secondResult: AuthResult? = null
        var thirdResult: AuthResult? = null

        auth.signInWithCustomToken("token-a", callback { firstResult = AuthResult.Cancelled })
        auth.signInWithPassword("b@example.test", "password", callback { secondResult = AuthResult.Cancelled })
        firebase.completeCustom(AuthResult.Success(user("firebase-a")))
        auth.signInWithCustomToken("token-c", object : AuthCallback {
            override fun complete(result: AuthResult) { thirdResult = result }
        })

        assertEquals(AuthResult.Cancelled, secondResult)
        assertEquals(listOf("custom:token-a", "signOut"), firebase.operations)
        firebase.completeSignOut()
        assertEquals(listOf("custom:token-a", "signOut", "custom:token-c"), firebase.operations)
        firebase.completeCustom(AuthResult.Success(user("firebase-c")))

        assertEquals(AuthResult.Cancelled, firstResult)
        assertEquals(AuthResult.Success(user("firebase-c")), thirdResult)
    }

    @Test
    fun `replacement arriving during failed cleanup is canceled without SDK mutation`() {
        val firebase = FakeAuth()
        val auth = SerializedNativeAuthPort(firebase)
        var secondResult: AuthResult? = null
        var thirdResult: AuthResult? = null

        auth.signInWithCustomToken("token-a", callback {})
        auth.signInWithPassword("b@example.test", "password", callback { secondResult = AuthResult.Cancelled })
        firebase.completeCustom(AuthResult.Success(user("firebase-a")))
        auth.signInWithCustomToken("token-c", callback { thirdResult = AuthResult.Cancelled })
        firebase.completeSignOut(OperationResult.Failure(br.com.saqz.access.domain.port.NativeFailureCode.UNKNOWN))

        assertEquals(AuthResult.Cancelled, secondResult)
        assertEquals(AuthResult.Cancelled, thirdResult)
        assertEquals(listOf("custom:token-a", "signOut"), firebase.operations)
    }

    @Test
    fun `canceled callback may enqueue a newer login without being erased by queue cleanup`() {
        val firebase = FakeAuth()
        val auth = SerializedNativeAuthPort(firebase)
        var oldCanceled = 0
        var queuedCanceled = 0

        auth.signInWithCustomToken("token-a", callback {
            oldCanceled++
            auth.signInWithPassword("reentrant@example.test", "password", callback {})
        })
        auth.signInWithPassword("queued@example.test", "password", callback { queuedCanceled++ })
        firebase.completeCustom(AuthResult.Success(user("firebase-a")))
        firebase.completeSignOut()
        assertEquals(listOf("custom:token-a", "signOut", "password:reentrant@example.test"), firebase.operations)
        firebase.emit(AuthState.SignedIn(user("firebase-c")))
        firebase.completePassword(AuthResult.Success(user("firebase-c")))

        assertEquals(1, oldCanceled)
        assertEquals(1, queuedCanceled)
        assertEquals("firebase-c", firebase.currentUser?.subject)
    }

    @Test
    fun `observer callback can synchronously request logout and settles on signed out`() {
        val firebase = FakeAuth()
        val auth = SerializedNativeAuthPort(firebase)
        var logoutResult: OperationResult? = null
        val observed = mutableListOf<AuthState>()
        auth.observe(object : AuthStateListener {
            override fun onStateChanged(state: AuthState) {
                observed += state
                if (state is AuthState.SignedIn) {
                    auth.signOut(object : ResultCallback {
                        override fun complete(result: OperationResult) { logoutResult = result }
                    })
                }
            }
        })

        firebase.emit(AuthState.SignedIn(user("firebase-a")))
        assertEquals(listOf("signOut"), firebase.operations)
        firebase.completeSignOut()

        assertEquals(OperationResult.Success, logoutResult)
        assertEquals(null, firebase.currentUser)
        assertEquals(listOf(AuthState.SignedIn(user("firebase-a")), AuthState.SignedOut), observed)
    }

    @Test
    fun `duplicate provider completion settles caller exactly once`() {
        val firebase = FakeAuth()
        val auth = SerializedNativeAuthPort(firebase)
        var callbacks = 0

        auth.signInWithCustomToken("token-a", callback { callbacks++ })
        firebase.completeCustomTwice(AuthResult.Success(user("firebase-a")))

        assertEquals(1, callbacks)
        assertEquals("firebase-a", firebase.currentUser?.subject)
    }

    @Test
    fun `observer reentrancy does not suppress settled auth callback`() {
        val firebase = FakeAuth()
        val auth = SerializedNativeAuthPort(firebase)
        var result: AuthResult? = null
        auth.observe(object : AuthStateListener {
            override fun onStateChanged(state: AuthState) {
                if (state is AuthState.SignedIn) {
                    auth.signOut(object : ResultCallback {
                        override fun complete(result: OperationResult) = Unit
                    })
                }
            }
        })

        auth.signInWithCustomToken("token-a", object : AuthCallback {
            override fun complete(value: AuthResult) { result = value }
        })
        firebase.completeCustomWithPendingObserver(AuthResult.Success(user("firebase-a")))

        assertEquals(AuthResult.Cancelled, result)
        assertEquals(listOf("custom:token-a", "signOut"), firebase.operations)
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

        fun completeCustomTwice(result: AuthResult) {
            val callback = custom ?: error("custom sign in was not started")
            custom = null
            if (result is AuthResult.Success) currentUser = result.user
            callback.complete(result)
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

        fun completeCustomWithPendingObserver(result: AuthResult) {
            val callback = custom ?: error("custom sign in was not started")
            custom = null
            val user = (result as? AuthResult.Success)?.user ?: error("expected success")
            currentUser = user
            observer?.onStateChanged(AuthState.SignedIn(user))
            callback.complete(result)
        }

        fun completePassword(result: AuthResult) {
            val callback = password ?: error("password sign in was not started")
            password = null
            if (result is AuthResult.Success) currentUser = result.user
            callback.complete(result)
        }

        fun completeSignOut(result: OperationResult = OperationResult.Success, observerBeforeCallback: Boolean = true) {
            val callback = signOut ?: error("sign out was not started")
            signOut = null
            if (result == OperationResult.Success) {
                currentUser = null
                if (observerBeforeCallback) observer?.onStateChanged(AuthState.SignedOut)
            }
            callback.complete(result)
            if (result == OperationResult.Success && !observerBeforeCallback) observer?.onStateChanged(AuthState.SignedOut)
        }

        fun emit(state: AuthState) = observer?.onStateChanged(state)
    }
}
