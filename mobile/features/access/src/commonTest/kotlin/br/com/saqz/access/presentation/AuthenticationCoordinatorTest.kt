package br.com.saqz.access.presentation

import br.com.saqz.access.port.AuthCallback
import br.com.saqz.access.port.AuthResult
import br.com.saqz.access.port.AuthStateListener
import br.com.saqz.access.port.Cancelable
import br.com.saqz.access.port.NativeAuthPort
import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.NativeUser
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.TokenCallback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthenticationCoordinatorTest {
    @Test
    fun `registration submits exact nonempty form once`() {
        val fixture = fixture().filledRegistration()

        fixture.coordinator.submitRegistration()

        assertEquals(listOf(RegistrationCall("Person Name", "person@example.test", "secret-value")), fixture.auth.registrations)
    }

    @Test
    fun `registration clears password as soon as submitted`() {
        val fixture = fixture().filledRegistration()

        fixture.coordinator.submitRegistration()

        assertEquals("", fixture.coordinator.state.value.password)
        assertEquals("Person Name", fixture.coordinator.state.value.name)
        assertEquals("person@example.test", fixture.coordinator.state.value.email)
    }

    @Test
    fun `successful registration requests email verification`() {
        val fixture = fixture().filledRegistration()

        fixture.coordinator.submitRegistration()
        fixture.auth.completeAuth(AuthResult.Success(unverifiedUser))

        assertEquals(1, fixture.auth.verificationRequests)
        assertTrue(fixture.coordinator.state.value.isLoading)
    }

    @Test
    fun `verified request completion emits verification required`() {
        val fixture = fixture().filledRegistration()

        fixture.coordinator.submitRegistration()
        fixture.auth.completeAuth(AuthResult.Success(unverifiedUser))
        fixture.auth.completeOperation(OperationResult.Success)

        assertEquals(AuthTransition.VerificationRequired(unverifiedUser), fixture.transitions.single())
        assertFalse(fixture.coordinator.state.value.isLoading)
    }

    @Test
    fun `registration failure maps email in use without provider detail`() {
        val fixture = fixture().filledRegistration()

        fixture.coordinator.submitRegistration()
        fixture.auth.completeAuth(AuthResult.Failure(NativeFailureCode.EMAIL_IN_USE))

        assertEquals(AuthUiError.EMAIL_IN_USE, fixture.coordinator.state.value.error)
        assertFalse(fixture.coordinator.state.value.isLoading)
    }

    @Test
    fun `registration provider failure preserves name and email`() {
        val fixture = fixture().filledRegistration()

        fixture.coordinator.submitRegistration()
        fixture.auth.completeAuth(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

        assertEquals("Person Name", fixture.coordinator.state.value.name)
        assertEquals("person@example.test", fixture.coordinator.state.value.email)
        assertEquals(AuthUiError.PROVIDER_UNAVAILABLE, fixture.coordinator.state.value.error)
    }

    @Test
    fun `registration verification failure ends loading with actionable error`() {
        val fixture = fixture().filledRegistration()

        fixture.coordinator.submitRegistration()
        fixture.auth.completeAuth(AuthResult.Success(unverifiedUser))
        fixture.auth.completeOperation(OperationResult.Failure(NativeFailureCode.NETWORK_UNAVAILABLE))

        assertEquals(AuthUiError.NETWORK_UNAVAILABLE, fixture.coordinator.state.value.error)
        assertTrue(fixture.transitions.isEmpty())
    }

    @Test
    fun `second registration submit while loading is ignored`() {
        val fixture = fixture().filledRegistration()

        fixture.coordinator.submitRegistration()
        fixture.coordinator.updatePassword("another-secret")
        fixture.coordinator.submitRegistration()

        assertEquals(1, fixture.auth.registrations.size)
    }

    @Test
    fun `password login submits email and captured password`() {
        val fixture = fixture()
        fixture.coordinator.updateEmail("person@example.test")
        fixture.coordinator.updatePassword("secret-value")

        fixture.coordinator.submitPasswordLogin()

        assertEquals(listOf(LoginCall("person@example.test", "secret-value")), fixture.auth.logins)
        assertEquals("", fixture.coordinator.state.value.password)
    }

    @Test
    fun `password login success emits authenticated user`() {
        val fixture = fixture()
        fixture.coordinator.updatePassword("secret-value")

        fixture.coordinator.submitPasswordLogin()
        fixture.auth.completeAuth(AuthResult.Success(verifiedUser))

        assertEquals(AuthTransition.Authenticated(verifiedUser), fixture.transitions.single())
        assertFalse(fixture.coordinator.state.value.isLoading)
    }

    @Test
    fun `invalid credentials become stable actionable error`() {
        val fixture = fixture()
        fixture.coordinator.updateEmail("person@example.test")
        fixture.coordinator.updatePassword("wrong")

        fixture.coordinator.submitPasswordLogin()
        fixture.auth.completeAuth(AuthResult.Failure(NativeFailureCode.INVALID_CREDENTIALS))

        assertEquals(AuthUiError.INVALID_CREDENTIALS, fixture.coordinator.state.value.error)
        assertEquals("person@example.test", fixture.coordinator.state.value.email)
    }

    @Test
    fun `second password login while loading is ignored`() {
        val fixture = fixture()
        fixture.coordinator.updatePassword("secret-value")

        fixture.coordinator.submitPasswordLogin()
        fixture.coordinator.submitPasswordLogin()

        assertEquals(1, fixture.auth.logins.size)
    }

    @Test
    fun `google login starts exactly once`() {
        val fixture = fixture()

        fixture.coordinator.submitGoogleLogin()
        fixture.coordinator.submitGoogleLogin()

        assertEquals(1, fixture.auth.googleCalls)
    }

    @Test
    fun `google success emits authenticated user`() {
        val fixture = fixture()

        fixture.coordinator.submitGoogleLogin()
        fixture.auth.completeAuth(AuthResult.Success(verifiedUser))

        assertEquals(AuthTransition.Authenticated(verifiedUser), fixture.transitions.single())
    }

    @Test
    fun `google cancellation stays on current screen without error`() {
        val fixture = fixture()
        fixture.coordinator.showRegistration()

        fixture.coordinator.submitGoogleLogin()
        fixture.auth.completeAuth(AuthResult.Cancelled)

        assertEquals(AuthScreen.REGISTRATION, fixture.coordinator.state.value.screen)
        assertFalse(fixture.coordinator.state.value.isLoading)
        assertNull(fixture.coordinator.state.value.error)
        assertTrue(fixture.transitions.isEmpty())
    }

    @Test
    fun `auth method conflict maps without account merge`() {
        val fixture = fixture()

        fixture.coordinator.submitGoogleLogin()
        fixture.auth.completeAuth(AuthResult.Failure(NativeFailureCode.AUTH_METHOD_CONFLICT))

        assertEquals(AuthUiError.AUTH_METHOD_CONFLICT, fixture.coordinator.state.value.error)
        assertTrue(fixture.transitions.isEmpty())
    }

    @Test
    fun `password reset submits exact email`() {
        val fixture = fixture()
        fixture.coordinator.showPasswordReset()
        fixture.coordinator.updateEmail("person@example.test")

        fixture.coordinator.submitPasswordReset()

        assertEquals(listOf("person@example.test"), fixture.auth.passwordResets)
    }

    @Test
    fun `password reset success shows neutral confirmation`() {
        val fixture = fixture()
        fixture.coordinator.submitPasswordReset()
        fixture.auth.completeOperation(OperationResult.Success)

        assertTrue(fixture.coordinator.state.value.resetConfirmation)
        assertFalse(fixture.coordinator.state.value.isLoading)
        assertNull(fixture.coordinator.state.value.error)
    }

    @Test
    fun `password reset provider outcome shows the same neutral confirmation`() {
        val fixture = fixture()
        fixture.coordinator.submitPasswordReset()
        fixture.auth.completeOperation(OperationResult.Failure(NativeFailureCode.UNKNOWN))

        assertTrue(fixture.coordinator.state.value.resetConfirmation)
        assertFalse(fixture.coordinator.state.value.isLoading)
        assertNull(fixture.coordinator.state.value.error)
    }

    private fun fixture(): Fixture {
        val auth = FakeAuthPort()
        val transitions = mutableListOf<AuthTransition>()
        return Fixture(AuthenticationCoordinator(auth, transitions::add), auth, transitions)
    }

    private fun Fixture.filledRegistration(): Fixture = apply {
        coordinator.showRegistration()
        coordinator.updateName("Person Name")
        coordinator.updateEmail("person@example.test")
        coordinator.updatePassword("secret-value")
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
        val coordinator: AuthenticationCoordinator,
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
