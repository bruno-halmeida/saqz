package br.com.saqz.groups.presentation.ui.finance.sheets

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.ChargeReminderGateway
import br.com.saqz.groups.domain.communication.ChargeReminderReceipt
import br.com.saqz.groups.domain.communication.CommunicationError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChargeReminderViewModelTest {
    @Test fun retryKeepsRequestAndSelectionAndSuccessPreventsDuplicateSend() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val requests = mutableListOf<Pair<String, List<String>>>()
            val gateway = ChargeReminderGateway { group, request, ids ->
                assertEquals("group", group.value)
                requests += request to ids
                if (requests.size == 1) SaqzResult.Failure(CommunicationError(DataError.Connectivity))
                else SaqzResult.Success(ChargeReminderReceipt(ids.size))
            }
            val saved = SavedStateHandle()
            val vm = ChargeReminderViewModel("group", gateway, saved)
            vm.onIntent(ChargeReminderIntent.Send(listOf("b", "a")))
            advanceUntilIdle()
            assertTrue(vm.state.value.failed)
            val restored = ChargeReminderViewModel("group", gateway, saved)
            restored.onIntent(ChargeReminderIntent.Send(listOf("a", "b")))
            advanceUntilIdle()
            assertEquals(requests[0], requests[1])
            assertEquals(listOf("a", "b"), requests[0].second)
            assertEquals(2, restored.state.value.sent)
            restored.onIntent(ChargeReminderIntent.Send(listOf("a", "b")))
            advanceUntilIdle()
            assertEquals(2, requests.size)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun inFlightRequestsAreNotDuplicatedAndEmptySelectionDoesNothing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val pending = CompletableDeferred<SaqzResult<ChargeReminderReceipt, CommunicationError>>()
            var calls = 0
            val vm = ChargeReminderViewModel("group", ChargeReminderGateway { _, _, _ -> calls++; pending.await() }, SavedStateHandle())
            vm.onIntent(ChargeReminderIntent.Send(emptyList()))
            runCurrent()
            assertEquals(0, calls)
            vm.onIntent(ChargeReminderIntent.Send(listOf("a")))
            vm.onIntent(ChargeReminderIntent.Send(listOf("b")))
            runCurrent()
            assertEquals(1, calls)
            assertTrue(vm.state.value.sending)
            pending.complete(SaqzResult.Success(ChargeReminderReceipt(1)))
            advanceUntilIdle()
            assertEquals(1, vm.state.value.sent)
            assertFalse(vm.state.value.sending)
        } finally { Dispatchers.resetMain() }
    }
}
