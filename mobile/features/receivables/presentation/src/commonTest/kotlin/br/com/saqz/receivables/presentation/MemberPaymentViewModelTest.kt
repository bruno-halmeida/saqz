package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.collect
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MemberPaymentViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }
    private val clock = object : Clock { override fun now() = Instant.parse("2026-09-13T12:00:00Z") }
    private fun model(f: MemberPaymentFake, saved: SavedStateHandle = SavedStateHandle(), key: () -> String? = { "session" }) =
        MemberPaymentViewModel("order", f, f, ReceivablesSessionContext(key), saved, ReceivablesRecoveryIdentity { "payer" }, clock)
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
            val expected = if (method == ReceiptMethod.PIX) MemberPaymentEffect.Copy("pix-copy")
                else MemberPaymentEffect.Open("https://asaas.com/i/payment")
            assertEquals(listOf(expected), effects)
            vm.onIntent(MemberPaymentIntent.Refresh)
            assertEquals("ACTIVE", vm.state.value.instrument?.status); assertTrue(f.commands.isEmpty())
            job.cancel()
        }
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
internal class MemberPaymentFake : MemberPaymentsGateway, GroupReceivablesGateway {
    var detail = MemberPaymentDetail(paymentOrder, emptyList())
    var document: ReceiptTerms? = ReceiptTerms("v1", "Termos publicados")
    var delayed: CompletableDeferred<SaqzResult<MemberPaymentDetail, ReceiptError>>? = null
    var createDelayed: CompletableDeferred<SaqzResult<MemberPaymentInstrument, ReceiptError>>? = null
    var uncertain = false; var rejected = false; var reconciles = 0
    var beforeCreate: () -> Unit = {}
    val commands = mutableListOf<MemberPaymentCommand>()
    var page = MemberPaymentPage(listOf(paymentOrder), null)
    var pageError = false
    var delayedPage: CompletableDeferred<SaqzResult<MemberPaymentPage, ReceiptError>>? = null
    val cursors = mutableListOf<String?>()
    override suspend fun orders(after: String?): SaqzResult<MemberPaymentPage, ReceiptError> {
        cursors += after
        return delayedPage?.await() ?: if (pageError) SaqzResult.Failure(ReceiptError.NETWORK) else SaqzResult.Success(page)
    }
    override suspend fun detail(orderId: String) = delayed?.await() ?: SaqzResult.Success(detail)
    override suspend fun reconcile(orderId: String, requestId: String): SaqzResult<MemberPaymentDetail, ReceiptError> { reconciles++; return SaqzResult.Success(detail) }
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
}
