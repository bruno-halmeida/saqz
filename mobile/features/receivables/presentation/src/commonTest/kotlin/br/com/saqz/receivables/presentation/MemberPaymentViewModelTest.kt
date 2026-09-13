package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MemberPaymentViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }
    private val clock = object : Clock { override fun now() = Instant.parse("2026-09-13T12:00:00Z") }
    private fun model(f: MemberPaymentFake, saved: SavedStateHandle = SavedStateHandle(), key: () -> String? = { "session" }) =
        MemberPaymentViewModel("order", f, f, f, f, saved,
            MemberPaymentRuntime(ReceivablesSessionContext(key), ReceivablesRecoveryIdentity { "payer" }, clock))
    private fun ready(f: MemberPaymentFake, saved: SavedStateHandle = SavedStateHandle()): MemberPaymentViewModel = model(f, saved).also {
        it.onIntent(MemberPaymentIntent.Method(ReceiptMethod.PIX))
        it.onIntent(MemberPaymentIntent.Name("Pessoa Teste"))
        it.onIntent(MemberPaymentIntent.Document("123.456.789-09"))
        it.onIntent(MemberPaymentIntent.Accept(true))
    }
    @Test fun explicitMethodExactTermsPayerAndAcceptanceGatePayment() = runTest {
        val f = MemberPaymentFake(); val vm = model(f)
        assertNull(vm.state.value.method)
        vm.onIntent(MemberPaymentIntent.Pay); assertTrue(f.commands.isEmpty())
        vm.onIntent(MemberPaymentIntent.Method(ReceiptMethod.PIX))
        assertEquals("v1", vm.state.value.terms?.version)
        vm.onIntent(MemberPaymentIntent.Accept(true)); assertFalse(vm.state.value.canPay)
        vm.onIntent(MemberPaymentIntent.Name("Pessoa Teste")); vm.onIntent(MemberPaymentIntent.Document("123.456.789-09"))
        assertTrue(vm.state.value.canPay)
        vm.onIntent(MemberPaymentIntent.Method(ReceiptMethod.CARD)); assertFalse(vm.state.value.accepted)
        assertEquals(ReceiptMethod.CARD, vm.state.value.quote?.method)
        vm.onIntent(MemberPaymentIntent.Accept(true)); vm.onIntent(MemberPaymentIntent.Pay)
        assertEquals(MemberPaymentPayer("Pessoa Teste", "12345678909"), f.commands.single().payer)
        assertEquals(ReceiptMethod.CARD, f.commands.single().method)
        assertEquals("a".repeat(64), f.commands.single().fingerprint)
        assertTrue(f.commands.single().accepted)
    }
    @Test fun missingOrMismatchedTermsAndInvalidPayerBlockPayment() = runTest {
        for (terms in listOf<ReceiptTerms?>(null, ReceiptTerms("other", "Termos"), ReceiptTerms("v1", ""))) {
            val f = MemberPaymentFake().apply { document = terms }; val vm = ready(f)
            vm.onIntent(MemberPaymentIntent.Pay); assertFalse(vm.state.value.canPay); assertTrue(f.commands.isEmpty())
        }
        val f = MemberPaymentFake(); val vm = ready(f)
        for (name in listOf("x", "a".repeat(121), "Pessoa\nTeste")) {
            vm.onIntent(MemberPaymentIntent.Name(name)); vm.onIntent(MemberPaymentIntent.Pay); assertTrue(f.commands.isEmpty())
        }
    }
    @Test fun uncertaintyLocksEditingAndReplaysTheSameCommandWithMarkerBeforeNetwork() = runTest {
        val saved = SavedStateHandle(); val f = MemberPaymentFake().apply { uncertain = true }
        f.beforeCreate = { assertNotNull(saved.get<String>("payment.request")); assertEquals("payer", saved.get<String>("payment.user")) }
        val vm = ready(f, saved); vm.onIntent(MemberPaymentIntent.Pay)
        assertTrue(vm.state.value.pending)
        vm.onIntent(MemberPaymentIntent.Name("Changed")); vm.onIntent(MemberPaymentIntent.Method(ReceiptMethod.CARD))
        vm.onIntent(MemberPaymentIntent.Pay); assertEquals(1, f.commands.size)
        vm.onIntent(MemberPaymentIntent.Replay); assertEquals(f.commands[0], f.commands[1])
        assertFalse(saved.keys().any { it.contains("name") || it.contains("document") || it.contains("cpf") })
        assertEquals("Pessoa Teste", vm.state.value.name)
        f.uncertain = false; vm.onIntent(MemberPaymentIntent.Replay)
        assertFalse(vm.state.value.pending); assertNull(saved.get<String>("payment.request"))
        assertEquals("ACTIVE", vm.state.value.instrument?.status)
    }
    @Test fun restoredUncertaintyOnlyReconcilesEvenWhenNoInstrumentIsVisibleYet() = runTest {
        val saved = SavedStateHandle(mapOf("payment.user" to "payer", "payment.order" to "order", "payment.request" to "old"))
        val f = MemberPaymentFake(); val vm = model(f, saved)
        assertTrue(vm.state.value.pending); assertFalse(vm.state.value.canReplay)
        assertEquals("", vm.state.value.name); assertEquals("", vm.state.value.document)
        vm.onIntent(MemberPaymentIntent.Pay); vm.onIntent(MemberPaymentIntent.Replay)
        vm.onIntent(MemberPaymentIntent.Refresh)
        assertTrue(f.commands.isEmpty()); assertTrue(f.reconciles >= 1); assertTrue(vm.state.value.pending)
        f.detail = f.detail.copy(instruments = listOf(paymentInstrument()))
        vm.onIntent(MemberPaymentIntent.Refresh)
        assertFalse(vm.state.value.pending); assertEquals("ACTIVE", vm.state.value.instrument?.status)
    }
    @Test fun foreignSavedActorAndForeignOrderNeverExposePaymentData() = runTest {
        val saved = SavedStateHandle(mapOf("payment.user" to "other", "payment.order" to "order", "payment.request" to "old"))
        val f = MemberPaymentFake().apply { detail = detail.copy(order = paymentOrder.copy(payerId = "other")) }
        val vm = model(f, saved)
        assertNull(saved.get<String>("payment.request")); assertNull(vm.state.value.detail)
        assertEquals(ReceiptError.DENIED, vm.state.value.error); assertFalse(vm.state.value.canPay)
    }
    @Test fun oldCancelledAttemptCannotResolveCurrentUncertaintyEvenAfterRestoration() = runTest {
        val saved = SavedStateHandle()
        val old = paymentInstrument().copy(id = "previous", status = "CANCELLED")
        val f = MemberPaymentFake().apply { uncertain = true; detail = detail.copy(instruments = listOf(old)) }
        f.beforeCreate = { assertEquals(listOf("previous"), saved.get<List<String>>("payment.previous")) }
        val vm = ready(f, saved); vm.onIntent(MemberPaymentIntent.Pay)
        val request = saved.get<String>("payment.request")
        vm.onIntent(MemberPaymentIntent.Refresh)
        assertTrue(vm.state.value.pending); assertFalse(vm.state.value.canReview)
        assertEquals(request, saved.get<String>("payment.request"))
        f.reconcileError = ReceiptError.NETWORK; vm.onIntent(MemberPaymentIntent.Refresh)
        assertTrue(vm.state.value.pending); assertEquals(ReceiptError.NETWORK, vm.state.value.error)
        assertEquals(request, saved.get<String>("payment.request")); f.reconcileError = null
        vm.onIntent(MemberPaymentIntent.Replay); assertEquals(f.commands[0], f.commands[1])
        val restored = model(f, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        restored.onIntent(MemberPaymentIntent.Pay); restored.onIntent(MemberPaymentIntent.Replay)
        assertTrue(restored.state.value.pending); assertFalse(restored.state.value.canReplay)
        assertEquals(2, f.commands.size)
        f.detail = f.detail.copy(instruments = listOf(old, paymentInstrument().copy(status = "CANCELLED")))
        restored.onIntent(MemberPaymentIntent.Refresh)
        assertFalse(restored.state.value.pending); assertTrue(restored.state.value.canReview)
        assertFalse(restored.state.value.accepted)
    }
    @Test fun legacyRecoveryMarkerDoesNotTreatOldCancelledInstrumentAsCurrentResult() = runTest {
        val saved = SavedStateHandle(mapOf("payment.user" to "payer", "payment.order" to "order", "payment.request" to "old"))
        val f = MemberPaymentFake().apply { detail = detail.copy(instruments = listOf(paymentInstrument().copy(status = "CANCELLED"))) }
        val vm = model(f, saved)
        assertTrue(vm.state.value.pending); assertEquals("old", saved.get<String>("payment.request"))
        f.detail = f.detail.copy(order = paymentOrder.copy(status = "REFUNDED"))
        vm.onIntent(MemberPaymentIntent.Refresh)
        assertFalse(vm.state.value.pending); assertFalse(vm.state.value.canPay)
        assertNull(saved.get<String>("payment.request")); assertTrue(f.commands.isEmpty())
    }
    @Test fun delayedDetailFromOldGenerationAndSessionCannotWin() = runTest {
        val f = MemberPaymentFake(); val first = CompletableDeferred<SaqzResult<MemberPaymentDetail, ReceiptError>>()
        f.delayed = first; var key: String? = "session"; val vm = model(f, key = { key })
        f.delayed = null; f.detail = f.detail.copy(order = paymentOrder.copy(status = "REFUNDED"))
        vm.onIntent(MemberPaymentIntent.Refresh)
        first.complete(SaqzResult.Success(MemberPaymentDetail(paymentOrder, emptyList())))
        assertEquals("REFUNDED", vm.state.value.detail?.order?.status)
        key = "new-session"; vm.onIntent(MemberPaymentIntent.Refresh)
        assertNull(vm.state.value.detail); assertEquals(ReceiptError.SIGNED_OUT, vm.state.value.error)
    }
    @Test fun delayedCreationAfterLogoutClearsSensitiveStateAndPendingMarker() = runTest {
        val f = MemberPaymentFake(); val pending = CompletableDeferred<SaqzResult<MemberPaymentInstrument, ReceiptError>>()
        f.createDelayed = pending; var key: String? = "session"; val saved = SavedStateHandle()
        val vm = model(f, saved, { key })
        vm.onIntent(MemberPaymentIntent.Method(ReceiptMethod.PIX)); vm.onIntent(MemberPaymentIntent.Name("Pessoa"))
        vm.onIntent(MemberPaymentIntent.Document("12345678909")); vm.onIntent(MemberPaymentIntent.Accept(true))
        vm.onIntent(MemberPaymentIntent.Pay); vm.onIntent(MemberPaymentIntent.Pay); assertEquals(1, f.commands.size)
        key = null; pending.complete(SaqzResult.Success(paymentInstrument()))
        assertEquals("", vm.state.value.name); assertEquals("", vm.state.value.document)
        assertNull(vm.state.value.detail); assertNull(saved.get<String>("payment.request"))
    }
    @Test fun expiredPixCannotBeCopiedOrReissuedAndRefundedMilestonesNeverEnablePayment() = runTest {
        for (status in listOf("EXPIRED", "REFUNDED", "DISPUTED", "UNKNOWN", "CREATING", "RECOVERY_PENDING", "CANCELLED", "NEW_STATUS")) {
            val i = paymentInstrument().copy(status = status, confirmed = true, available = true)
            val f = MemberPaymentFake().apply { detail = detail.copy(instruments = listOf(i)) }
            val vm = model(f); vm.onIntent(MemberPaymentIntent.CopyPix); vm.onIntent(MemberPaymentIntent.Pay)
            assertFalse(vm.state.value.canUseInstrument); assertTrue(f.commands.isEmpty())
            assertEquals(status, vm.state.value.instrument?.status)
        }
        val f = MemberPaymentFake().apply { detail = detail.copy(instruments = listOf(paymentInstrument().copy(expiresAt = "2026-09-12T12:00:00Z"))) }
        val vm = model(f); assertTrue(vm.state.value.pixExpired); assertFalse(vm.state.value.canUseInstrument)
    }
    @Test fun refreshDoesNotCreateAndDefinitiveRejectionNeedsNewAcceptance() = runTest {
        val f = MemberPaymentFake().apply { rejected = true }; val vm = ready(f)
        vm.onIntent(MemberPaymentIntent.Pay)
        assertFalse(vm.state.value.pending); assertFalse(vm.state.value.accepted)
        vm.onIntent(MemberPaymentIntent.Pay); assertEquals(1, f.commands.size)
        vm.onIntent(MemberPaymentIntent.Refresh); assertEquals(1, f.commands.size)
        assertTrue(f.reconciles > 0)
    }
    @Test fun activePixAndHostedCardEmitOnlyTheirOwnActionAndRefreshNeverConfirmsLocally() = runTest {
        for (method in ReceiptMethod.entries) {
            val f = MemberPaymentFake().apply { detail = detail.copy(instruments = listOf(paymentInstrument().copy(
                quote = paymentQuote.copy(method = method)))) }
            val vm = model(f); val effects = mutableListOf<MemberPaymentEffect>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
            vm.onIntent(MemberPaymentIntent.CopyPix); vm.onIntent(MemberPaymentIntent.OpenCard)
            val expected = if (method == ReceiptMethod.PIX) MemberPaymentEffect.Copy("pix-copy", 1)
                else MemberPaymentEffect.Open("https://asaas.com/i/payment", 1)
            assertEquals(listOf(expected), effects)
            assertTrue(vm.validEffect(effects.single())); assertFalse(vm.state.value.copied)
            if (method == ReceiptMethod.PIX) {
                vm.onIntent(MemberPaymentIntent.Copied); assertTrue(vm.state.value.copied)
            }
            vm.onIntent(MemberPaymentIntent.Refresh)
            assertEquals("ACTIVE", vm.state.value.instrument?.status); assertTrue(f.commands.isEmpty())
            job.cancel()
        }
    }

    @Test fun queuedInstrumentActionsAreRevalidatedAfterRefreshRefundExpiryAndSessionChange() = runTest {
        for (method in ReceiptMethod.entries) for (change in listOf("refresh", "refund", "expiry", "session")) {
            var now = clock.now(); var key = "session"
            val f = MemberPaymentFake().apply { detail = detail.copy(instruments = listOf(paymentInstrument().copy(
                quote = paymentQuote.copy(method = method), expiresAt = "2026-09-13T12:01:00Z"))) }
            val vm = MemberPaymentViewModel("order", f, f, f, f, SavedStateHandle(), MemberPaymentRuntime(
                ReceivablesSessionContext { key }, ReceivablesRecoveryIdentity { "payer" },
                object : Clock { override fun now() = now }))
            vm.onIntent(if (method == ReceiptMethod.PIX) MemberPaymentIntent.CopyPix else MemberPaymentIntent.OpenCard)
            when (change) {
                "refresh" -> vm.onIntent(MemberPaymentIntent.Refresh)
                "refund" -> {
                    f.detail = f.detail.copy(order = paymentOrder.copy(status = "REFUNDED"))
                    vm.onIntent(MemberPaymentIntent.Refresh)
                }
                "expiry" -> if (method == ReceiptMethod.PIX) now = Instant.parse("2026-09-13T12:02:00Z") else {
                    f.detail = f.detail.copy(instruments = listOf(f.detail.instruments.single().copy(status = "CANCELLED")))
                    vm.onIntent(MemberPaymentIntent.Refresh)
                }
                "session" -> key = "next-session"
            }
            assertFalse(vm.validEffect(vm.effects.first()), "$method/$change must discard the queued action")
            assertFalse(vm.state.value.copied)
        }
    }

    @Test fun copyRechecksClockBeforeEmittingEvenIfExpiryTimerHasNotRun() = runTest {
        var now = clock.now()
        val f = MemberPaymentFake().apply { detail = detail.copy(instruments = listOf(paymentInstrument().copy(
            expiresAt = "2026-09-13T12:01:00Z"))) }
        val vm = MemberPaymentViewModel("order", f, f, f, f, SavedStateHandle(), MemberPaymentRuntime(
            ReceivablesSessionContext { "session" }, ReceivablesRecoveryIdentity { "payer" },
            object : Clock { override fun now() = now }))
        val effects = mutableListOf<MemberPaymentEffect>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        assertTrue(vm.state.value.canUseInstrument)
        now = Instant.parse("2026-09-13T12:01:00Z")
        vm.onIntent(MemberPaymentIntent.CopyPix)
        assertTrue(effects.isEmpty()); assertTrue(vm.state.value.pixExpired); assertFalse(vm.state.value.copied)
        job.cancel()
    }

    @Test fun expiredPixRenewalPersistsNonSensitiveAttemptBeforeNetworkAndPreservesIdentityAndMoney() = runTest {
        val saved = SavedStateHandle(); val expired = paymentInstrument().copy(status = "EXPIRED",
            expiresAt = "2026-09-12T23:59:59Z")
        val f = MemberPaymentFake().apply { detail = detail.copy(instruments = listOf(expired)) }
        f.beforeRenewal = {
            assertEquals("payer", saved.get<String>("renewal.user"))
            assertEquals("account", saved.get<String>("renewal.account"))
            assertEquals("group", saved.get<String>("renewal.group"))
            assertEquals("order", saved.get<String>("renewal.order"))
            assertEquals("instrument", saved.get<String>("renewal.instrument"))
            assertNotNull(saved.get<String>("renewal.request"))
            assertFalse(saved.keys().any { it.contains("name") || it.contains("document") || it.contains("cpf") })
        }
        val vm = model(f, saved)
        vm.onIntent(MemberPaymentIntent.RenewalDueDate("2026-09-20")); vm.onIntent(MemberPaymentIntent.RenewPix)
        assertEquals(listOf(PixRenewalCommand(f.renewalCommands.single().requestId, "2026-09-20")), f.renewalCommands)
        assertEquals("instrument", vm.state.value.instrument?.id)
        assertEquals("payment", vm.state.value.instrument?.paymentId)
        assertEquals(paymentQuote, vm.state.value.instrument?.quote)
        assertEquals("new-pix", vm.state.value.instrument?.pixPayload)
        assertEquals("2026-09-20", vm.state.value.detail?.order?.dueDate)
        assertNull(saved.get<String>("renewal.request"))
    }

    @Test fun uncertainPixRenewalRestoresByRequestAndNeverPostsAnotherRenewal() = runTest {
        val saved = SavedStateHandle(); val expired = paymentInstrument().copy(status = "EXPIRED",
            expiresAt = "2026-09-12T23:59:59Z")
        val f = MemberPaymentFake().apply { detail = detail.copy(instruments = listOf(expired)); renewalUncertain = true }
        val vm = model(f, saved)
        vm.onIntent(MemberPaymentIntent.RenewalDueDate("2026-09-20")); vm.onIntent(MemberPaymentIntent.RenewPix)
        val request = saved.get<String>("renewal.request")
        assertNotNull(request); assertTrue(vm.state.value.renewalPending); assertEquals(1, f.renewalCommands.size)
        val restored = model(f, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        assertEquals(1, f.renewalCommands.size); assertEquals(listOf(request), f.renewalRecoveries)
        assertTrue(restored.state.value.renewalPending)
        f.recoveredRenewal = renewalResult
        restored.onIntent(MemberPaymentIntent.Refresh)
        assertEquals(1, f.renewalCommands.size); assertEquals("new-pix", restored.state.value.instrument?.pixPayload)
    }

    @Test fun receiptExportsOnlyObservedFinalRemoteStateAndReportsActualShareCallback() = runTest {
        val confirmed = paymentInstrument().copy(status = "CONFIRMED")
        val f = MemberPaymentFake().apply { detail = detail.copy(instruments = listOf(confirmed)); shareResult = ReceiptExportResult.Failed }
        val vm = model(f)
        assertTrue(vm.state.value.canExportReceipt)
        vm.onIntent(MemberPaymentIntent.ExportReceipt)
        assertTrue(vm.state.value.receiptShareFailed); assertFalse(vm.state.value.receiptShared)
        val text = requireNotNull(f.sharedText)
        assertTrue(text.contains("Referência: order")); assertTrue(text.contains("Cobrança: payment"))
        assertTrue(text.contains("Total: R$\u00a010,61")); assertTrue(text.contains("Status: Pagamento confirmado"))
        assertFalse(text.contains("payer")); assertFalse(text.contains("cpf", ignoreCase = true))
        f.shareResult = ReceiptExportResult.Shared; vm.onIntent(MemberPaymentIntent.ExportReceipt)
        assertTrue(vm.state.value.receiptShared); assertFalse(vm.state.value.receiptShareFailed)
        f.detail = f.detail.copy(order = paymentOrder.copy(status = "REFUNDED")); vm.onIntent(MemberPaymentIntent.Refresh)
        vm.onIntent(MemberPaymentIntent.ExportReceipt)
        assertFalse(vm.state.value.canExportReceipt); assertEquals(2, f.shareCalls)
    }

    @Test fun restoredInstrumentCreationUsesPersistedRequestInsteadOfRandomRecoveryId() = runTest {
        val saved = SavedStateHandle(mapOf("payment.user" to "payer", "payment.order" to "order", "payment.request" to "stable"))
        val f = MemberPaymentFake(); model(f, saved)
        assertEquals(listOf("stable"), f.reconcileRequests)
        assertTrue(f.commands.isEmpty())
    }

    @Test fun verifierQueuedRenewalAfterSessionChangeCannotReachNetwork() = runTest {
        var key: String? = "session"
        val f = MemberPaymentFake().apply { detail = detail.copy(instruments = listOf(paymentInstrument().copy(status = "EXPIRED"))) }
        val vm = model(f, key = { key })
        vm.onIntent(MemberPaymentIntent.RenewalDueDate("2026-09-20"))
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        vm.onIntent(MemberPaymentIntent.RenewPix)
        key = null
        runCurrent()
        assertTrue(f.renewalCommands.isEmpty(), "old session queued renewal must not reach network")
    }
    @Test fun hostedLinksAndExpiryFailClosed() {
        assertTrue(hostedPaymentUrl("https://www.asaas.com/i/pay_123"))
        assertTrue(hostedPaymentUrl("https://sandbox.asaas.com/i/pay_123"))
        listOf("http://asaas.com/i/x", "https://asaas.com.evil.test/i/x", "https://asaas.com@evil.test/i/x", "javascript:alert(1)")
            .forEach { assertFalse(hostedPaymentUrl(it)) }
        assertTrue(paymentInstrument().copy(expiresAt = "bad").expired(clock.now()))
    }
}

internal val paymentQuote = MemberPaymentQuote("schedule", "v1", ReceiptMethod.PIX, 1000, 61, 1061, 1000, 20, 41)
internal val paymentOrder = MemberPaymentOrder("order", "account", "charge", "group", "payer", "2026-09-20", "ISSUED",
    listOf(paymentQuote, paymentQuote.copy(method = ReceiptMethod.CARD)), "a".repeat(64))
internal fun paymentInstrument() = MemberPaymentInstrument("instrument", "account", "order", paymentQuote, "ACTIVE", "payment", null,
    "pix-copy", null, "https://asaas.com/i/payment", false, false, false, false, null)
internal class MemberPaymentFake : MemberPaymentsGateway, GroupReceivablesGateway, PixRenewalGateway, ReceiptExportPort {
    var detail = MemberPaymentDetail(paymentOrder, emptyList())
    var document: ReceiptTerms? = ReceiptTerms("v1", "Termos publicados")
    var delayed: CompletableDeferred<SaqzResult<MemberPaymentDetail, ReceiptError>>? = null
    var createDelayed: CompletableDeferred<SaqzResult<MemberPaymentInstrument, ReceiptError>>? = null
    var uncertain = false; var rejected = false; var reconciles = 0
    var reconcileError: ReceiptError? = null
    val reconcileRequests = mutableListOf<String>()
    var beforeCreate: () -> Unit = {}
    val commands = mutableListOf<MemberPaymentCommand>()
    var page = MemberPaymentPage(listOf(paymentOrder), null)
    var pageError = false
    var delayedPage: CompletableDeferred<SaqzResult<MemberPaymentPage, ReceiptError>>? = null
    val cursors = mutableListOf<String?>()
    var beforeRenewal: () -> Unit = {}
    var renewalUncertain = false
    var recoveredRenewal: PixRenewal? = null
    val renewalCommands = mutableListOf<PixRenewalCommand>()
    val renewalRecoveries = mutableListOf<String>()
    var shareResult: ReceiptExportResult = ReceiptExportResult.Shared
    var sharedText: String? = null
    var shareCalls = 0
    override suspend fun orders(after: String?): SaqzResult<MemberPaymentPage, ReceiptError> {
        cursors += after
        return delayedPage?.await() ?: if (pageError) SaqzResult.Failure(ReceiptError.NETWORK) else SaqzResult.Success(page)
    }
    override suspend fun detail(orderId: String) = delayed?.await() ?: SaqzResult.Success(detail)
    override suspend fun reconcile(orderId: String, requestId: String): SaqzResult<MemberPaymentDetail, ReceiptError> {
        reconciles++; reconcileRequests += requestId
        return reconcileError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(detail)
    }
    override suspend fun instrument(order: MemberPaymentOrder, command: MemberPaymentCommand): SaqzResult<MemberPaymentInstrument, ReceiptError> {
        beforeCreate(); commands += command
        return createDelayed?.await() ?: when {
            uncertain -> SaqzResult.Failure(ReceiptError.UNCERTAIN)
            rejected -> SaqzResult.Failure(ReceiptError.INVALID)
            else -> SaqzResult.Success(paymentInstrument().copy(quote = order.quotes.single { it.method == command.method }))
        }
    }
    override suspend fun terms(version: String) = document?.let { SaqzResult.Success(it) } ?: SaqzResult.Failure(ReceiptError.UNAVAILABLE)
    override suspend fun accounts() = error("Not used by payer")
    override suspend fun status(groupId: String, accountId: String) = error("Not used by payer")
    override suspend fun preview(groupId: String, command: ReceiptCommand) = error("Not used by payer")
    override suspend fun activate(groupId: String, command: ReceiptCommand) = error("Not used by payer")
    override suspend fun deactivate(groupId: String, command: ReceiptCommand) = error("Not used by payer")
    override suspend fun renew(order: MemberPaymentOrder, instrument: MemberPaymentInstrument, command: PixRenewalCommand):
        SaqzResult<PixRenewal, ReceiptError> {
        beforeRenewal(); renewalCommands += command
        return if (renewalUncertain) SaqzResult.Failure(ReceiptError.UNCERTAIN) else SaqzResult.Success(renewalResult)
    }
    override suspend fun recover(order: MemberPaymentOrder, instrument: MemberPaymentInstrument, requestId: String):
        SaqzResult<PixRenewal?, ReceiptError> {
        renewalRecoveries += requestId
        return SaqzResult.Success(recoveredRenewal)
    }
    override fun export(text: String, done: (ReceiptExportResult) -> Unit) {
        shareCalls++; sharedText = text; done(shareResult)
    }
}

private val renewalResult = PixRenewal("order", "instrument", "payment", ReceiptMethod.PIX, "ACTIVE", 1000, 61, 1061,
    "2026-09-20", "new-pix", "new-image", "2026-09-20T23:59:59Z")
