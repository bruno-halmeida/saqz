package br.com.saqz.receivables.presentation

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReceiptEntryDiscoveryTest {
    @Test fun offWithoutAccountHidesEntryAndOffWithAccountPreservesIt() = runTest {
        var accounts = emptyList<ReceiptAccount>()
        val coordinator = ReceivablesCoordinator(Availability(false), backgroundScope,
            ReceivablesSessionContext { "session" }, ReceiptAccountDirectory { SaqzResult.Success(accounts) })
        coordinator.onSessionChanged("session")
        runCurrent()
        assertFalse(coordinator.state.value.configurationEntryAvailable)
        accounts = listOf(ReceiptAccount("account", AccountRegistration.UNDER_REVIEW, false))
        coordinator.refresh()
        runCurrent()
        assertTrue(coordinator.state.value.configurationEntryAvailable)
        assertFalse(coordinator.state.value.discoveryAvailable)
        coordinator.onBackground()
        assertTrue(coordinator.state.value.configurationEntryAvailable)
    }

    @Test fun onWithoutAccountShowsEntryButDiscoveryFailureDoesNotDiscardMaintenance() = runTest {
        val availability = Availability(true)
        var result: SaqzResult<List<ReceiptAccount>, ReceiptError> = SaqzResult.Success(emptyList())
        val coordinator = ReceivablesCoordinator(availability, backgroundScope,
            ReceivablesSessionContext { "session" }, ReceiptAccountDirectory { result })
        coordinator.onSessionChanged("session")
        runCurrent()
        assertTrue(coordinator.state.value.configurationEntryAvailable)
        result = SaqzResult.Success(listOf(ReceiptAccount("account", AccountRegistration.APPROVED, true)))
        availability.fail = true
        coordinator.refresh()
        runCurrent()
        assertTrue(coordinator.state.value.configurationEntryAvailable)
        result = SaqzResult.Failure(ReceiptError.NETWORK)
        coordinator.refresh()
        runCurrent()
        assertTrue(coordinator.state.value.configurationEntryAvailable)
    }

    @Test fun delayedAccountFromPreviousSessionCannotExposeEntry() = runTest {
        val response = CompletableDeferred<List<ReceiptAccount>>()
        var session: String? = "old"
        val coordinator = ReceivablesCoordinator(Availability(false), backgroundScope,
            ReceivablesSessionContext { session }, ReceiptAccountDirectory {
                SaqzResult.Success(withContext(NonCancellable) { response.await() })
            })
        coordinator.onSessionChanged(session)
        runCurrent()
        session = null
        response.complete(listOf(ReceiptAccount("account", AccountRegistration.APPROVED, true)))
        runCurrent()
        assertFalse(coordinator.state.value.configurationEntryAvailable)
        assertFalse(coordinator.state.value.hasAccount)
    }

    private class Availability(private val enabled: Boolean) : ReceivablesAvailabilityGateway {
        var fail = false
        override suspend fun get(): SaqzResult<ReceivablesAvailability, ReceivablesError> = if (fail) {
            SaqzResult.Failure(ReceivablesError.Data(DataError.Connectivity))
        } else SaqzResult.Success(ReceivablesAvailability(enabled, enabled))
    }
}
