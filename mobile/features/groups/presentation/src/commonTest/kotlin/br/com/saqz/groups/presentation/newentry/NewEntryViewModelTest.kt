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

        viewModel.onIntent(NewEntryIntent.ApplyPrefill(NewEntryPrefill.GameCourt, "Aluguel da quadra"))

        assertEquals(NewEntryDirection.Out, viewModel.state.value.direction)
        assertEquals(NewEntryCategory.Court, viewModel.state.value.category)
        assertEquals("Aluguel da quadra", viewModel.state.value.description)
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

    private fun viewModel(
        gateway: FakeOrganizerGateway = FakeOrganizerGateway(),
    ) = NewEntryViewModel(
        groupId = "group-1",
        savedState = SavedStateHandle(),
        gateway = gateway,
        now = GroupNowPort { Instant.parse("2026-08-04T12:00:00Z") },
    )
}

private class FakeOrganizerGateway(
    private val createResults: ArrayDeque<SaqzResult<VersionedExpense, FinanceError>> = ArrayDeque(),
) : OrganizerFinanceGateway {
    var createCalls = 0
    var lastCommand: ExpenseWriteCommand? = null
    val commands = mutableListOf<ExpenseWriteCommand>()

    override suspend fun charges(groupId: GroupId) = SaqzResult.Success(ChargeList(emptyList()))
    override suspend fun generateMonthly(groupId: GroupId, command: MonthlyChargeCommand) = SaqzResult.Success(ChargeList(emptyList()))
    override suspend fun updateChargeStatus(groupId: GroupId, chargeId: String, version: FinanceVersionToken, command: ChargeStatusCommand) =
        SaqzResult.Success(VersionedCharge(sampleCharge(), version))
    override suspend fun expenses(groupId: GroupId) = SaqzResult.Success(ExpenseList(emptyList(), 0L))
    override suspend fun createExpense(groupId: GroupId, command: ExpenseWriteCommand): SaqzResult<VersionedExpense, FinanceError> {
        createCalls++
        lastCommand = command
        commands += command
        return createResults.removeFirstOrNull()
            ?: SaqzResult.Success(VersionedExpense(sampleExpense(), FinanceVersionToken("etag")))
    }
    override suspend fun editExpense(groupId: GroupId, expenseId: String, version: FinanceVersionToken, command: ExpenseWriteCommand) =
        SaqzResult.Success(VersionedExpense(sampleExpense(), version))
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
