package br.com.saqz.subscriptions.presentation.myplan

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.CanceledSubscription
import br.com.saqz.subscriptions.domain.subscription.Courtesy
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.myplan_courtesy_no_end
import br.com.saqz.subscriptions.resources.myplan_courtesy_subscription_note
import br.com.saqz.subscriptions.resources.myplan_courtesy_title
import br.com.saqz.subscriptions.resources.myplan_courtesy_until
import br.com.saqz.subscriptions.domain.subscription.ChangedPlan
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.ReactivatedSubscription
import br.com.saqz.subscriptions.domain.subscription.PlanCatalogItem
import br.com.saqz.subscriptions.domain.subscription.Receipt
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialError
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus
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

    private val canceledWithAccess = ACTIVE_SUBSCRIPTION.copy(canceledAt = "2026-08-01T00:00:00Z", entitled = true)

    @Test
    fun `reactivate is offered only on a canceled plan that still gives access`() = runTest {
        assertEquals(true, MyPlanViewModel(FakeSubscriptionGateway(subscriptionResult = SaqzResult.Success(canceledWithAccess))).state.value.canReactivate)
        assertEquals(false, MyPlanViewModel(FakeSubscriptionGateway()).state.value.canReactivate)
        val ended = canceledWithAccess.copy(status = SubscriptionStatus.Canceled, entitled = false)
        assertEquals(false, MyPlanViewModel(FakeSubscriptionGateway(subscriptionResult = SaqzResult.Success(ended))).state.value.canReactivate)
        val pastDue = ACTIVE_SUBSCRIPTION.copy(status = SubscriptionStatus.PastDue, entitled = true)
        assertEquals(false, MyPlanViewModel(FakeSubscriptionGateway(subscriptionResult = SaqzResult.Success(pastDue))).state.value.canReactivate)
    }

    @Test
    fun `reactivating a web plan calls the backend and reloads into active with the next charge`() = runTest {
        val gateway = FakeSubscriptionGateway(
            subscriptionResult = SaqzResult.Success(canceledWithAccess),
            subscriptionAfterReactivate = ACTIVE_SUBSCRIPTION,
        )
        val viewModel = MyPlanViewModel(gateway)

        viewModel.onIntent(MyPlanIntent.Reactivate)

        assertEquals(1, gateway.reactivateCalls)
        val state = viewModel.state.value
        assertEquals(false, state.canReactivate)
        assertEquals(false, state.isReactivating)
        assertEquals(MyPlanStatusTone.Active, state.plan?.statusTone)
        assertEquals("30/08/2026", state.plan?.nextChargeDate)
        assertNull(state.plan?.accessUntilDate)
    }

    @Test
    fun `reactivating a store plan opens the store management and never calls the backend`() = runTest {
        listOf(SubscriptionProvider.AppStore, SubscriptionProvider.GooglePlay).forEach { provider ->
            val gateway = FakeSubscriptionGateway(
                subscriptionResult = SaqzResult.Success(canceledWithAccess.copy(provider = provider, autoRenew = false)),
            )
            val viewModel = MyPlanViewModel(gateway)

            viewModel.onIntent(MyPlanIntent.Reactivate)

            assertEquals(MyPlanEffect.ManageStoreSubscription, viewModel.effects.first(), "provider $provider")
            assertEquals(0, gateway.reactivateCalls)
        }
    }

    @Test
    fun `a reactivation that fails keeps the plan and shows the error and a retry can succeed`() = runTest {
        val gateway = FakeSubscriptionGateway(
            subscriptionResult = SaqzResult.Success(canceledWithAccess),
            reactivateResult = SaqzResult.Failure(SubscriptionError.Data(DataError.Connectivity)),
            subscriptionAfterReactivate = ACTIVE_SUBSCRIPTION,
        )
        val viewModel = MyPlanViewModel(gateway)

        viewModel.onIntent(MyPlanIntent.Reactivate)

        assertEquals(true, viewModel.state.value.canReactivate)
        assertEquals(false, viewModel.state.value.isReactivating)
        assertNotNull(viewModel.state.value.reactivateError)
        gateway.reactivateResult = SaqzResult.Success(ReactivatedSubscription(SubscriptionStatus.Active, "2026-08-30T00:00:00Z"))
        viewModel.onIntent(MyPlanIntent.Reactivate)
        assertNull(viewModel.state.value.reactivateError)
        assertEquals(MyPlanStatusTone.Active, viewModel.state.value.plan?.statusTone)
    }

    @Test
    fun `a conflict on reactivate resyncs with the server instead of showing an error`() = runTest {
        val gateway = FakeSubscriptionGateway(
            subscriptionResult = SaqzResult.Success(canceledWithAccess),
            reactivateResult = SaqzResult.Failure(SubscriptionError.Conflict),
            subscriptionAfterReactivate = null,
        )
        val viewModel = MyPlanViewModel(gateway)
        gateway.subscriptionResult = SaqzResult.Success(ACTIVE_SUBSCRIPTION)

        viewModel.onIntent(MyPlanIntent.Reactivate)

        assertNull(viewModel.state.value.reactivateError)
        assertEquals(MyPlanStatusTone.Active, viewModel.state.value.plan?.statusTone)
    }

    @Test
    fun `reactivate without a canceled plan does nothing`() = runTest {
        val gateway = FakeSubscriptionGateway()
        val viewModel = MyPlanViewModel(gateway)

        viewModel.onIntent(MyPlanIntent.Reactivate)

        assertEquals(0, gateway.reactivateCalls)
    }

    @Test
    fun `an ended subscription without access offers to subscribe again on every store`() = runTest {
        // A conta da revisão da Apple: assinatura da App Store vencida sem renovar (CANCELED) e sem direito.
        listOf(SubscriptionProvider.AppStore, SubscriptionProvider.GooglePlay, SubscriptionProvider.Asaas).forEach { provider ->
            val gateway = FakeSubscriptionGateway(
                subscriptionResult = SaqzResult.Success(
                    ACTIVE_SUBSCRIPTION.copy(status = SubscriptionStatus.Canceled, entitled = false, provider = provider),
                ),
            )
            val viewModel = MyPlanViewModel(gateway)

            assertEquals(true, viewModel.state.value.canResubscribe, "provider $provider")
            assertEquals(MyPlanStatusTone.Canceled, viewModel.state.value.plan?.statusTone)
            viewModel.onIntent(MyPlanIntent.OpenSubscribe)
            assertEquals(MyPlanEffect.OpenSubscribe, viewModel.effects.first())
        }
    }

    @Test
    fun `a canceled plan still inside the paid period and a past due plan do not offer a second subscription`() = runTest {
        val stillPaid = MyPlanViewModel(
            FakeSubscriptionGateway(
                subscriptionResult = SaqzResult.Success(
                    ACTIVE_SUBSCRIPTION.copy(canceledAt = "2026-08-01T00:00:00Z", entitled = true),
                ),
            ),
        )
        val pastDue = MyPlanViewModel(
            FakeSubscriptionGateway(
                subscriptionResult = SaqzResult.Success(
                    ACTIVE_SUBSCRIPTION.copy(status = SubscriptionStatus.PastDue, entitled = false),
                ),
            ),
        )

        assertEquals(false, stillPaid.state.value.canResubscribe)
        assertEquals(false, pastDue.state.value.canResubscribe)
        assertEquals(false, MyPlanViewModel(FakeSubscriptionGateway()).state.value.canResubscribe)
    }

    @Test
    fun `a past due web plan carries the overdue invoice and any other plan has none`() = runTest {
        val link = "https://www.asaas.com/i/atrasada"
        fun payUrlOf(subscription: MySubscription) =
            MyPlanViewModel(FakeSubscriptionGateway(subscriptionResult = SaqzResult.Success(subscription))).state.value.payUrl

        assertEquals(link, payUrlOf(ACTIVE_SUBSCRIPTION.copy(status = SubscriptionStatus.PastDue, entitled = true, payUrl = link)))
        assertEquals(link, payUrlOf(ACTIVE_SUBSCRIPTION.copy(status = SubscriptionStatus.PastDue, entitled = false, payUrl = link)))
        assertNull(payUrlOf(ACTIVE_SUBSCRIPTION.copy(status = SubscriptionStatus.PastDue, entitled = true)))
        assertNull(payUrlOf(ACTIVE_SUBSCRIPTION))
    }

    @Test
    fun `a web checkout that was never paid is shown as the trial not as a past due plan`() = runTest {
        val neverPaid = ACTIVE_SUBSCRIPTION.copy(status = SubscriptionStatus.PastDue, entitled = false, paymentConfirmed = false)
        val viewModel = MyPlanViewModel(
            gateway = FakeSubscriptionGateway(subscriptionResult = SaqzResult.Success(neverPaid)),
            trialGateway = ownerTrial(TrialStatus.Expired),
        )

        assertNull(viewModel.state.value.plan)
        assertEquals(true, viewModel.state.value.trial?.canSubscribe)
        assertNull(viewModel.state.value.payUrl)
        assertEquals(false, viewModel.state.value.canResubscribe)
    }

    @Test
    fun `a never paid checkout without a usable trial read keeps the plan it has`() = runTest {
        val neverPaid = ACTIVE_SUBSCRIPTION.copy(status = SubscriptionStatus.PastDue, entitled = false, paymentConfirmed = false)
        val viewModel = MyPlanViewModel(
            gateway = FakeSubscriptionGateway(subscriptionResult = SaqzResult.Success(neverPaid)),
            trialGateway = ownerTrial(TrialStatus.Subscribed),
        )

        assertNotNull(viewModel.state.value.plan)
        assertNull(viewModel.state.value.trial)
    }

    @Test
    fun `a store subscription is never treated as a checkout that was not paid`() = runTest {
        for (provider in listOf(SubscriptionProvider.AppStore, SubscriptionProvider.GooglePlay)) {
            val viewModel = MyPlanViewModel(
                gateway = FakeSubscriptionGateway(
                    subscriptionResult = SaqzResult.Success(ACTIVE_SUBSCRIPTION.copy(provider = provider, paymentConfirmed = false)),
                ),
                trialGateway = ownerTrial(TrialStatus.Expired),
            )
            assertNotNull(viewModel.state.value.plan)
        }
    }

    private fun ownerTrial(status: TrialStatus) = object : TrialGateway {
        override suspend fun ownerTrial() = SaqzResult.Success(
            TrialAccess(
                status = status,
                startedAt = "2026-08-01T10:00:00Z",
                endsAt = "2026-08-15T10:00:00Z",
                serverTime = "2026-08-20T10:00:00Z",
                readOnly = false,
                canCreateGroup = false,
                maxGroups = 1,
                maxAthletes = 25,
                isOwner = true,
                appUrl = null,
            ),
        )
        override suspend fun groupTrial(groupId: br.com.saqz.domain.GroupId) = SaqzResult.Failure(TrialError.NotFound)
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
    fun `an app store subscription is managed at apple and never by the web cancel or change plan`() = runTest {
        val gateway = FakeSubscriptionGateway(
            subscriptionResult = SaqzResult.Success(
                ACTIVE_SUBSCRIPTION.copy(provider = SubscriptionProvider.AppStore, autoRenew = true),
            ),
        )
        val viewModel = MyPlanViewModel(gateway)
        assertEquals(SubscriptionProvider.AppStore, viewModel.state.value.managingStore)

        viewModel.onIntent(MyPlanIntent.OpenCancel)
        viewModel.onIntent(MyPlanIntent.ConfirmCancel)
        viewModel.onIntent(MyPlanIntent.OpenChangePlan)
        viewModel.onIntent(MyPlanIntent.ManageStoreSubscription)

        assertEquals(false, viewModel.state.value.isCancelSheetOpen)
        assertEquals(0, gateway.cancelCalls)
        assertEquals(MyPlanEffect.ManageStoreSubscription, viewModel.effects.first())
    }

    @Test
    fun `a google play subscription is managed at google and never by the web cancel`() = runTest {
        val gateway = FakeSubscriptionGateway(
            subscriptionResult = SaqzResult.Success(
                ACTIVE_SUBSCRIPTION.copy(provider = SubscriptionProvider.GooglePlay, autoRenew = true),
            ),
        )
        val viewModel = MyPlanViewModel(gateway)
        assertEquals(SubscriptionProvider.GooglePlay, viewModel.state.value.managingStore)

        viewModel.onIntent(MyPlanIntent.OpenCancel)
        viewModel.onIntent(MyPlanIntent.ManageStoreSubscription)

        assertEquals(false, viewModel.state.value.isCancelSheetOpen)
        assertEquals(MyPlanEffect.ManageStoreSubscription, viewModel.effects.first())
    }

    @Test
    fun `a web subscription never opens the app store management`() = runTest {
        val viewModel = MyPlanViewModel(FakeSubscriptionGateway())
        assertEquals(false, viewModel.state.value.managedByStore)

        viewModel.onIntent(MyPlanIntent.ManageStoreSubscription)
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

    @Test
    fun `trial account is shown without inventing a paid plan or receipts`() = runTest {
        val trial = TrialAccess(
            status = TrialStatus.Active,
            startedAt = "2026-08-01T10:00:00Z",
            endsAt = "2026-08-15T10:00:00Z",
            serverTime = "2026-08-02T10:00:00Z",
            readOnly = false,
            canCreateGroup = false,
            maxGroups = 1,
            maxAthletes = 25,
            isOwner = true,
            appUrl = null,
            daysLeft = 2,
            usage = br.com.saqz.subscriptions.domain.trial.TrialUsage(3, 12, 45_000),
        )
        val viewModel = MyPlanViewModel(
            gateway = FakeSubscriptionGateway(subscriptionResult = SaqzResult.Failure(SubscriptionError.NotFound)),
            trialGateway = object : TrialGateway {
                override suspend fun ownerTrial() = SaqzResult.Success(trial)
                override suspend fun groupTrial(groupId: br.com.saqz.domain.GroupId) = SaqzResult.Failure(TrialError.NotFound)
            },
        )

        assertNull(viewModel.state.value.plan)
        assertEquals(trial.endsAt, viewModel.state.value.trial?.endsAt)
        assertEquals(true, viewModel.state.value.trial?.canSubscribe)
        assertEquals(2, viewModel.state.value.trial?.daysLeft)
        assertEquals(br.com.saqz.subscriptions.domain.trial.TrialUsage(3, 12, 45_000), viewModel.state.value.trial?.usage)
        assertEquals(emptyList(), viewModel.state.value.receipts)
    }

    @Test
    fun `trial never invokes paid management even through stale intents`() = runTest {
        val gateway = FakeSubscriptionGateway(subscriptionResult = SaqzResult.Failure(SubscriptionError.NotFound))
        val viewModel = MyPlanViewModel(gateway, trialGateway = trialGateway(TrialStatus.Expired))
        listOf(MyPlanIntent.OpenCancel, MyPlanIntent.ConfirmCancel, MyPlanIntent.OpenReceipts,
            MyPlanIntent.RetryReceipts, MyPlanIntent.LoadMoreReceipts, MyPlanIntent.RetryLoadMore,
            MyPlanIntent.OpenChangePlan).forEach(viewModel::onIntent)
        assertEquals(false, viewModel.state.value.isCancelSheetOpen)
        assertEquals(false, viewModel.state.value.isReceiptsSheetOpen)
        assertEquals(0, gateway.cancelCalls)
        assertEquals(emptyList(), gateway.receiptRequests)
        viewModel.onIntent(MyPlanIntent.OpenSubscribe)
        assertEquals(MyPlanEffect.OpenSubscribe, viewModel.effects.first())
    }

    @Test
    fun `trial cannot mask network authorization or conflicting subscription failures`() = runTest {
        for (error in listOf(SubscriptionError.Conflict,
            SubscriptionError.Data(br.com.saqz.domain.DataError.Connectivity),
            SubscriptionError.Data(br.com.saqz.domain.DataError.Unauthenticated))) {
            val gateway = FakeSubscriptionGateway(subscriptionResult = SaqzResult.Failure(error))
            val viewModel = MyPlanViewModel(gateway, trialGateway = trialGateway(TrialStatus.Active))
            assertNotNull(viewModel.state.value.loadError)
            assertNull(viewModel.state.value.trial)
            assertEquals(emptyList(), gateway.receiptRequests)
        }
    }

    @Test
    fun `subscribed read model cannot substitute a missing paid subscription`() = runTest {
        val gateway = FakeSubscriptionGateway(subscriptionResult = SaqzResult.Failure(SubscriptionError.NotFound))
        val viewModel = MyPlanViewModel(gateway, trialGateway = trialGateway(TrialStatus.Subscribed))
        assertNotNull(viewModel.state.value.loadError)
        assertNull(viewModel.state.value.trial)
    }

    @Test
    fun `confirmed payment refresh replaces expired trial with real plan and receipts`() = runTest {
        val gateway = FakeSubscriptionGateway(subscriptionResult = SaqzResult.Failure(SubscriptionError.NotFound))
        val viewModel = MyPlanViewModel(gateway, trialGateway = trialGateway(TrialStatus.Expired))
        assertEquals(TrialStatus.Expired, viewModel.state.value.trial?.status)
        gateway.subscriptionResult = SaqzResult.Success(ACTIVE_SUBSCRIPTION)
        viewModel.onIntent(MyPlanIntent.Refresh)
        assertNull(viewModel.state.value.trial)
        assertEquals(MyPlanStatusTone.Active, viewModel.state.value.plan?.statusTone)
        assertEquals(listOf(ReceiptRequest(20, 0)), gateway.receiptRequests)
    }

    // DEB-097: cortesia de plano.
    @Test
    fun `courtesy only with an end shows the plan card without billing and no receipts or management`() = runTest {
        val gateway = FakeSubscriptionGateway(
            subscriptionResult = SaqzResult.Failure(SubscriptionError.NotFound),
            courtesyResult = SaqzResult.Success(Courtesy(Plan.Ilimitado, "2026-12-31T00:00:00Z")),
        )
        val viewModel = MyPlanViewModel(gateway, trialGateway = trialGateway(TrialStatus.Subscribed))

        val state = viewModel.state.value
        assertNull(state.loadError)
        assertEquals(true, state.courtesyOnly)
        assertEquals(UiText.Res(Res.string.myplan_courtesy_title, listOf("Ilimitado")), state.plan?.titleOverride)
        assertEquals(UiText.Res(Res.string.myplan_courtesy_until, listOf("31/12/2026")), state.plan?.courtesyNote)
        assertNull(state.plan?.nextChargeDate)
        assertNull(state.usage)
        assertEquals(false, state.canResubscribe)
        assertEquals(emptyList(), gateway.receiptRequests)
        viewModel.onIntent(MyPlanIntent.OpenReceipts)
        viewModel.onIntent(MyPlanIntent.OpenCancel)
        assertEquals(false, viewModel.state.value.isReceiptsSheetOpen)
        assertEquals(false, viewModel.state.value.isCancelSheetOpen)
    }

    @Test
    fun `courtesy only without an end says it has no deadline`() = runTest {
        val gateway = FakeSubscriptionGateway(
            subscriptionResult = SaqzResult.Failure(SubscriptionError.NotFound),
            courtesyResult = SaqzResult.Success(Courtesy(Plan.Titular, null)),
        )
        val viewModel = MyPlanViewModel(gateway, trialGateway = trialGateway(TrialStatus.Subscribed))

        assertEquals(UiText.Res(Res.string.myplan_courtesy_no_end), viewModel.state.value.plan?.courtesyNote)
        assertEquals(true, viewModel.state.value.courtesyOnly)
    }

    @Test
    fun `courtesy lookup failure keeps today's flow`() = runTest {
        val gateway = FakeSubscriptionGateway(
            subscriptionResult = SaqzResult.Failure(SubscriptionError.NotFound),
            courtesyResult = SaqzResult.Failure(SubscriptionError.Data(DataError.Server)),
        )
        val withTrial = MyPlanViewModel(gateway, trialGateway = trialGateway(TrialStatus.Expired))
        assertEquals(TrialStatus.Expired, withTrial.state.value.trial?.status)
        assertEquals(false, withTrial.state.value.courtesyOnly)

        val subscribedOnly = MyPlanViewModel(gateway, trialGateway = trialGateway(TrialStatus.Subscribed))
        assertNotNull(subscribedOnly.state.value.loadError)
        assertNull(subscribedOnly.state.value.plan)
    }

    @Test
    fun `smaller paid plan with a bigger courtesy shows the courtesy plan and keeps the paid billing`() = runTest {
        val gateway = FakeSubscriptionGateway(
            subscriptionResult = SaqzResult.Success(
                ACTIVE_SUBSCRIPTION.copy(courtesy = Courtesy(Plan.Ilimitado, null)),
            ),
        )
        val state = MyPlanViewModel(gateway).state.value

        assertEquals("Ilimitado", state.plan?.name)
        assertEquals(UiText.Res(Res.string.myplan_courtesy_no_end), state.plan?.courtesyNote)
        assertEquals(
            UiText.Res(Res.string.myplan_courtesy_subscription_note, listOf("Organizador")),
            state.plan?.subscriptionNote,
        )
        assertEquals("30/08/2026", state.plan?.nextChargeDate)
        assertNull(state.usage)
        assertEquals(false, state.courtesyOnly)
        assertEquals(listOf(ReceiptRequest(20, 0)), gateway.receiptRequests)
    }

    @Test
    fun `bigger paid plan with a smaller courtesy leaves the card as before`() = runTest {
        val gateway = FakeSubscriptionGateway(
            subscriptionResult = SaqzResult.Success(
                ACTIVE_SUBSCRIPTION.copy(courtesy = Courtesy(Plan.Titular, "2026-12-31T00:00:00Z")),
            ),
        )
        val state = MyPlanViewModel(gateway).state.value

        assertEquals("Organizador", state.plan?.name)
        assertNull(state.plan?.courtesyNote)
        assertNull(state.plan?.subscriptionNote)
        assertNotNull(state.usage)
    }

    @Test
    fun `paid plan without access and a courtesy shows the courtesy plan`() = runTest {
        val gateway = FakeSubscriptionGateway(
            subscriptionResult = SaqzResult.Success(
                ACTIVE_SUBSCRIPTION.copy(
                    status = SubscriptionStatus.Canceled,
                    entitled = false,
                    canceledAt = "2026-08-01T00:00:00Z",
                    courtesy = Courtesy(Plan.Titular, "2026-12-31T00:00:00Z"),
                ),
            ),
        )
        val state = MyPlanViewModel(gateway).state.value

        assertEquals("Titular", state.plan?.name)
        assertEquals(UiText.Res(Res.string.myplan_courtesy_until, listOf("31/12/2026")), state.plan?.courtesyNote)
        assertNull(state.usage)
        assertEquals(MyPlanStatusTone.Canceled, state.plan?.statusTone)
    }

    private fun trialGateway(status: TrialStatus) = object : TrialGateway {
        override suspend fun ownerTrial() = SaqzResult.Success(TrialAccess(
            status, "2026-09-01T12:00:00Z", "2026-09-15T12:00:00Z", "2026-09-16T12:00:00Z",
            status == TrialStatus.Expired, false, 1, 25, true, null,
        ))
        override suspend fun groupTrial(groupId: br.com.saqz.domain.GroupId) = SaqzResult.Failure(TrialError.NotFound)
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
    var reactivateResult: SaqzResult<ReactivatedSubscription, SubscriptionError> =
        SaqzResult.Success(ReactivatedSubscription(SubscriptionStatus.Active, "2026-08-30T00:00:00Z")),
    var subscriptionAfterReactivate: MySubscription? = null,
    var courtesyResult: SaqzResult<Courtesy, SubscriptionError> = SaqzResult.Failure(SubscriptionError.NotFound),
) : SubscriptionGateway {
    var reactivateCalls = 0

    val receiptRequests = mutableListOf<ReceiptRequest>()
    var cancelCalls = 0
    private var receiptPageIndex = 0

    override suspend fun mySubscription(): SaqzResult<MySubscription, SubscriptionError> = subscriptionResult

    override suspend fun myCourtesy(): SaqzResult<Courtesy, SubscriptionError> = courtesyResult

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

    override suspend fun reactivate(): SaqzResult<ReactivatedSubscription, SubscriptionError> {
        reactivateCalls++
        if (reactivateResult is SaqzResult.Success) subscriptionAfterReactivate?.let { subscriptionResult = SaqzResult.Success(it) }
        return reactivateResult
    }

    override suspend fun receipts(limit: Int, offset: Int): SaqzResult<List<Receipt>, SubscriptionError> {
        receiptRequests += ReceiptRequest(limit, offset)
        return receiptPages.getOrNull(receiptPageIndex++) ?: receiptsResult
    }
}
