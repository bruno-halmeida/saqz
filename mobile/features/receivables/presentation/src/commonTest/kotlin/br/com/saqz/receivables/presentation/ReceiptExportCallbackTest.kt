package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptExportCallbackTest {
    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun teardown() = Dispatchers.resetMain()
    private fun model(port: ReceiptExportPort): MemberPaymentViewModel {
        val fake = MemberPaymentFake().apply { detail = detail.copy(instruments = listOf(paymentInstrument().copy(status = "CONFIRMED"))) }
        return MemberPaymentViewModel("order", fake, fake, fake, port, SavedStateHandle(), MemberPaymentRuntime(
            ReceivablesSessionContext { "session" }, ReceivablesRecoveryIdentity { "payer" },
            object : Clock { override fun now() = Instant.parse("2026-09-13T12:00:00Z") }))
    }
    @Test fun verifierShareWaitsForRealCallback() = runTest {
        var callback: ((ReceiptExportResult) -> Unit)? = null
        val vm = model(ReceiptExportPort { _, done -> callback = done })
        vm.onIntent(MemberPaymentIntent.ExportReceipt)
        assertNotNull(callback)
        assertTrue(vm.state.value.receiptSharing)
        assertFalse(vm.state.value.receiptShared, "no optimistic success before callback")
        callback!!(ReceiptExportResult.Shared)
        assertFalse(vm.state.value.receiptSharing)
        assertTrue(vm.state.value.receiptShared)
    }
    @Test fun verifierRefreshDuringShareDoesNotLeavePermanentBusyState() = runTest {
        var callback: ((ReceiptExportResult) -> Unit)? = null
        val vm = model(ReceiptExportPort { _, done -> callback = done })
        vm.onIntent(MemberPaymentIntent.ExportReceipt)
        vm.onIntent(MemberPaymentIntent.Refresh)
        callback!!(ReceiptExportResult.Shared)
        assertFalse(vm.state.value.receiptSharing, "return from native share must not freeze all payment intents")
        assertTrue(vm.state.value.canExportReceipt)
    }
    @Test fun verifierInvalidCalendarRenewalDoesNotReachNetwork() = runTest {
        val fake = MemberPaymentFake().apply { detail = detail.copy(instruments = listOf(paymentInstrument().copy(status = "EXPIRED"))) }
        val vm = MemberPaymentViewModel("order", fake, fake, fake, fake, SavedStateHandle(), MemberPaymentRuntime(
            ReceivablesSessionContext { "session" }, ReceivablesRecoveryIdentity { "payer" }, Clock.System))
        vm.onIntent(MemberPaymentIntent.RenewalDueDate("2026-99-99"))
        vm.onIntent(MemberPaymentIntent.RenewPix)
        assertTrue(fake.renewalCommands.isEmpty(), "invalid date must not reach network")
    }
}
