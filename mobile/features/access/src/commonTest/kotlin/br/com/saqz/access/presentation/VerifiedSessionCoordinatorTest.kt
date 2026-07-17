package br.com.saqz.access.presentation

import br.com.saqz.access.port.AuthCallback
import br.com.saqz.access.port.AuthResult
import br.com.saqz.access.port.AuthStateListener
import br.com.saqz.access.port.Cancelable
import br.com.saqz.access.port.LocalAccessStatePort
import br.com.saqz.access.port.NativeAuthPort
import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.NativeUser
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.TokenCallback
import br.com.saqz.access.port.TokenResult
import br.com.saqz.access.port.ValueCallback
import br.com.saqz.network.ApiProblem
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.SessionDto
import br.com.saqz.network.SessionGateway
import br.com.saqz.network.SessionUserDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VerifiedSessionCoordinatorTest {
    @Test
    fun `unverified authentication remains blocked before bootstrap`() = runTest {
        val fixture = fixture(this)

        fixture.coordinator.accept(AuthTransition.Authenticated(unverified))

        assertEquals(unverified, assertIs<SessionAccessState.AwaitingVerification>(fixture.coordinator.state.value).user)
        assertEquals(0, fixture.session.calls)
    }

    @Test
    fun `verified user without usable name requires completion before bootstrap`() = runTest {
        val fixture = fixture(this)

        fixture.coordinator.accept(AuthTransition.Authenticated(verified.copy(displayName = " ")))

        assertEquals("", assertIs<SessionAccessState.CompletingName>(fixture.coordinator.state.value).name)
        assertEquals(0, fixture.session.calls)
    }

    @Test
    fun `verified named user bootstraps into ready state`() = runTest {
        val fixture = fixture(this)

        fixture.coordinator.accept(AuthTransition.Authenticated(verified))
        runCurrent()

        assertEquals(session, assertIs<SessionAccessState.Ready>(fixture.coordinator.state.value).session)
        assertEquals(1, fixture.session.calls)
    }

    @Test
    fun `already verified reload keeps unverified account blocked`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.accept(AuthTransition.VerificationRequired(unverified))

        fixture.coordinator.confirmVerification()
        fixture.auth.completeAuth(AuthResult.Success(unverified))

        assertIs<SessionAccessState.AwaitingVerification>(fixture.coordinator.state.value)
        assertEquals(0, fixture.auth.tokenCalls.size)
        assertEquals(0, fixture.session.calls)
    }

    @Test
    fun `verified reload forces token refresh`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.accept(AuthTransition.VerificationRequired(unverified))

        fixture.coordinator.confirmVerification()
        fixture.auth.completeAuth(AuthResult.Success(verified))

        assertEquals(listOf(true), fixture.auth.tokenCalls)
    }

    @Test
    fun `forced token success continues bootstrap`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.accept(AuthTransition.VerificationRequired(unverified))

        fixture.coordinator.confirmVerification()
        fixture.auth.completeAuth(AuthResult.Success(verified))
        fixture.auth.completeToken(TokenResult.Success("fresh-token"))
        runCurrent()

        assertIs<SessionAccessState.Ready>(fixture.coordinator.state.value)
        assertEquals(1, fixture.session.calls)
    }

    @Test
    fun `reload failure ends loading without bootstrap`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.accept(AuthTransition.VerificationRequired(unverified))

        fixture.coordinator.confirmVerification()
        fixture.auth.completeAuth(AuthResult.Failure(NativeFailureCode.NETWORK_UNAVAILABLE))

        val state = assertIs<SessionAccessState.AwaitingVerification>(fixture.coordinator.state.value)
        assertEquals(AuthUiError.NETWORK_UNAVAILABLE, state.error)
        assertFalse(state.isLoading)
        assertEquals(0, fixture.session.calls)
    }

    @Test
    fun `resend verification maps provider feedback without bootstrap`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.accept(AuthTransition.VerificationRequired(unverified))

        fixture.coordinator.resendVerification()
        fixture.auth.completeOperation(OperationResult.Success)

        assertTrue(assertIs<SessionAccessState.AwaitingVerification>(fixture.coordinator.state.value).verificationSent)
        assertEquals(0, fixture.session.calls)
    }

    @Test
    fun `name completion sends exact value`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.accept(AuthTransition.Authenticated(verified.copy(displayName = null)))

        fixture.coordinator.updateName("Person Name")
        fixture.coordinator.completeName()

        assertEquals(listOf("Person Name"), fixture.auth.nameUpdates)
    }

    @Test
    fun `name completion success refreshes token then bootstraps`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.accept(AuthTransition.Authenticated(verified.copy(displayName = null)))
        fixture.coordinator.updateName("Person Name")

        fixture.coordinator.completeName()
        fixture.auth.completeAuth(AuthResult.Success(verified))
        fixture.auth.completeToken(TokenResult.Success("fresh-token"))
        runCurrent()

        assertEquals(listOf(true), fixture.auth.tokenCalls)
        assertIs<SessionAccessState.Ready>(fixture.coordinator.state.value)
    }

    @Test
    fun `invalid completed name stays local and does not call provider`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.accept(AuthTransition.Authenticated(verified.copy(displayName = null)))
        fixture.coordinator.updateName("\n")

        fixture.coordinator.completeName()

        val state = assertIs<SessionAccessState.CompletingName>(fixture.coordinator.state.value)
        assertTrue(state.invalidName)
        assertTrue(fixture.auth.nameUpdates.isEmpty())
    }

    @Test
    fun `backend failure exposes retry without protected session`() = runTest {
        val fixture = fixture(this, NetworkResult.Failure(NetworkError.HttpStatus(500)))

        fixture.coordinator.accept(AuthTransition.Authenticated(verified))
        runCurrent()

        assertIs<SessionAccessState.BootstrapError>(fixture.coordinator.state.value)
        assertEquals(0, fixture.auth.signOutCalls)
    }

    @Test
    fun `bootstrap retry preserves firebase session and can recover`() = runTest {
        val fixture = fixture(this, NetworkResult.Failure(NetworkError.Unavailable))
        fixture.coordinator.accept(AuthTransition.Authenticated(verified))
        runCurrent()
        fixture.session.result = NetworkResult.Success(session)

        fixture.coordinator.retryBootstrap()
        runCurrent()

        assertIs<SessionAccessState.Ready>(fixture.coordinator.state.value)
        assertEquals(2, fixture.session.calls)
        assertEquals(0, fixture.auth.signOutCalls)
    }

    @Test
    fun `backend email not verified response returns to verification`() = runTest {
        val failure = NetworkResult.Failure(
            NetworkError.ApiProblemError(ApiProblem(403, "EMAIL_NOT_VERIFIED", "corr-403")),
        )
        val fixture = fixture(this, failure)

        fixture.coordinator.accept(AuthTransition.Authenticated(verified))
        runCurrent()

        assertIs<SessionAccessState.AwaitingVerification>(fixture.coordinator.state.value)
    }

    @Test
    fun `logout clears selected group pending invite and native session`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.accept(AuthTransition.Authenticated(verified))
        runCurrent()

        fixture.coordinator.logout()

        assertEquals(listOf<String?>(null), fixture.local.selectedWrites)
        assertEquals(listOf<String?>(null), fixture.local.pendingWrites)
        assertEquals(1, fixture.auth.signOutCalls)
        assertIs<SessionAccessState.SignedOut>(fixture.coordinator.state.value)
    }

    @Test
    fun `session invalidation uses the same local logout path`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.accept(AuthTransition.Authenticated(verified))
        runCurrent()

        fixture.coordinator.invalidate()

        assertEquals(listOf<String?>(null), fixture.local.selectedWrites)
        assertEquals(listOf<String?>(null), fixture.local.pendingWrites)
        assertEquals(1, fixture.auth.signOutCalls)
        assertIs<SessionAccessState.SignedOut>(fixture.coordinator.state.value)
    }

    @Test
    fun `duplicate verification confirmation is single flight`() = runTest {
        val fixture = fixture(this)
        fixture.coordinator.accept(AuthTransition.VerificationRequired(unverified))

        fixture.coordinator.confirmVerification()
        fixture.coordinator.confirmVerification()

        assertEquals(1, fixture.auth.reloadCalls)
    }

    private fun fixture(
        scope: kotlinx.coroutines.CoroutineScope,
        result: NetworkResult<SessionDto> = NetworkResult.Success(session),
    ): Fixture {
        val auth = FakeAuthPort()
        val local = FakeLocalState()
        val gateway = FakeSessionGateway(result)
        return Fixture(VerifiedSessionCoordinator(auth, local, gateway, scope), auth, local, gateway)
    }

    private class FakeSessionGateway(var result: NetworkResult<SessionDto>) : SessionGateway {
        var calls = 0
        override suspend fun bootstrap(): NetworkResult<SessionDto> {
            calls += 1
            return result
        }
    }

    private class FakeAuthPort : NativeAuthPort {
        val tokenCalls = mutableListOf<Boolean>()
        val nameUpdates = mutableListOf<String>()
        var reloadCalls = 0
        var signOutCalls = 0
        private var authCallback: AuthCallback? = null
        private var operationCallback: ResultCallback? = null
        private var tokenCallback: TokenCallback? = null

        override fun reloadUser(done: AuthCallback) { reloadCalls += 1; authCallback = done }
        override fun updateDisplayName(name: String, done: AuthCallback) { nameUpdates += name; authCallback = done }
        override fun sendVerification(done: ResultCallback) { operationCallback = done }
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) { tokenCalls += forceRefresh; tokenCallback = done }
        override fun signOut(done: ResultCallback) { signOutCalls += 1; done.complete(OperationResult.Success) }
        fun completeAuth(result: AuthResult) = authCallback!!.complete(result)
        fun completeOperation(result: OperationResult) = operationCallback!!.complete(result)
        fun completeToken(result: TokenResult) = tokenCallback!!.complete(result)
        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable { override fun cancel() = Unit }
        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = Unit
        override fun sendPasswordReset(email: String, done: ResultCallback) = Unit
    }

    private class FakeLocalState : LocalAccessStatePort {
        val selectedWrites = mutableListOf<String?>()
        val pendingWrites = mutableListOf<String?>()
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) { selectedWrites += value; done.complete(OperationResult.Success) }
        override fun writePendingInvite(value: String?, done: ResultCallback) { pendingWrites += value; done.complete(OperationResult.Success) }
        override fun readSelectedGroupId(done: ValueCallback) = Unit
        override fun readPendingInvite(done: ValueCallback) = Unit
    }

    private data class Fixture(
        val coordinator: VerifiedSessionCoordinator,
        val auth: FakeAuthPort,
        val local: FakeLocalState,
        val session: FakeSessionGateway,
    )

    private companion object {
        val unverified = NativeUser("subject", "person@example.test", false, "Person Name")
        val verified = unverified.copy(emailVerified = true)
        val session = SessionDto(SessionUserDto("user-id", "person@example.test", "Person Name"), emptyList())
    }
}
