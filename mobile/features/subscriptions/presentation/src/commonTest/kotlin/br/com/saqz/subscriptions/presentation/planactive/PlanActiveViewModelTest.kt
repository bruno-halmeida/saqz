package br.com.saqz.subscriptions.presentation.planactive

import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.CanceledSubscription
import br.com.saqz.subscriptions.domain.subscription.ChangePlanCommand
import br.com.saqz.subscriptions.domain.subscription.ChangePlanResult
import br.com.saqz.subscriptions.domain.subscription.CouponValidation
import br.com.saqz.subscriptions.domain.subscription.CreateSubscriptionCommand
import br.com.saqz.subscriptions.domain.subscription.CreatedSubscription
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.PlanDetails
import br.com.saqz.subscriptions.domain.subscription.Receipt
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.plan_active_error
import br.com.saqz.subscriptions.resources.plan_active_groups_unlimited
import br.com.saqz.subscriptions.resources.plan_active_value_month
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlanActiveViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `subscription loads with the price the plan details carry for its cycle`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway(
            subscriptionResults = listOf(SaqzResult.Success(subscription)),
            plansResult = SaqzResult.Success(listOf(planDetails)),
        )
        val viewModel = PlanActiveViewModel(gateway)

        val state = viewModel.state.value
        assertTrue(!state.isLoading)
        assertNull(state.error)
        assertEquals("Organizador", state.planName)
        assertEquals(UiText.Res(Res.string.plan_active_value_month, listOf(formatBrl(1_791))), state.priceLabel)
        assertEquals("24 de agosto", state.nextBillingLabel)
        assertEquals(UiText.Raw("3"), state.groupsAvailableLabel)
    }

    @Test
    fun `no group limit shows the unlimited label`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway(
            subscriptionResults = listOf(SaqzResult.Success(subscription.copy(usage = SubscriptionUsage(groupsUsed = 10, groupsLimit = null)))),
            plansResult = SaqzResult.Success(listOf(planDetails)),
        )
        val viewModel = PlanActiveViewModel(gateway)

        assertEquals(UiText.Res(Res.string.plan_active_groups_unlimited), viewModel.state.value.groupsAvailableLabel)
    }

    @Test
    fun `a failure to load the subscription surfaces the error state`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway(
            subscriptionResults = listOf(SaqzResult.Failure(SubscriptionError.NotFound)),
        )
        val viewModel = PlanActiveViewModel(gateway)

        val state = viewModel.state.value
        assertTrue(!state.isLoading)
        assertEquals(UiText.Res(Res.string.plan_active_error), state.error)
    }

    @Test
    fun `retrying after a failure loads the subscription that follows`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway(
            subscriptionResults = listOf(SaqzResult.Failure(SubscriptionError.NotFound), SaqzResult.Success(subscription)),
            plansResult = SaqzResult.Success(listOf(planDetails)),
        )
        val viewModel = PlanActiveViewModel(gateway)
        assertEquals(UiText.Res(Res.string.plan_active_error), viewModel.state.value.error)

        viewModel.onIntent(PlanActiveIntent.Retry)

        val state = viewModel.state.value
        assertNull(state.error)
        assertEquals("Organizador", state.planName)
    }

    @Test
    fun `creating a group emits the effect that leads to flow 2`() = runTest(mainDispatcher) {
        val viewModel = PlanActiveViewModel(FakeSubscriptionGateway(subscriptionResults = listOf(SaqzResult.Success(subscription))))

        viewModel.onIntent(PlanActiveIntent.CreateGroup)
        assertEquals(PlanActiveEffect.NavigateToCreateGroup, viewModel.effects.first())
    }

    @Test
    fun `viewing the plan emits the effect that leads to 8e`() = runTest(mainDispatcher) {
        val viewModel = PlanActiveViewModel(FakeSubscriptionGateway(subscriptionResults = listOf(SaqzResult.Success(subscription))))

        viewModel.onIntent(PlanActiveIntent.ViewMyPlan)
        assertEquals(PlanActiveEffect.NavigateToMyPlan, viewModel.effects.first())
    }
}

private val subscription = MySubscription(
    status = SubscriptionStatus.Active,
    plan = Plan.Organizador,
    cycle = SubscriptionCycle.Monthly,
    pendingPlan = null,
    pendingPlanEffectiveAt = null,
    currentPeriodEnd = "2026-08-24T00:00:00Z",
    paymentMethod = BillingType.Pix,
    usage = SubscriptionUsage(groupsUsed = 0, groupsLimit = 3),
    readOnly = false,
    pastDueSince = null,
    canceledAt = null,
)

private val planDetails = PlanDetails(
    id = Plan.Organizador,
    name = "Organizador",
    monthlyPriceCents = 1_791,
    annualPriceCents = 17_910,
    maxGroups = 3,
    maxAthletes = null,
    multiAdmin = true,
    reports = false,
    whatsappSla = false,
)

/** Cada `mySubscription()` consome o próximo resultado da lista; o último se repete. */
private class FakeSubscriptionGateway(
    private val subscriptionResults: List<SaqzResult<MySubscription, SubscriptionError>>,
    private val plansResult: SaqzResult<List<PlanDetails>, SubscriptionError> = SaqzResult.Success(emptyList()),
) : SubscriptionGateway {
    private var call = 0

    override suspend fun plans(): SaqzResult<List<PlanDetails>, SubscriptionError> = plansResult

    override suspend fun validateCoupon(
        code: String,
        planId: Plan,
        cycle: SubscriptionCycle,
    ): SaqzResult<CouponValidation, SubscriptionError> = SaqzResult.Failure(SubscriptionError.CouponNotFound)

    override suspend fun mySubscription(): SaqzResult<MySubscription, SubscriptionError> {
        val result = subscriptionResults[call.coerceAtMost(subscriptionResults.lastIndex)]
        call++
        return result
    }

    override suspend fun create(command: CreateSubscriptionCommand): SaqzResult<CreatedSubscription, SubscriptionError> =
        SaqzResult.Failure(SubscriptionError.Conflict)

    override suspend fun changePlan(command: ChangePlanCommand): SaqzResult<ChangePlanResult, SubscriptionError> =
        SaqzResult.Failure(SubscriptionError.Conflict)

    override suspend fun cancel(): SaqzResult<CanceledSubscription, SubscriptionError> =
        SaqzResult.Failure(SubscriptionError.Conflict)

    override suspend fun receipts(): SaqzResult<List<Receipt>, SubscriptionError> = SaqzResult.Success(emptyList())
}
