package br.com.saqz.access.presentation

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthenticationStateMachineTest {
    @Test
    fun `registration submits exact nonempty form once`() {
        val fixture = fixture().filledRegistration()

        fixture.machine.onIntent(AuthenticationIntent.SubmitRegistration)

        assertEquals(listOf(RegistrationCall("Person Name", "person@example.test", "secret-value")), fixture.auth.registrations)
    }

    @Test
    fun `invalid registration is reduced to validation state without provider call`() {
        val fixture = fixture()
        fixture.machine.onIntent(AuthenticationIntent.ShowRegistration)
        fixture.machine.onIntent(AuthenticationIntent.UpdateEmail("invalid"))

        fixture.machine.onIntent(AuthenticationIntent.SubmitRegistration)

        assertTrue(fixture.machine.state.value.validationAttempted)
        assertTrue(fixture.auth.registrations.isEmpty())
        assertFalse(fixture.machine.state.value.isLoading)
    }

    @Test
    fun `registration clears password as soon as submitted`() {
        val fixture = fixture().filledRegistration()

        fixture.machine.onIntent(AuthenticationIntent.SubmitRegistration)

        assertEquals("", fixture.machine.state.value.password)
        assertEquals("Person Name", fixture.machine.state.value.name)
        assertEquals("person@example.test", fixture.machine.state.value.email)
    }

    @Test
    fun `successful registration requests email verification`() {
        val fixture = fixture().filledRegistration()

        fixture.machine.onIntent(AuthenticationIntent.SubmitRegistration)
        fixture.auth.completeAuth(AuthResult.Success(unverifiedUser))

        assertEquals(1, fixture.auth.verificationRequests)
        assertTrue(fixture.machine.state.value.isLoading)
    }

    @Test
    fun `verified request completion emits verification required`() {
        val fixture = fixture().filledRegistration()

        fixture.machine.onIntent(AuthenticationIntent.SubmitRegistration)
        fixture.auth.completeAuth(AuthResult.Success(unverifiedUser))
        fixture.auth.completeOperation(OperationResult.Success)

        assertEquals(AuthTransition.VerificationRequired(unverifiedUser), fixture.transitions.single())
        assertFalse(fixture.machine.state.value.isLoading)
    }

    @Test
    fun `registration failure maps email in use without provider detail`() {
        val fixture = fixture().filledRegistration()

        fixture.machine.onIntent(AuthenticationIntent.SubmitRegistration)
        fixture.auth.completeAuth(AuthResult.Failure(NativeFailureCode.EMAIL_IN_USE))

        assertEquals(AuthUiError.EMAIL_IN_USE, fixture.machine.state.value.error)
        assertFalse(fixture.machine.state.value.isLoading)
    }

    @Test
    fun `registration provider failure preserves name and email`() {
        val fixture = fixture().filledRegistration()

        fixture.machine.onIntent(AuthenticationIntent.SubmitRegistration)
        fixture.auth.completeAuth(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

        assertEquals("Person Name", fixture.machine.state.value.name)
        assertEquals("person@example.test", fixture.machine.state.value.email)
        assertEquals(AuthUiError.PROVIDER_UNAVAILABLE, fixture.machine.state.value.error)
    }

    @Test
    fun `registration verification failure ends loading with actionable error`() {
        val fixture = fixture().filledRegistration()

        fixture.machine.onIntent(AuthenticationIntent.SubmitRegistration)
        fixture.auth.completeAuth(AuthResult.Success(unverifiedUser))
        fixture.auth.completeOperation(OperationResult.Failure(NativeFailureCode.NETWORK_UNAVAILABLE))

        assertEquals(AuthUiError.NETWORK_UNAVAILABLE, fixture.machine.state.value.error)
        assertTrue(fixture.transitions.isEmpty())
    }

    @Test
    fun `second registration submit while loading is ignored`() {
        val fixture = fixture().filledRegistration()

        fixture.machine.onIntent(AuthenticationIntent.SubmitRegistration)
        fixture.machine.onIntent(AuthenticationIntent.UpdatePassword("another-secret"))
        fixture.machine.onIntent(AuthenticationIntent.SubmitRegistration)

        assertEquals(1, fixture.auth.registrations.size)
    }

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
    fun `google cancellation stays on current screen without error`() {
        val fixture = fixture()
        fixture.machine.onIntent(AuthenticationIntent.ShowRegistration)

        fixture.machine.onIntent(AuthenticationIntent.SubmitGoogleLogin)
        fixture.auth.completeAuth(AuthResult.Cancelled)

        assertEquals(AuthScreen.REGISTRATION, fixture.machine.state.value.screen)
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

    @Test
    fun `password reset submits exact email`() {
        val fixture = fixture()
        fixture.machine.onIntent(AuthenticationIntent.ShowPasswordReset)
        fixture.machine.onIntent(AuthenticationIntent.UpdateEmail("person@example.test"))

        fixture.machine.onIntent(AuthenticationIntent.SubmitPasswordReset)

        assertEquals(listOf("person@example.test"), fixture.auth.passwordResets)
    }

    @Test
    fun `password reset success shows neutral confirmation`() {
        val fixture = fixture()
        fixture.machine.onIntent(AuthenticationIntent.UpdateEmail("person@example.test"))
        fixture.machine.onIntent(AuthenticationIntent.SubmitPasswordReset)
        fixture.auth.completeOperation(OperationResult.Success)

        assertTrue(fixture.machine.state.value.resetConfirmation)
        assertFalse(fixture.machine.state.value.isLoading)
        assertNull(fixture.machine.state.value.error)
    }

    @Test
    fun `password reset provider outcome shows the same neutral confirmation`() {
        val fixture = fixture()
        fixture.machine.onIntent(AuthenticationIntent.UpdateEmail("person@example.test"))
        fixture.machine.onIntent(AuthenticationIntent.SubmitPasswordReset)
        fixture.auth.completeOperation(OperationResult.Failure(NativeFailureCode.UNKNOWN))

        assertTrue(fixture.machine.state.value.resetConfirmation)
        assertFalse(fixture.machine.state.value.isLoading)
        assertNull(fixture.machine.state.value.error)
    }

    @Test
    fun `invalid password reset is reduced to validation state without provider call`() {
        val fixture = fixture()
        fixture.machine.onIntent(AuthenticationIntent.ShowPasswordReset)
        fixture.machine.onIntent(AuthenticationIntent.UpdateEmail("invalid"))

        fixture.machine.onIntent(AuthenticationIntent.SubmitPasswordReset)

        assertTrue(fixture.machine.state.value.validationAttempted)
        assertTrue(fixture.auth.passwordResets.isEmpty())
        assertFalse(fixture.machine.state.value.isLoading)
    }

    private fun fixture(): Fixture {
        val auth = FakeAuthPort()
        val transitions = mutableListOf<AuthTransition>()
        return Fixture(AuthenticationStateMachine(auth, transitions::add), auth, transitions)
    }

    private fun Fixture.filledRegistration(): Fixture = apply {
        machine.onIntent(AuthenticationIntent.ShowRegistration)
        machine.onIntent(AuthenticationIntent.UpdateName("Person Name"))
        machine.onIntent(AuthenticationIntent.UpdateEmail("person@example.test"))
        machine.onIntent(AuthenticationIntent.UpdatePassword("secret-value"))
    }

    private class FakeAuthPort : NativeAuthPort {
        val registrations = mutableListOf<RegistrationCall>()
        val logins = mutableListOf<LoginCall>()
        val passwordResets = mutableListOf<String>()
        var googleCalls = 0
        var verificationRequests = 0
        private var authCallback: AuthCallback? = null
        private var operationCallback: ResultCallback? = null

        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) {
            registrations += RegistrationCall(name, email, password)
            authCallback = done
        }

        override fun signInWithPassword(email: String, password: String, done: AuthCallback) {
            logins += LoginCall(email, password)
            authCallback = done
        }

        override fun signInWithGoogle(done: AuthCallback) {
            googleCalls += 1
            authCallback = done
        }

        override fun sendVerification(done: ResultCallback) {
            verificationRequests += 1
            operationCallback = done
        }

        override fun sendPasswordReset(email: String, done: ResultCallback) {
            passwordResets += email
            operationCallback = done
        }

        fun completeAuth(result: AuthResult) = authCallback!!.complete(result)
        fun completeOperation(result: OperationResult) = operationCallback!!.complete(result)

        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable { override fun cancel() = Unit }
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

    private data class RegistrationCall(val name: String, val email: String, val password: String)
    private data class LoginCall(val email: String, val password: String)

    private companion object {
        val unverifiedUser = NativeUser("subject", "person@example.test", false, "Person Name")
        val verifiedUser = NativeUser("subject", "person@example.test", true, "Person Name")
    }
}
