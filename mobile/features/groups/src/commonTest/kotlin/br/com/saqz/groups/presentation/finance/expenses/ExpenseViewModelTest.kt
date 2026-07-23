package br.com.saqz.groups.presentation.finance.expenses

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.finance.*
import br.com.saqz.groups.domain.group.GroupRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `athlete route is absent and loads no organizer resources or drafts`() = runTest(mainDispatcher) {
        val fixture = fixture(GroupRole.ATHLETE)
        runCurrent()
        assertFalse(fixture.vm.state.value.routeAvailable)
        assertEquals(0, fixture.api.expenseReads)
        assertEquals(0, fixture.api.totalReads)
        assertEquals(0, fixture.store.reads)
    }

    @Test
    fun `athlete cannot create edit void refresh or retry`() = runTest(mainDispatcher) {
        val fixture = fixture(GroupRole.ATHLETE)
        fixture.vm.onIntent(ExpenseIntent.OpenCreate)
        fixture.vm.onIntent(ExpenseIntent.OpenEdit(EXPENSE))
        fixture.vm.onIntent(ExpenseIntent.RequestVoid(EXPENSE))
        fixture.vm.onIntent(ExpenseIntent.Refresh)
        fixture.vm.onIntent(ExpenseIntent.Retry)
        runCurrent()
        assertNull(fixture.vm.state.value.draft)
        assertTrue(fixture.api.creates.isEmpty())
        assertTrue(fixture.api.edits.isEmpty())
        assertTrue(fixture.api.voids.isEmpty())
    }

    @Test
    fun `owner loads organizer expense list and finance totals`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        assertEquals(listOf(EXPENSE), fixture.vm.state.value.expenses.map { it.id })
        assertEquals(12345, fixture.vm.state.value.totals!!.activeExpenseCents)
        assertEquals(1, fixture.api.expenseReads)
        assertEquals(1, fixture.api.totalReads)
    }

    @Test
    fun `admin has full expense route`() = runTest(mainDispatcher) {
        val fixture = fixture(GroupRole.ADMIN)
        runCurrent()
        assertTrue(fixture.vm.state.value.organizer)
        fixture.vm.onIntent(ExpenseIntent.OpenCreate)
        assertNotNull(fixture.vm.state.value.draft)
    }

    @Test
    fun `matching draft restores schema group resource etag key and form`() = runTest(mainDispatcher) {
        val restored = draft(expenseId = EXPENSE, etag = "\"3\"")
        val fixture = fixture(restored = restored)
        runCurrent()
        assertEquals(restored, fixture.vm.state.value.draft)
    }

    @Test
    fun `foreign or old draft is discarded without mutation`() = runTest(mainDispatcher) {
        val fixture = fixture(restored = draft().copy(schemaVersion = 99, groupId = "other"))
        runCurrent()
        assertNull(fixture.vm.state.value.draft)
        assertTrue(fixture.api.creates.isEmpty())
    }

    @Test
    fun `open create persists empty form with stable command key`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.vm.onIntent(ExpenseIntent.OpenCreate)
        val draft = fixture.vm.state.value.draft!!
        assertEquals(KEY, draft.commandKey)
        assertNull(draft.expenseId)
        assertEquals(draft, fixture.store.writes.single())
    }

    @Test
    fun `open edit preserves values resource quoted version and new key`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.vm.onIntent(ExpenseIntent.OpenEdit(EXPENSE))
        val draft = fixture.vm.state.value.draft!!
        assertEquals(EXPENSE, draft.expenseId)
        assertEquals("\"3\"", draft.etag)
        assertEquals("Água", draft.form.customCategory)
        assertEquals("123,45", draft.form.amountBrl)
        assertEquals(KEY, draft.commandKey)
    }

    @Test
    fun `preset category clears custom value before draft write`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.vm.onIntent(ExpenseIntent.OpenCreate)
        fixture.vm.onIntent(ExpenseIntent.UpdateForm(valid().copy(category = ExpenseCategory.Other)))
        fixture.vm.onIntent(
            ExpenseIntent.UpdateForm(valid().copy(category = ExpenseCategory.Venue, customCategory = "deve sumir")),
        )
        assertEquals("", fixture.vm.state.value.draft!!.form.customCategory)
        assertEquals("", fixture.store.writes.last().form.customCategory)
    }

    @Test
    fun `local validation covers description amount date category custom and notes`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.vm.onIntent(ExpenseIntent.OpenCreate)
        fixture.vm.onIntent(
            ExpenseIntent.UpdateForm(ExpenseForm("x", "0", "bad", ExpenseCategory.Other, "x", "x")),
        )
        fixture.vm.onIntent(ExpenseIntent.Submit)
        assertEquals(
            setOf("description", "amountBrl", "expenseDate", "customCategory", "notes"),
            fixture.vm.state.value.fieldErrors.keys,
        )
        assertEquals(ExpenseError.VALIDATION, fixture.vm.state.value.error)
    }

    @Test
    fun `create sends stable key integer cents clears matching draft and refreshes totals`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        openValid(fixture)
        val effect = async { fixture.vm.effects.first() }
        fixture.vm.onIntent(ExpenseIntent.Submit)
        runCurrent()
        val command = fixture.api.creates.single()
        assertEquals(KEY, command.requestId)
        assertEquals(12345, command.amountCents)
        assertEquals(ExpenseCategory.Other, command.category)
        assertEquals(listOf(ClearCall(GROUP, null, KEY)), fixture.store.clears)
        assertEquals(2, fixture.api.expenseReads)
        assertEquals(2, fixture.api.totalReads)
        assertNull(fixture.vm.state.value.draft)
        assertEquals(ExpenseEffect.Saved(EXPENSE), effect.await())
    }

    @Test
    fun `double create submit remains one logical request`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        openValid(fixture)
        fixture.api.gate = CompletableDeferred()
        fixture.vm.onIntent(ExpenseIntent.Submit)
        fixture.vm.onIntent(ExpenseIntent.Submit)
        runCurrent()
        assertEquals(1, fixture.api.creates.size)
        fixture.api.gate!!.complete(Unit)
        runCurrent()
    }

    @Test
    fun `edit sends quoted ETag without create idempotency field`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.vm.onIntent(ExpenseIntent.OpenEdit(EXPENSE))
        fixture.vm.onIntent(ExpenseIntent.Submit)
        runCurrent()
        val call = fixture.api.edits.single()
        assertEquals("\"3\"", call.version.value)
        assertNull(call.command.requestId)
        assertEquals(12345, call.command.amountCents)
    }

    @Test
    fun `edit conflict preserves draft etag and operation for exact retry`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.api.editResult = SaqzResult.Failure(FinanceError.Conflict)
        fixture.vm.onIntent(ExpenseIntent.OpenEdit(EXPENSE))
        fixture.vm.onIntent(ExpenseIntent.Submit)
        runCurrent()
        assertEquals(ExpenseError.CONFLICT, fixture.vm.state.value.error)
        assertTrue(fixture.vm.state.value.reloadAvailable)
        fixture.api.editResult = success()
        fixture.vm.onIntent(ExpenseIntent.Retry)
        runCurrent()
        assertEquals(listOf("\"3\"", "\"3\""), fixture.api.edits.map { it.version.value })
    }

    @Test
    fun `void requires explicit confirmation and may be dismissed`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.vm.onIntent(ExpenseIntent.RequestVoid(EXPENSE))
        assertEquals(EXPENSE, fixture.vm.state.value.pendingVoid!!.id)
        assertTrue(fixture.api.voids.isEmpty())
        fixture.vm.onIntent(ExpenseIntent.DismissVoid)
        assertNull(fixture.vm.state.value.pendingVoid)
        assertTrue(fixture.api.voids.isEmpty())
    }

    @Test
    fun `void preserves quoted version refreshes totals and audit history`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.api.listed = listOf(voided())
        val effect = async { fixture.vm.effects.first() }
        fixture.vm.onIntent(ExpenseIntent.RequestVoid(EXPENSE))
        fixture.vm.onIntent(ExpenseIntent.ConfirmVoid)
        runCurrent()
        assertEquals(VoidCall(EXPENSE, FinanceVersionToken("\"3\"")), fixture.api.voids.single())
        val updated = fixture.vm.state.value.expenses.single()
        assertEquals(ExpenseStatus.Voided, updated.status)
        assertEquals(ExpenseAction.Voided, updated.audit.last().action)
        assertEquals("Despesa anulada no histórico manual.", fixture.vm.state.value.lastAuditOutcome)
        assertEquals(ExpenseEffect.Voided(EXPENSE), effect.await())
    }

    @Test
    fun `server validation fields remain attached to draft`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        openValid(fixture)
        val fields = mapOf("amountCents" to listOf("is invalid"))
        fixture.api.createResult = SaqzResult.Failure(
            FinanceError.Validation(DataError.Validation(ValidationDetails(emptyList(), fields))),
        )
        fixture.vm.onIntent(ExpenseIntent.Submit)
        runCurrent()
        assertEquals(fields, fixture.vm.state.value.fieldErrors)
        assertNotNull(fixture.vm.state.value.draft)
    }

    @Test
    fun `draft write failure is typed without losing form or key`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.store.failWrites = true
        fixture.vm.onIntent(ExpenseIntent.OpenCreate)
        fixture.vm.onIntent(ExpenseIntent.UpdateForm(valid()))
        assertEquals(ExpenseError.DRAFT_UNAVAILABLE, fixture.vm.state.value.error)
        assertEquals(KEY, fixture.vm.state.value.draft!!.commandKey)
        assertEquals("Água do jogo", fixture.vm.state.value.draft!!.form.description)
    }

    private fun fixture(
        role: GroupRole = GroupRole.OWNER,
        restored: ExpenseDraft? = null,
    ): Fixture {
        val api = FakeApi()
        val store = FakeStore(restored)
        val capability = if (role == GroupRole.ATHLETE) {
            ExpenseFinanceCapability.Athlete
        } else {
            ExpenseFinanceCapability.Organizer(api)
        }
        val viewModel = ExpenseViewModel(GROUP, role, capability, store, ExpenseCommandKeyFactory { KEY })
        return Fixture(viewModel, api, store)
    }

    private fun openValid(fixture: Fixture) {
        fixture.vm.onIntent(ExpenseIntent.OpenCreate)
        fixture.vm.onIntent(ExpenseIntent.UpdateForm(valid()))
    }

    private fun valid() = ExpenseForm(
        "Água do jogo",
        "123,45",
        "2026-08-12",
        ExpenseCategory.Other,
        "Água",
        "Compra manual",
    )

    private fun draft(expenseId: String? = null, etag: String? = null) = ExpenseDraft(
        groupId = GROUP,
        expenseId = expenseId,
        etag = etag,
        commandKey = KEY,
        form = valid(),
    )

    private fun expense(
        status: ExpenseStatus = ExpenseStatus.Active,
        version: Long = 3,
        audit: List<ExpenseAudit> = listOf(
            ExpenseAudit("actor", ExpenseAction.Created, "2026-08-12T10:00:00Z"),
        ),
    ) = Expense(
        EXPENSE,
        GroupId(GROUP),
        "Água do jogo",
        12345,
        "2026-08-12",
        ExpenseCategory.Other,
        "Água",
        "Compra manual",
        status,
        version,
        audit,
    )

    private fun voided() = expense(
        ExpenseStatus.Voided,
        4,
        expense().audit + ExpenseAudit("actor", ExpenseAction.Voided, "2026-08-13T10:00:00Z"),
    )

    private fun totals() = FinanceTotals(2500, 5000, 1000, 7000, 12345)
    private fun success() = SaqzResult.Success(VersionedExpense(expense(), FinanceVersionToken("\"3\"")))

    private data class Fixture(val vm: ExpenseViewModel, val api: FakeApi, val store: FakeStore)
    private data class EditCall(
        val expenseId: String,
        val version: FinanceVersionToken,
        val command: ExpenseWriteCommand,
    )
    private data class VoidCall(val expenseId: String, val version: FinanceVersionToken)
    private data class ClearCall(val groupId: String, val expenseId: String?, val key: String)

    private inner class FakeApi : OrganizerFinanceGateway {
        var expenseReads = 0
        var totalReads = 0
        var listed = listOf(expense())
        val creates = mutableListOf<ExpenseWriteCommand>()
        val edits = mutableListOf<EditCall>()
        val voids = mutableListOf<VoidCall>()
        var gate: CompletableDeferred<Unit>? = null
        var createResult: SaqzResult<VersionedExpense, FinanceError> = success()
        var editResult: SaqzResult<VersionedExpense, FinanceError> = success()
        var voidResult: SaqzResult<VersionedExpense, FinanceError> =
            SaqzResult.Success(VersionedExpense(voided(), FinanceVersionToken("\"4\"")))

        override suspend fun expenses(groupId: GroupId): SaqzResult<ExpenseList, FinanceError> {
            expenseReads++
            return SaqzResult.Success(
                ExpenseList(listed, listed.filter { it.status == ExpenseStatus.Active }.sumOf { it.amountCents }),
            )
        }

        override suspend fun totals(groupId: GroupId): SaqzResult<FinanceTotals, FinanceError> {
            totalReads++
            return SaqzResult.Success(totals())
        }

        override suspend fun createExpense(
            groupId: GroupId,
            command: ExpenseWriteCommand,
        ): SaqzResult<VersionedExpense, FinanceError> {
            creates += command
            gate?.await()
            return createResult
        }

        override suspend fun editExpense(
            groupId: GroupId,
            expenseId: String,
            version: FinanceVersionToken,
            command: ExpenseWriteCommand,
        ): SaqzResult<VersionedExpense, FinanceError> {
            edits += EditCall(expenseId, version, command)
            return editResult
        }

        override suspend fun voidExpense(
            groupId: GroupId,
            expenseId: String,
            version: FinanceVersionToken,
        ): SaqzResult<VersionedExpense, FinanceError> {
            voids += VoidCall(expenseId, version)
            return voidResult
        }

        override suspend fun charges(groupId: GroupId): SaqzResult<ChargeList, FinanceError> = error("not used")
        override suspend fun generateMonthly(
            groupId: GroupId,
            command: MonthlyChargeCommand,
        ): SaqzResult<ChargeList, FinanceError> = error("not used")

        override suspend fun updateChargeStatus(
            groupId: GroupId,
            chargeId: String,
            version: FinanceVersionToken,
            command: ChargeStatusCommand,
        ): SaqzResult<VersionedCharge, FinanceError> = error("not used")
    }

    private class FakeStore(private val restored: ExpenseDraft?) : ExpenseDraftStorePort {
        var reads = 0
        var failWrites = false
        val writes = mutableListOf<ExpenseDraft>()
        val clears = mutableListOf<ClearCall>()

        override fun read(groupId: String, expenseId: String?, done: (ExpenseDraftReadResult) -> Unit) {
            reads++
            done(ExpenseDraftReadResult.Success(restored))
        }

        override fun write(draft: ExpenseDraft, done: (ExpenseDraftWriteResult) -> Unit) {
            writes += draft
            done(if (failWrites) ExpenseDraftWriteResult.Failure else ExpenseDraftWriteResult.Success)
        }

        override fun clear(
            groupId: String,
            expenseId: String?,
            commandKey: String,
            done: (ExpenseDraftWriteResult) -> Unit,
        ) {
            clears += ClearCall(groupId, expenseId, commandKey)
            done(ExpenseDraftWriteResult.Success)
        }
    }

    private companion object {
        const val GROUP = "group"
        const val EXPENSE = "expense-1"
        const val KEY = "expense-key"
    }
}
