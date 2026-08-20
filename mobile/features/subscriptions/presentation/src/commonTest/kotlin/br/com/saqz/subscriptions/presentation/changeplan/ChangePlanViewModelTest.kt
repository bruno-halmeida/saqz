package br.com.saqz.subscriptions.presentation.changeplan

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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePlanViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `catalog marks the current plan`() = runTest {
        val viewModel = ChangePlanViewModel(FakeChangePlanGateway())
        val cards = viewModel.state.value.plans
        assertEquals(listOf(false, true, false), cards.map { it.isCurrent })
        assertEquals(Plan.Organizador, viewModel.state.value.currentPlan)
        assertNull(viewModel.state.value.pendingNote)
    }

    @Test
    fun `selecting the current plan does not open confirm`() = runTest {
        val viewModel = ChangePlanViewModel(FakeChangePlanGateway())
        viewModel.onIntent(ChangePlanIntent.SelectPlan(Plan.Organizador))
        assertNull(viewModel.state.value.confirmTarget)
    }

    @Test
    fun `selecting another plan opens confirm`() = runTest {
        val viewModel = ChangePlanViewModel(FakeChangePlanGateway())
        viewModel.onIntent(ChangePlanIntent.SelectPlan(Plan.Ilimitado))
        assertEquals(Plan.Ilimitado, viewModel.state.value.confirmTarget?.plan)
    }

    @Test
    fun `upgrade pending payment opens pix`() = runTest {
        val gateway = FakeChangePlanGateway(
            changeResult = SaqzResult.Success(
                changed(
                    pendingUpgradePlan = Plan.Ilimitado,
                    chargedCents = 1_500L,
                    pixCopyPaste = "000201PIX",
                ),
            ),
        )
        val viewModel = ChangePlanViewModel(gateway)
        viewModel.onIntent(ChangePlanIntent.SelectPlan(Plan.Ilimitado))
        viewModel.onIntent(ChangePlanIntent.ConfirmChange)

        assertEquals(ChangePlanPhase.Pix, viewModel.state.value.phase)
        assertEquals(Plan.Ilimitado, viewModel.state.value.pix?.targetPlan)
        assertEquals("000201PIX", viewModel.state.value.pix?.copyPaste)
        assertEquals(1, gateway.changeCalls)
        assertEquals(Plan.Ilimitado, gateway.lastTarget)
        assertNotNull(gateway.lastRequestId)
    }

    @Test
    fun `downgrade schedules the target plan`() = runTest {
        val viewModel = ChangePlanViewModel(
            FakeChangePlanGateway(
                changeResult = SaqzResult.Success(
                    changed(
                        pendingPlan = Plan.Titular,
                        pendingPlanEffectiveAt = "2026-08-30T00:00:00Z",
                    ),
                ),
            ),
        )
        viewModel.onIntent(ChangePlanIntent.SelectPlan(Plan.Titular))
        viewModel.onIntent(ChangePlanIntent.ConfirmChange)

        assertEquals(ChangePlanPhase.Scheduled, viewModel.state.value.phase)
        assertNotNull(viewModel.state.value.scheduled)
        assertNotNull(viewModel.state.value.pendingNote)
    }

    @Test
    fun `immediate upgrade marks the new current plan`() = runTest {
        val viewModel = ChangePlanViewModel(
            FakeChangePlanGateway(changeResult = SaqzResult.Success(changed(plan = Plan.Ilimitado))),
        )
        viewModel.onIntent(ChangePlanIntent.SelectPlan(Plan.Ilimitado))
        viewModel.onIntent(ChangePlanIntent.ConfirmChange)

        assertEquals(ChangePlanPhase.Upgraded, viewModel.state.value.phase)
        assertEquals(Plan.Ilimitado, viewModel.state.value.currentPlan)
        assertEquals(listOf(false, false, true), viewModel.state.value.plans.map { it.isCurrent })
    }

    @Test
    fun `downgrade blocked surfaces on the confirm sheet`() = runTest {
        val viewModel = ChangePlanViewModel(
            FakeChangePlanGateway(changeResult = SaqzResult.Failure(SubscriptionError.DowngradeBlocked)),
        )
        viewModel.onIntent(ChangePlanIntent.SelectPlan(Plan.Titular))
        viewModel.onIntent(ChangePlanIntent.ConfirmChange)

        assertEquals(ChangePlanPhase.Catalog, viewModel.state.value.phase)
        assertNotNull(viewModel.state.value.submitError)
        assertEquals(Plan.Titular, viewModel.state.value.confirmTarget?.plan)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `pix paid becomes upgraded when the subscription already changed`() = runTest {
        val gateway = FakeChangePlanGateway(
            changeResult = SaqzResult.Success(
                changed(pendingUpgradePlan = Plan.Ilimitado, chargedCents = 1_500L, pixCopyPaste = "pix"),
            ),
        )
        val viewModel = ChangePlanViewModel(gateway)
        viewModel.onIntent(ChangePlanIntent.SelectPlan(Plan.Ilimitado))
        viewModel.onIntent(ChangePlanIntent.ConfirmChange)

        gateway.subscriptionResult = SaqzResult.Success(ACTIVE.copy(plan = Plan.Ilimitado))
        viewModel.onIntent(ChangePlanIntent.PixPaid)

        assertEquals(ChangePlanPhase.Upgraded, viewModel.state.value.phase)
        assertEquals(Plan.Ilimitado, viewModel.state.value.currentPlan)
        assertNull(viewModel.state.value.pix)
    }

    @Test
    fun `pix paid stays on pix when the charge is still open`() = runTest {
        val viewModel = ChangePlanViewModel(
            FakeChangePlanGateway(
                changeResult = SaqzResult.Success(
                    changed(pendingUpgradePlan = Plan.Ilimitado, chargedCents = 1_500L, pixCopyPaste = "pix"),
                ),
            ),
        )
        viewModel.onIntent(ChangePlanIntent.SelectPlan(Plan.Ilimitado))
        viewModel.onIntent(ChangePlanIntent.ConfirmChange)
        viewModel.onIntent(ChangePlanIntent.PixPaid)

        assertEquals(ChangePlanPhase.Pix, viewModel.state.value.phase)
        assertNotNull(viewModel.state.value.submitError)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `retry recovers from a catalog load error`() = runTest {
        val gateway = FakeChangePlanGateway(subscriptionResult = SaqzResult.Failure(SubscriptionError.Conflict))
        val viewModel = ChangePlanViewModel(gateway)
        assertNotNull(viewModel.state.value.loadError)

        gateway.subscriptionResult = SaqzResult.Success(ACTIVE)
        viewModel.onIntent(ChangePlanIntent.Retry)

        assertNull(viewModel.state.value.loadError)
        assertTrue(viewModel.state.value.plans.any { it.isCurrent })
    }
}

private val ACTIVE = MySubscription(
    status = SubscriptionStatus.Active,
    entitled = true,
    plan = Plan.Organizador,
    cycle = SubscriptionCycle.Monthly,
    currentPeriodEnd = "2026-08-30T00:00:00Z",
    usage = SubscriptionUsage(groupsUsed = 2, groupsLimit = 3),
    canceledAt = null,
)

private val CATALOG = listOf(
    PlanCatalogItem(Plan.Titular, 3_990, 39_900, 1, 25, multiAdmin = false, reports = false, whatsappSla = false),
    PlanCatalogItem(Plan.Organizador, 5_990, 59_900, 3, null, multiAdmin = false, reports = false, whatsappSla = false),
    PlanCatalogItem(Plan.Ilimitado, 8_990, 89_900, null, null, multiAdmin = true, reports = true, whatsappSla = true),
)

private fun changed(
    plan: Plan = Plan.Organizador,
    pendingPlan: Plan? = null,
    pendingPlanEffectiveAt: String? = null,
    pendingUpgradePlan: Plan? = null,
    chargedCents: Long? = null,
    pixCopyPaste: String? = null,
) = ChangedPlan(
    plan = plan,
    pendingPlan = pendingPlan,
    pendingPlanEffectiveAt = pendingPlanEffectiveAt,
    pendingUpgradePlan = pendingUpgradePlan,
    status = SubscriptionStatus.Active,
    chargedCents = chargedCents,
    pixCopyPaste = pixCopyPaste,
    invoiceUrl = null,
    pixQrCodeBase64 = null,
)

private class FakeChangePlanGateway(
    var subscriptionResult: SaqzResult<MySubscription, SubscriptionError> = SaqzResult.Success(ACTIVE),
    var plansResult: SaqzResult<List<PlanCatalogItem>, SubscriptionError> = SaqzResult.Success(CATALOG),
    var changeResult: SaqzResult<ChangedPlan, SubscriptionError> = SaqzResult.Failure(SubscriptionError.Conflict),
) : SubscriptionGateway {
    var changeCalls = 0
    var lastRequestId: String? = null
    var lastTarget: Plan? = null

    override suspend fun mySubscription() = subscriptionResult
    override suspend fun listPlans() = plansResult
    override suspend fun changePlan(requestId: String, targetPlan: Plan): SaqzResult<ChangedPlan, SubscriptionError> {
        changeCalls++
        lastRequestId = requestId
        lastTarget = targetPlan
        return changeResult
    }
    override suspend fun cancel(): SaqzResult<CanceledSubscription, SubscriptionError> =
        SaqzResult.Failure(SubscriptionError.Conflict)
    override suspend fun receipts(limit: Int, offset: Int): SaqzResult<List<Receipt>, SubscriptionError> =
        SaqzResult.Success(emptyList())
}
