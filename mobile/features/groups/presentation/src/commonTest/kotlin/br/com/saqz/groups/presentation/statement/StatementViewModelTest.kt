package br.com.saqz.groups.presentation.statement

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceStatementGateway
import br.com.saqz.groups.domain.finance.FinanceStatementItem
import br.com.saqz.groups.domain.finance.FinanceStatementPage
import br.com.saqz.groups.domain.finance.FinanceStatementQuery
import br.com.saqz.groups.domain.finance.FinanceStatementSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val viewModel = StatementViewModel("group-1", gateway)
        viewModel.onIntent(StatementIntent.Retry)

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(FinanceStatementQuery(limit = 20, offset = 0), gateway.queries.single())
        assertEquals("Quadra · Pix · 04/08/2026", viewModel.state.value.items.single().meta)
        assertEquals("+R$ 80,00", viewModel.state.value.items.single().amountLabel)
        assertEquals(64_000L, viewModel.state.value.summary.periodBalanceCents)
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
        val viewModel = StatementViewModel("group-1", gateway)
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
        val viewModel = StatementViewModel("group-1", gateway)
        viewModel.onIntent(StatementIntent.Retry)

        viewModel.onIntent(StatementIntent.LoadMore)

        assertEquals(listOf("income-1", "expense-1"), viewModel.state.value.items.map { it.id })
        assertEquals(2, viewModel.state.value.nextOffset)
        assertFalse(viewModel.state.value.hasMore)
        assertEquals(1, gateway.queries.last().offset)
    }

    @Test
    fun `stale response cannot replace a newer filter request`() = runTest(dispatcher) {
        val old = CompletableDeferred<SaqzResult<FinanceStatementPage, FinanceError>>()
        val fresh = CompletableDeferred<SaqzResult<FinanceStatementPage, FinanceError>>()
        val gateway = FakeStatementGateway(deferreds = ArrayDeque(listOf(old, fresh)))
        val viewModel = StatementViewModel("group-1", gateway)

        viewModel.onIntent(StatementIntent.Retry)
        viewModel.onIntent(StatementIntent.SelectFilter(StatementFilter.Out))
        fresh.complete(SaqzResult.Success(statementPage(items = listOf(expenseItem))))
        old.complete(SaqzResult.Success(statementPage(items = listOf(incomeItem))))

        assertEquals(listOf("expense-1"), viewModel.state.value.items.map { it.id })
        assertEquals(StatementFilter.Out, viewModel.state.value.filter)
        assertTrue(gateway.queries.last().direction == FinanceDirection.Out)
    }

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
) : FinanceStatementGateway {
    val queries = mutableListOf<FinanceStatementQuery>()

    override suspend fun statement(
        groupId: GroupId,
        query: FinanceStatementQuery,
    ): SaqzResult<FinanceStatementPage, FinanceError> {
        queries += query
        return deferreds?.removeFirstOrNull()?.await()
            ?: pages?.removeFirstOrNull()?.let { SaqzResult.Success(it) }
            ?: SaqzResult.Failure(FinanceError.Data(DataError.Unknown))
    }
}
