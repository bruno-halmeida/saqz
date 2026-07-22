package br.com.saqz.access.presentation

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.session.AccessError
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
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
class SessionAccessStateMachineTest {
    @Test
    fun `unverified authentication remains blocked before bootstrap`() = runTest {
        val fixture = fixture(this)

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(unverified)))

        assertEquals(unverified, assertIs<SessionAccessState.AwaitingVerification>(fixture.machine.state.value).user)
        assertEquals(0, fixture.session.calls)
    }

    @Test
    fun `verified user without usable name requires completion before bootstrap`() = runTest {
        val fixture = fixture(this)

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified.copy(displayName = " "))))

        assertEquals("", assertIs<SessionAccessState.CompletingName>(fixture.machine.state.value).name)
        assertEquals(0, fixture.session.calls)
    }

    @Test
    fun `verified named user bootstraps into ready state`() = runTest {
        val fixture = fixture(this)

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertEquals(session, assertIs<SessionAccessState.Ready>(fixture.machine.state.value).session)
        assertEquals(1, fixture.session.calls)
    }

    @Test
    fun `already verified reload keeps unverified account blocked`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.VerificationRequired(unverified)))

        fixture.machine.onIntent(SessionIntent.ConfirmVerification)
        fixture.auth.completeAuth(AuthResult.Success(unverified))

        assertIs<SessionAccessState.AwaitingVerification>(fixture.machine.state.value)
        assertEquals(0, fixture.auth.tokenCalls.size)
        assertEquals(0, fixture.session.calls)
    }

    @Test
    fun `verified reload forces token refresh`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.VerificationRequired(unverified)))

        fixture.machine.onIntent(SessionIntent.ConfirmVerification)
        fixture.auth.completeAuth(AuthResult.Success(verified))

        assertEquals(listOf(true), fixture.auth.tokenCalls)
    }

    @Test
    fun `forced token success continues bootstrap`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.VerificationRequired(unverified)))

        fixture.machine.onIntent(SessionIntent.ConfirmVerification)
        fixture.auth.completeAuth(AuthResult.Success(verified))
        fixture.auth.completeToken(TokenResult.Success("fresh-token"))
        runCurrent()

        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
        assertEquals(1, fixture.session.calls)
    }

    @Test
    fun `reload failure ends loading without bootstrap`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.VerificationRequired(unverified)))

        fixture.machine.onIntent(SessionIntent.ConfirmVerification)
        fixture.auth.completeAuth(AuthResult.Failure(NativeFailureCode.NETWORK_UNAVAILABLE))

        val state = assertIs<SessionAccessState.AwaitingVerification>(fixture.machine.state.value)
        assertEquals(AuthUiError.NETWORK_UNAVAILABLE, state.error)
        assertFalse(state.isLoading)
        assertEquals(0, fixture.session.calls)
    }

    @Test
    fun `resend verification maps provider feedback without bootstrap`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.VerificationRequired(unverified)))

        fixture.machine.onIntent(SessionIntent.ResendVerification)
        fixture.auth.completeOperation(OperationResult.Success)

        assertTrue(assertIs<SessionAccessState.AwaitingVerification>(fixture.machine.state.value).verificationSent)
        assertEquals(0, fixture.session.calls)
    }

    @Test
    fun `name completion sends exact value`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified.copy(displayName = null))))

        fixture.machine.onIntent(SessionIntent.UpdateName("Person Name"))
        fixture.machine.onIntent(SessionIntent.CompleteName)

        assertEquals(listOf("Person Name"), fixture.auth.nameUpdates)
    }

    @Test
    fun `name completion success refreshes token then bootstraps`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified.copy(displayName = null))))
        fixture.machine.onIntent(SessionIntent.UpdateName("Person Name"))

        fixture.machine.onIntent(SessionIntent.CompleteName)
        fixture.auth.completeAuth(AuthResult.Success(verified))
        fixture.auth.completeToken(TokenResult.Success("fresh-token"))
        runCurrent()

        assertEquals(listOf(true), fixture.auth.tokenCalls)
        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
    }

    @Test
    fun `invalid completed name stays local and does not call provider`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified.copy(displayName = null))))
        fixture.machine.onIntent(SessionIntent.UpdateName("\n"))

        fixture.machine.onIntent(SessionIntent.CompleteName)

        val state = assertIs<SessionAccessState.CompletingName>(fixture.machine.state.value)
        assertTrue(state.invalidName)
        assertTrue(fixture.auth.nameUpdates.isEmpty())
    }

    @Test
    fun `backend failure exposes retry without protected session`() = runTest {
        val fixture = fixture(this, SaqzResult.Failure(AccessError.DataFailure(DataError.Server)))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertIs<SessionAccessState.BootstrapError>(fixture.machine.state.value)
        assertEquals(0, fixture.auth.signOutCalls)
    }

    @Test
    fun `bootstrap retry preserves firebase session and can recover`() = runTest {
        val fixture = fixture(this, SaqzResult.Failure(AccessError.DataFailure(DataError.Connectivity)))
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()
        fixture.session.result = SaqzResult.Success(session)

        fixture.machine.onIntent(SessionIntent.RetryBootstrap)
        runCurrent()

        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
        assertEquals(2, fixture.session.calls)
        assertEquals(0, fixture.auth.signOutCalls)
    }

    @Test
    fun `backend email not verified response returns to verification`() = runTest {
        val failure = SaqzResult.Failure(AccessError.EmailNotVerified)
        val fixture = fixture(this, failure)

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertIs<SessionAccessState.AwaitingVerification>(fixture.machine.state.value)
    }

    @Test
    fun `unauthenticated bootstrap remains a retryable bootstrap error`() = runTest {
        val fixture = fixture(this, SaqzResult.Failure(AccessError.Unauthenticated))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertIs<SessionAccessState.BootstrapError>(fixture.machine.state.value)
        assertEquals(0, fixture.auth.signOutCalls)
    }

    @Test
    fun `validation without global message uses generic bootstrap error state`() = runTest {
        val error = AccessError.Validation(
            ValidationDetails(globalMessages = emptyList(), fieldMessages = mapOf("email" to listOf("invalid"))),
        )
        val fixture = fixture(this, SaqzResult.Failure(error))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertIs<SessionAccessState.BootstrapError>(fixture.machine.state.value)
    }

    @Test
    fun `logout clears selected group pending invite and native session`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        fixture.machine.onIntent(SessionIntent.Logout)

        assertEquals(listOf<String?>(null), fixture.local.selectedWrites)
        assertEquals(listOf<String?>(null), fixture.local.pendingWrites)
        assertEquals(1, fixture.auth.signOutCalls)
        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
    }

    @Test
    fun `session invalidation uses the same local logout path`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        fixture.machine.invalidate()

        assertEquals(listOf<String?>(null), fixture.local.selectedWrites)
        assertEquals(listOf<String?>(null), fixture.local.pendingWrites)
        assertEquals(1, fixture.auth.signOutCalls)
        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
    }

    @Test
    fun `duplicate verification confirmation is single flight`() = runTest {
        val fixture = fixture(this)
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.VerificationRequired(unverified)))

        fixture.machine.onIntent(SessionIntent.ConfirmVerification)
        fixture.machine.onIntent(SessionIntent.ConfirmVerification)

        assertEquals(1, fixture.auth.reloadCalls)
    }

    private fun fixture(
        scope: kotlinx.coroutines.CoroutineScope,
        result: SaqzResult<AccessSession, AccessError> = SaqzResult.Success(session),
    ): Fixture {
        val auth = FakeAuthPort()
        val local = FakeLocalState()
        val gateway = FakeSessionGateway(result)
        return Fixture(SessionAccessStateMachine(auth, local, gateway, scope), auth, local, gateway)
    }

    private class FakeSessionGateway(var result: SaqzResult<AccessSession, AccessError>) : SessionGateway {
        var calls = 0
        override suspend fun bootstrap(): SaqzResult<AccessSession, AccessError> {
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
        val machine: SessionAccessStateMachine,
        val auth: FakeAuthPort,
        val local: FakeLocalState,
        val session: FakeSessionGateway,
    )

    private companion object {
        val unverified = NativeUser("subject", "person@example.test", false, "Person Name")
        val verified = unverified.copy(emailVerified = true)
        val session = AccessSession(AccessUser("user-id", "person@example.test", "Person Name"), emptyList())
    }
}
