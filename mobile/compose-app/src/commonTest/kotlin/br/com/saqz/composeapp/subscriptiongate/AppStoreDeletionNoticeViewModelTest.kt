package br.com.saqz.composeapp.subscriptiongate

import br.com.saqz.domain.DataError
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
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppStoreDeletionNoticeViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `renewing app store subscription warns before deleting the account`() = runTest {
        val gateway = Gateway(SaqzResult.Success(subscription(SubscriptionProvider.AppStore, autoRenew = true)))
        assertTrue(AppStoreDeletionNoticeViewModel(gateway).state.value.visible)
    }

    @Test
    fun `renewing google play subscription warns and says which store`() = runTest {
        val gateway = Gateway(SaqzResult.Success(subscription(SubscriptionProvider.GooglePlay, autoRenew = true)))
        val state = AppStoreDeletionNoticeViewModel(gateway).state.value
        assertTrue(state.visible)
        kotlin.test.assertEquals(SubscriptionProvider.GooglePlay, state.store)
    }

    @Test
    fun `web subscription or canceled renewal or no subscription show no warning`() = runTest {
        assertFalse(AppStoreDeletionNoticeViewModel(Gateway(SaqzResult.Success(subscription(SubscriptionProvider.Asaas, null)))).state.value.visible)
        assertFalse(AppStoreDeletionNoticeViewModel(Gateway(SaqzResult.Success(subscription(SubscriptionProvider.AppStore, false)))).state.value.visible)
        assertFalse(AppStoreDeletionNoticeViewModel(Gateway(SaqzResult.Failure(SubscriptionError.NotFound))).state.value.visible)
    }

    @Test
    fun `the warning goes away after the renewal is turned off at apple`() = runTest {
        val gateway = Gateway(SaqzResult.Success(subscription(SubscriptionProvider.AppStore, autoRenew = true)))
        val viewModel = AppStoreDeletionNoticeViewModel(gateway)

        gateway.result = SaqzResult.Success(subscription(SubscriptionProvider.AppStore, autoRenew = false))
        viewModel.onIntent(AppStoreDeletionNoticeIntent.Refresh)

        assertFalse(viewModel.state.value.visible)
    }

    private fun subscription(provider: SubscriptionProvider, autoRenew: Boolean?) = MySubscription(
        status = SubscriptionStatus.Active,
        entitled = true,
        plan = Plan.Organizador,
        cycle = SubscriptionCycle.Monthly,
        currentPeriodEnd = "2026-11-05T00:00:00Z",
        usage = SubscriptionUsage(groupsUsed = 1, groupsLimit = 3),
        canceledAt = null,
        provider = provider,
        autoRenew = autoRenew,
    )

    private class Gateway(var result: SaqzResult<MySubscription, SubscriptionError>) : SubscriptionGateway {
        override suspend fun mySubscription() = result
        override suspend fun listPlans(): SaqzResult<List<PlanCatalogItem>, SubscriptionError> =
            SaqzResult.Failure(SubscriptionError.Data(DataError.Unknown))
        override suspend fun changePlan(requestId: String, targetPlan: Plan): SaqzResult<ChangedPlan, SubscriptionError> =
            SaqzResult.Failure(SubscriptionError.Conflict)
        override suspend fun cancel(): SaqzResult<CanceledSubscription, SubscriptionError> =
            SaqzResult.Failure(SubscriptionError.Conflict)
        override suspend fun receipts(limit: Int, offset: Int): SaqzResult<List<Receipt>, SubscriptionError> =
            SaqzResult.Success(emptyList())
    }
}
