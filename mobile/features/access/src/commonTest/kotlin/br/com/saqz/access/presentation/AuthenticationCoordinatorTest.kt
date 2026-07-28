package br.com.saqz.access.presentation

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthenticationStateMachineTest {
    @Test
    fun `password login submits email and captured password`() {
        val fixture = fixture()
        fixture.machine.onIntent(AuthenticationIntent.UpdateEmail("person@example.test"))
        fixture.machine.onIntent(AuthenticationIntent.UpdatePassword("secret-value"))

        fixture.machine.onIntent(AuthenticationIntent.SubmitPasswordLogin)

        assertEquals(listOf(LoginCall("person@example.test", "secret-value")), fixture.auth.logins)
        assertEquals("", fixture.machine.state.value.password)
    }

    @Test
    fun `password login success emits authenticated user`() {
        val fixture = fixture()
        fixture.machine.onIntent(AuthenticationIntent.UpdatePassword("secret-value"))

        fixture.machine.onIntent(AuthenticationIntent.SubmitPasswordLogin)
        fixture.auth.completeAuth(AuthResult.Success(verifiedUser))

        assertEquals(AuthTransition.Authenticated(verifiedUser), fixture.transitions.single())
        assertFalse(fixture.machine.state.value.isLoading)
    }

    @Test
    fun `invalid credentials become stable actionable error`() {
        val fixture = fixture()
        fixture.machine.onIntent(AuthenticationIntent.UpdateEmail("person@example.test"))
        fixture.machine.onIntent(AuthenticationIntent.UpdatePassword("wrong"))

        fixture.machine.onIntent(AuthenticationIntent.SubmitPasswordLogin)
        fixture.auth.completeAuth(AuthResult.Failure(NativeFailureCode.INVALID_CREDENTIALS))

        assertEquals(AuthUiError.INVALID_CREDENTIALS, fixture.machine.state.value.error)
        assertEquals("person@example.test", fixture.machine.state.value.email)
    }

    @Test
    fun `second password login while loading is ignored`() {
        val fixture = fixture()
        fixture.machine.onIntent(AuthenticationIntent.UpdatePassword("secret-value"))

        fixture.machine.onIntent(AuthenticationIntent.SubmitPasswordLogin)
        fixture.machine.onIntent(AuthenticationIntent.SubmitPasswordLogin)

        assertEquals(1, fixture.auth.logins.size)
    }

    @Test
    fun `google login starts exactly once`() {
        val fixture = fixture()

        fixture.machine.onIntent(AuthenticationIntent.SubmitGoogleLogin)
        fixture.machine.onIntent(AuthenticationIntent.SubmitGoogleLogin)

        assertEquals(1, fixture.auth.googleCalls)
    }

    @Test
    fun `google success emits authenticated user`() {
        val fixture = fixture()

        fixture.machine.onIntent(AuthenticationIntent.SubmitGoogleLogin)
        fixture.auth.completeAuth(AuthResult.Success(verifiedUser))

        assertEquals(AuthTransition.Authenticated(verifiedUser), fixture.transitions.single())
    }

    @Test
    fun `google cancellation leaves the form untouched without error`() {
        val fixture = fixture()
        fixture.machine.onIntent(AuthenticationIntent.UpdateEmail("person@example.test"))

        fixture.machine.onIntent(AuthenticationIntent.SubmitGoogleLogin)
        fixture.auth.completeAuth(AuthResult.Cancelled)

        assertEquals("person@example.test", fixture.machine.state.value.email)
        assertFalse(fixture.machine.state.value.isLoading)
        assertNull(fixture.machine.state.value.error)
        assertTrue(fixture.transitions.isEmpty())
    }

    @Test
    fun `auth method conflict maps without account merge`() {
        val fixture = fixture()

        fixture.machine.onIntent(AuthenticationIntent.SubmitGoogleLogin)
        fixture.auth.completeAuth(AuthResult.Failure(NativeFailureCode.AUTH_METHOD_CONFLICT))

        assertEquals(AuthUiError.AUTH_METHOD_CONFLICT, fixture.machine.state.value.error)
        assertTrue(fixture.transitions.isEmpty())
    }

    private fun fixture(): Fixture {
        val auth = FakeAuthPort()
        val transitions = mutableListOf<AuthTransition>()
        return Fixture(AuthenticationStateMachine(auth, transitions::add), auth, transitions)
    }

    private class FakeAuthPort : NativeAuthPort {
        val logins = mutableListOf<LoginCall>()
        var googleCalls = 0
        private var authCallback: AuthCallback? = null

        override fun signInWithPassword(email: String, password: String, done: AuthCallback) {
            logins += LoginCall(email, password)
            authCallback = done
        }

        override fun signInWithGoogle(done: AuthCallback) {
            googleCalls += 1
            authCallback = done
        }

        fun completeAuth(result: AuthResult) = authCallback!!.complete(result)

        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable { override fun cancel() = Unit }
        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun sendVerification(done: ResultCallback) = Unit
        override fun reloadUser(done: AuthCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit
        override fun signOut(done: ResultCallback) = Unit
    }

    private data class Fixture(
        val machine: AuthenticationStateMachine,
        val auth: FakeAuthPort,
        val transitions: MutableList<AuthTransition>,
    )

    private data class LoginCall(val email: String, val password: String)

    private companion object {
        val verifiedUser = NativeUser("subject", "person@example.test", true, "Person Name")
    }
}
