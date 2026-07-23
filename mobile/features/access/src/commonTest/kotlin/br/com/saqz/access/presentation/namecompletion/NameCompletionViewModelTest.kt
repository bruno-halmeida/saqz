package br.com.saqz.access.presentation.namecompletion

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
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.session.AccessError
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NameCompletionViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `state projects the completing-name step with an empty name`() = runTest(mainDispatcher) {
        val fixture = completingFixture()
        runCurrent()

        assertEquals("", fixture.viewModel.state.value.name)
        assertNull(fixture.viewModel.state.value.error)
        assertFalse(fixture.viewModel.state.value.invalidName)
        assertFalse(fixture.viewModel.state.value.isLoading)
    }

    @Test
    fun `update name forwards to the shared session and reflects the typed value`() = runTest(mainDispatcher) {
        val fixture = completingFixture()

        fixture.viewModel.onIntent(NameCompletionIntent.UpdateName("Ada Lovelace"))
        runCurrent()

        assertEquals("Ada Lovelace", fixture.viewModel.state.value.name)
    }

    @Test
    fun `complete with a valid name submits to auth and enters loading`() = runTest(mainDispatcher) {
        val fixture = completingFixture()

        fixture.viewModel.onIntent(NameCompletionIntent.UpdateName("Ada Lovelace"))
        fixture.viewModel.onIntent(NameCompletionIntent.Complete)
        runCurrent()

        assertEquals("Ada Lovelace", fixture.auth.updatedName)
        assertTrue(fixture.viewModel.state.value.isLoading)
    }

    @Test
    fun `complete with an invalid name shows corrective state and never submits`() = runTest(mainDispatcher) {
        val fixture = completingFixture()

        fixture.viewModel.onIntent(NameCompletionIntent.UpdateName("A"))
        fixture.viewModel.onIntent(NameCompletionIntent.Complete)
        runCurrent()

        assertTrue(fixture.viewModel.state.value.invalidName)
        assertFalse(fixture.viewModel.state.value.isLoading)
        assertNull(fixture.auth.updatedName)
    }

    @Test
    fun `submission failure surfaces an auth error`() = runTest(mainDispatcher) {
        val fixture = completingFixture()

        fixture.viewModel.onIntent(NameCompletionIntent.UpdateName("Ada Lovelace"))
        fixture.viewModel.onIntent(NameCompletionIntent.Complete)
        fixture.auth.completeUpdate(AuthResult.Failure(NativeFailureCode.NETWORK_UNAVAILABLE))
        runCurrent()

        assertEquals(AuthUiError.NETWORK_UNAVAILABLE, fixture.viewModel.state.value.error)
        assertFalse(fixture.viewModel.state.value.isLoading)
    }

    private fun TestScope.completingFixture(): Fixture {
        val auth = FakeAuthPort()
        val machine = SessionAccessStateMachine(auth, FakeLocalState(), FakeSessionGateway(), backgroundScope)
        machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(unnamed)))
        return Fixture(NameCompletionViewModel(machine), auth)
    }

    private class Fixture(val viewModel: NameCompletionViewModel, val auth: FakeAuthPort)

    private class FakeSessionGateway : SessionGateway {
        override suspend fun bootstrap(): SaqzResult<AccessSession, AccessError> = error("not used")
        override suspend fun completeProfile(phone: String, displayName: String?): SaqzResult<AccessSession, AccessError> = error("not used")
    }

    private class FakeAuthPort : NativeAuthPort {
        var updatedName: String? = null
        private var authCallback: AuthCallback? = null

        override fun updateDisplayName(name: String, done: AuthCallback) { updatedName = name; authCallback = done }
        fun completeUpdate(result: AuthResult) = authCallback!!.complete(result)

        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable { override fun cancel() = Unit }
        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = Unit
        override fun sendPasswordReset(email: String, done: ResultCallback) = Unit
        override fun reloadUser(done: AuthCallback) = Unit
        override fun sendVerification(done: ResultCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit
        override fun signOut(done: ResultCallback) = Unit
    }

    private class FakeLocalState : LocalAccessStatePort {
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
        override fun writePendingInvite(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
        override fun readSelectedGroupId(done: ValueCallback) = Unit
        override fun readPendingInvite(done: ValueCallback) = Unit
    }

    private companion object {
        val unnamed = NativeUser("subject", "person@example.test", true, null)
    }
}
