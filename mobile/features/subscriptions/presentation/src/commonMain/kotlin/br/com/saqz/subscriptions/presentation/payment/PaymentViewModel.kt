package br.com.saqz.subscriptions.presentation.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.CouponValidation
import br.com.saqz.subscriptions.domain.subscription.CreateSubscriptionCommand
import br.com.saqz.subscriptions.domain.subscription.CustomerInfo
import br.com.saqz.subscriptions.domain.subscription.CustomerInfoProvider
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.payment_error_checkout_unavailable
import br.com.saqz.subscriptions.resources.payment_error_conflict_pending_checkout
import br.com.saqz.subscriptions.resources.payment_error_cpf_cnpj
import br.com.saqz.subscriptions.resources.payment_error_generic
import br.com.saqz.subscriptions.resources.payment_error_no_session
import br.com.saqz.domain.onFailure
import br.com.saqz.domain.onSuccess
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * `route` chega do `parametersOf` do koin (mesma [SubscriptionsRoute.Payment] que a 8a/8b
 * monta) — plano, ciclo e cupom são escalares porque a rota é `@Serializable` e não carrega
 * o [br.com.saqz.subscriptions.domain.subscription.PlanDetails] inteiro.
 *
 * [customerInfo] é um contrato próprio de `subscriptions:domain`, não `SessionGateway`
 * direto — ver [CustomerInfoProvider]. A implementação real mora em `:compose-app`.
 */
class PaymentViewModel(
    route: SubscriptionsRoute.Payment,
    private val savedStateHandle: SavedStateHandle,
    private val subscriptionGateway: SubscriptionGateway,
    private val customerInfo: CustomerInfoProvider,
) : MviViewModel<PaymentState, PaymentIntent, PaymentEffect>(
    PaymentState(
        plan = requirePlan(route.planId),
        cycle = requireCycle(route.cycle),
        couponCode = route.couponCode,
        cpfCnpj = savedStateHandle.get<String>(KEY_CPF_CNPJ) ?: "",
        billingType = savedStateHandle.get<String>(KEY_BILLING_TYPE)?.let(BillingType::valueOf) ?: BillingType.Pix,
        pixCopyPaste = savedStateHandle.get<String>(KEY_PIX_COPY_PASTE),
        invoiceUrl = savedStateHandle.get<String>(KEY_INVOICE_URL),
        // O checkout sobrevive à process death (ver `onCreated`); "tinha um checkout salvo"
        // É a mesma condição que decide "estou esperando confirmação".
        isWaitingConfirmation = savedStateHandle.get<String>(KEY_PIX_COPY_PASTE) != null ||
            savedStateHandle.get<String>(KEY_INVOICE_URL) != null,
    ),
) {
    // Sobrevive à process death via [KEY_REQUEST_ID]: reenviar `create()` com o mesmo id é
    // como o backend idempotente reemite o checkout em vez de cobrar duas vezes (VUL-107).
    private val requestId: String =
        savedStateHandle[KEY_REQUEST_ID] ?: newRequestId().also { savedStateHandle[KEY_REQUEST_ID] = it }

    private var customer: CustomerInfo? = null

    // Guarda de geração via cancelamento estrutural: um novo `submit`/poll cancela o
    // anterior antes de escrever, então nenhuma resposta atrasada sobrepõe a mais recente.
    private var submitJob: Job? = null
    private var pollJob: Job? = null
    private var confirmed = false

    init {
        viewModelScope.launch { customer = customerInfo.current() }
        loadSummary()
        // Retomada de process death: se o estado inicial já trouxe um checkout salvo, o
        // pagamento pode ter sido confirmado enquanto o app estava fora do ar. Sem checar
        // `receipts()` agora, um `submit()` futuro reenviaria o mesmo `requestId` contra uma
        // assinatura já confirmada e bateria em `AlreadySubscribed` — usuário que já pagou
        // ficaria preso num erro genérico. A checagem imediata roda em paralelo ao poll
        // regular, que seguem cuidando do caso "ainda não confirmou".
        if (state.value.isWaitingConfirmation) {
            viewModelScope.launch { checkReceipts() }
            startPolling()
        }
    }

    override fun onIntent(intent: PaymentIntent) {
        when (intent) {
            is PaymentIntent.UpdateCpfCnpj -> updateCpfCnpj(intent.value)
            is PaymentIntent.SelectBillingType -> selectBillingType(intent.value)
            PaymentIntent.Submit -> submit()
            PaymentIntent.RegeneratePix -> submit(skipValidation = true)
            PaymentIntent.ConfirmPayment -> checkNow()
            PaymentIntent.RequestBack -> update { it.copy(isBackConfirmationOpen = true) }
            PaymentIntent.DismissBackConfirmation -> update { it.copy(isBackConfirmationOpen = false) }
        }
    }

    private fun updateCpfCnpj(value: String) {
        val digits = value.filter(Char::isDigit).take(MAX_CPF_CNPJ_DIGITS)
        savedStateHandle[KEY_CPF_CNPJ] = digits
        update { it.copy(cpfCnpj = digits, cpfCnpjError = null) }
    }

    private fun selectBillingType(billingType: BillingType) {
        savedStateHandle[KEY_BILLING_TYPE] = billingType.name
        update { it.copy(billingType = billingType) }
    }

    private fun loadSummary() {
        viewModelScope.launch {
            subscriptionGateway.plans().onSuccess { plans ->
                val details = plans.firstOrNull { it.id == state.value.plan } ?: return@onSuccess
                update {
                    it.copy(
                        planName = details.name,
                        priceCents = when (it.cycle) {
                            SubscriptionCycle.Monthly -> details.monthlyPriceCents
                            SubscriptionCycle.Annual -> details.annualPriceCents
                        },
                    )
                }
            }
            val code = state.value.couponCode ?: return@launch
            subscriptionGateway.validateCoupon(code, state.value.plan, state.value.cycle).onSuccess { validation ->
                if (validation is CouponValidation.Applied) {
                    update { it.copy(priceCents = validation.finalPriceCents, discountPercent = validation.discountPercent) }
                }
            }
            // ponytail: cupom expirado/sumido entre a 8b e aqui não vira erro nesta tela —
            // o resumo só cai de volta ao preço de tabela. `create()` é quem recusa de verdade.
        }
    }

    private fun submit(skipValidation: Boolean = false) {
        if (state.value.isSubmitting) return
        val digits = state.value.cpfCnpj
        if (!skipValidation && !isValidCpfCnpj(digits)) {
            update { it.copy(cpfCnpjError = UiText.Res(Res.string.payment_error_cpf_cnpj)) }
            return
        }
        val currentCustomer = customer
        if (currentCustomer == null) {
            update { it.copy(submitError = UiText.Res(Res.string.payment_error_no_session)) }
            return
        }
        pollJob?.cancel()
        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            update { it.copy(isSubmitting = true, submitError = null, cpfCnpjError = null) }
            subscriptionGateway.create(
                CreateSubscriptionCommand(
                    requestId = requestId,
                    planId = state.value.plan,
                    cycle = state.value.cycle,
                    billingType = state.value.billingType,
                    name = currentCustomer.displayName,
                    email = currentCustomer.email.orEmpty(),
                    cpfCnpj = digits,
                    couponCode = state.value.couponCode,
                ),
            ).onSuccess { created ->
                onCreated(created.pixCopyPaste, created.invoiceUrl)
            }.onFailure { error ->
                update { it.copy(isSubmitting = false, submitError = error.toUiText()) }
            }
        }
    }

    /**
     * `resolveCheckout()` no backend pode devolver sucesso com os dois campos nulos se a
     * busca do pagamento/Asaas falhar (mesmo caminho do VUL-117). Tratar isso como
     * "aguardando confirmação" seria um travamento silencioso: `hasCheckout` fica falso, o
     * poll roda escondido, e o usuário não vê nem código de pagamento nem erro. Trata-se
     * como falha recuperável — mesmo padrão de `submitError` que uma recusa de `create()`
     * já usa, então "Pagar" de novo é o próprio retry (mesmo `requestId`, idempotente).
     */
    private fun onCreated(pixCopyPaste: String?, invoiceUrl: String?) {
        if (pixCopyPaste == null && invoiceUrl == null) {
            update {
                it.copy(isSubmitting = false, submitError = UiText.Res(Res.string.payment_error_checkout_unavailable))
            }
            return
        }
        savedStateHandle[KEY_PIX_COPY_PASTE] = pixCopyPaste
        savedStateHandle[KEY_INVOICE_URL] = invoiceUrl
        update {
            it.copy(
                isSubmitting = false,
                pixCopyPaste = pixCopyPaste,
                invoiceUrl = invoiceUrl,
                isWaitingConfirmation = true,
            )
        }
        startPolling()
    }

    private fun startPolling() {
        pollJob = viewModelScope.launch {
            while (!confirmed) {
                delay(POLL_INTERVAL_MS)
                checkReceipts()
            }
        }
    }

    private fun checkNow() {
        if (confirmed || state.value.isCheckingNow) return
        viewModelScope.launch {
            update { it.copy(isCheckingNow = true) }
            checkReceipts()
            update { it.copy(isCheckingNow = false) }
        }
    }

    /**
     * O único sinal real de pagamento confirmado — ver achado técnico do ticket.
     * `mySubscription().status` nasce `Active` na criação e não prova nada sozinho.
     */
    private suspend fun checkReceipts() {
        // Uma página de um item basta: a pergunta aqui é "já existe algum recibo?", não qual.
        subscriptionGateway.receipts(limit = 1, offset = 0).onSuccess { receipts ->
            if (receipts.isEmpty() || confirmed) return@onSuccess
            confirmed = true
            pollJob?.cancel()
            update { it.copy(isWaitingConfirmation = false) }
            emit(PaymentEffect.NavigateToPlanActive)
        }
    }

    private companion object {
        const val KEY_REQUEST_ID = "payment_request_id"
        const val KEY_CPF_CNPJ = "payment_cpf_cnpj"
        const val KEY_BILLING_TYPE = "payment_billing_type"
        const val KEY_PIX_COPY_PASTE = "payment_pix_copy_paste"
        const val KEY_INVOICE_URL = "payment_invoice_url"
        const val POLL_INTERVAL_MS = 5_000L
        const val MAX_CPF_CNPJ_DIGITS = 14
    }
}

// Mesma regra do backend (`CreateSubscription.isValidCpfCnpj`): 11 dígitos (CPF) ou 14 (CNPJ).
internal fun isValidCpfCnpj(digits: String): Boolean = digits.length == 11 || digits.length == 14

// VUL-119: o backend agora distingue os dois casos que colapsavam em `Conflict`
// (achado do Codex no PR #100) — `PendingCheckoutMismatch` é o único com mensagem própria
// e ação diferente (voltar/cancelar o checkout existente). `Conflict` puro é
// `AlreadySubscribed`, que continua com a mensagem genérica.
// ponytail: mensagem única para o resto dos motivos de recusa — a 8a/8b já validou o
// cupom antes de chegar aqui, e nenhum deles muda a ação do usuário: tentar de novo.
private fun SubscriptionError.toUiText(): UiText =
    if (this is SubscriptionError.PendingCheckoutMismatch) {
        UiText.Res(Res.string.payment_error_conflict_pending_checkout)
    } else {
        UiText.Res(Res.string.payment_error_generic)
    }

private fun requirePlan(planId: String) = Plan.valueOf(planId)
private fun requireCycle(cycle: String) = SubscriptionCycle.valueOf(cycle)

@OptIn(ExperimentalUuidApi::class)
private fun newRequestId(): String = Uuid.random().toString()
