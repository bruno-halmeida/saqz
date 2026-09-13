package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RecurrenceViewModelTest {
    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun teardown() = Dispatchers.resetMain()

    private fun model(fake: RecurrenceFake, saved: SavedStateHandle = SavedStateHandle(), key: () -> String? = { "session" }) =
        RecurrenceViewModel("account", "group", fake, fake, ReceivablesSessionContext(key),
            ReceivablesRecoveryIdentity { "payer" }, saved)

    @Test fun discoveryShowsOnlyOwnValidRecurrenceAndForeignPayloadFailsClosed() = runTest {
        val fake = RecurrenceFake(); val vm = model(fake)
        assertNull(vm.state.value.recurrence)
        assertEquals(listOf("account" to "group"), fake.discoveries)
        fake.recurrence = validRecurrence.copy(memberUserId = "other")
        vm.onIntent(RecurrenceIntent.Refresh)
        assertNull(vm.state.value.recurrence)
        assertEquals(ReceiptError.DENIED, vm.state.value.error)
    }

    @Test fun exactReviewTermsPayerAndAcceptanceAreRequiredForAuthorization() = runTest {
        val fake = RecurrenceFake(); val vm = model(fake)
        vm.onIntent(RecurrenceIntent.Submit)
        assertTrue(fake.acceptances.isEmpty())
        vm.onIntent(RecurrenceIntent.DueDate("2026-10-10"))
        vm.onIntent(RecurrenceIntent.Preview)
        assertEquals(validReview, vm.state.value.review)
        assertEquals("Termos publicados", vm.state.value.terms?.content)
        vm.onIntent(RecurrenceIntent.Name("Pessoa Teste"))
        vm.onIntent(RecurrenceIntent.Document("123.456.789-09"))
        vm.onIntent(RecurrenceIntent.Submit)
        assertTrue(fake.acceptances.isEmpty())
        vm.onIntent(RecurrenceIntent.Accept(true))
        vm.onIntent(RecurrenceIntent.Submit)
        val command = fake.acceptances.single()
        assertEquals("account", command.accountId)
        assertEquals("group", command.groupId)
        assertEquals("2026-10-10", command.firstDueDate)
        assertEquals("a".repeat(64), command.fingerprint)
        assertEquals(MemberPaymentPayer("Pessoa Teste", "12345678909"), command.payer)
        assertTrue(command.accepted)
        assertEquals("ACTIVE", vm.state.value.recurrence?.status)
    }

    @Test fun changingReviewedMethodOrDateInvalidatesTermsAndAcceptance() = runTest {
        val fake = RecurrenceFake(); val vm = model(fake)
        vm.onIntent(RecurrenceIntent.DueDate("2026-10-10")); vm.onIntent(RecurrenceIntent.Preview)
        vm.onIntent(RecurrenceIntent.Accept(true)); assertTrue(vm.state.value.accepted)
        vm.onIntent(RecurrenceIntent.Method(ReceiptMethod.CARD))
        assertNull(vm.state.value.review); assertNull(vm.state.value.terms); assertFalse(vm.state.value.accepted)
        vm.onIntent(RecurrenceIntent.DueDate("2026-11-10"))
        assertEquals("2026-11-10", vm.state.value.firstDueDate)
    }

    @Test fun attemptMarkerExistsBeforeNetworkAndContainsNoPayerPii() = runTest {
        val saved = SavedStateHandle(); val fake = RecurrenceFake()
        fake.beforeMutation = {
            assertEquals("payer", saved.get<String>("recurrence.user"))
            assertEquals("account", saved.get<String>("recurrence.account"))
            assertEquals("group", saved.get<String>("recurrence.group"))
            assertEquals("AUTHORIZE", saved.get<String>("recurrence.operation"))
            assertNotNull(saved.get<String>("recurrence.request"))
            assertFalse(saved.keys().any { it.contains("name") || it.contains("document") || it.contains("cpf") })
        }
        val vm = model(fake, saved)
        reviewAndAccept(vm); vm.onIntent(RecurrenceIntent.Submit)
        assertEquals(1, fake.acceptances.size)
        assertNull(saved.get<String>("recurrence.request"))
    }

    @Test fun timeoutRestoresByRequestWithoutRepeatingAuthorizeAndKeepsPiiOnlyInMemory() = runTest {
        val saved = SavedStateHandle(); val fake = RecurrenceFake().apply { mutationError = ReceiptError.UNCERTAIN }
        val vm = model(fake, saved); reviewAndAccept(vm); vm.onIntent(RecurrenceIntent.Submit)
        val request = saved.get<String>("recurrence.request")
        assertNotNull(request); assertTrue(vm.state.value.pending); assertEquals(1, fake.acceptances.size)
        val restored = model(fake, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        assertEquals("", restored.state.value.name); assertEquals("", restored.state.value.document)
        assertEquals(1, fake.acceptances.size)
        assertEquals(listOf(request), fake.recoveries.map { it.requestId })
        assertTrue(restored.state.value.pending)
        fake.recovered = validRecurrence
        restored.onIntent(RecurrenceIntent.Refresh)
        assertEquals(1, fake.acceptances.size)
        assertEquals("ACTIVE", restored.state.value.recurrence?.status)
    }

    @Test fun stoppedRecurrenceRequiresFreshReviewAndCreatesResumeWithNewAcceptance() = runTest {
        val fake = RecurrenceFake().apply { recurrence = validRecurrence.copy(status = "STOPPED", cutoffAt = "2026-09-13T12:00:00Z") }
        val vm = model(fake)
        assertTrue(vm.state.value.canResume); assertFalse(vm.state.value.canSubmit)
        reviewAndAccept(vm); vm.onIntent(RecurrenceIntent.Submit)
        assertTrue(fake.acceptances.isEmpty())
        assertEquals(listOf("recurrence"), fake.resumedIds)
        assertNotEquals("recurrence", vm.state.value.recurrence?.id)
        assertEquals("STOPPED", fake.recurrence?.status)
    }

    @Test fun cancelPendingRecoversUntilRemoteStoppedProofAndNeverLooksSuccessfulEarly() = runTest {
        val saved = SavedStateHandle(); val fake = RecurrenceFake().apply { recurrence = validRecurrence; mutationError = ReceiptError.UNCERTAIN }
        val vm = model(fake, saved); vm.onIntent(RecurrenceIntent.Cancel)
        assertEquals(1, fake.cancellations.size); assertTrue(vm.state.value.pending)
        assertEquals("CANCEL", saved.get<String>("recurrence.operation"))
        fake.mutationError = null; fake.recovered = validRecurrence.copy(status = "STOP_PENDING", cutoffAt = "2026-09-13T12:00:00Z")
        vm.onIntent(RecurrenceIntent.Refresh)
        assertTrue(vm.state.value.pending); assertEquals("STOP_PENDING", vm.state.value.recurrence?.status)
        fake.recovered = validRecurrence.copy(status = "STOPPED", cutoffAt = "2026-09-13T12:00:00Z")
        vm.onIntent(RecurrenceIntent.Refresh)
        assertFalse(vm.state.value.pending); assertEquals("STOPPED", vm.state.value.recurrence?.status)
        assertEquals(1, fake.cancellations.size)
    }

    @Test fun lateGenerationAndRevokedSessionCannotPublishRecurrenceOrCheckoutEffect() = runTest {
        val fake = RecurrenceFake(); val delayed = CompletableDeferred<SaqzResult<PaymentRecurrence?, ReceiptError>>()
        fake.delayedDiscovery = delayed; var key: String? = "session"; val vm = model(fake, key = { key })
        fake.delayedDiscovery = null; fake.recurrence = validRecurrence.copy(status = "AUTHORIZING", method = ReceiptMethod.CARD,
            hostedCheckoutUrl = "https://asaas.com/c/checkout")
        vm.onIntent(RecurrenceIntent.Refresh)
        delayed.complete(SaqzResult.Success(null))
        assertEquals("AUTHORIZING", vm.state.value.recurrence?.status)
        vm.onIntent(RecurrenceIntent.OpenCheckout)
        val effect = vm.effects.first()
        key = null
        assertFalse(vm.validEffect(effect))
        assertNull(vm.state.value.recurrence); assertEquals(ReceiptError.SIGNED_OUT, vm.state.value.error)
    }

    @Test fun verifierCancelRecoveryActiveDoesNotClearUncertainty() = runTest {
        val saved = SavedStateHandle()
        val fake = RecurrenceFake().apply { recurrence = validRecurrence; mutationError = ReceiptError.UNCERTAIN }
        val vm = model(fake, saved)
        vm.onIntent(RecurrenceIntent.Cancel)
        fake.recovered = validRecurrence.copy(status = "ACTIVE")
        vm.onIntent(RecurrenceIntent.Refresh)
        assertTrue(vm.state.value.pending, "ACTIVE is not remote cancellation proof")
        assertNotNull(saved.get<String>("recurrence.request"))
        assertFalse(vm.state.value.canCancel)
    }
    @Test fun verifierIneligiblePreviewInvalidatesPreviousConsent() = runTest {
        val fake = RecurrenceFake(); val vm = model(fake)
        reviewAndAccept(vm)
        assertTrue(vm.state.value.canSubmit)
        fake.previewError = ReceiptError.DENIED
        vm.onIntent(RecurrenceIntent.Preview)
        assertEquals(ReceiptError.DENIED, vm.state.value.error)
        vm.onIntent(RecurrenceIntent.Submit)
        assertTrue(fake.acceptances.isEmpty(), "403 must block old review authorization")
        assertFalse(vm.state.value.canSubmit)
    }
    @Test fun newPreviewClearsConsentBeforeResponseAndRequiresFreshAcceptance() = runTest {
        val fake = RecurrenceFake(); val vm = model(fake)
        reviewAndAccept(vm)
        val delayed = CompletableDeferred<SaqzResult<RecurrenceReview, ReceiptError>>()
        fake.delayedPreview = delayed
        vm.onIntent(RecurrenceIntent.Preview)
        assertTrue(vm.state.value.loading)
        assertNull(vm.state.value.review); assertNull(vm.state.value.terms); assertFalse(vm.state.value.accepted)
        vm.onIntent(RecurrenceIntent.Submit)
        assertTrue(fake.acceptances.isEmpty())
        delayed.complete(SaqzResult.Success(validReview))
        assertNotNull(vm.state.value.review); assertNotNull(vm.state.value.terms)
        assertFalse(vm.state.value.accepted); assertFalse(vm.state.value.canSubmit)
        vm.onIntent(RecurrenceIntent.Accept(true))
        assertTrue(vm.state.value.canSubmit)
    }

    @Test fun previewAndTermsFailuresDiscardPreviousConsent() = runTest {
        val failures = listOf<SaqzResult<ReceiptTerms, ReceiptError>>(
            SaqzResult.Failure(ReceiptError.DENIED),
            SaqzResult.Failure(ReceiptError.NETWORK),
            SaqzResult.Success(ReceiptTerms("wrong-version", "Termos publicados")),
            SaqzResult.Success(ReceiptTerms("terms", "")),
        )
        failures.forEach { failure ->
            val fake = RecurrenceFake(); val vm = model(fake)
            reviewAndAccept(vm)
            fake.termsResult = failure
            vm.onIntent(RecurrenceIntent.Preview)
            assertNotNull(vm.state.value.error)
            assertNull(vm.state.value.review); assertNull(vm.state.value.terms); assertFalse(vm.state.value.accepted)
            vm.onIntent(RecurrenceIntent.Accept(true)); vm.onIntent(RecurrenceIntent.Submit)
            assertFalse(vm.state.value.canSubmit); assertTrue(fake.acceptances.isEmpty())
        }
        val fake = RecurrenceFake(); val vm = model(fake)
        reviewAndAccept(vm)
        fake.previewError = ReceiptError.DENIED
        vm.onIntent(RecurrenceIntent.Preview)
        assertNull(vm.state.value.review); assertNull(vm.state.value.terms); assertFalse(vm.state.value.accepted)
    }

    @Test fun errorStateCannotSubmitEvenWithOtherwiseValidConsent() = runTest {
        val vm = model(RecurrenceFake())
        reviewAndAccept(vm)
        assertTrue(vm.state.value.canSubmit)
        ReceiptError.entries.forEach { error -> assertFalse(vm.state.value.copy(error = error).canSubmit) }
    }

    @Test fun restoredCancelRetainsMarkerUntilStoppedProof() = runTest {
        val saved = SavedStateHandle(mapOf(
            "recurrence.user" to "payer", "recurrence.account" to "account", "recurrence.group" to "group",
            "recurrence.request" to "cancel-request", "recurrence.id" to "recurrence", "recurrence.operation" to "CANCEL",
        ))
        val fake = RecurrenceFake().apply { recovered = validRecurrence }
        val vm = model(fake, saved)
        assertTrue(vm.state.value.pending); assertEquals(ReceiptError.UNCERTAIN, vm.state.value.error)
        assertEquals("cancel-request", saved.get<String>("recurrence.request"))
        assertEquals("CANCEL", saved.get<String>("recurrence.operation"))
        assertFalse(vm.state.value.canCancel); assertFalse(vm.state.value.canSubmit)
        fake.recovered = validRecurrence.copy(status = "STOP_PENDING", cutoffAt = "2026-09-13T12:00:00Z")
        vm.onIntent(RecurrenceIntent.Refresh)
        assertTrue(vm.state.value.pending); assertEquals(ReceiptError.UNCERTAIN, vm.state.value.error)
        assertEquals("cancel-request", saved.get<String>("recurrence.request"))
        fake.recovered = validRecurrence.copy(status = "STOPPED", cutoffAt = "2026-09-13T12:00:00Z")
        vm.onIntent(RecurrenceIntent.Refresh)
        assertFalse(vm.state.value.pending); assertNull(vm.state.value.error)
        assertNull(saved.get<String>("recurrence.request"))
        assertTrue(fake.cancellations.isEmpty())
    }

    private fun reviewAndAccept(vm: RecurrenceViewModel) {
        vm.onIntent(RecurrenceIntent.DueDate("2026-10-10")); vm.onIntent(RecurrenceIntent.Preview)
        vm.onIntent(RecurrenceIntent.Name("Pessoa Teste")); vm.onIntent(RecurrenceIntent.Document("12345678909"))
        vm.onIntent(RecurrenceIntent.Accept(true))
    }
}

private val validReview = RecurrenceReview("account", "group", "payer", ReceiptMethod.PIX, 10_000, 490, 10_490, 300, 190,
    10_000, "schedule", "terms", "2026-10-10", "MONTHLY", "a".repeat(64))
private val validRecurrence = PaymentRecurrence("recurrence", "account", "group", "payer", ReceiptMethod.PIX, 10_000, 490,
    10_490, "2026-10-10", "ACTIVE", "subscription", null, null)

private class RecurrenceFake : RecurrenceGateway, GroupReceivablesGateway {
    var recurrence: PaymentRecurrence? = null
    var recovered: PaymentRecurrence? = null
    var mutationError: ReceiptError? = null
    var previewError: ReceiptError? = null
    var delayedPreview: CompletableDeferred<SaqzResult<RecurrenceReview, ReceiptError>>? = null
    var termsResult: SaqzResult<ReceiptTerms, ReceiptError>? = null
    var delayedDiscovery: CompletableDeferred<SaqzResult<PaymentRecurrence?, ReceiptError>>? = null
    var beforeMutation: () -> Unit = {}
    val discoveries = mutableListOf<Pair<String, String>>()
    val acceptances = mutableListOf<RecurrenceAcceptanceCommand>()
    val resumedIds = mutableListOf<String>()
    val cancellations = mutableListOf<String>()
    val recoveries = mutableListOf<RecurrenceAttempt>()
    override suspend fun discover(accountId: String, groupId: String): SaqzResult<PaymentRecurrence?, ReceiptError> {
        discoveries += accountId to groupId
        return delayedDiscovery?.await() ?: SaqzResult.Success(recurrence)
    }
    override suspend fun preview(command: RecurrencePreviewCommand): SaqzResult<RecurrenceReview, ReceiptError> = delayedPreview?.await() ?: previewError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(validReview.copy(
        method = command.method, firstDueDate = command.firstDueDate))
    override suspend fun authorize(review: RecurrenceReview, command: RecurrenceAcceptanceCommand): SaqzResult<PaymentRecurrence, ReceiptError> {
        beforeMutation(); acceptances += command
        return mutationError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(validRecurrence.copy(method = command.method))
    }
    override suspend fun get(recurrenceId: String) = SaqzResult.Success(requireNotNull(recurrence))
    override suspend fun cancel(recurrence: PaymentRecurrence, requestId: String): SaqzResult<PaymentRecurrence, ReceiptError> {
        beforeMutation(); cancellations += requestId
        return mutationError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(recurrence.copy(status = "STOPPED"))
    }
    override suspend fun resume(stopped: PaymentRecurrence, review: RecurrenceReview, command: RecurrenceAcceptanceCommand):
        SaqzResult<PaymentRecurrence, ReceiptError> {
        beforeMutation(); resumedIds += stopped.id
        return mutationError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(stopped.copy(id = "new-recurrence", status = "ACTIVE",
            method = command.method, providerSubscriptionId = "new-subscription", cutoffAt = null))
    }
    override suspend fun recover(attempt: RecurrenceAttempt): SaqzResult<PaymentRecurrence?, ReceiptError> {
        recoveries += attempt
        return SaqzResult.Success(recovered)
    }
    override suspend fun terms(version: String) = termsResult ?: SaqzResult.Success(ReceiptTerms(version, "Termos publicados"))
    override suspend fun accounts() = error("unused")
    override suspend fun status(groupId: String, accountId: String) = error("unused")
    override suspend fun preview(groupId: String, command: ReceiptCommand) = error("unused")
    override suspend fun activate(groupId: String, command: ReceiptCommand) = error("unused")
    override suspend fun deactivate(groupId: String, command: ReceiptCommand) = error("unused")
}
