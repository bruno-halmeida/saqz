package br.com.saqz.receivables.presentation

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.ReceivablesAvailability
import br.com.saqz.receivables.domain.ReceivablesAvailabilityGateway
import br.com.saqz.receivables.domain.ReceivablesError
import br.com.saqz.receivables.domain.ReceivablesSessionContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReceivablesCoordinatorTest {
    @Test
    fun initialAndSignedOutStatesCannotStartAJourney() = runTest {
        val fake = FakeGateway()
        var currentKey: String? = null
        val coordinator = ReceivablesCoordinator(fake, backgroundScope, ReceivablesSessionContext { currentKey })
        coordinator.prepareNewJourney { error("must not navigate") }
        runCurrent()
        assertEquals(0, fake.requests.size)
        assertEquals(ReceivablesState(), coordinator.state.value)
    }

    @Test
    fun loginLoadsAndMaintenanceSurvivesOffAndFailure() = runTest {
        val fake = FakeGateway()
        var currentKey: String? = null
        val coordinator = ReceivablesCoordinator(fake, backgroundScope, ReceivablesSessionContext { currentKey })
        currentKey = "user-a"
        coordinator.onSessionChanged("user-a")
        assertTrue(coordinator.state.value.loading)
        assertTrue(coordinator.state.value.maintenanceAvailable)
        runCurrent()
        fake.requests[0].complete(success(false))
        runCurrent()
        assertFalse(coordinator.state.value.discoveryAvailable)
        assertTrue(coordinator.state.value.maintenanceAvailable)
        coordinator.refresh()
        runCurrent()
        fake.requests[1].complete(SaqzResult.Failure(ReceivablesError.Data(DataError.Connectivity)))
        runCurrent()
        assertFalse(coordinator.state.value.discoveryAvailable)
        assertTrue(coordinator.state.value.maintenanceAvailable)
        assertEquals(ReceivablesError.Data(DataError.Connectivity), coordinator.state.value.error)
    }

    @Test
    fun everyNewJourneyRechecksAndRevocationPreventsCallback() = runTest {
        val fake = FakeGateway()
        var currentKey: String? = null
        val coordinator = ReceivablesCoordinator(fake, backgroundScope, ReceivablesSessionContext { currentKey })
        currentKey = "user-a"
        coordinator.onSessionChanged("user-a")
        runCurrent()
        fake.requests[0].complete(success(true))
        runCurrent()
        assertTrue(coordinator.state.value.discoveryAvailable)
        var opened = 0
        coordinator.prepareNewJourney { opened++ }
        assertFalse(coordinator.state.value.discoveryAvailable)
        runCurrent()
        fake.requests[1].complete(success(false))
        runCurrent()
        assertEquals(0, opened)
        coordinator.prepareNewJourney { opened++ }
        runCurrent()
        fake.requests[2].complete(success(true))
        runCurrent()
        assertEquals(1, opened)
    }

    @Test
    fun previousSessionResponseAndCallbackAreDiscardedEvenIfCancellationIsIgnored() = runTest {
        val fake = FakeGateway()
        var currentKey: String? = null
        val coordinator = ReceivablesCoordinator(fake, backgroundScope, ReceivablesSessionContext { currentKey })
        currentKey = "user-a"
        coordinator.onSessionChanged("user-a")
        runCurrent()
        coordinator.prepareNewJourney { error("stale navigation") }
        runCurrent()
        currentKey = null
        coordinator.onSessionChanged(null)
        assertEquals(ReceivablesState(), coordinator.state.value)
        currentKey = "user-b"
        coordinator.onSessionChanged("user-b")
        runCurrent()
        fake.requests[2].complete(success(false))
        runCurrent()
        fake.requests[0].complete(success(true))
        fake.requests[1].complete(success(true))
        runCurrent()
        assertFalse(coordinator.state.value.discoveryAvailable)
        assertTrue(coordinator.state.value.maintenanceAvailable)
    }

    @Test
    fun backgroundInvalidatesAndResumeRefreshesWithoutReusingDiscovery() = runTest {
        val fake = FakeGateway()
        var currentKey: String? = null
        val coordinator = ReceivablesCoordinator(fake, backgroundScope, ReceivablesSessionContext { currentKey })
        currentKey = "user-a"
        coordinator.onSessionChanged("user-a")
        runCurrent()
        fake.requests[0].complete(success(true))
        runCurrent()
        currentKey = "user-a"
        coordinator.onSessionChanged("user-a")
        assertEquals(1, fake.requests.size)
        coordinator.onBackground()
        assertFalse(coordinator.state.value.discoveryAvailable)
        assertTrue(coordinator.state.value.maintenanceAvailable)
        coordinator.refresh()
        runCurrent()
        fake.requests[1].complete(success(false))
        runCurrent()
        assertFalse(coordinator.state.value.discoveryAvailable)
    }

    @Test
    fun newerRefreshWinsOverOlderResponse() = runTest {
        val fake = FakeGateway()
        var currentKey: String? = null
        val coordinator = ReceivablesCoordinator(fake, backgroundScope, ReceivablesSessionContext { currentKey })
        currentKey = "user-a"
        coordinator.onSessionChanged("user-a")
        runCurrent()
        coordinator.refresh()
        runCurrent()
        fake.requests[1].complete(success(false))
        runCurrent()
        fake.requests[0].complete(success(true))
        runCurrent()
        assertFalse(coordinator.state.value.discoveryAvailable)
    }

    @Test
    fun sessionChangeBeforeObserverRunsStillRejectsPendingNavigation() = runTest {
        val fake = FakeGateway()
        var currentKey: String? = "user-a"
        val coordinator = ReceivablesCoordinator(fake, backgroundScope, ReceivablesSessionContext { currentKey })
        coordinator.onSessionChanged(currentKey)
        runCurrent()
        fake.requests[0].complete(success(true))
        runCurrent()
        coordinator.prepareNewJourney { error("previous user callback") }
        runCurrent()
        currentKey = "user-b"
        fake.requests[1].complete(success(true))
        runCurrent()
        assertEquals(ReceivablesState(), coordinator.state.value)
    }

    private fun success(enabled: Boolean) = SaqzResult.Success(ReceivablesAvailability(enabled, enabled))

    private class FakeGateway : ReceivablesAvailabilityGateway {
        val requests = mutableListOf<CompletableDeferred<SaqzResult<ReceivablesAvailability, ReceivablesError>>>()
        override suspend fun get(): SaqzResult<ReceivablesAvailability, ReceivablesError> {
            val response = CompletableDeferred<SaqzResult<ReceivablesAvailability, ReceivablesError>>()
            requests += response
            return withContext(NonCancellable) { response.await() }
        }
    }
}
