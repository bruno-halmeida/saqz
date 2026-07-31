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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.getString
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
    fun `subscription loads with the price the most recent receipt reflects coupon included`() = runTest(mainDispatcher) {
        // O recibo mais antigo tem o preço de catálogo; o mais recente, com cupom, é menor.
        // Se a tela usasse `gateway.plans()` (catálogo) o teste pegaria 1_791, não 1_592.
        val gateway = FakeSubscriptionGateway(
            subscriptionResults = listOf(SaqzResult.Success(subscription)),
            receiptsResult = SaqzResult.Success(
                listOf(
                    receipt.copy(processedAt = "2026-07-01T00:00:00Z", valueCents = 1_791),
                    receipt.copy(processedAt = "2026-07-24T00:00:00Z", valueCents = 1_592),
                ),
            ),
        )
        val viewModel = PlanActiveViewModel(gateway)

        val state = viewModel.state.value
        assertTrue(!state.isLoading)
        assertNull(state.error)
        assertEquals("Organizador", state.planName)
        assertEquals(getString(Res.string.plan_active_value_month, formatBrl(1_592)), state.priceLabel)
        assertEquals("24 de agosto", state.nextBillingLabel)
        assertEquals("3", state.groupsAvailableLabel)
    }

    @Test
    fun `no group limit shows the unlimited label`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway(
            subscriptionResults = listOf(SaqzResult.Success(subscription.copy(usage = SubscriptionUsage(groupsUsed = 10, groupsLimit = null)))),
        )
        val viewModel = PlanActiveViewModel(gateway)

        assertEquals(getString(Res.string.plan_active_groups_unlimited), viewModel.state.value.groupsAvailableLabel)
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
    fun `a failure to load receipts surfaces the error state instead of a blank price`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway(
            subscriptionResults = listOf(SaqzResult.Success(subscription)),
            receiptsResult = SaqzResult.Failure(SubscriptionError.NotFound),
        )
        val viewModel = PlanActiveViewModel(gateway)

        val state = viewModel.state.value
        assertTrue(!state.isLoading)
        assertEquals(UiText.Res(Res.string.plan_active_error), state.error)
        assertEquals("", state.priceLabel)
    }

    @Test
    fun `no receipts yet surfaces the error state instead of a blank price`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway(
            subscriptionResults = listOf(SaqzResult.Success(subscription)),
            receiptsResult = SaqzResult.Success(emptyList()),
        )
        val viewModel = PlanActiveViewModel(gateway)

        assertEquals(UiText.Res(Res.string.plan_active_error), viewModel.state.value.error)
    }

    @Test
    fun `retrying after a failure loads the subscription that follows`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway(
            subscriptionResults = listOf(SaqzResult.Failure(SubscriptionError.NotFound), SaqzResult.Success(subscription)),
        )
        val viewModel = PlanActiveViewModel(gateway)
        assertEquals(UiText.Res(Res.string.plan_active_error), viewModel.state.value.error)

        viewModel.onIntent(PlanActiveIntent.Retry)

        val state = viewModel.state.value
        assertNull(state.error)
        assertEquals("Organizador", state.planName)
    }

    @Test
    fun `a second retry that answers first is not overwritten by the slower first load`() = runTest(mainDispatcher) {
        // Guarda de geração (AGENTS.md): o load do init demora mais que o do retry disparado
        // logo em seguida. Sem a guarda, a resposta do init (mais velha) sobrescreveria a do
        // retry (mais nova) ao chegar depois.
        val gateway = FakeSubscriptionGateway(
            subscriptionResults = listOf(
                SaqzResult.Success(subscription.copy(plan = Plan.Titular)),
                SaqzResult.Success(subscription.copy(plan = Plan.Ilimitado)),
            ),
            subscriptionDelaysMillis = listOf(1_000L, 100L),
        )
        val viewModel = PlanActiveViewModel(gateway)

        viewModel.onIntent(PlanActiveIntent.Retry)
        advanceUntilIdle()

        assertEquals("Ilimitado", viewModel.state.value.planName)
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

private val receipt = Receipt(
    asaasEventId = "evt-1",
    asaasPaymentId = "pay-1",
    valueCents = 1_791,
    confirmedAt = "2026-07-24T00:00:00Z",
    processedAt = "2026-07-24T00:00:00Z",
)

/** Cada `mySubscription()` consome o próximo resultado da lista; o último se repete. */
private class FakeSubscriptionGateway(
    private val subscriptionResults: List<SaqzResult<MySubscription, SubscriptionError>>,
    private val subscriptionDelaysMillis: List<Long> = emptyList(),
    private val receiptsResult: SaqzResult<List<Receipt>, SubscriptionError> = SaqzResult.Success(listOf(receipt)),
) : SubscriptionGateway {
    private var call = 0

    override suspend fun plans(): SaqzResult<List<PlanDetails>, SubscriptionError> = SaqzResult.Success(emptyList())

    override suspend fun validateCoupon(
        code: String,
        planId: Plan,
        cycle: SubscriptionCycle,
    ): SaqzResult<CouponValidation, SubscriptionError> = SaqzResult.Failure(SubscriptionError.CouponNotFound)

    override suspend fun mySubscription(): SaqzResult<MySubscription, SubscriptionError> {
        val index = call.coerceAtMost(subscriptionResults.lastIndex)
        val delayMillis = subscriptionDelaysMillis.getOrElse(index) { 0L }
        call++
        if (delayMillis > 0) delay(delayMillis)
        return subscriptionResults[index]
    }

    override suspend fun create(command: CreateSubscriptionCommand): SaqzResult<CreatedSubscription, SubscriptionError> =
        SaqzResult.Failure(SubscriptionError.Conflict)

    override suspend fun changePlan(command: ChangePlanCommand): SaqzResult<ChangePlanResult, SubscriptionError> =
        SaqzResult.Failure(SubscriptionError.Conflict)

    override suspend fun cancel(): SaqzResult<CanceledSubscription, SubscriptionError> =
        SaqzResult.Failure(SubscriptionError.Conflict)

    override suspend fun receipts(limit: Int, offset: Int): SaqzResult<List<Receipt>, SubscriptionError> = receiptsResult
}
