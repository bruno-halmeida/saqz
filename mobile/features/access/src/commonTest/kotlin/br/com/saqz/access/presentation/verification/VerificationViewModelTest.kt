package br.com.saqz.access.presentation.verification

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `state projects the awaiting verification email`() = runTest(mainDispatcher) {
        val fixture = awaitingFixture()
        runCurrent()

        assertEquals("person@example.test", fixture.viewModel.state.value.email)
        assertNull(fixture.viewModel.state.value.error)
        assertTrue(!fixture.viewModel.state.value.verificationSent)
    }

    @Test
    fun `confirm forwards to the shared session and enters loading`() = runTest(mainDispatcher) {
        val fixture = awaitingFixture()

        fixture.viewModel.onIntent(VerificationIntent.Confirm)
        runCurrent()

        assertEquals(1, fixture.auth.reloadCalls)
        assertTrue(fixture.viewModel.state.value.isLoading)
    }

    @Test
    fun `resend surfaces the sent confirmation`() = runTest(mainDispatcher) {
        val fixture = awaitingFixture()

        fixture.viewModel.onIntent(VerificationIntent.Resend)
        fixture.auth.completeOperation(OperationResult.Success)
        runCurrent()

        assertTrue(fixture.viewModel.state.value.verificationSent)
    }

    @Test
    fun `confirmation failure surfaces an auth error`() = runTest(mainDispatcher) {
        val fixture = awaitingFixture()

        fixture.viewModel.onIntent(VerificationIntent.Confirm)
        fixture.auth.completeAuth(AuthResult.Failure(NativeFailureCode.NETWORK_UNAVAILABLE))
        runCurrent()

        assertEquals(AuthUiError.NETWORK_UNAVAILABLE, fixture.viewModel.state.value.error)
    }

    private fun TestScope.awaitingFixture(): Fixture {
        val auth = FakeAuthPort()
        val machine = SessionAccessStateMachine(auth, FakeLocalState(), FakeSessionGateway(), backgroundScope)
        machine.onIntent(SessionIntent.Accept(AuthTransition.VerificationRequired(unverified)))
        return Fixture(VerificationViewModel(machine), auth)
    }

    private class Fixture(val viewModel: VerificationViewModel, val auth: FakeAuthPort)

    private class FakeSessionGateway : SessionGateway {
        override suspend fun bootstrap(): SaqzResult<AccessSession, AccessError> = error("not used")
    }

    private class FakeAuthPort : NativeAuthPort {
        var reloadCalls = 0
        private var authCallback: AuthCallback? = null
        private var operationCallback: ResultCallback? = null

        override fun reloadUser(done: AuthCallback) { reloadCalls += 1; authCallback = done }
        override fun sendVerification(done: ResultCallback) { operationCallback = done }
        fun completeAuth(result: AuthResult) = authCallback!!.complete(result)
        fun completeOperation(result: OperationResult) = operationCallback!!.complete(result)

        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable { override fun cancel() = Unit }
        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = Unit
        override fun sendPasswordReset(email: String, done: ResultCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
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
        val unverified = NativeUser("subject", "person@example.test", false, "Person Name")
    }
}
