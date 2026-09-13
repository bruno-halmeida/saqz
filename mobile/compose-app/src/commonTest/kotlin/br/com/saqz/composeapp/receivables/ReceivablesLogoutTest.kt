package br.com.saqz.composeapp.receivables

import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.composeapp.testSaqzPlatformDependencies
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.ReceivablesAvailability
import br.com.saqz.receivables.domain.ReceivablesAvailabilityGateway
import br.com.saqz.receivables.domain.ReceivablesSessionContext
import br.com.saqz.receivables.presentation.ReceivablesCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReceivablesLogoutTest {
    @Test
    fun logoutRevokesPendingCallbackBeforeNativeSignOutAndObserver() = runTest {
        val fixture = fixture()
        fixture.login()
        runCurrent()
        fixture.gateway.complete(0)
        assertTrue(fixture.receivables.state.value.discoveryAvailable)
        fixture.receivables.prepareNewJourney { error("callback after logout") }
        fixture.machine.onIntent(SessionIntent.Logout)
        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
        assertNull(fixture.machine.activeSessionKey.value)
        // Unconfined response resumes before the binding's queued session observation.
        fixture.gateway.complete(1)
        assertFalse(fixture.receivables.state.value.discoveryAvailable)
        fixture.receivables.prepareNewJourney { error("new journey during signOut") }
        fixture.binding.onResume()
        runCurrent()
        assertEquals(2, fixture.gateway.requests.size)
        assertFalse(fixture.receivables.state.value.maintenanceAvailable)
        fixture.auth.completeSignOut()
        assertIs<SessionAccessState.SignedOut>(fixture.machine.state.value)
    }

    @Test
    fun sameUserReloginRejectsOldResponseEvenWhenObserverMissesSignedOut() = runTest {
        val fixture = fixture()
        fixture.login()
        runCurrent()
        fixture.gateway.complete(0)
        val oldKey = fixture.machine.activeSessionKey.value
        fixture.receivables.prepareNewJourney { error("old lifecycle callback") }
        fixture.machine.onIntent(SessionIntent.Logout)
        fixture.auth.completeSignOut()
        fixture.login()
        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
        assertNotEquals(oldKey, fixture.machine.activeSessionKey.value)
        fixture.gateway.complete(1)
        assertFalse(fixture.receivables.state.value.discoveryAvailable)
        runCurrent()
        assertEquals(3, fixture.gateway.requests.size)
        fixture.gateway.complete(2, enabled = false)
        assertFalse(fixture.receivables.state.value.discoveryAvailable)
        assertTrue(fixture.receivables.state.value.maintenanceAvailable)
        fixture.binding.onPause()
        assertTrue(fixture.receivables.state.value.maintenanceAvailable)
    }

    @Test
    fun logoutRejectsNewDiscoveryBeforeBindingObservesIt() = runTest {
        val fixture = fixture()
        fixture.login()
        runCurrent()
        fixture.gateway.complete(0)
        fixture.machine.onIntent(SessionIntent.Logout)
        assertIs<SessionAccessState.Ready>(fixture.machine.state.value)
        fixture.receivables.prepareNewJourney { error("new callback after logout intent") }
        fixture.receivables.refresh()
        assertEquals(1, fixture.gateway.requests.size)
        assertFalse(fixture.receivables.state.value.discoveryAvailable)
        fixture.auth.completeSignOut()
    }

    private fun TestScope.fixture(): Fixture {
        val dependencies = testSaqzPlatformDependencies().access
        val auth = DelayedSignOut(dependencies.auth)
        val immediateScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val session = AccessSession(AccessUser("same-user", null, "Pessoa"), emptyList())
        val gateway = object : SessionGateway {
            override suspend fun uploadPhoto(bytes: ByteArray, mediaType: String) = SaqzResult.Success(Unit)
            override suspend fun bootstrap() = SaqzResult.Success(session)
            override suspend fun completeProfile(phone: String, displayName: String?) = SaqzResult.Success(session)
        }
        val machine = SessionAccessStateMachine(auth, dependencies.localState, gateway, immediateScope)
        val availability = DelayedAvailability()
        val receivables = ReceivablesCoordinator(
            availability, immediateScope, ReceivablesSessionContext { machine.activeSessionKey.value },
        )
        val binding = ReceivablesSessionBinding(machine.activeSessionKey, receivables, backgroundScope)
        return Fixture(machine, auth, availability, receivables, binding)
    }

    private class DelayedSignOut(delegate: NativeAuthPort) : NativeAuthPort by delegate {
        private var callback: ResultCallback? = null
        override fun signOut(done: ResultCallback) { callback = done }
        fun completeSignOut() = checkNotNull(callback).complete(OperationResult.Success)
    }

    private class DelayedAvailability : ReceivablesAvailabilityGateway {
        val requests = mutableListOf<CompletableDeferred<SaqzResult.Success<ReceivablesAvailability>>>()
        override suspend fun get(): SaqzResult.Success<ReceivablesAvailability> {
            val response = CompletableDeferred<SaqzResult.Success<ReceivablesAvailability>>()
            requests += response
            return withContext(NonCancellable) { response.await() }
        }
        fun complete(index: Int, enabled: Boolean = true) {
            requests[index].complete(SaqzResult.Success(ReceivablesAvailability(enabled, enabled)))
        }
    }

    private data class Fixture(
        val machine: SessionAccessStateMachine,
        val auth: DelayedSignOut,
        val gateway: DelayedAvailability,
        val receivables: ReceivablesCoordinator,
        val binding: ReceivablesSessionBinding,
    ) {
        fun login() = machine.onIntent(SessionIntent.Accept(
            AuthTransition.Authenticated(NativeUser("same-native-user", null, true, "Pessoa")),
        ))
    }
}
