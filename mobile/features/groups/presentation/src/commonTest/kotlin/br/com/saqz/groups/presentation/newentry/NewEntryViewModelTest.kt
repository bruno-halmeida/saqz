package br.com.saqz.groups.presentation.newentry

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.ChargeStatusCommand
import br.com.saqz.groups.domain.finance.ExpenseCategory
import br.com.saqz.groups.domain.finance.ExpenseList
import br.com.saqz.groups.domain.finance.ExpenseStatus
import br.com.saqz.groups.domain.finance.FinanceTotals
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.MonthlyChargeCommand
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.domain.finance.VersionedCharge
import br.com.saqz.groups.domain.finance.Expense
import br.com.saqz.groups.domain.finance.ExpenseAction
import br.com.saqz.groups.domain.finance.ExpenseAudit
import br.com.saqz.groups.domain.finance.VersionedExpense
import br.com.saqz.groups.domain.finance.ExpenseWriteCommand
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.port.GroupNowPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NewEntryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `defaults to today and exposes only the four allowed categories`() {
        val viewModel = viewModel()

        assertEquals("2026-08-04", viewModel.state.value.date)
        assertEquals(
            setOf(NewEntryCategory.Court, NewEntryCategory.Material, NewEntryCategory.Racha, NewEntryCategory.Other),
            NewEntryCategory.entries.toSet(),
        )
        assertFalse(NewEntryCategory.entries.any { it.name.contains("MENSAL", ignoreCase = true) })
    }

    @Test
    fun `game court prefill selects an outgoing court expense`() {
        val viewModel = viewModel()

        viewModel.onIntent(
            NewEntryIntent.ApplyPrefill(NewEntryPrefill.GameCourt("2026-08-03"), "Aluguel da quadra"),
        )

        assertEquals(NewEntryDirection.Out, viewModel.state.value.direction)
        assertEquals(NewEntryCategory.Court, viewModel.state.value.category)
        assertEquals("Aluguel da quadra", viewModel.state.value.description)
        assertEquals("2026-08-03", viewModel.state.value.date)

        viewModel.onIntent(NewEntryIntent.DateChanged("04082026"))

        assertEquals("2026-08-04", viewModel.state.value.date)
    }

    @Test
    fun `shortcut and numeric date input keep cents and ISO command values`() = runTest(dispatcher) {
        val gateway = FakeOrganizerGateway()
        val viewModel = viewModel(gateway = gateway)

        viewModel.onIntent(NewEntryIntent.SelectDirection(NewEntryDirection.Out))
        viewModel.onIntent(NewEntryIntent.SelectAmountShortcut(12_000L))
        viewModel.onIntent(NewEntryIntent.DescriptionChanged("Compra de bolas"))
        viewModel.onIntent(NewEntryIntent.SelectCategory(NewEntryCategory.Material))
        viewModel.onIntent(NewEntryIntent.DateChanged("12082026"))
        viewModel.onIntent(NewEntryIntent.Save)

        assertEquals(NewEntryEffect.Saved, viewModel.effects.first())
        assertEquals(
            ExpenseWriteCommand(
                requestId = gateway.lastCommand?.requestId,
                description = "Compra de bolas",
                amountCents = 12_000L,
                expenseDate = "2026-08-12",
                category = ExpenseCategory.Equipment,
                direction = br.com.saqz.groups.domain.finance.FinanceDirection.Out,
            ),
            gateway.lastCommand,
        )
        assertTrue(gateway.lastCommand?.requestId?.isNotBlank() == true)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `invalid form does not call gateway and exposes validation`() = runTest(dispatcher) {
        val gateway = FakeOrganizerGateway()
        val viewModel = viewModel(gateway = gateway)

        viewModel.onIntent(NewEntryIntent.Save)

        assertEquals(br.com.saqz.groups.presentation.GroupUiError.Validation, viewModel.state.value.error)
        assertEquals(0, gateway.createCalls)
    }

    @Test
    fun `other without custom category shows field validation and does not save`() = runTest(dispatcher) {
        val gateway = FakeOrganizerGateway()
        val viewModel = viewModel(gateway = gateway)
        viewModel.onIntent(NewEntryIntent.SelectAmountShortcut(8_000L))
        viewModel.onIntent(NewEntryIntent.DescriptionChanged("Compra de bolas"))
        viewModel.onIntent(NewEntryIntent.Save)

        assertEquals(br.com.saqz.groups.presentation.GroupUiError.Validation, viewModel.state.value.error)
        assertEquals(0, gateway.createCalls)
    }

    @Test
    fun `other with custom category saves the custom category`() = runTest(dispatcher) {
        val gateway = FakeOrganizerGateway()
        val viewModel = viewModel(gateway = gateway)
        viewModel.onIntent(NewEntryIntent.SelectAmountShortcut(8_000L))
        viewModel.onIntent(NewEntryIntent.DescriptionChanged("Compra de bolas"))
        viewModel.onIntent(NewEntryIntent.CustomCategoryChanged("Água"))
        viewModel.onIntent(NewEntryIntent.Save)

        assertEquals(NewEntryEffect.Saved, viewModel.effects.first())
        assertEquals(ExpenseCategory.Other, gateway.lastCommand?.category)
        assertEquals("Água", gateway.lastCommand?.customCategory)
    }

    @Test
    fun `retrying save reuses request id until success`() = runTest(dispatcher) {
        val gateway = FakeOrganizerGateway(
            createResults = ArrayDeque(
                listOf(SaqzResult.Failure(FinanceError.Data(DataError.Unknown))),
            ),
        )
        val viewModel = viewModel(gateway = gateway)
        viewModel.onIntent(NewEntryIntent.SelectAmountShortcut(8_000L))
        viewModel.onIntent(NewEntryIntent.DescriptionChanged("Aluguel da quadra"))
        viewModel.onIntent(NewEntryIntent.SelectCategory(NewEntryCategory.Court))

        viewModel.onIntent(NewEntryIntent.Save)
        val firstRequestId = gateway.commands.single().requestId

        viewModel.onIntent(NewEntryIntent.Save)
        assertEquals(NewEntryEffect.Saved, viewModel.effects.first())
        assertEquals(firstRequestId, gateway.commands[1].requestId)

        viewModel.onIntent(NewEntryIntent.Save)
        assertEquals(NewEntryEffect.Saved, viewModel.effects.first())
        assertNotEquals(firstRequestId, gateway.commands[2].requestId)
    }

    @Test
    fun `correction loads value and description and sends the rest as it is with the expense version`() =
        runTest(dispatcher) {
            val gateway = FakeOrganizerGateway(stored = listOf(storedExpense()))
            val viewModel = viewModel(gateway = gateway)

            viewModel.onIntent(NewEntryIntent.LoadEdit("expense-9"))

            assertTrue(viewModel.state.value.editing)
            assertFalse(viewModel.state.value.isLoadingEntry)
            assertEquals("80,00", viewModel.state.value.amountText)
            assertEquals("Aluguel da quadra", viewModel.state.value.description)

            viewModel.onIntent(NewEntryIntent.AmountChanged("95,50"))
            viewModel.onIntent(NewEntryIntent.DescriptionChanged("  Quadra e bolas "))
            viewModel.onIntent(NewEntryIntent.Save)

            assertEquals(NewEntryEffect.Saved, viewModel.effects.first())
            val edit = gateway.edits.single()
            assertEquals("expense-9", edit.expenseId)
            assertEquals(FinanceVersionToken("\"4\""), edit.version)
            assertEquals(
                ExpenseWriteCommand(
                    description = "Quadra e bolas",
                    amountCents = 9_550L,
                    expenseDate = "2026-08-01",
                    category = ExpenseCategory.Venue,
                    customCategory = null,
                    notes = "Pago em dinheiro",
                    direction = br.com.saqz.groups.domain.finance.FinanceDirection.Out,
                ),
                edit.command,
            )
            assertEquals(0, gateway.createCalls)
        }

    @Test
    fun `correction with invalid value or blank description does not call the gateway`() = runTest(dispatcher) {
        val gateway = FakeOrganizerGateway(stored = listOf(storedExpense()))
        val viewModel = viewModel(gateway = gateway)
        viewModel.onIntent(NewEntryIntent.LoadEdit("expense-9"))

        viewModel.onIntent(NewEntryIntent.AmountChanged("0"))
        viewModel.onIntent(NewEntryIntent.Save)
        assertEquals(br.com.saqz.groups.presentation.GroupUiError.Validation, viewModel.state.value.error)

        viewModel.onIntent(NewEntryIntent.AmountChanged("10"))
        viewModel.onIntent(NewEntryIntent.DescriptionChanged("  "))
        viewModel.onIntent(NewEntryIntent.Save)

        assertEquals(br.com.saqz.groups.presentation.GroupUiError.Validation, viewModel.state.value.error)
        assertTrue(gateway.edits.isEmpty())
    }

    @Test
    fun `correction conflict exposes the conflict and keeps what was typed`() = runTest(dispatcher) {
        val gateway = FakeOrganizerGateway(
            stored = listOf(storedExpense()),
            editResults = ArrayDeque(listOf(SaqzResult.Failure(FinanceError.Conflict))),
        )
        val viewModel = viewModel(gateway = gateway)
        viewModel.onIntent(NewEntryIntent.LoadEdit("expense-9"))
        viewModel.onIntent(NewEntryIntent.AmountChanged("90"))

        viewModel.onIntent(NewEntryIntent.Save)

        assertEquals(br.com.saqz.groups.presentation.GroupUiError.Conflict, viewModel.state.value.error)
        assertEquals("90", viewModel.state.value.amountText)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `correction cannot be saved twice while the first save is in flight`() = runTest(dispatcher) {
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val gateway = FakeOrganizerGateway(stored = listOf(storedExpense()), editGate = release)
        val viewModel = viewModel(gateway = gateway)
        viewModel.onIntent(NewEntryIntent.LoadEdit("expense-9"))

        viewModel.onIntent(NewEntryIntent.Save)
        viewModel.onIntent(NewEntryIntent.Save)
        release.complete(Unit)

        assertEquals(1, gateway.edits.size)
    }

    @Test
    fun `correction of a missing or already voided expense fails the load and offers retry`() = runTest(dispatcher) {
        val gateway = FakeOrganizerGateway(stored = listOf(storedExpense().copy(status = ExpenseStatus.Voided)))
        val viewModel = viewModel(gateway = gateway)

        viewModel.onIntent(NewEntryIntent.LoadEdit("expense-9"))

        assertTrue(viewModel.state.value.entryLoadFailed)
        assertEquals(br.com.saqz.groups.presentation.GroupUiError.NotFound, viewModel.state.value.error)
        viewModel.onIntent(NewEntryIntent.Save)
        assertTrue(gateway.edits.isEmpty())

        gateway.stored = listOf(storedExpense())
        viewModel.onIntent(NewEntryIntent.RetryLoadEdit)

        assertFalse(viewModel.state.value.entryLoadFailed)
        assertEquals("80,00", viewModel.state.value.amountText)
    }

    @Test
    fun `correction keeps what was typed when the form is restored after process death`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val gateway = FakeOrganizerGateway(stored = listOf(storedExpense()))
        val first = viewModel(gateway = gateway, savedState = saved)
        first.onIntent(NewEntryIntent.LoadEdit("expense-9"))
        first.onIntent(NewEntryIntent.AmountChanged("99"))

        val restored = viewModel(gateway = gateway, savedState = saved)
        restored.onIntent(NewEntryIntent.LoadEdit("expense-9"))

        assertEquals("99", restored.state.value.amountText)
        restored.onIntent(NewEntryIntent.Save)
        assertEquals(9_900L, gateway.edits.single().command.amountCents)
    }

    private fun storedExpense() = Expense(
        id = "expense-9", groupId = GroupId("group-1"), description = "Aluguel da quadra", amountCents = 8_000L,
        expenseDate = "2026-08-01", category = ExpenseCategory.Venue, notes = "Pago em dinheiro",
        status = ExpenseStatus.Active, version = 4,
        audit = listOf(ExpenseAudit("actor", ExpenseAction.Created, "2026-08-01")),
        direction = br.com.saqz.groups.domain.finance.FinanceDirection.Out,
    )

    private fun viewModel(
        gateway: FakeOrganizerGateway = FakeOrganizerGateway(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = NewEntryViewModel(
        groupId = "group-1",
        savedState = savedState,
        gateway = gateway,
        now = GroupNowPort { Instant.parse("2026-08-04T12:00:00Z") },
    )
}

private data class RecordedEdit(
    val expenseId: String,
    val version: FinanceVersionToken,
    val command: ExpenseWriteCommand,
)

private class FakeOrganizerGateway(
    private val createResults: ArrayDeque<SaqzResult<VersionedExpense, FinanceError>> = ArrayDeque(),
    var stored: List<Expense> = emptyList(),
    private val editResults: ArrayDeque<SaqzResult<VersionedExpense, FinanceError>> = ArrayDeque(),
    private val editGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null,
) : OrganizerFinanceGateway {
    val edits = mutableListOf<RecordedEdit>()
    var createCalls = 0
    var lastCommand: ExpenseWriteCommand? = null
    val commands = mutableListOf<ExpenseWriteCommand>()

    override suspend fun charges(groupId: GroupId) = SaqzResult.Success(ChargeList(emptyList()))
    override suspend fun generateMonthly(groupId: GroupId, command: MonthlyChargeCommand) = SaqzResult.Success(ChargeList(emptyList()))
    override suspend fun updateChargeStatus(groupId: GroupId, chargeId: String, version: FinanceVersionToken, command: ChargeStatusCommand) =
        SaqzResult.Success(VersionedCharge(sampleCharge(), version))
    override suspend fun resolveChargeReview(groupId: GroupId, chargeId: String, version: FinanceVersionToken) =
        SaqzResult.Success(VersionedCharge(sampleCharge(), version))
    override suspend fun expenses(groupId: GroupId) = SaqzResult.Success(ExpenseList(stored, stored.sumOf { it.amountCents }))
    override suspend fun createExpense(groupId: GroupId, command: ExpenseWriteCommand): SaqzResult<VersionedExpense, FinanceError> {
        createCalls++
        lastCommand = command
        commands += command
        return createResults.removeFirstOrNull()
            ?: SaqzResult.Success(VersionedExpense(sampleExpense(), FinanceVersionToken("etag")))
    }
    override suspend fun editExpense(
        groupId: GroupId,
        expenseId: String,
        version: FinanceVersionToken,
        command: ExpenseWriteCommand,
    ): SaqzResult<VersionedExpense, FinanceError> {
        edits += RecordedEdit(expenseId, version, command)
        editGate?.await()
        return editResults.removeFirstOrNull() ?: SaqzResult.Success(VersionedExpense(sampleExpense(), version))
    }
    override suspend fun voidExpense(groupId: GroupId, expenseId: String, version: FinanceVersionToken) =
        SaqzResult.Success(VersionedExpense(sampleExpense(), version))
    override suspend fun totals(groupId: GroupId) = SaqzResult.Success(FinanceTotals(0, 0, 0, 0, 0))

    private fun sampleCharge() = Charge(
        id = "charge", groupId = GroupId("group-1"), memberId = "member", kind = ChargeKind.Game,
        amountCents = 1, dueDate = "2026-08-04", status = ChargeStatus.Pending,
        version = 1, audit = emptyList(), paidMethod = PaidMethod.Pix,
    )

    private fun sampleExpense() = Expense(
        id = "expense", groupId = GroupId("group-1"), description = "expense", amountCents = 1,
        expenseDate = "2026-08-04", category = ExpenseCategory.Other, status = ExpenseStatus.Active,
        version = 1, audit = listOf(ExpenseAudit("actor", ExpenseAction.Created, "2026-08-04")),
    )
}
