package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChargeApprovalViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }
    private fun model(f: ApprovalFake, saved: SavedStateHandle = SavedStateHandle(), key: () -> String? = { "session" }) =
        ChargeApprovalViewModel("group", "charge", f, f, ReceivablesSessionContext(key), ReceivablesRecoveryIdentity { "owner" }, saved)
    private fun review(f: ApprovalFake, saved: SavedStateHandle = SavedStateHandle()) = model(f, saved).also {
        it.onIntent(ChargeApprovalIntent.Account("account"))
    }
    @Test fun explicitSelectionAllTermsAndAcceptancePrecedeRelease() = runTest {
        val f = ApprovalFake(); val vm = model(f)
        assertNull(vm.state.value.accountId); assertEquals(listOf("account"), vm.state.value.accounts.map { it.id })
        vm.onIntent(ChargeApprovalIntent.Account("foreign")); assertTrue(f.lookups.isEmpty())
        vm.onIntent(ChargeApprovalIntent.Account("account"))
        assertEquals(listOf(approvalTarget), f.lookups); assertEquals(listOf("v1", "v2"), f.termRequests)
        assertEquals(approvalReview, vm.state.value.review); assertFalse(vm.state.value.canApprove)
        vm.onIntent(ChargeApprovalIntent.Approve); assertTrue(f.approvals.isEmpty())
        vm.onIntent(ChargeApprovalIntent.Accept(true)); assertTrue(vm.state.value.canApprove)
        vm.onIntent(ChargeApprovalIntent.Approve)
        assertEquals(approvalTarget, f.approvals.single().first)
        assertEquals(approvalReview.fingerprint, f.approvals.single().second.fingerprint)
        assertTrue(f.approvals.single().second.accepted); assertTrue(f.approvals.single().second.requestId.isNotBlank())
        assertEquals(approvalOrder, vm.state.value.detail?.order); assertFalse(vm.state.value.accepted)
    }
    @Test fun missingMismatchedTermsAndRefreshRequireNewReview() = runTest {
        for (bad in listOf<ReceiptTerms?>(null, ReceiptTerms("other", "Terms"), ReceiptTerms("v2", ""))) {
            val f = ApprovalFake().apply { term2 = bad }; val vm = review(f)
            vm.onIntent(ChargeApprovalIntent.Accept(true)); vm.onIntent(ChargeApprovalIntent.Approve)
            assertFalse(vm.state.value.canAccept); assertTrue(f.approvals.isEmpty())
        }
        val f = ApprovalFake(); val vm = review(f)
        vm.onIntent(ChargeApprovalIntent.Accept(true)); vm.onIntent(ChargeApprovalIntent.Refresh)
        assertFalse(vm.state.value.accepted); assertEquals(approvalReview, vm.state.value.review)
        vm.onIntent(ChargeApprovalIntent.Accept(true)); vm.onIntent(ChargeApprovalIntent.ChooseAccount)
        assertNull(vm.state.value.accountId); assertNull(vm.state.value.review); assertFalse(vm.state.value.accepted)
    }
    @Test fun existingOrderIsManagedWithoutNewBusinessPreview() = runTest {
        val f = ApprovalFake().apply { detail = MemberPaymentDetail(approvalOrder, emptyList()) }; val vm = review(f)
        assertEquals(approvalOrder, vm.state.value.detail?.order); assertEquals(0, f.previews)
        assertTrue(f.termRequests.isEmpty()); vm.onIntent(ChargeApprovalIntent.Approve); assertTrue(f.approvals.isEmpty())
        assertTrue(vm.state.value.canCancel)
    }
    @Test fun uncertainApprovalPersistsBeforeNetworkAndReplaysIdenticalAfterProcessDeath() = runTest {
        val saved = SavedStateHandle(); val f = ApprovalFake().apply { writeError = ReceiptError.UNCERTAIN }
        f.beforeWrite = { assertNotNull(saved.get<String>("approval.attempt")) }
        val vm = review(f, saved); vm.onIntent(ChargeApprovalIntent.Accept(true)); vm.onIntent(ChargeApprovalIntent.Approve)
        val marker = saved.get<String>("approval.attempt")!!
        assertEquals(ApprovalAttempt("owner", "group", "charge", "account", f.approvals.single().second.requestId,
            approvalReview.fingerprint), Json.decodeFromString<ApprovalAttempt>(marker))
        vm.onIntent(ChargeApprovalIntent.ChooseAccount); vm.onIntent(ChargeApprovalIntent.Account("foreign"))
        vm.onIntent(ChargeApprovalIntent.Approve); assertEquals(1, f.approvals.size)
        vm.onIntent(ChargeApprovalIntent.Refresh); assertNotNull(vm.state.value.attempt)
        assertEquals(marker, saved.get<String>("approval.attempt"))
        f.lookupError = ReceiptError.NETWORK; vm.onIntent(ChargeApprovalIntent.Refresh)
        assertEquals(marker, saved.get<String>("approval.attempt")); assertEquals(ReceiptError.NETWORK, vm.state.value.error)
        f.lookupError = null
        val restoredSaved = SavedStateHandle(mapOf("approval.attempt" to marker)); val restored = model(f, restoredSaved)
        assertNotNull(restored.state.value.attempt); assertFalse(restored.state.value.accepted); assertEquals(1, f.approvals.size)
        restored.onIntent(ChargeApprovalIntent.Replay); assertEquals(f.approvals[0], f.approvals[1])
        f.writeError = null; restored.onIntent(ChargeApprovalIntent.Replay)
        assertNull(restored.state.value.attempt); assertNull(restoredSaved.get<String>("approval.attempt"))
        assertEquals(approvalOrder, restored.state.value.detail?.order)
    }
    @Test fun approvalFoundByLookupSettlesUncertaintyWithoutAnotherWrite() = runTest {
        val f = ApprovalFake().apply { writeError = ReceiptError.UNCERTAIN }; val saved = SavedStateHandle()
        val vm = review(f, saved); vm.onIntent(ChargeApprovalIntent.Accept(true)); vm.onIntent(ChargeApprovalIntent.Approve)
        f.detail = MemberPaymentDetail(approvalOrder, emptyList()); vm.onIntent(ChargeApprovalIntent.Refresh)
        assertNull(vm.state.value.attempt); assertEquals(1, f.approvals.size)
        assertNull(saved.get<String>("approval.attempt")); assertEquals(approvalOrder, vm.state.value.detail?.order)
    }
    @Test fun explicitReplayConsultsFirstAndLookupFailurePreservesExactCommand() = runTest {
        val saved = SavedStateHandle(); val f = ApprovalFake().apply { writeError = ReceiptError.UNCERTAIN }
        val vm = review(f, saved); vm.onIntent(ChargeApprovalIntent.Accept(true)); vm.onIntent(ChargeApprovalIntent.Approve)
        val marker = saved.get<String>("approval.attempt")
        f.lookupError = ReceiptError.NETWORK; vm.onIntent(ChargeApprovalIntent.Replay)
        assertEquals(1, f.approvals.size); assertEquals(marker, saved.get<String>("approval.attempt"))
        assertEquals(ReceiptError.NETWORK, vm.state.value.error)
        f.lookupError = null; f.detail = MemberPaymentDetail(approvalOrder, emptyList())
        vm.onIntent(ChargeApprovalIntent.Replay)
        assertEquals(1, f.approvals.size); assertEquals(approvalOrder, vm.state.value.detail?.order)
        assertNull(vm.state.value.attempt); assertNull(saved.get<String>("approval.attempt"))
    }
    @Test fun paidOrRefundedDuringPendingCancellationStopsReplayAndPreservesActualStatus() = runTest {
        for (status in listOf("PAID", "REFUNDED", "CHARGEBACK", "CANCELLED")) {
            val saved = SavedStateHandle()
            val f = ApprovalFake().apply { detail = MemberPaymentDetail(approvalOrder, emptyList()) }
            val vm = review(f, saved); vm.onIntent(ChargeApprovalIntent.RequestCancel); vm.onIntent(ChargeApprovalIntent.ConfirmCancel)
            assertNotNull(vm.state.value.attempt)
            f.detail = MemberPaymentDetail(approvalOrder.copy(status = status), emptyList())
            vm.onIntent(ChargeApprovalIntent.Replay)
            assertEquals(status, vm.state.value.detail?.order?.status); assertEquals(1, f.cancels.size)
            assertNull(vm.state.value.attempt); assertNull(saved.get<String>("approval.attempt"))
            assertFalse(vm.state.value.canCancel); assertFalse(vm.state.value.canApprove)
        }
    }
    @Test fun cancellationRequiresConfirmationAndKeepsPendingUntilRemoteOutcome() = runTest {
        val saved = SavedStateHandle(); val f = ApprovalFake().apply { detail = MemberPaymentDetail(approvalOrder, emptyList()) }
        val vm = review(f, saved); vm.onIntent(ChargeApprovalIntent.ConfirmCancel); assertTrue(f.cancels.isEmpty())
        vm.onIntent(ChargeApprovalIntent.RequestCancel); assertTrue(vm.state.value.confirmCancel)
        vm.onIntent(ChargeApprovalIntent.DismissCancel); assertFalse(vm.state.value.confirmCancel)
        vm.onIntent(ChargeApprovalIntent.RequestCancel)
        f.beforeWrite = { assertEquals("order", Json.decodeFromString<ApprovalAttempt>(saved.get<String>("approval.attempt")!!).orderId) }
        vm.onIntent(ChargeApprovalIntent.ConfirmCancel)
        assertEquals("CANCEL_PENDING", vm.state.value.detail?.order?.status); assertNotNull(vm.state.value.attempt)
        assertEquals(approvalTarget, f.cancels.single().first); assertEquals("order", f.cancels.single().second)
        vm.onIntent(ChargeApprovalIntent.Refresh); assertNotNull(vm.state.value.attempt)
        vm.onIntent(ChargeApprovalIntent.Replay); assertEquals(f.cancels[0], f.cancels[1])
        f.detail = MemberPaymentDetail(approvalOrder.copy(status = "CANCELLED"), emptyList()); vm.onIntent(ChargeApprovalIntent.Refresh)
        assertNull(vm.state.value.attempt); assertNull(saved.get<String>("approval.attempt")); assertFalse(vm.state.value.canCancel)
        assertEquals("CANCELLED", vm.state.value.detail?.order?.status)
    }
    @Test fun terminalOrderNeverOffersCancellationOrApproval() = runTest {
        for (status in listOf("PAID", "REFUNDED", "CHARGEBACK", "CANCELLED", "CANCEL_PENDING", "OTHER")) {
            val f = ApprovalFake().apply { detail = MemberPaymentDetail(approvalOrder.copy(status = status), emptyList()) }; val vm = review(f)
            vm.onIntent(ChargeApprovalIntent.RequestCancel); vm.onIntent(ChargeApprovalIntent.ConfirmCancel)
            vm.onIntent(ChargeApprovalIntent.Approve)
            assertFalse(vm.state.value.canCancel); assertTrue(f.cancels.isEmpty()); assertTrue(f.approvals.isEmpty())
        }
    }
    @Test fun staleRejectionClearsAttemptAndForcesReviewAgain() = runTest {
        val f = ApprovalFake().apply { writeError = ReceiptError.STALE }; val saved = SavedStateHandle(); val vm = review(f, saved)
        vm.onIntent(ChargeApprovalIntent.Accept(true)); vm.onIntent(ChargeApprovalIntent.Approve)
        assertEquals(ReceiptError.STALE, vm.state.value.error); assertNull(vm.state.value.attempt)
        assertNull(vm.state.value.review); assertFalse(vm.state.value.accepted); assertNull(saved.get<String>("approval.attempt"))
        vm.onIntent(ChargeApprovalIntent.Approve); assertEquals(1, f.approvals.size)
        vm.onIntent(ChargeApprovalIntent.Refresh); assertFalse(vm.state.value.canApprove)
    }
    @Test fun staleGenerationAndNewSessionCannotDeliverOldDataOrEffects() = runTest {
        var key: String? = "session"; val f = ApprovalFake(); val vm = model(f, key = { key })
        val old = CompletableDeferred<SaqzResult<MemberPaymentDetail?, ReceiptError>>()
        f.delayedLookup = old; vm.onIntent(ChargeApprovalIntent.Account("account"))
        f.delayedLookup = null; f.detail = MemberPaymentDetail(approvalOrder.copy(status = "REFUNDED"), emptyList())
        vm.onIntent(ChargeApprovalIntent.Refresh); old.complete(SaqzResult.Success(null))
        assertEquals("REFUNDED", vm.state.value.detail?.order?.status); assertNull(vm.state.value.review)
        key = "new-session"; vm.onIntent(ChargeApprovalIntent.Refresh)
        assertNull(vm.state.value.detail); assertEquals(ReceiptError.SIGNED_OUT, vm.state.value.error)
        val f2 = ApprovalFake(); val vm2 = review(f2)
        vm2.onIntent(ChargeApprovalIntent.Accept(true)); vm2.onIntent(ChargeApprovalIntent.Approve)
        vm2.onIntent(ChargeApprovalIntent.Refresh)
        assertFalse(vm2.validEffect(vm2.effects.first()))
    }
    @Test fun foreignSavedIdentityAndDelayedWriteAfterLogoutAreDiscarded() = runTest {
        val foreign = ApprovalAttempt("other", "group", "charge", "account", "request", approvalReview.fingerprint)
        val saved = SavedStateHandle(mapOf("approval.attempt" to Json.encodeToString(foreign)))
        val f = ApprovalFake(); val vm = model(f, saved)
        assertNull(vm.state.value.attempt); assertNull(saved.get<String>("approval.attempt")); assertTrue(f.lookups.isEmpty())
        var key: String? = "session"; val saved2 = SavedStateHandle()
        val f2 = ApprovalFake().apply { delayedApprove = CompletableDeferred() }; val vm2 = model(f2, saved2, { key })
        vm2.onIntent(ChargeApprovalIntent.Account("account")); vm2.onIntent(ChargeApprovalIntent.Accept(true))
        vm2.onIntent(ChargeApprovalIntent.Approve); vm2.onIntent(ChargeApprovalIntent.Approve); assertEquals(1, f2.approvals.size)
        key = null; f2.delayedApprove!!.complete(SaqzResult.Success(approvalOrder))
        assertNull(vm2.state.value.detail); assertNull(saved2.get<String>("approval.attempt"))
        assertEquals(ReceiptError.SIGNED_OUT, vm2.state.value.error)
    }
}

internal val approvalTarget = ChargeApprovalTarget("group", "charge", "account")
internal val approvalReview = ChargeApprovalReview(approvalTarget, "payer", "2026-09-20", "2026-08-01",
    listOf(paymentQuote, paymentQuote.copy(method = ReceiptMethod.CARD, termsVersion = "v2", feesCents = 72, totalCents = 1072)), "a".repeat(64))
internal val approvalOrder = paymentOrder.copy(quotes = approvalReview.quotes)
internal class ApprovalFake : ChargeApprovalGateway, GroupReceivablesGateway by MemberPaymentFake() {
    var detail: MemberPaymentDetail? = null
    var lookupError: ReceiptError? = null
    var writeError: ReceiptError? = null
    var term2: ReceiptTerms? = ReceiptTerms("v2", "Termos cartão")
    var beforeWrite: () -> Unit = {}
    var previews = 0
    var delayedLookup: CompletableDeferred<SaqzResult<MemberPaymentDetail?, ReceiptError>>? = null
    var delayedApprove: CompletableDeferred<SaqzResult<MemberPaymentOrder, ReceiptError>>? = null
    val lookups = mutableListOf<ChargeApprovalTarget>()
    val termRequests = mutableListOf<String>()
    val approvals = mutableListOf<Pair<ChargeApprovalTarget, ChargeApprovalCommand>>()
    val cancels = mutableListOf<Triple<ChargeApprovalTarget, String, String>>()
    override suspend fun accounts() = SaqzResult.Success(listOf(ReceiptAccount("account", AccountRegistration.APPROVED, true)))
    override suspend fun terms(version: String): SaqzResult<ReceiptTerms, ReceiptError> {
        termRequests += version
        return if (version == "v1") SaqzResult.Success(ReceiptTerms("v1", "Termos Pix"))
        else term2?.let { SaqzResult.Success(it) } ?: SaqzResult.Failure(ReceiptError.NETWORK)
    }
    override suspend fun lookup(target: ChargeApprovalTarget): SaqzResult<MemberPaymentDetail?, ReceiptError> {
        lookups += target; return delayedLookup?.await() ?: lookupError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(detail)
    }
    override suspend fun preview(target: ChargeApprovalTarget, requestId: String): SaqzResult<ChargeApprovalReview, ReceiptError> {
        previews++; return SaqzResult.Success(approvalReview)
    }
    override suspend fun approve(target: ChargeApprovalTarget, command: ChargeApprovalCommand): SaqzResult<MemberPaymentOrder, ReceiptError> {
        beforeWrite(); approvals += target to command
        return delayedApprove?.await() ?: writeError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(approvalOrder)
    }
    override suspend fun cancel(target: ChargeApprovalTarget, orderId: String, requestId: String): SaqzResult<MemberPaymentDetail, ReceiptError> {
        beforeWrite(); cancels += Triple(target, orderId, requestId)
        return writeError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(MemberPaymentDetail(approvalOrder.copy(status = "CANCEL_PENDING"), emptyList()))
    }
}
