package br.com.saqz.androidapp.access

import br.com.saqz.access.port.AuthResult
import br.com.saqz.access.port.AuthState
import br.com.saqz.access.port.AuthStateListener
import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.TokenResult
import br.com.saqz.access.port.NativeUser
import br.com.saqz.access.port.AuthCallback
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.TokenCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAuthAdapterTest {
    @Test
    fun observeMapsSignedOutState() {
        val fixture = Fixture()
        var state: AuthState? = null

        fixture.adapter.observe(stateListener { state = it })
        fixture.firebase.emit(null)

        assertSame(AuthState.SignedOut, state)
    }

    @Test
    fun observeMapsProviderUserWithoutLeakingSdkTypes() {
        val fixture = Fixture()
        var state: AuthState? = null

        fixture.adapter.observe(stateListener { state = it })
        fixture.firebase.emit(providerUser(displayName = null))

        assertEquals(nativeUser(displayName = null), (state as AuthState.SignedIn).user)
    }

    @Test
    fun cancellingObservationDetachesFirebaseListener() {
        val fixture = Fixture()
        var callbacks = 0
        val observation = fixture.adapter.observe(stateListener { callbacks += 1 })

        observation.cancel()
        fixture.firebase.emit(providerUser())

        assertEquals(0, callbacks)
        assertTrue(fixture.firebase.observationCancelled)
    }

    @Test
    fun createAccountForwardsNameEmailAndPassword() {
        val fixture = Fixture()
        var result: AuthResult? = null

        fixture.adapter.createAccount("Ana", "ana@example.test", "strong-pass", authCallback { result = it })
        fixture.firebase.completeAuth(AndroidProviderResult.Success(providerUser()))

        assertEquals(listOf("create:Ana:ana@example.test:strong-pass"), fixture.firebase.calls)
        assertEquals(nativeUser(), (result as AuthResult.Success).user)
    }

    @Test
    fun passwordSignInMapsSuccessfulSession() {
        val fixture = Fixture()
        var result: AuthResult? = null

        fixture.adapter.signInWithPassword("ana@example.test", "pass", authCallback { result = it })
        fixture.firebase.completeAuth(AndroidProviderResult.Success(providerUser()))

        assertEquals(listOf("password:ana@example.test:pass"), fixture.firebase.calls)
        assertEquals(nativeUser(), (result as AuthResult.Success).user)
    }

    @Test
    fun googleCredentialIsExchangedForFirebaseCredential() {
        val fixture = Fixture()
        var result: AuthResult? = null

        fixture.adapter.signInWithGoogle(authCallback { result = it })
        fixture.google.complete(AndroidProviderResult.Success("google-id-token"))
        fixture.firebase.completeAuth(AndroidProviderResult.Success(providerUser()))

        assertEquals(listOf("google:google-id-token"), fixture.firebase.calls)
        assertEquals(nativeUser(), (result as AuthResult.Success).user)
    }

    @Test
    fun googleCancellationIsNotRenderedAsProviderFailure() {
        val fixture = Fixture()
        var result: AuthResult? = null

        fixture.adapter.signInWithGoogle(authCallback { result = it })
        fixture.google.complete(AndroidProviderResult.Cancelled)

        assertSame(AuthResult.Cancelled, result)
        assertTrue(fixture.firebase.calls.isEmpty())
    }

    @Test
    fun googleProviderFailureUsesStableCode() {
        val fixture = Fixture()
        var result: AuthResult? = null

        fixture.adapter.signInWithGoogle(authCallback { result = it })
        fixture.google.complete(AndroidProviderResult.Failure(AndroidProviderFailure.UNAVAILABLE))

        assertEquals(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE), result)
    }

    @Test
    fun sendVerificationDelegatesToCurrentFirebaseUser() {
        val fixture = Fixture()
        var result: OperationResult? = null

        fixture.adapter.sendVerification(resultCallback { result = it })
        fixture.firebase.completeOperation(AndroidProviderResult.Success(Unit))

        assertEquals(listOf("verification"), fixture.firebase.calls)
        assertSame(OperationResult.Success, result)
    }

    @Test
    fun reloadRefreshesAndReturnsCurrentUser() {
        val fixture = Fixture()
        var result: AuthResult? = null

        fixture.adapter.reloadUser(authCallback { result = it })
        fixture.firebase.completeAuth(AndroidProviderResult.Success(providerUser(emailVerified = true)))

        assertEquals(listOf("reload"), fixture.firebase.calls)
        assertTrue((result as AuthResult.Success).user.emailVerified)
    }

    @Test
    fun passwordResetForwardsEmail() {
        val fixture = Fixture()
        var result: OperationResult? = null

        fixture.adapter.sendPasswordReset("ana@example.test", resultCallback { result = it })
        fixture.firebase.completeOperation(AndroidProviderResult.Success(Unit))

        assertEquals(listOf("reset:ana@example.test"), fixture.firebase.calls)
        assertSame(OperationResult.Success, result)
    }

    @Test
    fun displayNameUpdateReturnsUpdatedUser() {
        val fixture = Fixture()
        var result: AuthResult? = null

        fixture.adapter.updateDisplayName("Novo nome", authCallback { result = it })
        fixture.firebase.completeAuth(AndroidProviderResult.Success(providerUser(displayName = "Novo nome")))

        assertEquals(listOf("display-name:Novo nome"), fixture.firebase.calls)
        assertEquals("Novo nome", (result as AuthResult.Success).user.displayName)
    }

    @Test
    fun idTokenForwardsRefreshFlagAndReturnsTokenOnlyToCallback() {
        val fixture = Fixture()
        var result: TokenResult? = null

        fixture.adapter.idToken(true, tokenCallback { result = it })
        fixture.firebase.completeToken(AndroidProviderResult.Success("fresh-token"))

        assertEquals(listOf("token:true"), fixture.firebase.calls)
        assertEquals(TokenResult.Success("fresh-token"), result)
        assertFalse(fixture.adapter.toString().contains("fresh-token"))
    }

    @Test
    fun signOutClearsFirebaseAndCredentialManagerState() {
        val fixture = Fixture()
        var result: OperationResult? = null

        fixture.adapter.signOut(resultCallback { result = it })
        fixture.firebase.completeOperation(AndroidProviderResult.Success(Unit))
        fixture.google.completeClear(AndroidProviderResult.Success(Unit))

        assertEquals(listOf("sign-out"), fixture.firebase.calls)
        assertEquals(1, fixture.google.clearCalls)
        assertSame(OperationResult.Success, result)
    }

    @Test
    fun invalidCredentialExceptionsMapToStableFailure() {
        assertEquals(
            AndroidProviderFailure.INVALID_CREDENTIALS,
            mapFirebaseFailure(FirebaseFailureFamily.INVALID_CREDENTIALS),
        )
        assertEquals(
            AndroidProviderFailure.INVALID_CREDENTIALS,
            mapFirebaseFailure(FirebaseFailureFamily.INVALID_USER),
        )
    }

    @Test
    fun accountCollisionDistinguishesEmailInUseFromMethodConflict() {
        assertEquals(
            AndroidProviderFailure.EMAIL_IN_USE,
            mapFirebaseFailure(FirebaseFailureFamily.USER_COLLISION, "ERROR_EMAIL_ALREADY_IN_USE"),
        )
        assertEquals(
            AndroidProviderFailure.AUTH_METHOD_CONFLICT,
            mapFirebaseFailure(
                FirebaseFailureFamily.USER_COLLISION,
                "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL",
            ),
        )
    }

    @Test
    fun weakPasswordAndNetworkExceptionsHaveActionableCodes() {
        assertEquals(
            AndroidProviderFailure.WEAK_PASSWORD,
            mapFirebaseFailure(FirebaseFailureFamily.WEAK_PASSWORD),
        )
        assertEquals(
            AndroidProviderFailure.NETWORK,
            mapFirebaseFailure(FirebaseFailureFamily.NETWORK),
        )
    }

    @Test
    fun providerFailuresNeverCrossTheNativePort() {
        val fixture = Fixture()
        var auth: AuthResult? = null
        var operation: OperationResult? = null
        var token: TokenResult? = null

        fixture.adapter.signInWithPassword("a@example.test", "bad", authCallback { auth = it })
        fixture.firebase.completeAuth(AndroidProviderResult.Failure(AndroidProviderFailure.INVALID_CREDENTIALS))
        fixture.adapter.sendPasswordReset("a@example.test", resultCallback { operation = it })
        fixture.firebase.completeOperation(AndroidProviderResult.Failure(AndroidProviderFailure.NETWORK))
        fixture.adapter.idToken(false, tokenCallback { token = it })
        fixture.firebase.completeToken(AndroidProviderResult.Failure(AndroidProviderFailure.UNKNOWN))

        assertEquals(AuthResult.Failure(NativeFailureCode.INVALID_CREDENTIALS), auth)
        assertEquals(OperationResult.Failure(NativeFailureCode.NETWORK_UNAVAILABLE), operation)
        assertEquals(TokenResult.Failure(NativeFailureCode.UNKNOWN), token)
    }

    private class Fixture {
        val firebase = FakeFirebaseAuthClient()
        val google = FakeGoogleCredentialClient()
        val adapter = AndroidAuthAdapter(firebase, google)
    }

    private class FakeFirebaseAuthClient : AndroidFirebaseAuthClient {
        val calls = mutableListOf<String>()
        var observationCancelled = false
        private var listener: ((AndroidProviderUser?) -> Unit)? = null
        private var authDone: ((AndroidProviderResult<AndroidProviderUser>) -> Unit)? = null
        private var operationDone: ((AndroidProviderResult<Unit>) -> Unit)? = null
        private var tokenDone: ((AndroidProviderResult<String>) -> Unit)? = null

        override fun observe(listener: (AndroidProviderUser?) -> Unit): br.com.saqz.access.port.Cancelable {
            this.listener = listener
            return object : br.com.saqz.access.port.Cancelable {
                override fun cancel() {
                    observationCancelled = true
                    this@FakeFirebaseAuthClient.listener = null
                }
            }
        }

        override fun createAccount(name: String, email: String, password: String, done: (AndroidProviderResult<AndroidProviderUser>) -> Unit) {
            calls += "create:$name:$email:$password"
            authDone = done
        }

        override fun signInWithPassword(email: String, password: String, done: (AndroidProviderResult<AndroidProviderUser>) -> Unit) {
            calls += "password:$email:$password"
            authDone = done
        }

        override fun signInWithGoogle(idToken: String, done: (AndroidProviderResult<AndroidProviderUser>) -> Unit) {
            calls += "google:$idToken"
            authDone = done
        }

        override fun sendVerification(done: (AndroidProviderResult<Unit>) -> Unit) {
            calls += "verification"
            operationDone = done
        }

        override fun reloadUser(done: (AndroidProviderResult<AndroidProviderUser>) -> Unit) {
            calls += "reload"
            authDone = done
        }

        override fun sendPasswordReset(email: String, done: (AndroidProviderResult<Unit>) -> Unit) {
            calls += "reset:$email"
            operationDone = done
        }

        override fun updateDisplayName(name: String, done: (AndroidProviderResult<AndroidProviderUser>) -> Unit) {
            calls += "display-name:$name"
            authDone = done
        }

        override fun idToken(forceRefresh: Boolean, done: (AndroidProviderResult<String>) -> Unit) {
            calls += "token:$forceRefresh"
            tokenDone = done
        }

        override fun signOut(done: (AndroidProviderResult<Unit>) -> Unit) {
            calls += "sign-out"
            operationDone = done
        }

        fun emit(user: AndroidProviderUser?) = listener?.invoke(user)
        fun completeAuth(result: AndroidProviderResult<AndroidProviderUser>) = authDone!!.invoke(result)
        fun completeOperation(result: AndroidProviderResult<Unit>) = operationDone!!.invoke(result)
        fun completeToken(result: AndroidProviderResult<String>) = tokenDone!!.invoke(result)
    }

    private class FakeGoogleCredentialClient : AndroidGoogleCredentialClient {
        var clearCalls = 0
        private var done: ((AndroidProviderResult<String>) -> Unit)? = null
        private var clearDone: ((AndroidProviderResult<Unit>) -> Unit)? = null

        override fun requestIdToken(done: (AndroidProviderResult<String>) -> Unit) { this.done = done }
        override fun clearCredentialState(done: (AndroidProviderResult<Unit>) -> Unit) {
            clearCalls += 1
            clearDone = done
        }

        fun complete(result: AndroidProviderResult<String>) = done!!.invoke(result)
        fun completeClear(result: AndroidProviderResult<Unit>) = clearDone!!.invoke(result)
    }

    private companion object {
        fun stateListener(block: (AuthState) -> Unit) = object : AuthStateListener {
            override fun onStateChanged(state: AuthState) = block(state)
        }

        fun authCallback(block: (AuthResult) -> Unit) = object : AuthCallback {
            override fun complete(result: AuthResult) = block(result)
        }

        fun resultCallback(block: (OperationResult) -> Unit) = object : ResultCallback {
            override fun complete(result: OperationResult) = block(result)
        }

        fun tokenCallback(block: (TokenResult) -> Unit) = object : TokenCallback {
            override fun complete(result: TokenResult) = block(result)
        }

        fun providerUser(
            emailVerified: Boolean = false,
            displayName: String? = "Ana",
        ) = AndroidProviderUser("firebase-subject", "ana@example.test", emailVerified, displayName)

        fun nativeUser(displayName: String? = "Ana") =
            NativeUser("firebase-subject", "ana@example.test", false, displayName)
    }
}
