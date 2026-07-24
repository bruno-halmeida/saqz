package br.com.saqz.access.presentation.route

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.session.AccessError
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.CompletableDeferred
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

/**
 * State/intent coverage for each [AccessRouteMode] (T11). Derived from
 * ACCESSNAV-01, LIFE-01, LIFE-03, LIFE-05.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccessRouteViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `STARTING mode projects the Starting state`() = runTest(mainDispatcher) {
        val machine = SessionAccessStateMachine(FakeAuthPort(), FakeLocalState(), FakeSessionGateway(), backgroundScope)

        val viewModel = AccessRouteViewModel(AccessRouteMode.STARTING, machine)

        assertEquals(AccessRouteState.Starting, viewModel.state.value)
    }

    @Test
    fun `STARTING mode owns no session state machine and is not coupled to session transitions`() =
        runTest(mainDispatcher) {
            val machine = SessionAccessStateMachine(
                FakeAuthPort(),
                FakeLocalState(),
                FakeSessionGateway(),
                backgroundScope,
            )
            val viewModel = AccessRouteViewModel(AccessRouteMode.STARTING, machine)

            machine.onIntent(SessionIntent.Accept(AuthTransition.VerificationRequired(unverifiedUser)))
            runCurrent()

            assertEquals(AccessRouteState.Starting, viewModel.state.value)
        }

    @Test
    fun `BOOTSTRAP mode projects Bootstrapping while the shared session is bootstrapping`() = runTest(mainDispatcher) {
        val fixture = bootstrappingFixture()

        assertEquals(AccessRouteState.Bootstrap(isLoading = true, failed = false), fixture.viewModel.state.value)
    }

    @Test
    fun `BOOTSTRAP mode projects the failure when the shared session bootstrap fails`() = runTest(mainDispatcher) {
        val fixture = bootstrappingFixture()

        fixture.gateway.calls.single().complete(SaqzResult.Failure(AccessError.Unauthenticated))
        runCurrent()

        assertEquals(AccessRouteState.Bootstrap(isLoading = false, failed = true), fixture.viewModel.state.value)
    }

    @Test
    fun `BOOTSTRAP mode projects an inert state for any other session state`() = runTest(mainDispatcher) {
        val machine = SessionAccessStateMachine(FakeAuthPort(), FakeLocalState(), FakeSessionGateway(), backgroundScope)

        val viewModel = AccessRouteViewModel(AccessRouteMode.BOOTSTRAP, machine)

        assertEquals(AccessRouteState.Bootstrap(isLoading = false, failed = false), viewModel.state.value)
    }

    @Test
    fun `RetryBootstrap forwards to the shared session and re-enters Bootstrapping`() = runTest(mainDispatcher) {
        val fixture = bootstrappingFixture()
        fixture.gateway.calls.single().complete(SaqzResult.Failure(AccessError.Unauthenticated))
        runCurrent()

        fixture.viewModel.onIntent(AccessRouteIntent.RetryBootstrap)
        runCurrent()

        assertEquals(AccessRouteState.Bootstrap(isLoading = true, failed = false), fixture.viewModel.state.value)
        assertEquals(2, fixture.gateway.calls.size)
    }

    @Test
    fun `RetryBootstrap in STARTING mode does not forward to the shared session`() = runTest(mainDispatcher) {
        val gateway = FakeSessionGateway()
        val machine = SessionAccessStateMachine(FakeAuthPort(), FakeLocalState(), gateway, backgroundScope)
        val viewModel = AccessRouteViewModel(AccessRouteMode.STARTING, machine)

        viewModel.onIntent(AccessRouteIntent.RetryBootstrap)
        runCurrent()

        assertEquals(AccessRouteState.Starting, viewModel.state.value)
    }

    private fun TestScope.bootstrappingFixture(): BootstrapFixture {
        val gateway = FakeSessionGateway()
        val machine = SessionAccessStateMachine(FakeAuthPort(), FakeLocalState(), gateway, backgroundScope)
        machine.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(verifiedNamedUser)))
        runCurrent()
        return BootstrapFixture(AccessRouteViewModel(AccessRouteMode.BOOTSTRAP, machine), gateway)
    }

    private class BootstrapFixture(val viewModel: AccessRouteViewModel, val gateway: FakeSessionGateway)

    private class FakeSessionGateway : SessionGateway {
        val calls = mutableListOf<CompletableDeferred<SaqzResult<AccessSession, AccessError>>>()

        override suspend fun completeProfile(
            phone: String,
            displayName: String?,
        ): SaqzResult<AccessSession, AccessError> = error("not used")

        override suspend fun bootstrap(): SaqzResult<AccessSession, AccessError> {
            val deferred = CompletableDeferred<SaqzResult<AccessSession, AccessError>>()
            calls += deferred
            return deferred.await()
        }
    }

    private class FakeAuthPort : NativeAuthPort {
        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable { override fun cancel() = Unit }
        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = Unit
        override fun sendPasswordReset(email: String, done: ResultCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit
        override fun signOut(done: ResultCallback) = Unit
        override fun reloadUser(done: AuthCallback) = Unit
        override fun sendVerification(done: ResultCallback) = Unit
    }

    private class FakeLocalState : LocalAccessStatePort {
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
        override fun writePendingInvite(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
        override fun readSelectedGroupId(done: ValueCallback) = Unit
        override fun readPendingInvite(done: ValueCallback) = Unit
    }

    private companion object {
        val unverifiedUser = NativeUser("subject", "person@example.test", false, "Person Name")
        val verifiedNamedUser = NativeUser("subject-2", "person2@example.test", true, "Person Two")
    }
}
