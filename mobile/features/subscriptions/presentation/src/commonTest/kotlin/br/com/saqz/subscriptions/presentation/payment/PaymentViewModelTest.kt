package br.com.saqz.subscriptions.presentation.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.access.domain.session.AccessError
import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.payment_error_cpf_cnpj
import br.com.saqz.subscriptions.resources.payment_error_no_session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `PaymentViewModel.startPolling()` roda em `viewModelScope`, que não é filho do `TestScope`
 * de `runTest` — só compartilha o mesmo `TestCoroutineScheduler` via `Dispatchers.setMain`.
 * Um teste que termina com o poll ainda pendente (confirmação nunca chega) deixa um
 * `while (!confirmed) { delay(...) }` reagendando para sempre; o `advanceUntilIdle` implícito
 * do `runTest` no fim do teste então gira em tempo virtual sem parar — CPU real a 100%, sem
 * IO, sem terminar nunca. [withPaymentViewModel] cancela o escopo no `finally`, então nenhum
 * teste (nem um que falhe no meio) pode vazar o poll pro fechamento do `runTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `invalid cpf cnpj blocks submit without calling create`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway()
        withPaymentViewModel(gateway = gateway) { viewModel ->
            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("123"))
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()

            assertTrue(gateway.createCalls.isEmpty())
            assertEquals(UiText.Res(Res.string.payment_error_cpf_cnpj), viewModel.state.value.cpfCnpjError)
        }
    }

    @Test
    fun `missing session blocks submit with a generic error`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway()
        withPaymentViewModel(
            gateway = gateway,
            session = FakeSessionGateway(result = SaqzResult.Failure(AccessError.Unauthenticated)),
        ) { viewModel ->
            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()

            assertTrue(gateway.createCalls.isEmpty())
            assertEquals(UiText.Res(Res.string.payment_error_no_session), viewModel.state.value.submitError)
        }
    }

    @Test
    fun `pending confirmation keeps waiting when poll finds no receipt`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway().apply {
            createResult = SaqzResult.Success(createdPix())
            receiptsResults = listOf(SaqzResult.Success(emptyList()), SaqzResult.Success(emptyList()))
        }
        withPaymentViewModel(gateway = gateway) { viewModel ->
            val effects = mutableListOf<PaymentEffect>()
            backgroundScope.launch { viewModel.effects.collect { effects.add(it) } }

            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()

            assertEquals("00020126chavepix", viewModel.state.value.pixCopyPaste)
            assertTrue(viewModel.state.value.isWaitingConfirmation)

            advanceTimeBy(5_000)
            runCurrent()

            assertTrue(viewModel.state.value.isWaitingConfirmation)
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun `confirmation received once a receipt appears navigates to plan active`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway().apply {
            createResult = SaqzResult.Success(createdPix())
            receiptsResults = listOf(SaqzResult.Success(emptyList()), SaqzResult.Success(listOf(fakeReceipt())))
        }
        withPaymentViewModel(gateway = gateway) { viewModel ->
            val effects = mutableListOf<PaymentEffect>()
            backgroundScope.launch { viewModel.effects.collect { effects.add(it) } }

            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertTrue(effects.isEmpty())

            advanceTimeBy(5_000)
            runCurrent()

            assertEquals(listOf<PaymentEffect>(PaymentEffect.NavigateToPlanActive), effects)
            assertFalse(viewModel.state.value.isWaitingConfirmation)
        }
    }

    @Test
    fun `confirm payment intent checks immediately without waiting for the next poll tick`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway().apply {
            createResult = SaqzResult.Success(createdPix())
            receiptsResults = listOf(SaqzResult.Success(listOf(fakeReceipt())))
        }
        withPaymentViewModel(gateway = gateway) { viewModel ->
            val effects = mutableListOf<PaymentEffect>()
            backgroundScope.launch { viewModel.effects.collect { effects.add(it) } }

            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()

            viewModel.onIntent(PaymentIntent.ConfirmPayment)
            runCurrent()

            assertEquals(listOf<PaymentEffect>(PaymentEffect.NavigateToPlanActive), effects)
        }
    }

    @Test
    fun `regenerate pix reuses the same request id and refreshes the code`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway().apply {
            createResult = SaqzResult.Success(createdPix("expired-code"))
            receiptsResults = listOf(SaqzResult.Success(emptyList()))
        }
        withPaymentViewModel(gateway = gateway) { viewModel ->
            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()
            assertEquals("expired-code", viewModel.state.value.pixCopyPaste)

            gateway.createResult = SaqzResult.Success(createdPix("fresh-code"))
            viewModel.onIntent(PaymentIntent.RegeneratePix)
            runCurrent()

            assertEquals("fresh-code", viewModel.state.value.pixCopyPaste)
            assertEquals(2, gateway.createCalls.size)
            assertEquals(gateway.createCalls[0].requestId, gateway.createCalls[1].requestId)
        }
    }

    @Test
    fun `create failure surfaces a generic error and does not start waiting`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway().apply {
            createResult = SaqzResult.Failure(SubscriptionError.Conflict)
        }
        withPaymentViewModel(gateway = gateway) { viewModel ->
            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()

            assertFalse(viewModel.state.value.isWaitingConfirmation)
            assertEquals(null, viewModel.state.value.pixCopyPaste)
            assertTrue(viewModel.state.value.submitError != null)
        }
    }

    @Test
    fun `selecting card billing sends it on create`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway().apply {
            createResult = SaqzResult.Success(createdPix())
        }
        withPaymentViewModel(gateway = gateway) { viewModel ->
            viewModel.onIntent(PaymentIntent.SelectBillingType(BillingType.CreditCard))
            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()

            assertEquals(BillingType.CreditCard, gateway.createCalls.single().billingType)
        }
    }

    /**
     * Cria a ViewModel, deixa `runCurrent()` drenar o `init` (bootstrap da sessão + resumo),
     * roda [block] e cancela `viewModelScope` no `finally` — mesmo se [block] lançar. Sem
     * isso, qualquer teste que termina com o poll de confirmação ainda pendente trava o
     * `runTest` inteiro (ver doc da classe).
     */
    private suspend fun TestScope.withPaymentViewModel(
        route: SubscriptionsRoute.Payment = SubscriptionsRoute.Payment(
            planId = Plan.Titular.name,
            cycle = SubscriptionCycle.Monthly.name,
            couponCode = null,
        ),
        gateway: FakeSubscriptionGateway = FakeSubscriptionGateway(),
        session: FakeSessionGateway = FakeSessionGateway(),
        block: suspend (PaymentViewModel) -> Unit,
    ) {
        val viewModel = PaymentViewModel(route, SavedStateHandle(), gateway, session)
        try {
            runCurrent()
            block(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
