package br.com.saqz.subscriptions.presentation.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.payment_error_checkout_unavailable
import br.com.saqz.subscriptions.resources.payment_error_conflict_pending_checkout
import br.com.saqz.subscriptions.resources.payment_error_cpf_cnpj
import br.com.saqz.subscriptions.resources.payment_error_generic
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
            customer = FakeCustomerInfoProvider(info = null),
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
    fun `charge already paid navigates to plan active instead of asking to pay again`() = runTest(mainDispatcher) {
        // Backend consultou o Asaas na recuperacao, viu a cobranca paga e confirmou: volta
        // ACTIVE e sem checkout. Antes disso o usuario caia no erro de checkout indisponivel.
        val gateway = FakeSubscriptionGateway().apply {
            createResult = SaqzResult.Success(createdPaid())
            receiptsResults = listOf(SaqzResult.Success(emptyList()))
        }
        withPaymentViewModel(gateway = gateway) { viewModel ->
            val effects = mutableListOf<PaymentEffect>()
            backgroundScope.launch { viewModel.effects.collect { effects.add(it) } }

            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()

            assertEquals(listOf<PaymentEffect>(PaymentEffect.NavigateToPlanActive), effects)
            assertNull(viewModel.state.value.submitError)
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
            assertEquals(UiText.Res(Res.string.payment_error_generic), viewModel.state.value.submitError)
        }
    }

    /**
     * VUL-119: `Conflict` puro é o caso `AlreadySubscribed` — mensagem genérica, mesmo
     * comportamento independente de já existir um checkout criado nesta sessão. Achado do
     * Codex no PR #100: uma heurística client-only por `hasCheckout` não dava pra
     * distinguir os dois casos (o repro real cria uma instância NOVA de `PaymentViewModel`
     * pra cada plano) — o backend agora tem um código próprio pra isso (ver o teste abaixo).
     */
    @Test
    fun `conflict keeps the generic error regardless of a checkout already created in this session`() =
        runTest(mainDispatcher) {
            val gateway = FakeSubscriptionGateway().apply {
                createResult = SaqzResult.Success(createdPix())
                receiptsResults = listOf(SaqzResult.Success(emptyList()))
            }
            withPaymentViewModel(gateway = gateway) { viewModel ->
                viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
                viewModel.onIntent(PaymentIntent.Submit)
                runCurrent()
                assertTrue(viewModel.state.value.hasCheckout)

                gateway.createResult = SaqzResult.Failure(SubscriptionError.Conflict)
                viewModel.onIntent(PaymentIntent.RegeneratePix)
                runCurrent()

                assertEquals(UiText.Res(Res.string.payment_error_generic), viewModel.state.value.submitError)
            }
        }

    /**
     * VUL-119: `PendingCheckoutMismatch` (código `SUBSCRIPTION_PENDING_CHECKOUT_MISMATCH`,
     * próprio do backend desde o achado do Codex no PR #100) sempre ganha a mensagem
     * acionável, direto pelo tipo do erro — sem depender de `hasCheckout` desta sessão. É o
     * caminho real do repro: a tela do Plano B é uma instância NOVA de `PaymentViewModel`,
     * então `hasCheckout` está falso quando o backend recusa por causa do checkout do Plano A.
     */
    @Test
    fun `pending checkout mismatch shows the specific message even without a checkout in this session`() =
        runTest(mainDispatcher) {
            val gateway = FakeSubscriptionGateway().apply {
                createResult = SaqzResult.Failure(SubscriptionError.PendingCheckoutMismatch)
            }
            withPaymentViewModel(gateway = gateway) { viewModel ->
                viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
                viewModel.onIntent(PaymentIntent.Submit)
                runCurrent()

                assertFalse(viewModel.state.value.hasCheckout)
                assertEquals(
                    UiText.Res(Res.string.payment_error_conflict_pending_checkout),
                    viewModel.state.value.submitError,
                )
            }
        }

    @Test
    fun `back is requested with a pending checkout opens the confirmation and dismiss closes it`() =
        runTest(mainDispatcher) {
            val gateway = FakeSubscriptionGateway().apply {
                createResult = SaqzResult.Success(createdPix())
                receiptsResults = listOf(SaqzResult.Success(emptyList()))
            }
            withPaymentViewModel(gateway = gateway) { viewModel ->
                viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
                viewModel.onIntent(PaymentIntent.Submit)
                runCurrent()

                viewModel.onIntent(PaymentIntent.RequestBack)
                assertTrue(viewModel.state.value.isBackConfirmationOpen)

                viewModel.onIntent(PaymentIntent.DismissBackConfirmation)
                assertFalse(viewModel.state.value.isBackConfirmationOpen)
            }
        }

    /**
     * Achado #1 do Codex no PR #96: `resolveCheckout()` no backend pode devolver sucesso com
     * `pixCopyPaste`/`invoiceUrl` os dois nulos (mesmo caminho do VUL-117). Sem o guard, isso
     * virava um "aguardando confirmação" silencioso — `hasCheckout` falso, poll rodando
     * escondido, usuário sem código de pagamento e sem erro.
     */
    @Test
    fun `create success with both checkout fields null surfaces a retryable error instead of waiting silently`() =
        runTest(mainDispatcher) {
            val gateway = FakeSubscriptionGateway().apply {
                createResult = SaqzResult.Success(createdPix().copy(pixCopyPaste = null, invoiceUrl = null))
            }
            withPaymentViewModel(gateway = gateway) { viewModel ->
                viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
                viewModel.onIntent(PaymentIntent.Submit)
                runCurrent()

                assertFalse(viewModel.state.value.isWaitingConfirmation)
                assertFalse(viewModel.state.value.hasCheckout)
                assertFalse(viewModel.state.value.isSubmitting)
                assertEquals(
                    UiText.Res(Res.string.payment_error_checkout_unavailable),
                    viewModel.state.value.submitError,
                )
            }
        }

    /**
     * Achado #2 do Codex no PR #96, o mais importante: um checkout criado antes da process
     * death não persistia — a ViewModel recriada voltava pro formulário em branco, e um
     * resubmit reenviaria o mesmo `requestId` contra uma assinatura já confirmada
     * (`AlreadySubscribed`), deixando quem já pagou preso num erro genérico. As chaves
     * literais abaixo espelham as `private const val KEY_*` de [PaymentViewModel] — é
     * exatamente o que um `SavedStateHandle` restaurado pelo processo entregaria.
     */
    @Test
    fun `resumes waiting from a restored SavedStateHandle without resubmitting`() = runTest(mainDispatcher) {
        val restored = SavedStateHandle(
            mapOf(
                "payment_request_id" to "req-before-death",
                "payment_cpf_cnpj" to "12345678900",
                "payment_billing_type" to BillingType.Pix.name,
                "payment_pix_copy_paste" to "00020126chave-antes-da-morte",
            ),
        )
        val gateway = FakeSubscriptionGateway().apply {
            receiptsResults = listOf(SaqzResult.Success(emptyList()))
        }
        withPaymentViewModel(gateway = gateway, savedStateHandle = restored) { viewModel ->
            // O estado já nasce em espera — não é o formulário em branco.
            assertTrue(viewModel.state.value.isWaitingConfirmation)
            assertEquals("00020126chave-antes-da-morte", viewModel.state.value.pixCopyPaste)

            runCurrent()

            assertTrue(gateway.createCalls.isEmpty())
        }
    }

    /**
     * A metade que fecha o achado #2: se o pagamento foi confirmado enquanto o processo
     * estava fora do ar, a checagem imediata do `init` (não o poll de 5s) encontra o recibo
     * e navega — sem esperar o usuário reenviar nada.
     */
    @Test
    fun `reconciles an already-confirmed payment on resume without waiting for the next poll tick`() =
        runTest(mainDispatcher) {
            val restored = SavedStateHandle(
                mapOf(
                    "payment_request_id" to "req-before-death",
                    "payment_billing_type" to BillingType.Pix.name,
                    "payment_pix_copy_paste" to "00020126chave-antes-da-morte",
                ),
            )
            val gateway = FakeSubscriptionGateway().apply {
                receiptsResults = listOf(SaqzResult.Success(listOf(fakeReceipt())))
            }
            withPaymentViewModel(gateway = gateway, savedStateHandle = restored) { viewModel ->
                val effects = mutableListOf<PaymentEffect>()
                backgroundScope.launch { viewModel.effects.collect { effects.add(it) } }

                runCurrent()

                assertEquals(listOf<PaymentEffect>(PaymentEffect.NavigateToPlanActive), effects)
                assertFalse(viewModel.state.value.isWaitingConfirmation)
                assertTrue(gateway.createCalls.isEmpty())
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
            fillValidCardForm(viewModel)
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()

            assertEquals(BillingType.CreditCard, gateway.createCalls.single().billingType)
        }
    }

    /**
     * VUL-196: `creditCard`/`creditCardHolderInfo` só entram no comando quando o cartão é a
     * forma escolhida — `name`/`email`/`cpfCnpj` do holder info reaproveitam sessão e o
     * CPF/CNPJ já digitado (ver doc de `CardFormState`), sem campo próprio pra eles.
     */
    @Test
    fun `submitting with card billing sends the credit card and holder info blocks`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway().apply {
            createResult = SaqzResult.Success(createdPix().copy(billingType = BillingType.CreditCard))
        }
        withPaymentViewModel(gateway = gateway) { viewModel ->
            viewModel.onIntent(PaymentIntent.SelectBillingType(BillingType.CreditCard))
            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
            fillValidCardForm(viewModel)
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()

            val command = gateway.createCalls.single()
            val card = assertNotNull(command.creditCard)
            assertEquals("Ana Silva", card.holderName)
            assertEquals("4111111111111111", card.number)
            assertEquals("12", card.expiryMonth)
            assertEquals("2028", card.expiryYear)
            assertEquals("123", card.ccv)

            val holderInfo = assertNotNull(command.creditCardHolderInfo)
            assertEquals("Ana Silva", holderInfo.name)
            assertEquals("ana@exemplo.com", holderInfo.email)
            assertEquals("12345678900", holderInfo.cpfCnpj)
            assertEquals("01310100", holderInfo.postalCode)
            assertEquals("1000", holderInfo.addressNumber)
            assertEquals("11999990000", holderInfo.phone)
        }
    }

    @Test
    fun `submitting card billing with pix billing sends neither credit card block`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway().apply { createResult = SaqzResult.Success(createdPix()) }
        withPaymentViewModel(gateway = gateway) { viewModel ->
            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()

            val command = gateway.createCalls.single()
            assertNull(command.creditCard)
            assertNull(command.creditCardHolderInfo)
        }
    }

    @Test
    fun `invalid card fields block submit without calling create`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway()
        withPaymentViewModel(gateway = gateway) { viewModel ->
            viewModel.onIntent(PaymentIntent.SelectBillingType(BillingType.CreditCard))
            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
            viewModel.onIntent(PaymentIntent.UpdateCardNumber("1234"))
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()

            assertTrue(gateway.createCalls.isEmpty())
            assertTrue(CardFormError.NumberInvalid in viewModel.state.value.cardForm.errors)
        }
    }

    @Test
    fun `editing any card field clears the previous errors`() = runTest(mainDispatcher) {
        withPaymentViewModel { viewModel ->
            viewModel.onIntent(PaymentIntent.SelectBillingType(BillingType.CreditCard))
            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()
            assertTrue(viewModel.state.value.cardForm.errors.isNotEmpty())

            viewModel.onIntent(PaymentIntent.UpdateCardNumber("4111111111111111"))

            assertTrue(viewModel.state.value.cardForm.errors.isEmpty())
        }
    }

    /**
     * VUL-196: 402 card_declined mostra a mensagem PT-BR do backend e não apaga o que o
     * portador digitou — "tentar outro cartão" não pode virar "redigitar tudo".
     */
    @Test
    fun `card declined surfaces the backend message and keeps the holder data`() = runTest(mainDispatcher) {
        val gateway = FakeSubscriptionGateway().apply {
            createResult = SaqzResult.Failure(SubscriptionError.CardDeclined("insufficient_funds", "Saldo insuficiente."))
        }
        withPaymentViewModel(gateway = gateway) { viewModel ->
            viewModel.onIntent(PaymentIntent.SelectBillingType(BillingType.CreditCard))
            viewModel.onIntent(PaymentIntent.UpdateCpfCnpj("12345678900"))
            fillValidCardForm(viewModel)
            viewModel.onIntent(PaymentIntent.Submit)
            runCurrent()

            assertEquals(UiText.Raw("Saldo insuficiente."), viewModel.state.value.submitError)
            assertFalse(viewModel.state.value.isSubmitting)
            assertEquals("4111111111111111", viewModel.state.value.cardForm.number)
            assertEquals("Ana Silva", viewModel.state.value.cardForm.holderName)
        }
    }

    /** PCI (VUL-196): nada do bloco de cartão pode sobreviver a process death. */
    @Test
    fun `card fields never reach the saved state handle`() = runTest(mainDispatcher) {
        val savedStateHandle = SavedStateHandle()
        withPaymentViewModel(savedStateHandle = savedStateHandle) { viewModel ->
            viewModel.onIntent(PaymentIntent.SelectBillingType(BillingType.CreditCard))
            fillValidCardForm(viewModel)

            assertEquals("4111111111111111", viewModel.state.value.cardForm.number)
            val persistedValues = savedStateHandle.keys().map { savedStateHandle.get<Any?>(it) }
            assertTrue(persistedValues.none { it == "4111111111111111" || it == "123" })
        }
    }

    private fun fillValidCardForm(viewModel: PaymentViewModel) {
        viewModel.onIntent(PaymentIntent.UpdateCardNumber("4111111111111111"))
        viewModel.onIntent(PaymentIntent.UpdateCardExpiry("1228"))
        viewModel.onIntent(PaymentIntent.UpdateCardCvv("123"))
        viewModel.onIntent(PaymentIntent.UpdateCardHolderName("Ana Silva"))
        viewModel.onIntent(PaymentIntent.UpdateCardPostalCode("01310100"))
        viewModel.onIntent(PaymentIntent.UpdateCardAddressNumber("1000"))
        viewModel.onIntent(PaymentIntent.UpdateCardPhone("11999990000"))
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
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        gateway: FakeSubscriptionGateway = FakeSubscriptionGateway(),
        customer: FakeCustomerInfoProvider = FakeCustomerInfoProvider(),
        block: suspend (PaymentViewModel) -> Unit,
    ) {
        val viewModel = PaymentViewModel(route, savedStateHandle, gateway, customer)
        try {
            runCurrent()
            block(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
