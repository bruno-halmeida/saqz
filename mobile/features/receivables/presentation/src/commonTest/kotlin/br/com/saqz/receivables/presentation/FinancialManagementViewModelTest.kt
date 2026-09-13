package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class FinancialManagementViewModelTest {
    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun teardown() = Dispatchers.resetMain()

    @Test fun `delegate edits exact allowed money fields but cannot grant or revoke`() = runTest {
        val fake = Fake().apply { role = ReceiptManagementRole.DELEGATE }
        val vm = model(fake)
        assertEquals("actor", vm.state.value.actorId); assertEquals("account", vm.state.value.selectedAccountId)
        assertEquals(ReceiptManagementRole.DELEGATE, vm.state.value.role); assertEquals(250001, vm.state.value.form.correction()!!.incomeCents)
        vm.onIntent(FinancialManagementIntent.DelegateUser("other")); vm.onIntent(FinancialManagementIntent.AcceptDelegation(true))
        vm.onIntent(FinancialManagementIntent.Grant); vm.onIntent(FinancialManagementIntent.Revoke("admin"))
        assertTrue(fake.grants.isEmpty()); assertTrue(fake.revokes.isEmpty())
        vm.onIntent(FinancialManagementIntent.AcceptMunicipalityWarning(true)); vm.onIntent(FinancialManagementIntent.Correct)
        assertEquals(250001, fake.corrections.single().correction.incomeCents)
        assertFalse(fake.corrections.single().toString().contains("owner@example.test"))
        assertNotNull(vm.state.value.completedRequestId)
    }

    @Test fun `owner grant requires terms and whole-account consent and persists identifiers only`() = runTest {
        val fake = Fake(); val saved = SavedStateHandle(); fake.grantError = ReceiptError.UNCERTAIN
        val vm = model(fake, saved)
        vm.onIntent(FinancialManagementIntent.DelegateUser("admin")); vm.onIntent(FinancialManagementIntent.Grant)
        assertTrue(fake.grants.isEmpty())
        vm.onIntent(FinancialManagementIntent.AcceptDelegation(true)); vm.onIntent(FinancialManagementIntent.Grant)
        val marker = saved.get<String>("financial-management.attempt")!!
        val decoded = Json.decodeFromString<FinancialManagementAttempt>(marker)
        assertEquals(listOf("actor", "account", "admin", "v1"), listOf(decoded.actor, decoded.accountId, decoded.targetId, decoded.termsVersion))
        assertFalse(marker.contains("owner@example.test")); assertEquals(decoded.requestId, fake.grants.single().third)
        fake.grantError = null; vm.onIntent(FinancialManagementIntent.Recover)
        assertEquals(fake.grants[0], fake.grants[1]); assertNull(saved.get<String>("financial-management.attempt"))
    }

    @Test fun `uncertain correction restores and recovers without retaining legal or editable values`() = runTest {
        val fake = Fake().apply { correctionError = ReceiptError.UNCERTAIN }; val saved = SavedStateHandle(); val vm = model(fake, saved)
        vm.onIntent(FinancialManagementIntent.AcceptMunicipalityWarning(true)); vm.onIntent(FinancialManagementIntent.Correct)
        val marker = saved.get<String>("financial-management.attempt")!!
        assertFalse(marker.contains("owner@example.test")); assertFalse(marker.contains("250001"))
        val restoredFake = Fake(); val restored = model(restoredFake, SavedStateHandle(mapOf("financial-management.attempt" to marker)))
        assertTrue(restored.state.value.pending); restored.onIntent(FinancialManagementIntent.Recover)
        assertEquals(Json.decodeFromString<FinancialManagementAttempt>(marker).requestId, restoredFake.recoveries.single())
        assertTrue(restoredFake.corrections.isEmpty())
    }

    @Test fun `logout discards delayed result generation and clears actor account request state`() = runTest {
        var key: String? = "session"; val fake = Fake(); fake.delayed = CompletableDeferred()
        val saved = SavedStateHandle(); val vm = model(fake, saved) { key }
        vm.onIntent(FinancialManagementIntent.AcceptMunicipalityWarning(true)); vm.onIntent(FinancialManagementIntent.Correct)
        key = null; fake.delayed!!.complete(SaqzResult.Success(Unit))
        assertEquals(ReceiptError.SIGNED_OUT, vm.state.value.error); assertNull(vm.state.value.actorId)
        assertNull(vm.state.value.selectedAccountId); assertNull(saved.get<String>("financial-management.attempt"))
    }

    @Test fun `grant cannot target an identifier outside the current administrators`() = runTest {
        val fake = Fake(); val vm = model(fake)
        vm.onIntent(FinancialManagementIntent.DelegateUser("outsider"))
        vm.onIntent(FinancialManagementIntent.AcceptDelegation(true))
        vm.onIntent(FinancialManagementIntent.Grant)
        assertEquals("", vm.state.value.delegateUserId)
        assertTrue(fake.grants.isEmpty())
    }

    @Test fun `revoked access clears commercial data and permission to submit`() = runTest {
        val fake = Fake(); val vm = model(fake)
        fake.managementError = ReceiptError.DENIED
        vm.onIntent(FinancialManagementIntent.Refresh)
        assertEquals(ReceiptError.DENIED, vm.state.value.error)
        assertTrue(vm.state.value.form.values.isEmpty())
        assertTrue(vm.state.value.candidates.isEmpty())
        assertNull(vm.state.value.role)
        assertFalse(vm.state.value.canCorrect)
        assertFalse(vm.state.value.canGrant)
    }

    private fun model(fake: Fake, saved: SavedStateHandle = SavedStateHandle(), key: () -> String? = { "session" }) =
        FinancialManagementViewModel(fake, ReceivablesSessionContext(key), ReceivablesRecoveryIdentity { "actor" }, saved)

    private class Fake : FinancialManagementGateway {
        override suspend fun candidates(accountId: String) = SaqzResult.Success(
            listOf(ReceiptAdministrator("admin", "Ana", listOf("Futebol"))))
        var role = ReceiptManagementRole.OWNER; var managementError: ReceiptError? = null; var correctionError: ReceiptError? = null; var grantError: ReceiptError? = null
        var delayed: CompletableDeferred<SaqzResult<Unit, ReceiptError>>? = null
        val corrections = mutableListOf<ReceiptCorrectionCommand>(); val recoveries = mutableListOf<String>()
        val grants = mutableListOf<Triple<String, String, String>>(); val revokes = mutableListOf<Pair<String, String>>()
        private val correction = ReceiptRegistrationCorrection("owner@example.test", null, "11999999999", null, 250001,
            "01001000", "Rua", "10", null, "Centro")
        override suspend fun accounts() = SaqzResult.Success(listOf(ManagedReceiptAccount("account", "owner",
            AccountRegistration.CORRECTION_REQUIRED, false)))
        override suspend fun management(accountId: String): SaqzResult<ReceiptManagementView, ReceiptError> =
            managementError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(ReceiptManagementView(accountId, role, correction))
        override suspend fun correct(accountId: String, command: ReceiptCorrectionCommand): SaqzResult<Unit, ReceiptError> {
            corrections += command; delayed?.let { return it.await() }; return correctionError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(Unit)
        }
        override suspend fun recover(accountId: String, requestId: String): SaqzResult<Unit, ReceiptError> {
            recoveries += requestId; return SaqzResult.Success(Unit)
        }
        override suspend fun delegations(accountId: String) = SaqzResult.Success(listOf(ReceiptDelegation(accountId, "admin", false)))
        override suspend fun terms() = SaqzResult.Success(ReceiptTerms("v1", "Termos integrais"))
        override suspend fun grant(accountId: String, userId: String, termsVersion: String, requestId: String): SaqzResult<Unit, ReceiptError> {
            grants += Triple(userId, termsVersion, requestId); return grantError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(Unit)
        }
        override suspend fun revoke(accountId: String, userId: String, requestId: String): SaqzResult<Unit, ReceiptError> {
            revokes += userId to requestId; return SaqzResult.Success(Unit)
        }
    }
}
