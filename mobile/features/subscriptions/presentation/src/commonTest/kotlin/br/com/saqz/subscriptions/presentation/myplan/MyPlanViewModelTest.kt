package br.com.saqz.subscriptions.presentation.myplan

import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.CanceledSubscription
import br.com.saqz.subscriptions.domain.subscription.ChangedPlan
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.PlanCatalogItem
import br.com.saqz.subscriptions.domain.subscription.Receipt
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Comportamentos preservados do Meu plano: leitura, recibos paginados, cancelamento,
 * carregamento e recuperação de erro.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyPlanViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `canceling refreshes the subscription as canceled and closes the sheet`() = runTest {
        val gateway = FakeSubscriptionGateway(
            cancelResult = SaqzResult.Success(
                CanceledSubscription(
                    status = SubscriptionStatus.Canceled,
                    canceledAt = "2026-08-01T00:00:00Z",
                    currentPeriodEnd = "2026-08-30T00:00:00Z",
                ),
            ),
            subscriptionAfterMutation = ACTIVE_SUBSCRIPTION.copy(
                status = SubscriptionStatus.Canceled,
                canceledAt = "2026-08-01T00:00:00Z",
            ),
        )
        val viewModel = MyPlanViewModel(gateway)
        viewModel.onIntent(MyPlanIntent.OpenCancel)

        viewModel.onIntent(MyPlanIntent.ConfirmCancel)

        assertEquals(1, gateway.cancelCalls)
        assertEquals(false, viewModel.state.value.isCancelSheetOpen)
        assertEquals(false, viewModel.state.value.isCanceling)
        assertEquals(MyPlanStatusTone.Canceled, viewModel.state.value.plan?.statusTone)
    }

    @Test
    fun `receipts load with the confirmed date and the formatted value`() = runTest {
        val gateway = FakeSubscriptionGateway(
            receiptsResult = SaqzResult.Success(
                listOf(
                    Receipt(
                        asaasEventId = "evt-1",
                        asaasPaymentId = "pay-1",
                        valueCents = 4990,
                        confirmedAt = "2026-07-01T00:00:00Z",
                        processedAt = "2026-07-01T00:05:00Z",
                    ),
                ),
            ),
        )
        val viewModel = MyPlanViewModel(gateway)

        assertEquals(
            listOf(MyPlanReceiptUi("evt-1", "01/07/2026", "R$ 49,90")),
            viewModel.state.value.receipts,
        )
        assertEquals(listOf(ReceiptRequest(limit = 20, offset = 0)), gateway.receiptRequests)
        assertEquals(false, viewModel.state.value.hasMoreReceipts)

        viewModel.onIntent(MyPlanIntent.OpenReceipts)
        assertEquals(true, viewModel.state.value.isReceiptsSheetOpen)
        viewModel.onIntent(MyPlanIntent.DismissReceipts)
        assertEquals(false, viewModel.state.value.isReceiptsSheetOpen)
    }

    @Test
    fun `load more appends the next full page at the current offset`() = runTest {
        val firstPage = (1..20).map(::testReceipt)
        val secondPage = (21..40).map(::testReceipt)
        val gateway = FakeSubscriptionGateway(
            receiptPages = listOf(SaqzResult.Success(firstPage), SaqzResult.Success(secondPage)),
        )
        val viewModel = MyPlanViewModel(gateway)

        assertEquals(true, viewModel.state.value.hasMoreReceipts)
        viewModel.onIntent(MyPlanIntent.LoadMoreReceipts)

        assertEquals((1..40).map { "evt-$it" }, viewModel.state.value.receipts.map { it.id })
        assertEquals(
            listOf(ReceiptRequest(20, 0), ReceiptRequest(20, 20)),
            gateway.receiptRequests,
        )
        assertEquals(true, viewModel.state.value.hasMoreReceipts)
        assertEquals(false, viewModel.state.value.isLoadingMoreReceipts)
    }

    @Test
    fun `an incomplete receipt page disables loading more`() = runTest {
        val firstPage = (1..20).map(::testReceipt)
        val lastPage = (21..22).map(::testReceipt)
        val gateway = FakeSubscriptionGateway(
            receiptPages = listOf(SaqzResult.Success(firstPage), SaqzResult.Success(lastPage)),
        )
        val viewModel = MyPlanViewModel(gateway)

        viewModel.onIntent(MyPlanIntent.LoadMoreReceipts)

        assertEquals((1..22).map { "evt-$it" }, viewModel.state.value.receipts.map { it.id })
        assertEquals(false, viewModel.state.value.hasMoreReceipts)
        assertEquals(false, viewModel.state.value.isLoadingMoreReceipts)
    }

    @Test
    fun `a load more failure preserves loaded receipts and keeps the initial error empty`() = runTest {
        val firstPage = (1..20).map(::testReceipt)
        val gateway = FakeSubscriptionGateway(
            receiptPages = listOf(
                SaqzResult.Success(firstPage),
                SaqzResult.Failure(SubscriptionError.Conflict),
            ),
        )
        val viewModel = MyPlanViewModel(gateway)

        viewModel.onIntent(MyPlanIntent.LoadMoreReceipts)

        assertEquals((1..20).map { "evt-$it" }, viewModel.state.value.receipts.map { it.id })
        assertNull(viewModel.state.value.receiptsError)
        assertNotNull(viewModel.state.value.loadMoreReceiptsError)
        assertEquals(false, viewModel.state.value.isLoadingMoreReceipts)
        assertEquals(
            listOf(ReceiptRequest(20, 0), ReceiptRequest(20, 20)),
            gateway.receiptRequests,
        )
    }

    @Test
    fun `retrying a load more failure requests the same offset and appends only once`() = runTest {
        val firstPage = (1..20).map(::testReceipt)
        val lastPage = (21..22).map(::testReceipt)
        val gateway = FakeSubscriptionGateway(
            receiptPages = listOf(
                SaqzResult.Success(firstPage),
                SaqzResult.Failure(SubscriptionError.Conflict),
                SaqzResult.Success(lastPage),
            ),
        )
        val viewModel = MyPlanViewModel(gateway)

        viewModel.onIntent(MyPlanIntent.LoadMoreReceipts)
        viewModel.onIntent(MyPlanIntent.RetryLoadMore)

        assertEquals((1..22).map { "evt-$it" }, viewModel.state.value.receipts.map { it.id })
        assertNull(viewModel.state.value.receiptsError)
        assertNull(viewModel.state.value.loadMoreReceiptsError)
        assertEquals(false, viewModel.state.value.hasMoreReceipts)
        assertEquals(
            listOf(ReceiptRequest(20, 0), ReceiptRequest(20, 20), ReceiptRequest(20, 20)),
            gateway.receiptRequests,
        )
    }

    // O backend marca `canceledAt` sem esperar o webhook migrar `status` (achado do Codex
    // no PR #93, confirmado em CancelSubscriptionTest) — a tela não pode confiar cegamente
    // em `status` cru, senão mostra "Ativo" de novo com o cancelar liberado.
    @Test
    fun `a canceled subscription shows canceled status and access-until even before status catches up`() = runTest {
        val gateway = FakeSubscriptionGateway(
            subscriptionResult = SaqzResult.Success(
                ACTIVE_SUBSCRIPTION.copy(status = SubscriptionStatus.Active, canceledAt = "2026-08-01T00:00:00Z"),
            ),
        )
        val viewModel = MyPlanViewModel(gateway)

        val plan = viewModel.state.value.plan
        assertEquals(MyPlanStatusTone.Canceled, plan?.statusTone)
        assertNull(plan?.nextChargeDate)
        assertEquals("30/08/2026", plan?.accessUntilDate)
    }

    @Test
    fun `receipts failure surfaces an error instead of an empty list and retry recovers`() = runTest {
        val gateway = FakeSubscriptionGateway(receiptsResult = SaqzResult.Failure(SubscriptionError.Conflict))
        val viewModel = MyPlanViewModel(gateway)

        assertEquals(emptyList(), viewModel.state.value.receipts)
        assertNotNull(viewModel.state.value.receiptsError)

        gateway.receiptsResult = SaqzResult.Success(
            listOf(
                Receipt(
                    asaasEventId = "evt-1",
                    asaasPaymentId = "pay-1",
                    valueCents = 4990,
                    confirmedAt = "2026-07-01T00:00:00Z",
                    processedAt = "2026-07-01T00:05:00Z",
                ),
            ),
        )
        viewModel.onIntent(MyPlanIntent.RetryReceipts)

        assertNull(viewModel.state.value.receiptsError)
        assertEquals(listOf(MyPlanReceiptUi("evt-1", "01/07/2026", "R$ 49,90")), viewModel.state.value.receipts)
    }

    @Test
    fun `opening change plan emits the navigation effect`() = runTest {
        val viewModel = MyPlanViewModel(FakeSubscriptionGateway())
        viewModel.onIntent(MyPlanIntent.OpenChangePlan)
        assertEquals(MyPlanEffect.OpenChangePlan, viewModel.effects.first())
    }

    @Test
    fun `refresh reloads the current plan after a change`() = runTest {
        val gateway = FakeSubscriptionGateway()
        val viewModel = MyPlanViewModel(gateway)
        assertEquals("Organizador", viewModel.state.value.plan?.name)

        gateway.subscriptionResult = SaqzResult.Success(ACTIVE_SUBSCRIPTION.copy(plan = Plan.Ilimitado))
        viewModel.onIntent(MyPlanIntent.Refresh)

        assertEquals("Ilimitado", viewModel.state.value.plan?.name)
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun `subscription failure surfaces as a load error`() = runTest {
        val gateway = FakeSubscriptionGateway(subscriptionResult = SaqzResult.Failure(SubscriptionError.Conflict))
        val viewModel = MyPlanViewModel(gateway)

        assertEquals(false, viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.plan)
        assertNotNull(viewModel.state.value.loadError)
    }
}

private val ACTIVE_SUBSCRIPTION = MySubscription(
    status = SubscriptionStatus.Active,
    entitled = true,
    plan = Plan.Organizador,
    cycle = SubscriptionCycle.Monthly,
    currentPeriodEnd = "2026-08-30T00:00:00Z",
    usage = SubscriptionUsage(groupsUsed = 2, groupsLimit = 3),
    canceledAt = null,
)

private fun testReceipt(id: Int) = Receipt(
    asaasEventId = "evt-$id",
    asaasPaymentId = null,
    valueCents = 1_990,
    confirmedAt = "2026-07-01T00:00:00Z",
    processedAt = "2026-07-01T00:05:00Z",
)

private data class ReceiptRequest(val limit: Int, val offset: Int)

/**
 * [subscriptionAfterMutation], quando presente, é o que `mySubscription()` passa a
 * devolver depois de um cancelamento bem-sucedido — simula o servidor já ter persistido
 * a mudança para a recarga que o ViewModel dispara em seguida.
 */
private class FakeSubscriptionGateway(
    var subscriptionResult: SaqzResult<MySubscription, SubscriptionError> = SaqzResult.Success(ACTIVE_SUBSCRIPTION),
    var receiptsResult: SaqzResult<List<Receipt>, SubscriptionError> = SaqzResult.Success(emptyList()),
    var receiptPages: List<SaqzResult<List<Receipt>, SubscriptionError>> = emptyList(),
    var cancelResult: SaqzResult<CanceledSubscription, SubscriptionError> = SaqzResult.Failure(SubscriptionError.Conflict),
    var subscriptionAfterMutation: MySubscription? = null,
) : SubscriptionGateway {
    val receiptRequests = mutableListOf<ReceiptRequest>()
    var cancelCalls = 0
    private var receiptPageIndex = 0

    override suspend fun mySubscription(): SaqzResult<MySubscription, SubscriptionError> = subscriptionResult

    override suspend fun listPlans(): SaqzResult<List<PlanCatalogItem>, SubscriptionError> =
        SaqzResult.Success(emptyList())

    override suspend fun changePlan(
        requestId: String,
        targetPlan: Plan,
    ): SaqzResult<ChangedPlan, SubscriptionError> = SaqzResult.Failure(SubscriptionError.Conflict)

    override suspend fun cancel(): SaqzResult<CanceledSubscription, SubscriptionError> {
        cancelCalls++
        subscriptionAfterMutation?.let { subscriptionResult = SaqzResult.Success(it) }
        return cancelResult
    }

    override suspend fun receipts(limit: Int, offset: Int): SaqzResult<List<Receipt>, SubscriptionError> {
        receiptRequests += ReceiptRequest(limit, offset)
        return receiptPages.getOrNull(receiptPageIndex++) ?: receiptsResult
    }
}
