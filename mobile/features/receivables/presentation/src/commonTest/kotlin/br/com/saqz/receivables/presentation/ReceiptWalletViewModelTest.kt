package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.domain.port.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptWalletViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }
    private fun model(f: WalletFake, saved: SavedStateHandle = SavedStateHandle(), session: () -> String? = { "session" }) =
        ReceiptWalletViewModel(f, f, ReceivablesSessionContext(session), ReceivablesRecoveryIdentity { "actor" }, f, saved)
    private fun ready(vm: ReceiptWalletViewModel) {
        vm.onIntent(ReceiptWalletIntent.Destination("bank")); vm.onIntent(ReceiptWalletIntent.Amount("123,45"))
    }
    @Test fun expiredPlanStillLoadsWalletWithoutNewBusinessGate() = runTest {
        val f = WalletFake(); val vm = model(f)
        assertEquals(false, vm.state.value.accounts.single().operationsEnabled)
        assertEquals(12345L, vm.state.value.balance!!.availableBalanceCents)
        assertEquals(6789L, vm.state.value.balance!!.pendingReceivablesCents)
        assertFalse(vm.state.value.loading)
    }
    @Test fun consentAndExactAmountAreRequiredAndMarkerPrecedesFinancialIo() = runTest {
        val f = WalletFake(); val saved = SavedStateHandle(); val vm = model(f, saved); ready(vm)
        vm.onIntent(ReceiptWalletIntent.Withdraw); assertTrue(f.commands.isEmpty())
        f.beforeWrite = {
            val marker = Json.decodeFromString<WalletAttempt>(saved.get<String>("wallet.attempt")!!)
            assertEquals("actor", marker.actor); assertEquals("account", marker.accountId)
            assertEquals(12345L, marker.amountCents); assertEquals("bank", marker.destinationId)
        }
        vm.onIntent(ReceiptWalletIntent.Accept(true)); vm.onIntent(ReceiptWalletIntent.Withdraw)
        assertEquals(12345L, f.commands.single().amountCents); assertNotNull(vm.state.value.attempt)
        assertFalse(vm.state.value.canWithdraw)
    }
    @Test fun amountOrDestinationChangesInvalidateConsentAndInsufficientAmountCannotSubmit() = runTest {
        val f = WalletFake(); val vm = model(f); ready(vm)
        vm.onIntent(ReceiptWalletIntent.Accept(true)); vm.onIntent(ReceiptWalletIntent.Amount("123,46"))
        assertFalse(vm.state.value.accepted)
        vm.onIntent(ReceiptWalletIntent.Accept(true)); vm.onIntent(ReceiptWalletIntent.Withdraw)
        assertTrue(f.commands.isEmpty()); assertFalse(vm.state.value.canWithdraw)
        for (amount in listOf("-1", "1.001", "NaN", "1e2", "0", "999999999999999999")) {
            vm.onIntent(ReceiptWalletIntent.Amount(amount)); assertNull(vm.state.value.amountCents)
        }
    }
    @Test fun recreationRecoversOriginalAttemptWithoutPostingAgainOrRestoringBankData() = runTest {
        val f = WalletFake().apply { writeError = WalletError.UNCERTAIN }
        val saved = SavedStateHandle(); val first = model(f, saved); ready(first)
        first.onIntent(ReceiptWalletIntent.Accept(true)); first.onIntent(ReceiptWalletIntent.Withdraw)
        val request = f.commands.single().requestId
        f.recovered = ReceiptWithdrawal("withdrawal", request, "bank", 12345, 173, "COMPLETED", "provider")
        val restored = model(f, saved)
        assertEquals(listOf(request), f.recoveries); assertEquals(1, f.commands.size)
        assertNull(restored.state.value.attempt); assertEquals(173L, restored.state.value.withdrawal!!.feeCents)
        assertEquals("", restored.state.value.password); assertTrue(restored.state.value.bankForm.fields.isEmpty())
        assertNull(saved.get<String>("wallet.attempt"))
    }
    @Test fun originalPendingRequestCannotBeReplacedByAnotherAmountOrAccount() = runTest {
        val f = WalletFake().apply { writeError = WalletError.UNCERTAIN }; val vm = model(f); ready(vm)
        vm.onIntent(ReceiptWalletIntent.Accept(true)); vm.onIntent(ReceiptWalletIntent.Withdraw)
        vm.onIntent(ReceiptWalletIntent.Amount("1")); vm.onIntent(ReceiptWalletIntent.Account("other"))
        vm.onIntent(ReceiptWalletIntent.Withdraw)
        assertEquals("123,45", vm.state.value.amount); assertEquals("account", vm.state.value.accountId)
        assertEquals(1, f.commands.size)
    }
    @Test fun oldActorMarkerIsDiscardedWithoutRecovery() = runTest {
        val saved = SavedStateHandle(mapOf("wallet.attempt" to Json.encodeToString(WalletAttempt("other", "account", "request", "BANK"))))
        val f = WalletFake(); val vm = model(f, saved)
        assertNull(vm.state.value.attempt); assertTrue(f.recoveries.isEmpty()); assertNull(saved.get<String>("wallet.attempt"))
    }
    @Test fun sessionChangeDiscardsLateResponseAndSensitiveState() = runTest {
        var key: String? = "session"
        val f = WalletFake(); val vm = model(f, session = { key }); ready(vm)
        f.writeGate = CompletableDeferred()
        vm.onIntent(ReceiptWalletIntent.Accept(true)); vm.onIntent(ReceiptWalletIntent.Withdraw)
        key = "different-session"; f.writeGate!!.complete(Unit)
        assertEquals(WalletError.SIGNED_OUT, vm.state.value.error); assertNull(vm.state.value.balance)
        assertNull(vm.state.value.withdrawal); assertNull(vm.state.value.attempt)
    }
    @Test fun reauthenticationClearsPasswordAndNeverAutomaticallyExecutesWithdrawal() = runTest {
        val f = WalletFake(); val vm = model(f); ready(vm)
        vm.onIntent(ReceiptWalletIntent.Accept(true)); vm.onIntent(ReceiptWalletIntent.Password("sensitive"))
        vm.onIntent(ReceiptWalletIntent.AuthenticatePassword)
        assertEquals("", vm.state.value.password); assertFalse(vm.state.value.accepted); assertTrue(vm.state.value.authenticating)
        f.authCallback!!.onReauthenticated(ReceiptReauthenticationResult.AUTHENTICATED)
        assertFalse(vm.state.value.authenticating); assertTrue(f.commands.isEmpty())
    }
    @Test fun responseWithDifferentAmountDoesNotClearOriginalRecoveryMarker() = runTest {
        val marker = WalletAttempt("actor", "account", "request", "WITHDRAW", 12345, "bank")
        val saved = SavedStateHandle(mapOf("wallet.attempt" to Json.encodeToString(marker)))
        val f = WalletFake().apply { recovered = ReceiptWithdrawal("w", "request", "bank", 12346, 0, "COMPLETED", null) }
        val vm = model(f, saved)
        assertEquals(WalletError.INVALID, vm.state.value.error); assertEquals(marker, vm.state.value.attempt)
        assertNull(vm.state.value.withdrawal)
    }
    @Test fun revokedAccessClearsStaleBalanceAndCannotOfferWithdrawal() = runTest {
        val f = WalletFake(); val vm = model(f); ready(vm)
        f.balanceError = WalletError.DENIED
        vm.onIntent(ReceiptWalletIntent.Refresh)
        assertNull(vm.state.value.balance); assertTrue(vm.state.value.destinations.isEmpty())
        assertFalse(vm.state.value.canEdit); assertFalse(vm.state.value.canWithdraw)
    }
    @Test fun paginationKeepsExistingSignedEntries() = runTest {
        val f = WalletFake(); val vm = model(f)
        vm.onIntent(ReceiptWalletIntent.More)
        assertEquals(listOf(10000L, -12345L), vm.state.value.entries.map { it.amountCents })
        assertNull(vm.state.value.nextCursor)
    }
}
private class WalletFake : ReceiptWalletGateway, ReceiptAccountDirectory, ReceiptReauthenticationPort {
    val commands = mutableListOf<WalletAttempt>()
    val recoveries = mutableListOf<String>()
    var beforeWrite: () -> Unit = {}
    var writeError: WalletError? = null
    var balanceError: WalletError? = null
    var writeGate: CompletableDeferred<Unit>? = null
    var recovered: ReceiptWithdrawal? = null
    var authCallback: ReceiptReauthenticationCallback? = null
    override suspend fun accounts() = SaqzResult.Success(listOf(ReceiptAccount("account", AccountRegistration.APPROVED, false)))
    override suspend fun balance(accountId: String): SaqzResult<ReceiptWalletBalance, WalletError> =
        balanceError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(ReceiptWalletBalance("account", 12345, 6789, "2026-09-13"))
    override suspend fun statement(accountId: String, cursor: String?) = SaqzResult.Success(ReceiptWalletStatement(
        listOf(ReceiptWalletEntry(if (cursor == null) "first" else "second", "TRANSFER", if (cursor == null) 10000 else -12345,
            12345, "2026-09-13", "Movimento")), if (cursor == null) "next" else null))
    override suspend fun destinations(accountId: String) = SaqzResult.Success(listOf(bank))
    override suspend fun saveDestination(accountId: String, requestId: String, details: ReceiptBankDetails) = SaqzResult.Success(bank)
    override suspend fun recoverDestination(accountId: String, requestId: String) = SaqzResult.Success(bank)
    override suspend fun withdraw(accountId: String, requestId: String, destinationId: String, amountCents: Long):
        SaqzResult<ReceiptWithdrawal, WalletError> {
        beforeWrite(); commands += WalletAttempt("actor", accountId, requestId, "WITHDRAW", amountCents, destinationId)
        writeGate?.await()
        return writeError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(
            ReceiptWithdrawal("withdrawal", requestId, destinationId, amountCents, 0, "PROCESSING", null))
    }
    override suspend fun recoverWithdrawal(accountId: String, requestId: String): SaqzResult<ReceiptWithdrawal, WalletError> {
        recoveries += requestId
        return recovered?.let { SaqzResult.Success(it) } ?: SaqzResult.Failure(WalletError.UNCERTAIN)
    }
    override fun password(password: String, done: ReceiptReauthenticationCallback) { authCallback = done }
    override fun google(done: ReceiptReauthenticationCallback) { authCallback = done }
    private val bank = ReceiptBankDestination("bank", "001", "CHECKING", "T***", "1234", "1234", "1234", false)
}
