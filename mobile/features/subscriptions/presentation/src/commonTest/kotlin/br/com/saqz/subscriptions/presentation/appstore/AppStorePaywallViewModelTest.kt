package br.com.saqz.subscriptions.presentation.appstore

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubmissionError
import br.com.saqz.subscriptions.domain.port.AppStoreProductsResult
import br.com.saqz.subscriptions.domain.port.AppStorePurchaseResult
import br.com.saqz.subscriptions.domain.port.AppStoreTransactionsResult
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppStorePaywallViewModelTest {
    private val port = FakeAppStorePort()
    private val appStore = FakeAppStoreGateway()
    private val catalog = FakeCatalogGateway()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = AppStorePaywallViewModel(
        port = port,
        sync = AppStoreTransactionSync(port, appStore, CoroutineScope(UnconfinedTestDispatcher(testScheduler))),
        appStore = appStore,
        subscriptions = catalog,
    )

    @Test
    fun `ready shows the store price for each cycle of the plan`() = runTest {
        val state = viewModel().state.value

        assertEquals(AppStorePaywallPhase.Ready, state.phase)
        val plan = state.plans.single()
        assertEquals(Plan.Organizador, plan.plan)
        assertEquals("R$ 59,90", plan.offer(SubscriptionCycle.Monthly)?.displayPrice)
        assertEquals("R$ 539,90", plan.offer(SubscriptionCycle.Annual)?.displayPrice)
        assertEquals(listOf(listOf(ORGANIZADOR_MENSAL, ORGANIZADOR_ANUAL)), port.requestedProducts)
    }

    @Test
    fun `purchase carries the account token and confirms with the backend`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(AppStorePaywallIntent.Purchase(ORGANIZADOR_ANUAL))

        assertEquals(listOf(ORGANIZADOR_ANUAL to ACCOUNT_TOKEN), port.purchases)
        assertEquals(listOf("tx-1"), port.finished)
        assertTrue(viewModel.state.value.isSubscribed)
        assertNull(viewModel.state.value.purchasingProductId)
        assertEquals(AppStorePaywallEffect.Subscribed, viewModel.effects.first())
    }

    @Test
    fun `a web subscriber is never offered a second subscription`() = runTest {
        catalog.subscriptionResult = SaqzResult.Success(
            appStoreSubscription().copy(provider = SubscriptionProvider.Asaas, autoRenew = null),
        )

        assertEquals(AppStorePaywallPhase.Unavailable, viewModel().state.value.phase)
        assertTrue(port.requestedProducts.isEmpty())
    }

    @Test
    fun `device without purchases or store without products falls back to the gate`() = runTest {
        port.canMakePayments = false
        assertEquals(AppStorePaywallPhase.Unavailable, viewModel().state.value.phase)

        port.canMakePayments = true
        port.productsResult = AppStoreProductsResult.Loaded(emptyList())
        assertEquals(AppStorePaywallPhase.Unavailable, viewModel().state.value.phase)
    }

    @Test
    fun `catalog or store failure offers retry`() = runTest {
        catalog.plansResult = SaqzResult.Failure(SubscriptionError.Data(DataError.Connectivity))
        val viewModel = viewModel()
        assertEquals(AppStorePaywallPhase.LoadFailed, viewModel.state.value.phase)

        catalog.plansResult = FakeCatalogGateway().plansResult
        viewModel.onIntent(AppStorePaywallIntent.Retry)
        assertEquals(AppStorePaywallPhase.Ready, viewModel.state.value.phase)
    }

    @Test
    fun `current app store plan is marked and cannot be bought again`() = runTest {
        catalog.subscriptionResult = SaqzResult.Success(appStoreSubscription())
        val viewModel = viewModel()

        val monthly = viewModel.state.value.plans.single().offer(SubscriptionCycle.Monthly)
        assertEquals(true, monthly?.isCurrent)
        assertEquals(false, viewModel.state.value.plans.single().offer(SubscriptionCycle.Annual)?.isCurrent)

        viewModel.onIntent(AppStorePaywallIntent.Purchase(ORGANIZADOR_MENSAL))
        assertTrue(port.purchases.isEmpty())
    }

    @Test
    fun `cancelled purchase leaves the paywall quietly`() = runTest {
        port.purchaseResult = AppStorePurchaseResult.Cancelled
        val viewModel = viewModel()

        viewModel.onIntent(AppStorePaywallIntent.Purchase(ORGANIZADOR_MENSAL))

        assertNull(viewModel.state.value.notice)
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `ask to buy shows the pending notice`() = runTest {
        port.purchaseResult = AppStorePurchaseResult.Pending
        val viewModel = viewModel()

        viewModel.onIntent(AppStorePaywallIntent.Purchase(ORGANIZADOR_MENSAL))

        assertEquals(AppStorePaywallNotice.Pending, viewModel.state.value.notice)
    }

    @Test
    fun `subscription owned by another account is explained`() = runTest {
        appStore.defaultSubmission = SaqzResult.Failure(AppStoreSubmissionError.OwnedByAnotherAccount)
        val viewModel = viewModel()

        viewModel.onIntent(AppStorePaywallIntent.Purchase(ORGANIZADOR_MENSAL))

        assertEquals(AppStorePaywallNotice.OwnedByAnotherAccount, viewModel.state.value.notice)
        assertFalse(viewModel.state.value.isSubscribed)
    }

    @Test
    fun `backend outage keeps the purchase pending and retry confirms it`() = runTest {
        appStore.defaultSubmission = SaqzResult.Failure(AppStoreSubmissionError.Data(DataError.Server))
        val viewModel = viewModel()

        viewModel.onIntent(AppStorePaywallIntent.Purchase(ORGANIZADOR_MENSAL))
        assertEquals(AppStorePaywallNotice.ConfirmationPending, viewModel.state.value.notice)
        assertTrue(port.finished.isEmpty())

        appStore.defaultSubmission = SaqzResult.Success(appStoreSubscription())
        port.unfinished = AppStoreTransactionsResult.Loaded(listOf(transaction("tx-1")))
        viewModel.onIntent(AppStorePaywallIntent.RetryConfirmation)

        assertTrue(viewModel.state.value.isSubscribed)
        assertEquals(listOf("tx-1"), port.finished)
    }

    @Test
    fun `restore with an active subscription subscribes and without one says so`() = runTest {
        port.restored = AppStoreTransactionsResult.Loaded(listOf(transaction("old")))
        appStore.defaultSubmission = SaqzResult.Success(appStoreSubscription(entitled = false))
        val viewModel = viewModel()

        viewModel.onIntent(AppStorePaywallIntent.Restore)
        assertEquals(AppStorePaywallNotice.NothingToRestore, viewModel.state.value.notice)

        appStore.defaultSubmission = SaqzResult.Success(appStoreSubscription())
        viewModel.onIntent(AppStorePaywallIntent.Restore)
        assertTrue(viewModel.state.value.isSubscribed)
    }

    @Test
    fun `purchase approved later in the background subscribes`() = runTest {
        port.purchaseResult = AppStorePurchaseResult.Pending
        val sync = AppStoreTransactionSync(port, appStore, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val viewModel = AppStorePaywallViewModel(port, sync, appStore, catalog)
        sync.start()
        sync.onAuthenticated()

        viewModel.onIntent(AppStorePaywallIntent.Purchase(ORGANIZADOR_MENSAL))
        checkNotNull(port.listener).onAppStoreTransactionUpdate(transaction("approved"))

        assertTrue(viewModel.state.value.isSubscribed)
        assertEquals(AppStorePaywallEffect.Subscribed, viewModel.effects.first())
    }
}
