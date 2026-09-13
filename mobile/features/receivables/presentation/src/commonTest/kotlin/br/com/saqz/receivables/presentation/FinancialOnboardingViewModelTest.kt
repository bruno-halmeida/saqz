package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.domain.port.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class FinancialOnboardingViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }
    private fun model(f: OnboardingFake, saved: SavedStateHandle = SavedStateHandle(), key: () -> String? = { "session" }) =
        FinancialOnboardingViewModel(f, f, ReceivablesSessionContext(key), ReceivablesRecoveryIdentity { "owner" }, f, saved)
    private fun ready(f: OnboardingFake, saved: SavedStateHandle = SavedStateHandle()) = model(f, saved).also { vm ->
        onboardingForm.values.forEach { (field, value) -> vm.onIntent(FinancialOnboardingIntent.Edit(field, value)) }
    }
    @Test fun discoveryNeverConfusesUnavailableLookupWithMissingAccountOrRechecksNewBusinessForMaintenance() = runTest {
        val failed = model(OnboardingFake().apply { mineError = ReceiptError.NETWORK })
        assertFalse(failed.state.value.discovered); assertFalse(failed.state.value.canEdit); assertEquals(ReceiptError.NETWORK, failed.state.value.error)
        val off = model(OnboardingFake().apply { enabled = false })
        assertTrue(off.state.value.discovered); assertFalse(off.state.value.canEdit); assertNull(off.state.value.terms)
        val f = OnboardingFake().apply { account = onboardingAccount; enabled = false }
        val vm = model(f); assertEquals(onboardingAccount, vm.state.value.account)
        assertEquals(listOf(onboardingDocument), vm.state.value.documents); assertEquals(0, f.availabilityReads); assertEquals(0, f.termReads)
    }
    @Test fun exactPfPjMoneyDatesAndRequiredFieldsAreValidatedWithoutFloatingPoint() {
        val registration = onboardingForm.registration()!!
        assertEquals(250001L, registration.incomeCents); assertEquals("1990-01-01", registration.birthDate); assertNull(registration.companyType)
        for (field in OnboardingField.entries.filter { it != OnboardingField.COMPANY_TYPE })
            assertNull(onboardingForm.copy(values = onboardingForm.values - field).registration(), field.name)
        for (input in listOf("", "-1", "2,001", "1e2", "1.000,00", "999999999999999999")) assertNull(incomeCents(input))
        assertEquals(0L, incomeCents("0")); assertEquals(123456789012L, incomeCents("1234567890,12"))
        assertNull(onboardingForm.copy(values = onboardingForm.values + (OnboardingField.BIRTH_DATE to "31/02/1990")).registration())
        for (type in listOf("MEI", "LIMITED", "INDIVIDUAL", "ASSOCIATION")) {
            val pj = onboardingForm.copy(company = true, values = onboardingForm.values +
                mapOf(OnboardingField.DOCUMENT to "12345678000199", OnboardingField.COMPANY_TYPE to type)).registration()!!
            assertNull(pj.birthDate); assertEquals(type, pj.companyType); assertEquals(250001L, pj.incomeCents)
        }
    }
    @Test fun onlyExplicitConsentSubmitsExactFormAndMarkerPrecedesNetwork() = runTest {
        val f = OnboardingFake(); val saved = SavedStateHandle(); val vm = ready(f, saved)
        vm.onIntent(FinancialOnboardingIntent.Create); assertTrue(f.commands.isEmpty())
        f.beforeWrite = { assertNotNull(saved.get<String>("onboarding.attempt")) }
        vm.onIntent(FinancialOnboardingIntent.Accept(true)); vm.onIntent(FinancialOnboardingIntent.Create)
        val c = f.commands.single(); val r = c.registration
        assertEquals("v1", c.termsVersion); assertTrue(c.accepted); assertTrue(c.requestId.isNotBlank())
        assertEquals(listOf("Titular", "owner@example.test", "12345678901", "11999999999", "Rua", "10", "Centro", "01001000"),
            listOf(r.name, r.email, r.cpfCnpj, r.mobilePhone, r.address.street, r.address.number, r.address.province, r.address.postalCode))
        assertEquals(onboardingAccount, vm.state.value.account); assertFalse(vm.state.value.account!!.operationsEnabled)
        assertEquals(OnboardingForm(), vm.state.value.form); assertNull(saved.get<String>("onboarding.attempt")); assertFalse(vm.state.value.accepted)
    }
    @Test fun missingTermsOrEditsRefreshAndCompanySwitchInvalidateConsent() = runTest {
        val f = OnboardingFake().apply { terms = ReceiptTerms("v1", " ") }; val vm = ready(f)
        vm.onIntent(FinancialOnboardingIntent.Accept(true)); vm.onIntent(FinancialOnboardingIntent.Create); assertTrue(f.commands.isEmpty())
        f.terms = ReceiptTerms("v1", "Terms"); vm.onIntent(FinancialOnboardingIntent.Refresh)
        vm.onIntent(FinancialOnboardingIntent.Accept(true)); assertTrue(vm.state.value.canCreate)
        vm.onIntent(FinancialOnboardingIntent.Edit(OnboardingField.NAME, "Outro")); assertFalse(vm.state.value.accepted)
        vm.onIntent(FinancialOnboardingIntent.Accept(true)); vm.onIntent(FinancialOnboardingIntent.Refresh); assertFalse(vm.state.value.accepted)
        vm.onIntent(FinancialOnboardingIntent.Company(true)); assertEquals(OnboardingForm(company = true), vm.state.value.form)
    }
    @Test fun uncertainCreatePreservesNonsensitiveMarkerAndExactReplayAfterReadWhileRestorationNeverRecreates() = runTest {
        val f = OnboardingFake().apply { createError = ReceiptError.UNCERTAIN }; val saved = SavedStateHandle(); val vm = ready(f, saved)
        vm.onIntent(FinancialOnboardingIntent.Accept(true)); vm.onIntent(FinancialOnboardingIntent.Create)
        val marker = saved.get<String>("onboarding.attempt")!!
        assertEquals(OnboardingAttempt("owner", f.commands.single().requestId, "CREATE"), Json.decodeFromString<OnboardingAttempt>(marker))
        assertFalse(marker.contains("12345678901")); assertFalse(marker.contains("Titular"))
        vm.onIntent(FinancialOnboardingIntent.Edit(OnboardingField.NAME, "Changed")); vm.onIntent(FinancialOnboardingIntent.Create)
        assertEquals(1, f.commands.size); assertEquals("Titular", vm.state.value.form[OnboardingField.NAME])
        f.mineError = ReceiptError.NETWORK; vm.onIntent(FinancialOnboardingIntent.Recover)
        assertEquals(1, f.commands.size); assertEquals(marker, saved.get<String>("onboarding.attempt"))
        f.mineError = null; val reads = f.mineReads; vm.onIntent(FinancialOnboardingIntent.Recover)
        assertTrue(f.mineReads > reads); assertSame(f.commands[0], f.commands[1])
        val restored = model(f, SavedStateHandle(mapOf("onboarding.attempt" to marker)))
        assertEquals(OnboardingForm(), restored.state.value.form); restored.onIntent(FinancialOnboardingIntent.Recover)
        assertEquals(2, f.commands.size); assertTrue(restored.state.value.pending)
        f.account = onboardingAccount; restored.onIntent(FinancialOnboardingIntent.Recover)
        assertFalse(restored.state.value.pending); assertEquals(onboardingAccount, restored.state.value.account); assertEquals(2, f.commands.size)
    }
    @Test fun existingIncompleteAccountRecoversOnlyOnExplicitCommand() = runTest {
        val f = OnboardingFake().apply { account = onboardingAccount.copy(registration = AccountRegistration.INCOMPLETE) }
        val saved = SavedStateHandle(); val vm = model(f, saved)
        assertTrue(f.recoveries.isEmpty()); f.beforeWrite = { assertNotNull(saved.get<String>("onboarding.attempt")) }
        f.recoverError = ReceiptError.UNCERTAIN; vm.onIntent(FinancialOnboardingIntent.Recover)
        val marker = saved.get<String>("onboarding.attempt")
        vm.onIntent(FinancialOnboardingIntent.Refresh); assertEquals(marker, saved.get<String>("onboarding.attempt"))
        vm.onIntent(FinancialOnboardingIntent.Recover); assertEquals(f.recoveries[0], f.recoveries[1]); assertTrue(f.commands.isEmpty())
        f.recoverError = null; vm.onIntent(FinancialOnboardingIntent.Recover)
        assertEquals(onboardingAccount, vm.state.value.account); assertNull(saved.get<String>("onboarding.attempt"))
    }
    @Test fun fileSelectionRequiresExplicitUploadAndSuccessOnlyIndicatesSubmission() = runTest {
        val f = OnboardingFake().apply { account = onboardingAccount }; val vm = model(f)
        f.fileResult = ReceiptFileSelection.Cancelled; vm.onIntent(FinancialOnboardingIntent.ChooseFile("doc"))
        assertNull(vm.state.value.selectedFile); assertTrue(f.uploads.isEmpty())
        f.fileResult = ReceiptFileSelection.Selected(onboardingFile); vm.onIntent(FinancialOnboardingIntent.ChooseFile("doc"))
        assertSame(onboardingFile, vm.state.value.selectedFile); assertTrue(f.uploads.isEmpty())
        vm.onIntent(FinancialOnboardingIntent.Upload)
        assertEquals(onboardingDocument, f.uploads.single().first); assertContentEquals(onboardingFile.bytes, f.uploads.single().third.bytes)
        assertNull(vm.state.value.selectedFile); assertTrue(vm.state.value.uploaded)
        assertEquals(AccountRegistration.UNDER_REVIEW, vm.state.value.account?.registration)
        assertEquals("PENDING", vm.state.value.documents.single().status)
    }
    @Test fun uncertainUploadCannotRepeatOnUnchangedStatusAndRestoresWithoutBytes() = runTest {
        val f = OnboardingFake().apply { account = onboardingAccount; uploadError = ReceiptError.UNCERTAIN }
        val saved = SavedStateHandle(); val vm = model(f, saved)
        f.beforeWrite = { assertNotNull(saved.get<String>("onboarding.attempt")) }
        vm.onIntent(FinancialOnboardingIntent.ChooseFile("doc")); vm.onIntent(FinancialOnboardingIntent.Upload)
        val marker = saved.get<String>("onboarding.attempt")!!
        assertEquals(OnboardingAttempt("owner", f.uploads.single().second, "UPLOAD", "doc", "PENDING"), Json.decodeFromString<OnboardingAttempt>(marker))
        assertNull(vm.state.value.selectedFile); vm.onIntent(FinancialOnboardingIntent.Recover); vm.onIntent(FinancialOnboardingIntent.Upload)
        assertEquals(1, f.uploads.size); assertEquals(marker, saved.get<String>("onboarding.attempt"))
        val restored = model(f, SavedStateHandle(mapOf("onboarding.attempt" to marker)))
        assertTrue(restored.state.value.pending); assertNull(restored.state.value.selectedFile)
        f.docs = listOf(onboardingDocument.copy(status = "AWAITING_APPROVAL")); restored.onIntent(FinancialOnboardingIntent.Refresh)
        assertFalse(restored.state.value.pending); assertEquals("AWAITING_APPROVAL", restored.state.value.documents.single().status)
        assertEquals(1, f.uploads.size)
    }
    @Test fun hostedOnboardingNeverUsesApiUploadAndQueuedLinksExpireOnRefreshOrSessionChange() = runTest {
        val f = OnboardingFake().apply { account = onboardingAccount; docs = listOf(onboardingDocument.copy(onboardingUrl = "https://asaas.com/onboarding/test")) }
        var key: String? = "session"; val vm = model(f, key = { key })
        vm.onIntent(FinancialOnboardingIntent.ChooseFile("doc")); assertEquals(0, f.picks)
        vm.onIntent(FinancialOnboardingIntent.Open("doc")); val effect = vm.effects.first()
        assertEquals(FinancialOnboardingEffect.Open("doc", "https://asaas.com/onboarding/test", effect.generation), effect)
        assertTrue(vm.validEffect(effect)); vm.onIntent(FinancialOnboardingIntent.Refresh); assertFalse(vm.validEffect(effect))
        assertEquals(AccountRegistration.UNDER_REVIEW, vm.state.value.account?.registration)
        vm.onIntent(FinancialOnboardingIntent.Open("doc")); val next = vm.effects.first()
        key = "other-session"; assertFalse(vm.validEffect(next)); assertNull(vm.state.value.account)
        assertEquals(ReceiptError.SIGNED_OUT, vm.state.value.error)
        assertFalse(safeOnboardingUrl("https://evil.test/asaas.com")); assertFalse(safeOnboardingUrl("https://user@asaas.com/a"))
    }
    @Test fun logoutDiscardsDelayedCreateAndPickerCallbacksAndForeignMarkers() = runTest {
        val marker = Json.encodeToString(OnboardingAttempt("foreign", "request", "CREATE"))
        val saved = SavedStateHandle(mapOf("onboarding.attempt" to marker)); val f = OnboardingFake(); val vm = model(f, saved)
        assertFalse(vm.state.value.pending); assertNull(saved.get<String>("onboarding.attempt"))
        var key: String? = "session"; val f2 = OnboardingFake().apply { delayedCreate = CompletableDeferred() }
        val saved2 = SavedStateHandle(); val vm2 = model(f2, saved2, { key })
        onboardingForm.values.forEach { (field, value) -> vm2.onIntent(FinancialOnboardingIntent.Edit(field, value)) }
        vm2.onIntent(FinancialOnboardingIntent.Accept(true)); vm2.onIntent(FinancialOnboardingIntent.Create)
        vm2.onIntent(FinancialOnboardingIntent.Create); assertEquals(1, f2.commands.size)
        key = null; f2.delayedCreate!!.complete(SaqzResult.Success(onboardingAccount))
        assertNull(vm2.state.value.account); assertEquals(OnboardingForm(), vm2.state.value.form); assertNull(saved2.get<String>("onboarding.attempt"))
        key = "session"; val f3 = OnboardingFake().apply { account = onboardingAccount; deferFile = true }; val vm3 = model(f3, key = { key })
        vm3.onIntent(FinancialOnboardingIntent.ChooseFile("doc")); key = null
        f3.fileCallback!!.onFileSelected(ReceiptFileSelection.Selected(onboardingFile))
        assertNull(vm3.state.value.selectedFile); assertEquals(ReceiptError.SIGNED_OUT, vm3.state.value.error); assertTrue(f3.pickCancelled)
    }
    @Test fun definitiveRejectionClearsMarkerAndRequiresNewConsent() = runTest {
        val f = OnboardingFake().apply { createError = ReceiptError.INVALID }; val saved = SavedStateHandle(); val vm = ready(f, saved)
        vm.onIntent(FinancialOnboardingIntent.Accept(true)); vm.onIntent(FinancialOnboardingIntent.Create)
        assertFalse(vm.state.value.pending); assertFalse(vm.state.value.accepted); assertNull(saved.get<String>("onboarding.attempt"))
        vm.onIntent(FinancialOnboardingIntent.Create); assertEquals(1, f.commands.size)
    }
}
internal val onboardingAccount = OwnedReceiptAccount("account", "owner", AccountRegistration.UNDER_REVIEW, false)
internal val onboardingDocument = ReceiptDocument("doc", "CUSTOM", "PENDING", null, "Ata de eleição")
internal val onboardingFile = ReceiptDocumentFile("%PDF-test".encodeToByteArray(), "application/pdf")
internal val onboardingForm = OnboardingForm(mapOf(OnboardingField.NAME to "Titular", OnboardingField.EMAIL to "owner@example.test",
    OnboardingField.DOCUMENT to "12345678901", OnboardingField.PHONE to "11999999999", OnboardingField.INCOME to "2500,01",
    OnboardingField.STREET to "Rua", OnboardingField.NUMBER to "10", OnboardingField.PROVINCE to "Centro",
    OnboardingField.POSTAL_CODE to "01001000", OnboardingField.BIRTH_DATE to "01/01/1990"))
internal class OnboardingFake : FinancialOnboardingGateway, ReceivablesAvailabilityGateway, ReceiptDocumentPicker {
    var account: OwnedReceiptAccount? = null; var mineError: ReceiptError? = null; var enabled = true
    var terms = ReceiptTerms("v1", "Termos de teste"); var docs = listOf(onboardingDocument)
    var createError: ReceiptError? = null; var recoverError: ReceiptError? = null; var uploadError: ReceiptError? = null
    var beforeWrite: () -> Unit = {}; var mineReads = 0; var availabilityReads = 0; var termReads = 0; var picks = 0
    var delayedCreate: CompletableDeferred<SaqzResult<OwnedReceiptAccount, ReceiptError>>? = null
    var fileResult: ReceiptFileSelection = ReceiptFileSelection.Selected(onboardingFile)
    var fileCallback: ReceiptFileCallback? = null; var deferFile = false; var pickCancelled = false
    val commands = mutableListOf<ReceiptRegistrationCommand>(); val recoveries = mutableListOf<String>()
    val uploads = mutableListOf<Triple<ReceiptDocument, String, ReceiptDocumentFile>>()
    override suspend fun mine(ownerId: String): SaqzResult<OwnedReceiptAccount?, ReceiptError> {
        mineReads++; return mineError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(account)
    }
    override suspend fun get(): SaqzResult<ReceivablesAvailability, ReceivablesError> {
        availabilityReads++; return SaqzResult.Success(ReceivablesAvailability(enabled, enabled))
    }
    override suspend fun currentTerms(): SaqzResult<ReceiptTerms, ReceiptError> { termReads++; return SaqzResult.Success(terms) }
    override suspend fun create(ownerId: String, command: ReceiptRegistrationCommand): SaqzResult<OwnedReceiptAccount, ReceiptError> {
        beforeWrite(); commands += command
        delayedCreate?.let { return it.await() }
        createError?.let { return SaqzResult.Failure(it) }; account = onboardingAccount; return SaqzResult.Success(onboardingAccount)
    }
    override suspend fun recover(ownerId: String, requestId: String): SaqzResult<OwnedReceiptAccount, ReceiptError> {
        beforeWrite(); recoveries += requestId; recoverError?.let { return SaqzResult.Failure(it) }
        account = onboardingAccount; return SaqzResult.Success(onboardingAccount)
    }
    override suspend fun documents(): SaqzResult<List<ReceiptDocument>, ReceiptError> = SaqzResult.Success(docs)
    override suspend fun upload(document: ReceiptDocument, requestId: String, file: ReceiptDocumentFile): SaqzResult<Unit, ReceiptError> {
        beforeWrite(); uploads += Triple(document, requestId, file); return uploadError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(Unit)
    }
    override fun choose(done: ReceiptFileCallback): ReceiptFileCancellation {
        picks++; fileCallback = done; if (!deferFile) done.onFileSelected(fileResult)
        return ReceiptFileCancellation { pickCancelled = true }
    }
}
