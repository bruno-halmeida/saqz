package br.com.saqz.groups.presentation.statement

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.ExpenseStatus
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceStatementGateway
import br.com.saqz.groups.domain.finance.FinanceStatementItem
import br.com.saqz.groups.domain.finance.FinanceStatementPage
import br.com.saqz.groups.domain.finance.FinanceStatementQuery
import br.com.saqz.groups.domain.finance.FinanceStatementSummary
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.presentation.GroupUiError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StatementViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads statement with summary and localized item metadata`() = runTest(dispatcher) {
        val gateway = FakeStatementGateway(
            pages = ArrayDeque(listOf(statementPage(items = listOf(incomeItem), balance = 64_000L))),
        )
        val viewModel = StatementViewModel("group-1", gateway, FakeExpenseFinance(), br.com.saqz.groups.presentation.FakeCashboxGateway())
        viewModel.onIntent(StatementIntent.Retry)

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(FinanceStatementQuery(limit = 20, offset = 0), gateway.queries.single())
        assertEquals("Pix", viewModel.state.value.items.single().meta)
        assertEquals("+R$\u00A080", viewModel.state.value.items.single().amountLabel)
        assertEquals(64_000L, viewModel.state.value.summary.periodBalanceCents)
    }

    @Test
    fun `charges say the method and manual entries say the direction and category`() = runTest(dispatcher) {
        val gateway = FakeStatementGateway(
            pages = ArrayDeque(
                listOf(
                    statementPage(
                        items = listOf(
                            incomeItem.copy(category = "MONTHLY"),
                            expenseItem.copy(category = "GAME"),
                        ),
                    ),
                ),
            ),
        )
        val viewModel = StatementViewModel("group-1", gateway, FakeExpenseFinance(), br.com.saqz.groups.presentation.FakeCashboxGateway())
        viewModel.onIntent(StatementIntent.Retry)

        assertEquals(
            listOf("Pix", "Saída · Jogo"),
            viewModel.state.value.items.map { it.meta },
        )
    }

    @Test
    fun `formats negative outgoing amount using direction sign`() {
        assertEquals("−R$ 7,00", formatStatementAmount(-700L, FinanceDirection.Out))
    }

    @Test
    fun `formats instant in local timezone before displaying date`() {
        assertEquals(
            "04/08/2026",
            "2026-08-05T01:30:00Z".toStatementDateLabel(TimeZone.of("America/Sao_Paulo")),
        )
    }

    @Test
    fun `filter changes direction and resets pagination`() = runTest(dispatcher) {
        val gateway = FakeStatementGateway(
            pages = ArrayDeque(
                listOf(
                    statementPage(items = listOf(incomeItem), hasMore = true),
                    statementPage(items = listOf(expenseItem), offset = 0),
                ),
            ),
        )
        val viewModel = StatementViewModel("group-1", gateway, FakeExpenseFinance(), br.com.saqz.groups.presentation.FakeCashboxGateway())
        viewModel.onIntent(StatementIntent.Retry)

        viewModel.onIntent(StatementIntent.SelectFilter(StatementFilter.Out))

        assertEquals(FinanceDirection.Out, gateway.queries.last().direction)
        assertEquals(0, gateway.queries.last().offset)
        assertEquals(listOf("expense-1"), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun `load more appends page and advances offset`() = runTest(dispatcher) {
        val gateway = FakeStatementGateway(
            pages = ArrayDeque(
                listOf(
                    statementPage(items = listOf(incomeItem), hasMore = true),
                    statementPage(items = listOf(expenseItem), offset = 1),
                ),
            ),
        )
        val viewModel = StatementViewModel("group-1", gateway, FakeExpenseFinance(), br.com.saqz.groups.presentation.FakeCashboxGateway())
        viewModel.onIntent(StatementIntent.Retry)

        viewModel.onIntent(StatementIntent.LoadMore)

        assertEquals(listOf("income-1", "expense-1"), viewModel.state.value.items.map { it.id })
        assertEquals(2, viewModel.state.value.nextOffset)
        assertFalse(viewModel.state.value.hasMore)
        assertEquals(1, gateway.queries.last().offset)
    }

    @Test
    fun `pagination failure keeps loaded items and retries inline`() = runTest(dispatcher) {
        val gateway = FakeStatementGateway(
            results = ArrayDeque(
                listOf(
                    SaqzResult.Success(statementPage(items = listOf(incomeItem), hasMore = true)),
                    SaqzResult.Failure(FinanceError.Data(DataError.Unknown)),
                    SaqzResult.Success(statementPage(items = listOf(expenseItem), offset = 1)),
                ),
            ),
        )
        val viewModel = StatementViewModel("group-1", gateway, FakeExpenseFinance(), br.com.saqz.groups.presentation.FakeCashboxGateway())
        viewModel.onIntent(StatementIntent.Retry)

        viewModel.onIntent(StatementIntent.LoadMore)

        assertEquals(listOf("income-1"), viewModel.state.value.items.map { it.id })
        assertFalse(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.Network, viewModel.state.value.paginationFailed)

        viewModel.onIntent(StatementIntent.LoadMore)

        assertEquals(listOf("income-1", "expense-1"), viewModel.state.value.items.map { it.id })
        assertNull(viewModel.state.value.paginationFailed)
    }

    @Test
    fun `stale response cannot replace a newer filter request`() = runTest(dispatcher) {
        val old = CompletableDeferred<SaqzResult<FinanceStatementPage, FinanceError>>()
        val fresh = CompletableDeferred<SaqzResult<FinanceStatementPage, FinanceError>>()
        val gateway = FakeStatementGateway(deferreds = ArrayDeque(listOf(old, fresh)))
        val viewModel = StatementViewModel("group-1", gateway, FakeExpenseFinance(), br.com.saqz.groups.presentation.FakeCashboxGateway())

        viewModel.onIntent(StatementIntent.Retry)
        viewModel.onIntent(StatementIntent.SelectFilter(StatementFilter.Out))
        fresh.complete(SaqzResult.Success(statementPage(items = listOf(expenseItem))))
        old.complete(SaqzResult.Success(statementPage(items = listOf(incomeItem))))

        assertEquals(listOf("expense-1"), viewModel.state.value.items.map { it.id })
        assertEquals(StatementFilter.Out, viewModel.state.value.filter)
        assertTrue(gateway.queries.last().direction == FinanceDirection.Out)
    }

    @Test
    fun `only manual entries are marked for correction and void`() = runTest(dispatcher) {
        val viewModel = loaded(statementPage(items = listOf(incomeItem, expenseItem)))

        assertEquals(listOf(false, true), viewModel.state.value.items.map { it.manual })
    }

    @Test
    fun `edit opens the correction of a manual entry and ignores charges`() = runTest(dispatcher) {
        val viewModel = loaded(statementPage(items = listOf(incomeItem, expenseItem)))

        viewModel.onIntent(StatementIntent.Edit("income-1"))
        viewModel.onIntent(StatementIntent.Edit("expense-1"))

        assertEquals(StatementEffect.OpenEditEntry("group-1", "expense-1"), viewModel.effects.first())
    }

    @Test
    fun `void asks first then voids with the version read from the expense list and refreshes`() =
        runTest(dispatcher) {
            val finance = FakeExpenseFinance(stored = listOf(storedExpense(version = 3)))
            val gateway = FakeStatementGateway(
                pages = ArrayDeque(
                    listOf(
                        statementPage(items = listOf(expenseItem)),
                        statementPage(items = emptyList()),
                    ),
                ),
            )
            val viewModel = StatementViewModel("group-1", gateway, finance, br.com.saqz.groups.presentation.FakeCashboxGateway())
            viewModel.onIntent(StatementIntent.Retry)

            viewModel.onIntent(StatementIntent.AskVoid("expense-1"))
            assertEquals("expense-1", viewModel.state.value.voidTargetId)
            assertTrue(finance.voids.isEmpty())

            viewModel.onIntent(StatementIntent.ConfirmVoid)

            assertEquals(listOf(FakeExpenseFinance.VoidCall("expense-1", FinanceVersionToken("\"3\""))), finance.voids)
            assertEquals(StatementEffect.Voided, viewModel.effects.first())
            assertNull(viewModel.state.value.voidTargetId)
            assertFalse(viewModel.state.value.isVoiding)
            assertTrue(viewModel.state.value.items.isEmpty())
            assertEquals(2, gateway.queries.size)
        }

    @Test
    fun `double tap on confirm voids only once`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val finance = FakeExpenseFinance(stored = listOf(storedExpense()), voidGate = gate)
        val viewModel = loaded(statementPage(items = listOf(expenseItem)), finance)
        viewModel.onIntent(StatementIntent.AskVoid("expense-1"))

        viewModel.onIntent(StatementIntent.ConfirmVoid)
        viewModel.onIntent(StatementIntent.ConfirmVoid)
        assertTrue(viewModel.state.value.isVoiding)
        viewModel.onIntent(StatementIntent.DismissVoid)
        assertEquals("expense-1", viewModel.state.value.voidTargetId)
        viewModel.onIntent(StatementIntent.AskVoid("expense-1"))
        gate.complete(Unit)

        assertEquals(1, finance.voids.size)
    }

    @Test
    fun `version conflict reloads the statement and tells the entry changed`() = runTest(dispatcher) {
        val finance = FakeExpenseFinance(
            stored = listOf(storedExpense()),
            voidResult = SaqzResult.Failure(FinanceError.Conflict),
        )
        val gateway = FakeStatementGateway(
            pages = ArrayDeque(
                listOf(statementPage(items = listOf(expenseItem)), statementPage(items = listOf(expenseItem))),
            ),
        )
        val viewModel = StatementViewModel("group-1", gateway, finance, br.com.saqz.groups.presentation.FakeCashboxGateway())
        viewModel.onIntent(StatementIntent.Retry)
        viewModel.onIntent(StatementIntent.AskVoid("expense-1"))

        viewModel.onIntent(StatementIntent.ConfirmVoid)

        assertEquals(StatementActionError.Conflict, viewModel.state.value.actionError)
        assertNull(viewModel.state.value.voidTargetId)
        assertFalse(viewModel.state.value.isVoiding)
        assertEquals(2, gateway.queries.size)
    }

    @Test
    fun `entry already voided elsewhere counts as a conflict without calling void`() = runTest(dispatcher) {
        val finance = FakeExpenseFinance(stored = listOf(storedExpense(status = ExpenseStatus.Voided)))
        val viewModel = loaded(statementPage(items = listOf(expenseItem)), finance)
        viewModel.onIntent(StatementIntent.AskVoid("expense-1"))

        viewModel.onIntent(StatementIntent.ConfirmVoid)

        assertTrue(finance.voids.isEmpty())
        assertEquals(StatementActionError.Conflict, viewModel.state.value.actionError)
    }

    @Test
    fun `forbidden void tells only admins can change entries and keeps the list`() = runTest(dispatcher) {
        val finance = FakeExpenseFinance(
            stored = listOf(storedExpense()),
            voidResult = SaqzResult.Failure(FinanceError.Forbidden),
        )
        val viewModel = loaded(statementPage(items = listOf(expenseItem)), finance)
        viewModel.onIntent(StatementIntent.AskVoid("expense-1"))

        viewModel.onIntent(StatementIntent.ConfirmVoid)

        assertEquals(StatementActionError.Forbidden, viewModel.state.value.actionError)
        assertEquals(listOf("expense-1"), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun `read failure before voiding reports a generic failure and never voids`() = runTest(dispatcher) {
        val finance = FakeExpenseFinance(stored = listOf(storedExpense()), readFailure = FinanceError.Data(DataError.Unknown))
        val viewModel = loaded(statementPage(items = listOf(expenseItem)), finance)
        viewModel.onIntent(StatementIntent.AskVoid("expense-1"))

        viewModel.onIntent(StatementIntent.ConfirmVoid)

        assertEquals(StatementActionError.Failed, viewModel.state.value.actionError)
        assertTrue(finance.voids.isEmpty())
    }

    @Test
    fun `charge entries and unknown ids cannot be voided`() = runTest(dispatcher) {
        val finance = FakeExpenseFinance(stored = listOf(storedExpense()))
        val viewModel = loaded(statementPage(items = listOf(incomeItem)), finance)

        viewModel.onIntent(StatementIntent.AskVoid("income-1"))
        viewModel.onIntent(StatementIntent.AskVoid("missing"))
        viewModel.onIntent(StatementIntent.ConfirmVoid)

        assertNull(viewModel.state.value.voidTargetId)
        assertTrue(finance.voids.isEmpty())
    }

    @Test
    fun `a statement the role cannot read exposes no entries to act on`() = runTest(dispatcher) {
        val gateway = FakeStatementGateway(results = ArrayDeque(listOf(SaqzResult.Failure(FinanceError.Forbidden))))
        val viewModel = StatementViewModel("group-1", gateway, FakeExpenseFinance(), br.com.saqz.groups.presentation.FakeCashboxGateway())
        viewModel.onIntent(StatementIntent.Retry)

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
        viewModel.onIntent(StatementIntent.Edit("expense-1"))
        viewModel.onIntent(StatementIntent.AskVoid("expense-1"))
        assertNull(viewModel.state.value.voidTargetId)
    }

    @Test
    fun `month summary and the pinned opening balance come with the first page`() = runTest(dispatcher) {
        val gateway = FakeStatementGateway(pages = ArrayDeque(listOf(statementPage(items = listOf(incomeItem)))))
        val cashbox = br.com.saqz.groups.presentation.FakeCashboxGateway(
            openingResult = SaqzResult.Success(
                br.com.saqz.groups.domain.finance.OpeningBalance(25_000L, FinanceVersionToken("\"2\"")),
            ),
        )
        val viewModel = StatementViewModel("group-1", gateway, FakeExpenseFinance(), cashbox)
        viewModel.onIntent(StatementIntent.Retry)

        assertEquals("agosto", viewModel.state.value.summary.monthName)
        assertTrue(viewModel.state.value.openingLoaded)
        assertEquals(25_000L, viewModel.state.value.openingBalanceCents)
    }

    @Test
    fun `editing the opening balance saves with the version and tells the cashbox`() = runTest(dispatcher) {
        val gateway = FakeStatementGateway(pages = ArrayDeque(listOf(statementPage(items = emptyList()), statementPage(items = emptyList()))))
        val cashbox = br.com.saqz.groups.presentation.FakeCashboxGateway(
            openingResult = SaqzResult.Success(
                br.com.saqz.groups.domain.finance.OpeningBalance(25_000L, FinanceVersionToken("\"2\"")),
            ),
        )
        val viewModel = StatementViewModel("group-1", gateway, FakeExpenseFinance(), cashbox)
        viewModel.onIntent(StatementIntent.Retry)

        viewModel.onIntent(StatementIntent.EditOpening)
        assertEquals("250,00", viewModel.state.value.openingInput)
        viewModel.onIntent(StatementIntent.OpeningInputChanged("300"))
        viewModel.onIntent(StatementIntent.SaveOpening)

        assertEquals(listOf(FinanceVersionToken("\"2\"") to 30_000L), cashbox.saves)
        assertFalse(viewModel.state.value.openingSheetOpen)
        assertEquals(StatementEffect.Voided, viewModel.effects.first())
    }

    @Test
    fun `opening balance conflict keeps the sheet with the new value and warns`() = runTest(dispatcher) {
        val gateway = FakeStatementGateway(pages = ArrayDeque(listOf(statementPage(items = emptyList()))))
        val cashbox = br.com.saqz.groups.presentation.FakeCashboxGateway(
            openingResult = SaqzResult.Success(
                br.com.saqz.groups.domain.finance.OpeningBalance(25_000L, FinanceVersionToken("\"2\"")),
            ),
            saveResult = SaqzResult.Failure(FinanceError.Conflict),
        )
        val viewModel = StatementViewModel("group-1", gateway, FakeExpenseFinance(), cashbox)
        viewModel.onIntent(StatementIntent.Retry)
        viewModel.onIntent(StatementIntent.EditOpening)
        cashbox.openingResult = SaqzResult.Success(
            br.com.saqz.groups.domain.finance.OpeningBalance(10_000L, FinanceVersionToken("\"3\"")),
        )

        viewModel.onIntent(StatementIntent.SaveOpening)

        assertTrue(viewModel.state.value.openingConflict)
        assertTrue(viewModel.state.value.openingSheetOpen)
        assertEquals("100,00", viewModel.state.value.openingInput)
    }

    @Test
    fun `income and expense buttons open their own screens`() = runTest(dispatcher) {
        val viewModel = loaded(statementPage(items = emptyList()))

        viewModel.onIntent(StatementIntent.NewExpense)
        assertEquals(StatementEffect.OpenNewEntry("group-1", out = true), viewModel.effects.first())
    }

    private fun loaded(
        page: FinanceStatementPage,
        finance: FakeExpenseFinance = FakeExpenseFinance(),
    ) = StatementViewModel(
        "group-1",
        FakeStatementGateway(pages = ArrayDeque(listOf(page, page))),
        finance,
        br.com.saqz.groups.presentation.FakeCashboxGateway(),
    ).also { it.onIntent(StatementIntent.Retry) }

    private companion object {
        val incomeItem = FinanceStatementItem(
            id = "income-1",
            type = "CHARGE",
            direction = FinanceDirection.In,
            title = "Mensalidade · Bia",
            category = "VENUE",
            paidMethod = br.com.saqz.groups.domain.finance.PaidMethod.Pix,
            occurredAt = "2026-08-04T10:00:00Z",
            amountCents = 8_000L,
        )
        val expenseItem = incomeItem.copy(
            id = "expense-1",
            type = "EXPENSE",
            direction = FinanceDirection.Out,
            title = "Aluguel da quadra",
            category = "QUADRA",
            paidMethod = null,
            amountCents = 32_000L,
        )

        fun statementPage(
            items: List<FinanceStatementItem>,
            offset: Int = 0,
            hasMore: Boolean = false,
            balance: Long = 0,
        ) = FinanceStatementPage(
            month = "2026-08",
            items = items,
            summary = FinanceStatementSummary(80_000L, 32_000L, balance, balance),
            limit = 20,
            offset = offset,
            hasMore = hasMore,
        )
    }
}

private class FakeStatementGateway(
    private val pages: ArrayDeque<FinanceStatementPage>? = null,
    private val deferreds: ArrayDeque<CompletableDeferred<SaqzResult<FinanceStatementPage, FinanceError>>>? = null,
    private val results: ArrayDeque<SaqzResult<FinanceStatementPage, FinanceError>>? = null,
) : FinanceStatementGateway {
    val queries = mutableListOf<FinanceStatementQuery>()

    override suspend fun statement(
        groupId: GroupId,
        query: FinanceStatementQuery,
    ): SaqzResult<FinanceStatementPage, FinanceError> {
        queries += query
        return results?.removeFirstOrNull()
            ?: deferreds?.removeFirstOrNull()?.await()
            ?: pages?.removeFirstOrNull()?.let { SaqzResult.Success(it) }
            ?: SaqzResult.Failure(FinanceError.Data(DataError.Unknown))
    }
}
