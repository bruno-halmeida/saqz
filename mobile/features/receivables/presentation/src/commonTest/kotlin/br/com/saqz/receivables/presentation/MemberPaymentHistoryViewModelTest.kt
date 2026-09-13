package br.com.saqz.receivables.presentation

import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MemberPaymentHistoryViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }
    @Test fun pagesDeduplicateAndRefreshStartsAtTheBeginningWithoutCommercialGate() = runTest {
        val f = MemberPaymentFake().apply { page = MemberPaymentPage(listOf(paymentOrder), "order") }
        val vm = MemberPaymentHistoryViewModel(f, ReceivablesSessionContext { "session" })
        assertEquals(listOf(paymentOrder), vm.state.value.orders)
        f.page = MemberPaymentPage(listOf(paymentOrder, paymentOrder.copy(id = "second")), null)
        vm.onIntent(MemberPaymentHistoryIntent.More)
        assertEquals(listOf("order", "second"), vm.state.value.orders.map { it.id })
        assertNull(vm.state.value.nextCursor)
        vm.onIntent(MemberPaymentHistoryIntent.More); assertEquals(listOf(null, "order"), f.cursors)
        f.page = MemberPaymentPage(emptyList(), null); vm.onIntent(MemberPaymentHistoryIntent.Refresh)
        assertTrue(vm.state.value.orders.isEmpty()); assertEquals(listOf(null, "order", null), f.cursors)
    }
    @Test fun failedLoadCanRetryAndOnlyListedOrdersNavigate() = runTest {
        val f = MemberPaymentFake().apply { pageError = true }
        val vm = MemberPaymentHistoryViewModel(f, ReceivablesSessionContext { "session" })
        assertEquals(ReceiptError.NETWORK, vm.state.value.error); assertFalse(vm.state.value.loading)
        f.pageError = false; vm.onIntent(MemberPaymentHistoryIntent.Refresh)
        assertNull(vm.state.value.error)
        val opened = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { if (vm.validEffect(it)) opened += it.orderId }
        }
        vm.onIntent(MemberPaymentHistoryIntent.Open("foreign")); vm.onIntent(MemberPaymentHistoryIntent.Open("order"))
        assertEquals(listOf("order"), opened); job.cancel()
    }
    @Test fun stalePageCannotReplaceARefreshedHistory() = runTest {
        val f = MemberPaymentFake()
        val first = CompletableDeferred<SaqzResult<MemberPaymentPage, ReceiptError>>()
        f.delayedPage = first; val vm = MemberPaymentHistoryViewModel(f, ReceivablesSessionContext { "session" })
        f.delayedPage = null; f.page = MemberPaymentPage(emptyList(), null)
        vm.onIntent(MemberPaymentHistoryIntent.Refresh)
        first.complete(SaqzResult.Success(MemberPaymentPage(listOf(paymentOrder), "order")))
        assertTrue(vm.state.value.orders.isEmpty()); assertNull(vm.state.value.nextCursor)
    }
    @Test fun newSessionDiscardsAllHistoryAndDoesNotFetchWithOldViewModel() = runTest {
        var key = "session"; val f = MemberPaymentFake()
        val vm = MemberPaymentHistoryViewModel(f, ReceivablesSessionContext { key })
        key = "next-session"; vm.onIntent(MemberPaymentHistoryIntent.Refresh)
        assertTrue(vm.state.value.orders.isEmpty()); assertEquals(ReceiptError.SIGNED_OUT, vm.state.value.error)
        assertEquals(1, f.cursors.size)
    }
    @Test fun queuedNavigationCannotOutliveHistoryRefreshOrSession() = runTest {
        for (change in listOf("same-list", "empty-list", "session")) {
            var key = "session"; val f = MemberPaymentFake()
            val vm = MemberPaymentHistoryViewModel(f, ReceivablesSessionContext { key })
            vm.onIntent(MemberPaymentHistoryIntent.Open("order"))
            if (change == "session") key = "next-session" else {
                if (change == "empty-list") f.page = MemberPaymentPage(emptyList(), null)
                vm.onIntent(MemberPaymentHistoryIntent.Refresh)
            }
            assertFalse(vm.validEffect(vm.effects.first()), change)
        }
    }
}
