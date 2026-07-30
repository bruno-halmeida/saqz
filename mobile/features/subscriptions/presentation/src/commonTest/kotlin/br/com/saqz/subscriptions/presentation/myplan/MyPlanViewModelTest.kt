package br.com.saqz.subscriptions.presentation.myplan

import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.ChangePlanCommand
import br.com.saqz.subscriptions.domain.subscription.ChangePlanResult
import br.com.saqz.subscriptions.domain.subscription.CanceledSubscription
import br.com.saqz.subscriptions.domain.subscription.CreateSubscriptionCommand
import br.com.saqz.subscriptions.domain.subscription.CreatedSubscription
import br.com.saqz.subscriptions.domain.subscription.CouponValidation
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
import br.com.saqz.subscriptions.resources.myplan_downgrade_blocked
import br.com.saqz.subscriptions.resources.myplan_pending_change
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
 * Os cinco comportamentos que o VUL-112 promete: troca recusada por limite, downgrade
 * agendado, upgrade com cobrança pendente, cancelamento e listagem de recibos.
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
    fun `downgrade is refused with the current usage and the target plan limit`() = runTest {
        val gateway = FakeSubscriptionGateway(
            subscriptionResult = SaqzResult.Success(
                ACTIVE_SUBSCRIPTION.copy(usage = SubscriptionUsage(groupsUsed = 3, groupsLimit = 3)),
            ),
            changePlanResult = SaqzResult.Failure(SubscriptionError.DowngradeBlocked),
        )
        val viewModel = MyPlanViewModel(gateway)

        viewModel.onIntent(MyPlanIntent.SelectPlan(Plan.Titular))

        assertEquals(
            UiText.Res(Res.string.myplan_downgrade_blocked, listOf(3, "Amador", 1)),
            viewModel.state.value.changeError,
        )
        assertEquals(false, viewModel.state.value.isChangingPlan)
        assertEquals(Plan.Titular, gateway.changePlanCommands.single().targetPlanId)
    }

    @Test
    fun `scheduled downgrade shows the target plan name and the effective date`() = runTest {
        val gateway = FakeSubscriptionGateway(
            changePlanResult = SaqzResult.Success(
                ChangePlanResult(
                    planId = Plan.Organizador,
                    pendingPlanId = Plan.Titular,
                    pendingPlanEffectiveAt = "2026-09-01T00:00:00Z",
                    pendingUpgradePlanId = null,
                    status = SubscriptionStatus.Active,
                    chargedCents = null,
                    pixCopyPaste = null,
                    invoiceUrl = null,
                ),
            ),
            subscriptionAfterMutation = ACTIVE_SUBSCRIPTION.copy(
                pendingPlan = Plan.Titular,
                pendingPlanEffectiveAt = "2026-09-01T00:00:00Z",
            ),
        )
        val viewModel = MyPlanViewModel(gateway)

        viewModel.onIntent(MyPlanIntent.SelectPlan(Plan.Titular))

        assertEquals(
            UiText.Res(Res.string.myplan_pending_change, listOf("Amador", "01/09/2026")),
            viewModel.state.value.plan?.pendingChangeLine,
        )
        assertEquals(false, viewModel.state.value.isChangeSheetOpen)
        assertNull(viewModel.state.value.pendingPayment)
    }

    @Test
    fun `upgrade with a pending charge surfaces the pix code to confirm`() = runTest {
        val gateway = FakeSubscriptionGateway(
            changePlanResult = SaqzResult.Success(
                ChangePlanResult(
                    planId = Plan.Ilimitado,
                    pendingPlanId = null,
                    pendingPlanEffectiveAt = null,
                    pendingUpgradePlanId = Plan.Ilimitado,
                    status = SubscriptionStatus.Active,
                    chargedCents = 3000,
                    pixCopyPaste = "00020126chavepix",
                    invoiceUrl = null,
                ),
            ),
        )
        val viewModel = MyPlanViewModel(gateway)

        viewModel.onIntent(MyPlanIntent.SelectPlan(Plan.Ilimitado))

        assertEquals("00020126chavepix", viewModel.state.value.pendingPayment?.pixCopyPaste)
        assertEquals(false, viewModel.state.value.isChangeSheetOpen)
        assertEquals(false, viewModel.state.value.isChangingPlan)
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

        viewModel.onIntent(MyPlanIntent.OpenReceipts)
        assertEquals(true, viewModel.state.value.isReceiptsSheetOpen)
        viewModel.onIntent(MyPlanIntent.DismissReceipts)
        assertEquals(false, viewModel.state.value.isReceiptsSheetOpen)
    }

    // Disciplina obrigatória do AGENTS.md §4: intent inválido retorna cedo — um segundo
    // toque em "trocar" enquanto a primeira troca ainda está em voo não dispara outra.
    @Test
    fun `a second select while changing is already in flight is ignored`() = runTest {
        val gateway = FakeSubscriptionGateway(neverResolveChangePlan = true)
        val viewModel = MyPlanViewModel(gateway)

        viewModel.onIntent(MyPlanIntent.SelectPlan(Plan.Titular))
        viewModel.onIntent(MyPlanIntent.SelectPlan(Plan.Titular))

        assertEquals(1, gateway.changePlanCommands.size)
    }

    // Achado do Codex no PR #93: troca e cancelamento mexem na mesma cobrança, então uma
    // em voo tem que bloquear a outra também, não só ela mesma.
    @Test
    fun `a plan change in flight blocks a cancel`() = runTest {
        val gateway = FakeSubscriptionGateway(neverResolveChangePlan = true)
        val viewModel = MyPlanViewModel(gateway)

        viewModel.onIntent(MyPlanIntent.SelectPlan(Plan.Titular))
        viewModel.onIntent(MyPlanIntent.ConfirmCancel)

        assertEquals(0, gateway.cancelCalls)
    }

    @Test
    fun `a cancel in flight blocks a plan change`() = runTest {
        val gateway = FakeSubscriptionGateway(neverResolveCancel = true)
        val viewModel = MyPlanViewModel(gateway)

        viewModel.onIntent(MyPlanIntent.ConfirmCancel)
        viewModel.onIntent(MyPlanIntent.SelectPlan(Plan.Titular))

        assertEquals(0, gateway.changePlanCommands.size)
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
    fun `plans failure surfaces as a load error and not an empty catalog`() = runTest {
        val gateway = FakeSubscriptionGateway(plansResult = SaqzResult.Failure(SubscriptionError.Conflict))
        val viewModel = MyPlanViewModel(gateway)

        assertEquals(false, viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.plan)
        assertEquals(emptyList(), viewModel.state.value.changeOptions)
        assertNotNull(viewModel.state.value.loadError)
    }
}

private val PLAN_DETAILS = listOf(
    PlanDetails(
        id = Plan.Titular,
        name = "Amador",
        monthlyPriceCents = 0,
        annualPriceCents = 0,
        maxGroups = 1,
        maxAthletes = 20,
        multiAdmin = false,
        reports = false,
        whatsappSla = false,
    ),
    PlanDetails(
        id = Plan.Organizador,
        name = "Organizador",
        monthlyPriceCents = 1990,
        annualPriceCents = 19900,
        maxGroups = 3,
        maxAthletes = null,
        multiAdmin = false,
        reports = false,
        whatsappSla = false,
    ),
    PlanDetails(
        id = Plan.Ilimitado,
        name = "Quadra Cheia",
        monthlyPriceCents = 3990,
        annualPriceCents = 39900,
        maxGroups = null,
        maxAthletes = null,
        multiAdmin = true,
        reports = true,
        whatsappSla = true,
    ),
)

private val ACTIVE_SUBSCRIPTION = MySubscription(
    status = SubscriptionStatus.Active,
    plan = Plan.Organizador,
    cycle = SubscriptionCycle.Monthly,
    pendingPlan = null,
    pendingPlanEffectiveAt = null,
    currentPeriodEnd = "2026-08-30T00:00:00Z",
    paymentMethod = BillingType.Pix,
    usage = SubscriptionUsage(groupsUsed = 2, groupsLimit = 3),
    readOnly = false,
    pastDueSince = null,
    canceledAt = null,
)

/**
 * [subscriptionAfterMutation], quando presente, é o que `mySubscription()` passa a
 * devolver depois de um `changePlan`/`cancel` bem-sucedido — simula o servidor já ter
 * persistido a troca, para a recarga que o ViewModel dispara em seguida enxergar o
 * resultado novo, sem precisar remontar o card a partir do `ChangePlanResult`.
 */
private class FakeSubscriptionGateway(
    var plansResult: SaqzResult<List<PlanDetails>, SubscriptionError> = SaqzResult.Success(PLAN_DETAILS),
    var subscriptionResult: SaqzResult<MySubscription, SubscriptionError> = SaqzResult.Success(ACTIVE_SUBSCRIPTION),
    var receiptsResult: SaqzResult<List<Receipt>, SubscriptionError> = SaqzResult.Success(emptyList()),
    var changePlanResult: SaqzResult<ChangePlanResult, SubscriptionError> = SaqzResult.Failure(SubscriptionError.Conflict),
    var cancelResult: SaqzResult<CanceledSubscription, SubscriptionError> = SaqzResult.Failure(SubscriptionError.Conflict),
    var subscriptionAfterMutation: MySubscription? = null,
    // Nunca resolvem — só servem para segurar `isChangingPlan`/`isCanceling == true`
    // enquanto o teste do guard de mutação dispara a outra intent por cima, ainda em voo.
    private val neverResolveChangePlan: Boolean = false,
    private val neverResolveCancel: Boolean = false,
) : SubscriptionGateway {
    val changePlanCommands = mutableListOf<ChangePlanCommand>()
    var cancelCalls = 0

    override suspend fun plans(): SaqzResult<List<PlanDetails>, SubscriptionError> = plansResult

    override suspend fun validateCoupon(
        code: String,
        planId: Plan,
        cycle: SubscriptionCycle,
    ): SaqzResult<CouponValidation, SubscriptionError> = error("not used by MyPlanViewModel")

    override suspend fun mySubscription(): SaqzResult<MySubscription, SubscriptionError> = subscriptionResult

    override suspend fun create(command: CreateSubscriptionCommand): SaqzResult<CreatedSubscription, SubscriptionError> =
        error("not used by MyPlanViewModel")

    override suspend fun changePlan(command: ChangePlanCommand): SaqzResult<ChangePlanResult, SubscriptionError> {
        changePlanCommands += command
        if (neverResolveChangePlan) awaitCancellation()
        subscriptionAfterMutation?.let { subscriptionResult = SaqzResult.Success(it) }
        return changePlanResult
    }

    override suspend fun cancel(): SaqzResult<CanceledSubscription, SubscriptionError> {
        cancelCalls++
        if (neverResolveCancel) awaitCancellation()
        subscriptionAfterMutation?.let { subscriptionResult = SaqzResult.Success(it) }
        return cancelResult
    }

    override suspend fun receipts(): SaqzResult<List<Receipt>, SubscriptionError> = receiptsResult
}
