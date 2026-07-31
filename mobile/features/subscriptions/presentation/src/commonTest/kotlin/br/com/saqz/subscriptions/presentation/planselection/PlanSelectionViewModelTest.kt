package br.com.saqz.subscriptions.presentation.planselection

import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.ChangePlanCommand
import br.com.saqz.subscriptions.domain.subscription.ChangePlanResult
import br.com.saqz.subscriptions.domain.subscription.CanceledSubscription
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlanSelectionViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `carrega os planos e seleciona o mais escolhido`() = runTest(mainDispatcher) {
        val viewModel = PlanSelectionViewModel(FakeGateway())
        runCurrent()

        assertEquals(false, viewModel.state.value.isLoading)
        assertEquals(SamplePlans.map { it.id }, viewModel.state.value.plans.map { it.id })
        assertEquals(Plan.Organizador, viewModel.state.value.selectedPlanId)
        assertEquals(1990L, viewModel.state.value.totalCents)
    }

    @Test
    fun `trocar o ciclo recalcula o total e limpa o cupom aplicado`() = runTest(mainDispatcher) {
        val gateway = FakeGateway(couponResult = appliedResult())
        val viewModel = PlanSelectionViewModel(gateway)
        runCurrent()
        viewModel.onIntent(PlanSelectionIntent.UpdateCouponCode("GALERA10"))
        viewModel.onIntent(PlanSelectionIntent.ApplyCoupon)
        runCurrent()
        assertIs<CouponUiState.Applied>(viewModel.state.value.coupon)

        viewModel.onIntent(PlanSelectionIntent.SelectCycle(SubscriptionCycle.Annual))
        runCurrent()

        assertEquals(SubscriptionCycle.Annual, viewModel.state.value.cycle)
        assertEquals(CouponUiState.Idle, viewModel.state.value.coupon)
        assertEquals("", viewModel.state.value.couponCode)
        // Organizador · anual, sem cupom (o desconto anterior era pro ciclo mensal).
        assertEquals(19_900L, viewModel.state.value.totalCents)
    }

    @Test
    fun `selecionar outro plano troca o total e limpa o cupom aplicado`() = runTest(mainDispatcher) {
        val gateway = FakeGateway(couponResult = appliedResult())
        val viewModel = PlanSelectionViewModel(gateway)
        runCurrent()
        viewModel.onIntent(PlanSelectionIntent.UpdateCouponCode("GALERA10"))
        viewModel.onIntent(PlanSelectionIntent.ApplyCoupon)
        runCurrent()

        viewModel.onIntent(PlanSelectionIntent.SelectPlan(Plan.Ilimitado))
        runCurrent()

        assertEquals(Plan.Ilimitado, viewModel.state.value.selectedPlanId)
        assertEquals(CouponUiState.Idle, viewModel.state.value.coupon)
        assertEquals(3_990L, viewModel.state.value.totalCents)
    }

    @Test
    fun `cupom aplicado mostra percentual e preco final`() = runTest(mainDispatcher) {
        val gateway = FakeGateway(
            couponResult = { code, planId, cycle ->
                SaqzResult.Success(
                    CouponValidation.Applied(
                        code = code,
                        planId = planId,
                        cycle = cycle,
                        discountPercent = 10,
                        listPriceCents = 1990,
                        finalPriceCents = 1791,
                        validUntil = null,
                    ),
                )
            },
        )
        val viewModel = PlanSelectionViewModel(gateway)
        runCurrent()

        viewModel.onIntent(PlanSelectionIntent.UpdateCouponCode(" galera10 "))
        viewModel.onIntent(PlanSelectionIntent.ApplyCoupon)
        runCurrent()

        val coupon = assertIs<CouponUiState.Applied>(viewModel.state.value.coupon)
        assertEquals(10, coupon.discountPercent)
        assertEquals(1791L, coupon.finalPriceCents)
        assertEquals(1791L, viewModel.state.value.totalCents)
        assertEquals(listOf(Triple("galera10", Plan.Organizador, SubscriptionCycle.Monthly)), gateway.couponRequests)
    }

    @Test
    fun `cupom nao encontrado fica vermelho e nao mexe no total`() = runTest(mainDispatcher) {
        val gateway = FakeGateway(couponResult = { _, _, _ -> SaqzResult.Success(CouponValidation.NotFound) })
        val viewModel = PlanSelectionViewModel(gateway)
        runCurrent()

        viewModel.onIntent(PlanSelectionIntent.UpdateCouponCode("VOLEI99"))
        viewModel.onIntent(PlanSelectionIntent.ApplyCoupon)
        runCurrent()

        assertEquals(CouponUiState.NotFound, viewModel.state.value.coupon)
        assertEquals(1990L, viewModel.state.value.totalCents)
        assertFalse(viewModel.state.value.isValidatingCoupon)
    }

    @Test
    fun `cupom expirado mantem o preco cheio`() = runTest(mainDispatcher) {
        val gateway = FakeGateway(couponResult = { _, _, _ -> SaqzResult.Success(CouponValidation.Expired) })
        val viewModel = PlanSelectionViewModel(gateway)
        runCurrent()

        viewModel.onIntent(PlanSelectionIntent.UpdateCouponCode("SAQUE20"))
        viewModel.onIntent(PlanSelectionIntent.ApplyCoupon)
        runCurrent()

        assertEquals(CouponUiState.Expired(code = "SAQUE20"), viewModel.state.value.coupon)
        assertEquals(1990L, viewModel.state.value.totalCents)
    }

    @Test
    fun `confirmar emite o efeito com plano ciclo e cupom aplicado`() = runTest(mainDispatcher) {
        val gateway = FakeGateway(couponResult = appliedResult())
        val viewModel = PlanSelectionViewModel(gateway)
        val effects = collectEffects(viewModel)
        runCurrent()
        viewModel.onIntent(PlanSelectionIntent.UpdateCouponCode("GALERA10"))
        viewModel.onIntent(PlanSelectionIntent.ApplyCoupon)
        runCurrent()

        viewModel.onIntent(PlanSelectionIntent.Confirm)
        runCurrent()

        assertEquals(
            listOf(PlanSelectionEffect.NavigateToPayment(Plan.Organizador, SubscriptionCycle.Monthly, "GALERA10")),
            effects,
        )
    }

    @Test
    fun `confirmar sem cupom manda o codigo nulo`() = runTest(mainDispatcher) {
        val viewModel = PlanSelectionViewModel(FakeGateway())
        val effects = collectEffects(viewModel)
        runCurrent()

        viewModel.onIntent(PlanSelectionIntent.Confirm)
        runCurrent()

        assertEquals(
            listOf(PlanSelectionEffect.NavigateToPayment(Plan.Organizador, SubscriptionCycle.Monthly, null)),
            effects,
        )
    }

    /** Mesma guarda de geração do 1d: resposta de um cupom que não está mais na tela morre aqui. */
    @Test
    fun `resposta de validacao para um plano que ja mudou nao aplica`() = runTest(mainDispatcher) {
        val gateway = SuspendingGateway()
        val viewModel = PlanSelectionViewModel(gateway)
        runCurrent()
        viewModel.onIntent(PlanSelectionIntent.UpdateCouponCode("GALERA10"))
        viewModel.onIntent(PlanSelectionIntent.ApplyCoupon)
        runCurrent()
        assertTrue(viewModel.state.value.isValidatingCoupon)

        viewModel.onIntent(PlanSelectionIntent.SelectPlan(Plan.Ilimitado))
        gateway.complete(
            SaqzResult.Success(
                CouponValidation.Applied(
                    code = "GALERA10",
                    planId = Plan.Organizador,
                    cycle = SubscriptionCycle.Monthly,
                    discountPercent = 10,
                    listPriceCents = 1990,
                    finalPriceCents = 1791,
                    validUntil = null,
                ),
            ),
        )
        runCurrent()

        assertEquals(CouponUiState.Idle, viewModel.state.value.coupon)
        assertEquals(Plan.Ilimitado, viewModel.state.value.selectedPlanId)
    }

    /**
     * ABA: a validação A (plano P, código X) fica pendurada, a pessoa troca de plano,
     * volta pro mesmo P e dispara uma validação B com o mesmo X. Uma guarda por
     * igualdade de campos aceitaria a resposta atrasada de A, porque plano/ciclo/código
     * bateram de novo com o que está na tela — mesmo já não sendo a pergunta mais
     * recente. Só o contador monotônico (`couponValidationGeneration`) resolve isso.
     */
    @Test
    fun `resposta atrasada de uma validacao ABA nao sobrescreve a mais nova`() = runTest(mainDispatcher) {
        val gateway = QueuedGateway()
        val viewModel = PlanSelectionViewModel(gateway)
        runCurrent()

        viewModel.onIntent(PlanSelectionIntent.UpdateCouponCode("GALERA10"))
        viewModel.onIntent(PlanSelectionIntent.ApplyCoupon)
        runCurrent()

        viewModel.onIntent(PlanSelectionIntent.SelectPlan(Plan.Ilimitado))
        viewModel.onIntent(PlanSelectionIntent.SelectPlan(Plan.Organizador))
        viewModel.onIntent(PlanSelectionIntent.UpdateCouponCode("GALERA10"))
        viewModel.onIntent(PlanSelectionIntent.ApplyCoupon)
        runCurrent()

        assertEquals(2, gateway.pendingCount)

        // B (a mais nova) responde primeiro — a rede não garante ordem.
        gateway.complete(1, couponApplied(discountPercent = 20, finalPriceCents = 1_592))
        runCurrent()
        assertEquals(20, (viewModel.state.value.coupon as CouponUiState.Applied).discountPercent)

        // A resposta atrasada de A chega depois, com plano/ciclo/código idênticos aos
        // que já estão na tela — uma guarda por valor aceitaria e sobrescreveria B.
        gateway.complete(0, couponApplied(discountPercent = 10, finalPriceCents = 1_791))
        runCurrent()

        assertEquals(20, (viewModel.state.value.coupon as CouponUiState.Applied).discountPercent)
    }

    /**
     * Mesmo defeito do `couponValidationGeneration`, agora em `loadPlans()`: dois toques
     * em "Tentar de novo" disparam duas `gateway.plans()` concorrentes, e sem contador a
     * resposta mais velha — chegando depois — pode sobrescrever a mais nova. Uma falha
     * atrasada não pode apagar um sucesso que já carregou os planos na tela.
     */
    @Test
    fun `resposta atrasada de um carregamento de planos nao sobrescreve o mais novo`() = runTest(mainDispatcher) {
        val gateway = QueuedPlansGateway()
        val viewModel = PlanSelectionViewModel(gateway)
        // O `init` já disparou a primeira chamada (A); dispara a segunda (B) via Retry.
        viewModel.onIntent(PlanSelectionIntent.Retry)
        runCurrent()

        assertEquals(2, gateway.pendingCount)

        // B (a mais nova) responde primeiro com sucesso.
        gateway.complete(1, SaqzResult.Success(SamplePlans))
        runCurrent()
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(SamplePlans.map { it.id }, viewModel.state.value.plans.map { it.id })
        assertNull(viewModel.state.value.loadError)

        // A resposta atrasada de A chega depois, com uma falha — não pode apagar o
        // sucesso que B já colocou na tela.
        gateway.complete(0, SaqzResult.Failure(SubscriptionError.NotFound))
        runCurrent()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(SamplePlans.map { it.id }, viewModel.state.value.plans.map { it.id })
        assertNull(viewModel.state.value.loadError)
    }

    private fun couponApplied(discountPercent: Int, finalPriceCents: Long) = SaqzResult.Success(
        CouponValidation.Applied(
            code = "GALERA10",
            planId = Plan.Organizador,
            cycle = SubscriptionCycle.Monthly,
            discountPercent = discountPercent,
            listPriceCents = 1990,
            finalPriceCents = finalPriceCents,
            validUntil = null,
        ),
    )

    private fun appliedResult(): (String, Plan, SubscriptionCycle) -> SaqzResult<CouponValidation, SubscriptionError> =
        { code, planId, cycle ->
            SaqzResult.Success(
                CouponValidation.Applied(
                    code = code,
                    planId = planId,
                    cycle = cycle,
                    discountPercent = 10,
                    listPriceCents = 1990,
                    finalPriceCents = 1791,
                    validUntil = null,
                ),
            )
        }

    private fun TestScope.collectEffects(viewModel: PlanSelectionViewModel): List<PlanSelectionEffect> {
        val received = mutableListOf<PlanSelectionEffect>()
        (backgroundScope as CoroutineScope).launch(mainDispatcher) { viewModel.effects.toList(received) }
        runCurrent()
        return received
    }

    private val SamplePlans = listOf(
        PlanDetails(
            id = Plan.Titular,
            name = "Titular",
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
            annualPriceCents = 19_900,
            maxGroups = 3,
            maxAthletes = null,
            multiAdmin = false,
            reports = false,
            whatsappSla = false,
        ),
        PlanDetails(
            id = Plan.Ilimitado,
            name = "Ilimitado",
            monthlyPriceCents = 3990,
            annualPriceCents = 39_900,
            maxGroups = null,
            maxAthletes = null,
            multiAdmin = true,
            reports = true,
            whatsappSla = true,
        ),
    )

    private inner class FakeGateway(
        private val plansResult: SaqzResult<List<PlanDetails>, SubscriptionError> = SaqzResult.Success(SamplePlans),
        private val couponResult: (String, Plan, SubscriptionCycle) -> SaqzResult<CouponValidation, SubscriptionError> =
            { _, _, _ -> SaqzResult.Success(CouponValidation.NotFound) },
    ) : SubscriptionGateway by NotUsedGateway {
        val couponRequests = mutableListOf<Triple<String, Plan, SubscriptionCycle>>()

        override suspend fun plans(): SaqzResult<List<PlanDetails>, SubscriptionError> = plansResult

        override suspend fun validateCoupon(
            code: String,
            planId: Plan,
            cycle: SubscriptionCycle,
        ): SaqzResult<CouponValidation, SubscriptionError> {
            couponRequests += Triple(code, planId, cycle)
            return couponResult(code, planId, cycle)
        }
    }

    /** O cupom fica pendurado até o teste responder, que é quando a tela já mudou. */
    private inner class SuspendingGateway : SubscriptionGateway by NotUsedGateway {
        private val response = CompletableDeferred<SaqzResult<CouponValidation, SubscriptionError>>()

        override suspend fun plans(): SaqzResult<List<PlanDetails>, SubscriptionError> = SaqzResult.Success(SamplePlans)

        override suspend fun validateCoupon(
            code: String,
            planId: Plan,
            cycle: SubscriptionCycle,
        ): SaqzResult<CouponValidation, SubscriptionError> = response.await()

        fun complete(result: SaqzResult<CouponValidation, SubscriptionError>) = response.complete(result)
    }

    /** Uma resposta pendurada por chamada, endereçável por índice — pro cenário ABA. */
    private inner class QueuedGateway : SubscriptionGateway by NotUsedGateway {
        private val responses = mutableListOf<CompletableDeferred<SaqzResult<CouponValidation, SubscriptionError>>>()
        val pendingCount: Int get() = responses.size

        override suspend fun plans(): SaqzResult<List<PlanDetails>, SubscriptionError> = SaqzResult.Success(SamplePlans)

        override suspend fun validateCoupon(
            code: String,
            planId: Plan,
            cycle: SubscriptionCycle,
        ): SaqzResult<CouponValidation, SubscriptionError> {
            val deferred = CompletableDeferred<SaqzResult<CouponValidation, SubscriptionError>>()
            responses += deferred
            return deferred.await()
        }

        fun complete(index: Int, result: SaqzResult<CouponValidation, SubscriptionError>) =
            responses[index].complete(result)
    }

    /** Mesma ideia do [QueuedGateway], mas pra `plans()` — pro cenário ABA de `loadPlans()`. */
    private inner class QueuedPlansGateway : SubscriptionGateway by NotUsedGateway {
        private val responses = mutableListOf<CompletableDeferred<SaqzResult<List<PlanDetails>, SubscriptionError>>>()
        val pendingCount: Int get() = responses.size

        override suspend fun plans(): SaqzResult<List<PlanDetails>, SubscriptionError> {
            val deferred = CompletableDeferred<SaqzResult<List<PlanDetails>, SubscriptionError>>()
            responses += deferred
            return deferred.await()
        }

        fun complete(index: Int, result: SaqzResult<List<PlanDetails>, SubscriptionError>) =
            responses[index].complete(result)
    }

    private object NotUsedGateway : SubscriptionGateway {
        override suspend fun plans(): SaqzResult<List<PlanDetails>, SubscriptionError> =
            error("cada fake implementa o seu")

        override suspend fun validateCoupon(
            code: String,
            planId: Plan,
            cycle: SubscriptionCycle,
        ): SaqzResult<CouponValidation, SubscriptionError> = error("cada fake implementa o seu")

        override suspend fun mySubscription(): SaqzResult<MySubscription, SubscriptionError> =
            error("fora do escopo do VUL-109")

        override suspend fun create(
            command: CreateSubscriptionCommand,
        ): SaqzResult<CreatedSubscription, SubscriptionError> = error("fora do escopo do VUL-109")

        override suspend fun changePlan(command: ChangePlanCommand): SaqzResult<ChangePlanResult, SubscriptionError> =
            error("fora do escopo do VUL-109")

        override suspend fun cancel(): SaqzResult<CanceledSubscription, SubscriptionError> =
            error("fora do escopo do VUL-109")

        override suspend fun receipts(): SaqzResult<List<Receipt>, SubscriptionError> =
            error("fora do escopo do VUL-109")
    }
}
