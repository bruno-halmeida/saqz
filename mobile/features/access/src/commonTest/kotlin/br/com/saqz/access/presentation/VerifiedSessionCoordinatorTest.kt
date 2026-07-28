package br.com.saqz.access.presentation

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ProfilePhotoResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.session.AccessError
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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

    // ---- a trava de e-mail saiu (VUL-76 no backend, VUL-84 aqui) ----

    @Test
    fun `unverified authentication bootstraps instead of blocking`() = runTest {
        val fixture = fixture(this)

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(unverified)))
        runCurrent()

        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
        assertEquals(1, fixture.session.calls)
    }

    @Test
    fun `authentication no longer force refreshes the token before bootstrap`() = runTest {
        val fixture = fixture(this)

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertTrue(fixture.auth.tokenCalls.isEmpty())
        assertEquals(0, fixture.auth.reloadCalls)
    }

    // ---- emailVerified chega ao estado Ready ----

    @Test
    fun `ready carries the unverified email signal from the session`() = runTest {
        val fixture = fixture(this, SaqzResult.Success(session))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertFalse(assertIs<SessionAccessState.Ready>(fixture.machine.state.value).emailVerified)
    }

    @Test
    fun `ready carries the verified email signal from the session`() = runTest {
        val confirmed = session.copy(user = session.user.copy(emailVerified = true))
        val fixture = fixture(this, SaqzResult.Success(confirmed))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(unverified)))
        runCurrent()

        assertTrue(assertIs<SessionAccessState.Ready>(fixture.machine.state.value).emailVerified)
    }

    // ---- o portão único da 1c ----

    @Test
    fun `a session missing the phone opens identity completion`() = runTest {
        val fixture = fixture(this, SaqzResult.Success(phoneRequiredSession))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertEquals("Person Name", state.name)
        assertEquals("", state.phone)
    }

    @Test
    fun `a session missing a usable name opens identity completion`() = runTest {
        val nameless = SaqzResult.Success(session.copy(user = session.user.copy(displayName = " ")))
        val fixture = fixture(this, nameless)

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
    }

    @Test
    fun `identity completion seeds the fields the backend already knows`() = runTest {
        val known = phoneRequiredSession.copy(user = phoneRequiredSession.user.copy(phone = "+5511999990000"))
        val fixture = fixture(this, SaqzResult.Success(known))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertEquals("Person Name", state.name)
        assertEquals("+5511999990000", state.phone)
    }

    @Test
    fun `identity completion sends name and phone in a single call`() = runTest {
        val fixture = identityFixture()

        fixture.machine.onIntent(SessionIntent.UpdateName("Outra Pessoa"))
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        fixture.session.result = SaqzResult.Success(session)
        runCurrent()

        assertEquals(listOf<Pair<String, String?>>("+5511999990000" to "Outra Pessoa"), fixture.session.profileCalls)
        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
    }

    @Test
    fun `an invalid phone stays local and never reaches the backend`() = runTest {
        val fixture = identityFixture()

        fixture.machine.onIntent(SessionIntent.UpdatePhone("1234"))
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertTrue(state.invalidPhone)
        assertFalse(state.invalidName)
        assertTrue(fixture.session.profileCalls.isEmpty())
    }

    // Uma tela só recusa os dois campos de uma vez — a 1c não tem passo intermediário
    // onde só um erro possa aparecer.
    @Test
    fun `both invalid fields are reported together`() = runTest {
        val fixture = identityFixture()

        fixture.machine.onIntent(SessionIntent.UpdateName("\n"))
        fixture.machine.onIntent(SessionIntent.UpdatePhone("1234"))
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertTrue(state.invalidName)
        assertTrue(state.invalidPhone)
        assertTrue(fixture.session.profileCalls.isEmpty())
    }

    @Test
    fun `the chosen photo is kept in the identity state`() = runTest {
        val fixture = identityFixture()
        val photo = ProfilePhotoResult.Selected(byteArrayOf(1, 2, 3), "image/jpeg")

        fixture.machine.onIntent(SessionIntent.UpdatePhoto(photo))

        assertEquals(photo, assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value).photo)
    }

    @Test
    fun `identity completion is single flight`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))

        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        assertEquals(1, fixture.session.profileCalls.size)
    }

    @Test
    fun `a connectivity failure on completion is retryable in place`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))
        fixture.session.result = SaqzResult.Failure(AccessError.DataFailure(DataError.Connectivity))

        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertEquals(AuthUiError.NETWORK_UNAVAILABLE, state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `editing a field clears the error it produced`() = runTest {
        val fixture = identityFixture()
        fixture.machine.onIntent(SessionIntent.UpdatePhone("1234"))
        fixture.machine.onIntent(SessionIntent.CompleteIdentity)
        runCurrent()

        fixture.machine.onIntent(SessionIntent.UpdatePhone("(11) 99999-0000"))

        val state = assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        assertFalse(state.invalidPhone)
        assertNull(state.error)
    }

    // ---- bootstrap ----

    @Test
    fun `backend failure exposes retry without protected session`() = runTest {
        val fixture = fixture(this, SaqzResult.Failure(AccessError.DataFailure(DataError.Server)))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertIs<SessionAccessState.BootstrapError>(fixture.machine.state.value)
        assertEquals(0, fixture.auth.signOutCalls)
    }

    @Test
    fun `bootstrap retry preserves the native session and can recover`() = runTest {
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

    // A recusa some do backend no VUL-76, mas o tipo sobrevive no domínio: se voltar a
    // chegar, é erro de bootstrap como qualquer outro — não há mais tela para onde mandar.
    @Test
    fun `a stale email-not-verified refusal is a plain bootstrap error`() = runTest {
        val fixture = fixture(this, SaqzResult.Failure(AccessError.EmailNotVerified))

        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()

        assertIs<SessionAccessState.BootstrapError>(fixture.machine.state.value)
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

    // ---- saída ----

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

    private fun fixture(
        scope: kotlinx.coroutines.CoroutineScope,
        result: SaqzResult<AccessSession, AccessError> = SaqzResult.Success(session),
    ): Fixture {
        val auth = FakeAuthPort()
        val local = FakeLocalState()
        val gateway = FakeSessionGateway(result)
        return Fixture(SessionAccessStateMachine(auth, local, gateway, scope), auth, local, gateway)
    }

    /** Parado na 1c: o backend pediu telefone, e o nome já veio preenchido. */
    private fun TestScope.identityFixture(): Fixture {
        val fixture = fixture(this, SaqzResult.Success(phoneRequiredSession))
        fixture.machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verified)))
        runCurrent()
        assertIs<SessionAccessState.CompletingIdentity>(fixture.machine.state.value)
        return fixture
    }

    private class FakeSessionGateway(var result: SaqzResult<AccessSession, AccessError>) : SessionGateway {
        var calls = 0
        val profileCalls = mutableListOf<Pair<String, String?>>()

        override suspend fun bootstrap(): SaqzResult<AccessSession, AccessError> {
            calls += 1
            return result
        }

        override suspend fun completeProfile(
            phone: String,
            displayName: String?,
        ): SaqzResult<AccessSession, AccessError> {
            profileCalls += phone to displayName
            return result
        }
    }

    private class FakeAuthPort : NativeAuthPort {
        val tokenCalls = mutableListOf<Boolean>()
        var reloadCalls = 0
        var signOutCalls = 0

        override fun reloadUser(done: AuthCallback) { reloadCalls += 1 }
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun sendVerification(done: ResultCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) { tokenCalls += forceRefresh }
        override fun signOut(done: ResultCallback) { signOutCalls += 1; done.complete(OperationResult.Success) }
        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable { override fun cancel() = Unit }
        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = Unit
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
        val phoneRequiredSession = session.copy(user = session.user.copy(phoneRequired = true))
    }
}
