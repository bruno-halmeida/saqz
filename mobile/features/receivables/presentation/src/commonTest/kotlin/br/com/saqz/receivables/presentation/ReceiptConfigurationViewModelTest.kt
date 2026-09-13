package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReceiptConfigurationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test fun percentageFormattingPreservesExactDecimal() {
        assertEquals("2,99%", receiptPercentage("0.0299"))
        assertEquals("0,000001%", receiptPercentage("1E-8"))
        assertEquals("100%", receiptPercentage("1.000"))
    }

    @Test fun emptyAccountsNeverCreatesOrPreviewsAnything() = runTest {
        val fake = ReceiptFake().apply { listed = emptyList() }
        val vm = model(fake)
        vm.onIntent(ReceiptConfigurationIntent.Preview)
        vm.onIntent(ReceiptConfigurationIntent.Activate)
        assertTrue(vm.state.value.accounts.isEmpty())
        assertFalse(vm.state.value.accepted)
        assertEquals(0, fake.previews)
        assertTrue(fake.activations.isEmpty())
    }

    @Test fun explicitSelectionAndTermsAreRequiredForEachMethodCombination() = runTest {
        listOf(setOf(ReceiptMethod.PIX), setOf(ReceiptMethod.CARD), ReceiptMethod.entries.toSet()).forEach { methods ->
            val fake = ReceiptFake()
            val vm = model(fake)
            vm.onIntent(ReceiptConfigurationIntent.SelectAccount("account"))
            assertTrue(vm.state.value.methods.isEmpty())
            vm.onIntent(ReceiptConfigurationIntent.Preview)
            assertEquals(0, fake.previews)
            methods.forEach { vm.onIntent(ReceiptConfigurationIntent.ToggleMethod(it)) }
            vm.onIntent(ReceiptConfigurationIntent.Preview)
            assertFalse(vm.state.value.accepted)
            vm.onIntent(ReceiptConfigurationIntent.Activate)
            assertTrue(fake.activations.isEmpty())
            vm.onIntent(ReceiptConfigurationIntent.Accept(true))
            vm.onIntent(ReceiptConfigurationIntent.Activate)
            assertEquals(methods, fake.activations.single().methods)
            assertEquals("a".repeat(64), fake.activations.single().fingerprint)
            assertTrue(fake.activations.single().accepted)
            assertTrue(vm.state.value.completed)
        }
    }

    @Test fun failedTermsCannotBeAcceptedAndMethodChangeInvalidatesReview() = runTest {
        val fake = ReceiptFake().apply { termsFail = true }
        val vm = prepared(fake)
        vm.onIntent(ReceiptConfigurationIntent.Accept(true))
        assertFalse(vm.state.value.accepted)
        assertFalse(vm.state.value.canActivate)
        fake.termsFail = false
        vm.onIntent(ReceiptConfigurationIntent.Preview)
        vm.onIntent(ReceiptConfigurationIntent.Accept(true))
        vm.onIntent(ReceiptConfigurationIntent.ToggleMethod(ReceiptMethod.CARD))
        assertNull(vm.state.value.review)
        assertTrue(vm.state.value.terms.isEmpty())
        assertFalse(vm.state.value.accepted)
    }

    @Test fun retryReusesCommandAndConflictRequiresNewReviewAndAcceptance() = runTest {
        val fake = ReceiptFake().apply { mutationError = ReceiptError.NETWORK }
        val vm = prepared(fake)
        vm.onIntent(ReceiptConfigurationIntent.Accept(true))
        vm.onIntent(ReceiptConfigurationIntent.Activate)
        vm.onIntent(ReceiptConfigurationIntent.RetryMutation)
        assertEquals(fake.activations[0], fake.activations[1])
        fake.mutationError = ReceiptError.STALE
        vm.onIntent(ReceiptConfigurationIntent.RetryMutation)
        assertNull(vm.state.value.review)
        assertFalse(vm.state.value.accepted)
        vm.onIntent(ReceiptConfigurationIntent.Activate)
        assertEquals(3, fake.activations.size)
        vm.onIntent(ReceiptConfigurationIntent.Preview)
        vm.onIntent(ReceiptConfigurationIntent.Accept(true))
        fake.mutationError = null
        vm.onIntent(ReceiptConfigurationIntent.Activate)
        assertNotEquals(fake.activations[0].requestId, fake.activations.last().requestId)
    }

    @Test fun maintenanceWorksWithRolloutOffWithoutPreviewOrTerms() = runTest {
        val fake = ReceiptFake().apply { enabled = true; termsFail = true }
        val vm = model(fake, available = false)
        vm.onIntent(ReceiptConfigurationIntent.SelectAccount("account"))
        assertFalse(vm.state.value.canPreview)
        assertTrue(vm.state.value.canDeactivate)
        vm.onIntent(ReceiptConfigurationIntent.Deactivate)
        assertTrue(fake.deactivations.isEmpty())
        vm.onIntent(ReceiptConfigurationIntent.RequestDeactivation)
        fake.mutationError = ReceiptError.NETWORK
        vm.onIntent(ReceiptConfigurationIntent.Deactivate)
        fake.mutationError = null
        vm.onIntent(ReceiptConfigurationIntent.RetryMutation)
        assertEquals(fake.deactivations[0], fake.deactivations[1])
        assertEquals(0, fake.previews)
        assertFalse(vm.state.value.status!!.state.enabled)
    }

    @Test fun uncertainResultLocksEditingAndRetriesEvenAfterRolloutOff() = runTest {
        val fake = ReceiptFake().apply { mutationError = ReceiptError.UNCERTAIN }
        var available = true
        val vm = ReceiptConfigurationViewModel("group", fake, object : ReceivablesAvailabilityGateway {
            override suspend fun get() = SaqzResult.Success(ReceivablesAvailability(available, available))
        }, ReceivablesSessionContext { "session" }, SavedStateHandle(), ReceivablesRecoveryIdentity { "user" })
        vm.onIntent(ReceiptConfigurationIntent.SelectAccount("account"))
        vm.onIntent(ReceiptConfigurationIntent.ToggleMethod(ReceiptMethod.PIX))
        vm.onIntent(ReceiptConfigurationIntent.Preview)
        vm.onIntent(ReceiptConfigurationIntent.Accept(true))
        vm.onIntent(ReceiptConfigurationIntent.Activate)
        assertTrue(vm.state.value.pendingMutation)
        listOf(ReceiptConfigurationIntent.Refresh, ReceiptConfigurationIntent.ToggleMethod(ReceiptMethod.CARD),
            ReceiptConfigurationIntent.SelectAccount("other"), ReceiptConfigurationIntent.Deactivate).forEach(vm::onIntent)
        assertEquals(setOf(ReceiptMethod.PIX), vm.state.value.methods)
        assertEquals(1, fake.activations.size)
        available = false
        fake.mutationError = null
        vm.onIntent(ReceiptConfigurationIntent.RetryMutation)
        assertEquals(fake.activations[0], fake.activations[1])
        assertFalse(vm.state.value.pendingMutation)
    }

    @Test fun inFlightMutationBlocksEditsAndSavedCommandRecoversAfterRecreation() = runTest {
        val fake = ReceiptFake().apply { mutationDelay = CompletableDeferred() }
        val saved = SavedStateHandle()
        val available = object : ReceivablesAvailabilityGateway {
            override suspend fun get() = SaqzResult.Success(ReceivablesAvailability(true, true))
        }
        var sessionKey = "7:user"
        val session = ReceivablesSessionContext { sessionKey }
        val vm = ReceiptConfigurationViewModel("group", fake, available, session, saved, ReceivablesRecoveryIdentity { "user" })
        vm.onIntent(ReceiptConfigurationIntent.SelectAccount("account"))
        vm.onIntent(ReceiptConfigurationIntent.ToggleMethod(ReceiptMethod.PIX))
        vm.onIntent(ReceiptConfigurationIntent.Preview)
        vm.onIntent(ReceiptConfigurationIntent.Accept(true))
        vm.onIntent(ReceiptConfigurationIntent.Activate)
        assertTrue(vm.state.value.loading)
        assertTrue(vm.state.value.pendingMutation)
        vm.onIntent(ReceiptConfigurationIntent.Refresh)
        vm.onIntent(ReceiptConfigurationIntent.ToggleMethod(ReceiptMethod.CARD))
        assertEquals(1, fake.activations.size)
        fake.mutationError = ReceiptError.NETWORK
        fake.mutationDelay!!.complete(Unit)
        runCurrent()
        sessionKey = "1:user"
        val restored = ReceiptConfigurationViewModel("group", fake, available, session, saved, ReceivablesRecoveryIdentity { "user" })
        assertTrue(restored.state.value.pendingMutation)
        fake.mutationError = null
        restored.onIntent(ReceiptConfigurationIntent.RetryMutation)
        assertEquals(fake.activations[0], fake.activations[1])
        assertFalse(restored.state.value.pendingMutation)
        assertTrue(restored.state.value.status!!.state.enabled)
        assertEquals(1, restored.state.value.accounts.size)
        assertTrue(restored.state.value.status!!.permissions.getValue("CANCEL").allowed)
        assertTrue(saved.keys().isEmpty())
    }

    @Test fun logoutDiscardsMutationResultAndClearsSavedCommand() = runTest {
        val fake = ReceiptFake().apply { mutationDelay = CompletableDeferred() }
        val saved = SavedStateHandle()
        var key: String? = "session"
        val vm = ReceiptConfigurationViewModel("group", fake, object : ReceivablesAvailabilityGateway {
            override suspend fun get() = SaqzResult.Success(ReceivablesAvailability(true, true))
        }, ReceivablesSessionContext { key }, saved, ReceivablesRecoveryIdentity { "user" })
        vm.onIntent(ReceiptConfigurationIntent.SelectAccount("account"))
        vm.onIntent(ReceiptConfigurationIntent.ToggleMethod(ReceiptMethod.PIX))
        vm.onIntent(ReceiptConfigurationIntent.Preview)
        vm.onIntent(ReceiptConfigurationIntent.Accept(true))
        vm.onIntent(ReceiptConfigurationIntent.Activate)
        key = null
        fake.mutationDelay!!.complete(Unit)
        runCurrent()
        assertEquals(ReceiptError.SIGNED_OUT, vm.state.value.error)
        assertFalse(vm.state.value.completed)
        assertTrue(saved.keys().isEmpty())
    }

    @Test fun anotherUserCannotRestoreSavedCommand() = runTest {
        val saved = SavedStateHandle(mapOf("receipt.user" to "previous-user", "receipt.request" to "request"))
        val vm = ReceiptConfigurationViewModel("group", ReceiptFake(), object : ReceivablesAvailabilityGateway {
            override suspend fun get() = SaqzResult.Success(ReceivablesAvailability(true, true))
        }, ReceivablesSessionContext { "1:new-user" }, saved, ReceivablesRecoveryIdentity { "new-user" })
        assertFalse(vm.state.value.pendingMutation)
        assertTrue(saved.keys().isEmpty())
    }

    @Test fun hangingAvailabilityCannotBlockMaintenance() = runTest {
        val fake = ReceiptFake().apply { enabled = true }
        val response = CompletableDeferred<ReceivablesAvailability>()
        val vm = ReceiptConfigurationViewModel("group", fake, object : ReceivablesAvailabilityGateway {
            override suspend fun get() = SaqzResult.Success(response.await())
        }, ReceivablesSessionContext { "session" }, SavedStateHandle(), ReceivablesRecoveryIdentity { "user" })
        vm.onIntent(ReceiptConfigurationIntent.SelectAccount("account"))
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.canDeactivate)
        vm.onIntent(ReceiptConfigurationIntent.RequestDeactivation)
        vm.onIntent(ReceiptConfigurationIntent.Deactivate)
        assertEquals(1, fake.deactivations.size)
        response.complete(ReceivablesAvailability(true, true))
        runCurrent()
        assertFalse(vm.state.value.discoveryAvailable)
    }

    @Test fun denialAndPendingRegistrationDoNotGrantActivation() = runTest {
        val fake = ReceiptFake().apply { allowed = false; listed = listOf(ReceiptAccount("account", AccountRegistration.UNDER_REVIEW, false)) }
        val vm = prepared(fake)
        vm.onIntent(ReceiptConfigurationIntent.Accept(true))
        vm.onIntent(ReceiptConfigurationIntent.Activate)
        assertFalse(vm.state.value.canActivate)
        assertTrue(fake.activations.isEmpty())
        assertEquals(AccountRegistration.UNDER_REVIEW, vm.state.value.accounts.single().registration)
    }

    @Test fun logoutDiscardsDelayedPreviewAndTermsAndCannotSubmit() = runTest {
        val fake = ReceiptFake().apply { delayed = CompletableDeferred() }
        var key: String? = "session-a"
        val vm = model(fake, session = ReceivablesSessionContext { key })
        vm.onIntent(ReceiptConfigurationIntent.SelectAccount("account"))
        vm.onIntent(ReceiptConfigurationIntent.ToggleMethod(ReceiptMethod.PIX))
        vm.onIntent(ReceiptConfigurationIntent.Preview)
        key = "session-b"
        fake.delayed!!.complete(Unit)
        runCurrent()
        assertEquals(ReceiptError.SIGNED_OUT, vm.state.value.error)
        assertNull(vm.state.value.review)
        assertTrue(vm.state.value.accounts.isEmpty())
        vm.onIntent(ReceiptConfigurationIntent.Activate)
        assertTrue(fake.activations.isEmpty())
    }

    private fun prepared(fake: ReceiptFake): ReceiptConfigurationViewModel = model(fake).also {
        it.onIntent(ReceiptConfigurationIntent.SelectAccount("account"))
        it.onIntent(ReceiptConfigurationIntent.ToggleMethod(ReceiptMethod.PIX))
        it.onIntent(ReceiptConfigurationIntent.Preview)
    }
    private fun model(fake: ReceiptFake, available: Boolean = true,
        session: ReceivablesSessionContext = ReceivablesSessionContext { "session" }) = ReceiptConfigurationViewModel(
        "group", fake, object : ReceivablesAvailabilityGateway {
            override suspend fun get() = SaqzResult.Success(ReceivablesAvailability(available, available))
        }, session, SavedStateHandle(), ReceivablesRecoveryIdentity { "user" },
    )
}

internal class ReceiptFake : GroupReceivablesGateway {
    var listed = listOf(ReceiptAccount("account", AccountRegistration.APPROVED, true))
    var enabled = false
    var allowed = true
    var termsFail = false
    var mutationError: ReceiptError? = null
    var delayed: CompletableDeferred<Unit>? = null
    var mutationDelay: CompletableDeferred<Unit>? = null
    var previews = 0
    val activations = mutableListOf<ReceiptCommand>()
    val deactivations = mutableListOf<ReceiptCommand>()
    private fun configuration() = ReceiptConfiguration("account", "group", enabled, enabled, false)
    override suspend fun accounts() = SaqzResult.Success(listed)
    override suspend fun status(groupId: String, accountId: String) = SaqzResult.Success(
        ReceiptStatus(configuration(), mapOf("READ" to ReceiptPermission(true), "CANCEL" to ReceiptPermission(true))))
    override suspend fun preview(groupId: String, command: ReceiptCommand): SaqzResult<ReceiptReview, ReceiptError> {
        previews++
        delayed?.await()
        return SaqzResult.Success(ReceiptReview(configuration(), command.methods.map {
            ReceiptSchedule(it, "v1", "0.01", 10, "0.02", 20)
        }, emptyList(), mapOf("ACTIVATE_GROUP" to ReceiptPermission(allowed)), null, "a".repeat(64)))
    }
    override suspend fun terms(version: String): SaqzResult<ReceiptTerms, ReceiptError> =
        if (termsFail) SaqzResult.Failure(ReceiptError.UNAVAILABLE) else SaqzResult.Success(ReceiptTerms(version, "Termos completos de teste"))
    override suspend fun activate(groupId: String, command: ReceiptCommand): SaqzResult<ReceiptConfiguration, ReceiptError> {
        activations.add(command)
        mutationDelay?.await()
        mutationError?.let { return SaqzResult.Failure(it) }
        enabled = true
        return SaqzResult.Success(configuration())
    }
    override suspend fun deactivate(groupId: String, command: ReceiptCommand): SaqzResult<ReceiptConfiguration, ReceiptError> {
        deactivations.add(command)
        mutationError?.let { return SaqzResult.Failure(it) }
        enabled = false
        return SaqzResult.Success(configuration())
    }
}
