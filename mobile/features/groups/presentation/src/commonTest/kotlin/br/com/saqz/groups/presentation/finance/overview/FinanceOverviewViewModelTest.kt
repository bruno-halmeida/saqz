package br.com.saqz.groups.presentation.finance.overview

import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceOverview
import br.com.saqz.groups.domain.finance.FinanceOverviewGateway
import br.com.saqz.groups.domain.finance.FinanceOverviewGroup
import br.com.saqz.groups.domain.finance.FinanceOverviewGroupStatus
import br.com.saqz.groups.domain.finance.FinanceOverviewPeriod
import br.com.saqz.groups.domain.finance.FinanceOverviewQuery
import br.com.saqz.groups.domain.finance.FinanceOverviewTotals
import br.com.saqz.groups.domain.finance.FinanceOverviewTransaction
import br.com.saqz.groups.port.GroupNowPort
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceOverviewViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = GroupNowPort { Instant.parse("2026-07-15T12:00:00Z") }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial load maps totals groups status and signed transactions`() = runTest {
        val gateway = FakeFinanceOverviewGateway(result = SaqzResult.Success(overview()))
        val viewModel = FinanceOverviewViewModel(gateway, now)

        assertEquals(FinanceOverviewQuery(month = "2026-07"), gateway.queries.single())
        assertFalse(viewModel.state.value.isLoading)
        assertEquals("R$ 60,00", viewModel.state.value.balance)
        assertEquals("R$ 120,00", viewModel.state.value.entered)
        assertEquals("R$ 60,00", viewModel.state.value.left)
        assertEquals("R$ 40,00", viewModel.state.value.receivable)
        assertEquals(2, viewModel.state.value.groups.single().pendingMonthlyCount)
        assertTrue(viewModel.state.value.groups.single().hasBillingConfigured)
        assertEquals("+R$ 12,00", viewModel.state.value.recentTransactions.single().amount)
        assertEquals("Grupo · 02/07", viewModel.state.value.recentTransactions.single().groupAndDate)
    }

    @Test
    fun `transaction without direction keeps the amount without a direction sign`() = runTest {
        val transaction = FinanceOverviewTransaction(
            id = "transaction-unknown",
            groupId = "group-1",
            groupName = "Grupo",
            kind = "LAUNCH",
            direction = null,
            memberName = null,
            description = null,
            amountCents = 1_200,
            occurredAt = "2026-07-02T10:00:00Z",
        )
        val viewModel = FinanceOverviewViewModel(
            FakeFinanceOverviewGateway(
                result = SaqzResult.Success(overview(recentTransactions = listOf(transaction))),
            ),
            now,
        )

        assertEquals("R$ 12,00", viewModel.state.value.recentTransactions.single().amount)
    }

    @Test
    fun `period intents map previous month and year to endpoint queries`() = runTest {
        val gateway = FakeFinanceOverviewGateway()
        val viewModel = FinanceOverviewViewModel(gateway, now)

        viewModel.onIntent(FinanceOverviewIntent.SelectPeriod(FinanceOverviewPeriodSelection.PreviousMonth))
        viewModel.onIntent(FinanceOverviewIntent.SelectPeriod(FinanceOverviewPeriodSelection.Year))

        assertEquals(
            listOf(
                FinanceOverviewQuery(month = "2026-07"),
                FinanceOverviewQuery(month = "2026-06"),
                FinanceOverviewQuery(year = 2026),
            ),
            gateway.queries,
        )
        assertEquals(FinanceOverviewPeriodSelection.Year, viewModel.state.value.selectedPeriod)
    }

    @Test
    fun `refresh recomputes period options after the current month changes`() = runTest {
        var currentNow = Instant.parse("2026-07-31T12:00:00Z")
        val rollingNow = GroupNowPort { currentNow }
        val gateway = FakeFinanceOverviewGateway()
        val viewModel = FinanceOverviewViewModel(gateway, rollingNow)

        currentNow = Instant.parse("2026-08-01T12:00:00Z")
        viewModel.onIntent(FinanceOverviewIntent.Retry)

        assertEquals(
            listOf(
                FinanceOverviewQuery(month = "2026-07"),
                FinanceOverviewQuery(month = "2026-08"),
            ),
            gateway.queries,
        )
        assertEquals(
            FinanceOverviewQuery(month = "2026-08"),
            viewModel.state.value.periods.first {
                it.selection == FinanceOverviewPeriodSelection.CurrentMonth
            }.query,
        )
        assertEquals(
            FinanceOverviewQuery(month = "2026-07"),
            viewModel.state.value.periods.first {
                it.selection == FinanceOverviewPeriodSelection.PreviousMonth
            }.query,
        )
    }

    @Test
    fun `empty overview becomes the no-administered-group state`() = runTest {
        val viewModel = FinanceOverviewViewModel(
            FakeFinanceOverviewGateway(
                result = SaqzResult.Success(overview(groups = emptyList(), recentTransactions = emptyList())),
            ),
            now,
        )

        assertTrue(viewModel.state.value.isEmpty)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `opening a group emits its route id`() = runTest {
        val viewModel = FinanceOverviewViewModel(FakeFinanceOverviewGateway(), now)

        viewModel.onIntent(FinanceOverviewIntent.OpenGroup("group-2"))

        assertEquals(FinanceOverviewEffect.OpenGroup("group-2"), viewModel.effects.first())
    }

    @Test
    fun `older response cannot replace the selected newer period`() = runTest {
        val first = CompletableDeferred<SaqzResult<FinanceOverview, FinanceError>>()
        val second = CompletableDeferred<SaqzResult<FinanceOverview, FinanceError>>()
        val viewModel = FinanceOverviewViewModel(
            DeferredFinanceOverviewGateway(first, second),
            now,
        )

        viewModel.onIntent(FinanceOverviewIntent.SelectPeriod(FinanceOverviewPeriodSelection.PreviousMonth))
        first.complete(SaqzResult.Success(overview(balanceCents = 1_00)))

        assertTrue(viewModel.state.value.isLoading)
        assertEquals(FinanceOverviewPeriodSelection.PreviousMonth, viewModel.state.value.selectedPeriod)

        second.complete(SaqzResult.Success(overview(balanceCents = 2_00)))
        assertEquals("R$ 2,00", viewModel.state.value.balance)
        assertFalse(viewModel.state.value.isLoading)
    }

    private fun overview(
        balanceCents: Long = 6_000,
        groups: List<FinanceOverviewGroup> = listOf(
            FinanceOverviewGroup(
                id = "group-1",
                name = "Grupo",
                balanceCents = 12_000,
                status = FinanceOverviewGroupStatus(
                    pendingMonthlyCount = 2,
                    hasBillingConfigured = true,
                ),
            ),
        ),
        recentTransactions: List<FinanceOverviewTransaction> = listOf(
            FinanceOverviewTransaction(
                id = "transaction-1",
                groupId = "group-1",
                groupName = "Grupo",
                kind = "LAUNCH",
                direction = FinanceDirection.In,
                memberName = null,
                description = "Racha",
                amountCents = 1_200,
                occurredAt = "2026-07-02T10:00:00Z",
            ),
        ),
    ) = FinanceOverview(
        period = FinanceOverviewPeriod(month = "2026-07", year = null),
        totals = FinanceOverviewTotals(
            balanceCents = balanceCents,
            inCents = 12_000,
            outCents = 6_000,
            pendingCents = 4_000,
        ),
        groups = groups,
        recentTransactions = recentTransactions,
    )

    private class FakeFinanceOverviewGateway(
        private val result: SaqzResult<FinanceOverview, FinanceError> = SaqzResult.Success(
            FinanceOverview(
                period = FinanceOverviewPeriod(null, 2026),
                totals = FinanceOverviewTotals(0, 0, 0, 0),
                groups = emptyList(),
                recentTransactions = emptyList(),
            ),
        ),
    ) : FinanceOverviewGateway {
        val queries = mutableListOf<FinanceOverviewQuery>()

        override suspend fun overview(query: FinanceOverviewQuery): SaqzResult<FinanceOverview, FinanceError> {
            queries += query
            return result
        }
    }

    private class DeferredFinanceOverviewGateway(
        private vararg val responses: CompletableDeferred<SaqzResult<FinanceOverview, FinanceError>>,
    ) : FinanceOverviewGateway {
        private var index = 0

        override suspend fun overview(query: FinanceOverviewQuery): SaqzResult<FinanceOverview, FinanceError> =
            responses[index++].await()
    }
}
