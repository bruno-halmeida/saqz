package br.com.saqz.receivables.presentation

import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptFinanceHomeViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }
    @Test fun noticesFromPreviousSessionCannotReappearAfterLateResponse() = runTest {
        var key = "session"; var calls = 0
        val gate = CompletableDeferred<Unit>()
        val vm = ReceiptFinanceHomeViewModel(ReceiptNoticesGateway {
            calls++; gate.await(); SaqzResult.Success(listOf(ReceiptNotice("id", "Plano", "Aviso")))
        }, ReceivablesSessionContext { key })
        vm.onIntent(Unit); vm.onIntent(Unit); assertEquals(1, calls)
        key = "new-session"; gate.complete(Unit)
        assertEquals(ReceiptError.SIGNED_OUT, vm.state.value.error); assertTrue(vm.state.value.notices.isEmpty())
    }
    @Test fun failureIsVisibleAndRetryCanLoadPublishedNotice() = runTest {
        var fail = true
        val vm = ReceiptFinanceHomeViewModel(ReceiptNoticesGateway {
            if (fail) SaqzResult.Failure(ReceiptError.NETWORK) else SaqzResult.Success(listOf(ReceiptNotice("id", "Plano", "Aviso")))
        }, ReceivablesSessionContext { "session" })
        vm.onIntent(Unit); assertEquals(ReceiptError.NETWORK, vm.state.value.error)
        fail = false; vm.onIntent(Unit)
        assertNull(vm.state.value.error); assertEquals("Aviso", vm.state.value.notices.single().message)
    }
}
