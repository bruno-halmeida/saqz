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
    @Test fun loadingAndFailuresDoNotAdvertiseAnUnreleasedFeature() = runTest {
        val availability = Availability(false)
        val payments = emptyPaymentHistory().apply { pageError = true }
        val coordinator = ReceivablesCoordinator(availability, backgroundScope,
            ReceivablesSessionContext { "session" }, ReceiptAccountDirectory { SaqzResult.Failure(ReceiptError.NETWORK) }, payments)
        coordinator.onSessionChanged("session")
        assertFalse(coordinator.state.value.configurationEntryAvailable)
        assertFalse(coordinator.state.value.paymentEntryAvailable)
        runCurrent()
        assertTrue(coordinator.state.value.accountLookupFailed)
        assertFalse(coordinator.state.value.configurationEntryAvailable)
        assertFalse(coordinator.state.value.paymentEntryAvailable)
        availability.fail = true
        coordinator.refresh()
        runCurrent()
        assertFalse(coordinator.state.value.configurationEntryAvailable)
        assertFalse(coordinator.state.value.paymentEntryAvailable)
    }

    @Test fun disablingRolloutHidesUnusedEntriesOnRefresh() = runTest {
        val availability = Availability(true)
        val coordinator = ReceivablesCoordinator(availability, backgroundScope,
            ReceivablesSessionContext { "session" }, ReceiptAccountDirectory { SaqzResult.Success(emptyList()) }, emptyPaymentHistory())
        coordinator.onSessionChanged("session")
        runCurrent()
        assertTrue(coordinator.state.value.configurationEntryAvailable)
        assertTrue(coordinator.state.value.paymentEntryAvailable)
        availability.enabled = false
        coordinator.refresh()
        runCurrent()
        assertFalse(coordinator.state.value.configurationEntryAvailable)
        assertFalse(coordinator.state.value.paymentEntryAvailable)
    }

    @Test fun existingPaymentsRemainAccessibleWithoutRolloutOrAFinancialAccount() = runTest {
        val payments = MemberPaymentFake()
        val coordinator = ReceivablesCoordinator(Availability(false), backgroundScope,
            ReceivablesSessionContext { "session" }, ReceiptAccountDirectory { SaqzResult.Success(emptyList()) }, payments)
        coordinator.onSessionChanged("session")
        runCurrent()
        assertTrue(coordinator.state.value.paymentEntryAvailable)
        assertFalse(coordinator.state.value.configurationEntryAvailable)
        payments.pageError = true
        coordinator.refresh()
        runCurrent()
        assertTrue(coordinator.state.value.paymentEntryAvailable)
        coordinator.onBackground()
        assertTrue(coordinator.state.value.paymentEntryAvailable)
        coordinator.onSessionChanged(null)
        assertFalse(coordinator.state.value.paymentEntryAvailable)
        assertFalse(coordinator.state.value.hasPayments)
    }

    @Test fun previousSessionsPaymentsCannotExposeEntry() = runTest {
        var session = "old"
        val response = CompletableDeferred<SaqzResult<MemberPaymentPage, ReceiptError>>()
        val payments = object : MemberPaymentsGateway by emptyPaymentHistory() {
            override suspend fun orders(after: String?) = withContext(NonCancellable) { response.await() }
        }
        val coordinator = ReceivablesCoordinator(Availability(false), backgroundScope,
            ReceivablesSessionContext { session }, ReceiptAccountDirectory { SaqzResult.Success(emptyList()) }, payments)
        coordinator.onSessionChanged(session)
        runCurrent()
        session = "new"
        response.complete(SaqzResult.Success(MemberPaymentPage(listOf(paymentOrder), null)))
        runCurrent()
        assertFalse(coordinator.state.value.paymentEntryAvailable)
        assertFalse(coordinator.state.value.hasPayments)
    }

    @Test fun offWithoutAccountHidesEntryAndOffWithAccountPreservesIt() = runTest {
        var accounts = emptyList<ReceiptAccount>()
        val coordinator = ReceivablesCoordinator(Availability(false), backgroundScope,
            ReceivablesSessionContext { "session" }, ReceiptAccountDirectory { SaqzResult.Success(accounts) }, emptyPaymentHistory())
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
            ReceivablesSessionContext { "session" }, ReceiptAccountDirectory { result }, emptyPaymentHistory())
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
            }, emptyPaymentHistory())
        coordinator.onSessionChanged(session)
        runCurrent()
        session = null
        response.complete(listOf(ReceiptAccount("account", AccountRegistration.APPROVED, true)))
        runCurrent()
        assertFalse(coordinator.state.value.configurationEntryAvailable)
        assertFalse(coordinator.state.value.hasAccount)
    }

    private class Availability(var enabled: Boolean) : ReceivablesAvailabilityGateway {
        var fail = false
        override suspend fun get(): SaqzResult<ReceivablesAvailability, ReceivablesError> = if (fail) {
            SaqzResult.Failure(ReceivablesError.Data(DataError.Connectivity))
        } else SaqzResult.Success(ReceivablesAvailability(enabled, enabled))
    }
}

internal fun emptyPaymentHistory() = MemberPaymentFake().apply { page = MemberPaymentPage(emptyList(), null) }
