package br.com.saqz.subscriptions.presentation.payment

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle

/**
 * 8c — plano/ciclo/cupom chegam prontos da 8a/8b (argumentos da rota) e nunca mudam aqui.
 * [pixCopyPaste]/[invoiceUrl] nulos é a fase de formulário; um dos dois preenchido é a fase
 * de espera — não existe um booleano `isCreated` solto, o checkout É o estado.
 *
 * [isWaitingConfirmation] só desce quando `receipts()` traz algo real: ver achado técnico
 * do ticket — `mySubscription().status` nasce `Active` na criação e não serve de sinal.
 */
@Immutable
data class PaymentState(
    val plan: Plan,
    val cycle: SubscriptionCycle,
    val couponCode: String? = null,
    val planName: String = "",
    val priceCents: Long? = null,
    val discountPercent: Int? = null,
    val billingType: BillingType = BillingType.Pix,
    val cpfCnpj: String = "",
    val cpfCnpjError: UiText? = null,
    // PCI (VUL-196): só nesta `MutableStateFlow` em memória — nunca em `SavedStateHandle`.
    // Process death redigita o cartão; não é regressão, é o requisito.
    val cardForm: CardFormState = CardFormState(),
    val isSubmitting: Boolean = false,
    val submitError: UiText? = null,
    val pixCopyPaste: String? = null,
    val invoiceUrl: String? = null,
    val isWaitingConfirmation: Boolean = false,
    val isCheckingNow: Boolean = false,
    val isBackConfirmationOpen: Boolean = false,
) {
    val hasCheckout: Boolean get() = pixCopyPaste != null || invoiceUrl != null
}

/**
 * Captura do cartão + dados do portador exigidos pelo Asaas para cobrança direta
 * (VUL-196). `name`/`email`/`cpfCnpj` do `creditCardHolderInfo` não têm campo próprio
 * aqui — o `PaymentViewModel` reaproveita a sessão (`CustomerInfo`) e o `cpfCnpj` já
 * capturado no formulário de cima, então o portador não digita os três de novo.
 */
@Immutable
data class CardFormState(
    val number: String = "",
    /** Dígitos só, "MMAA" (mês + 2 dígitos do ano) — a máscara exibe "MM/AA". */
    val expiry: String = "",
    val cvv: String = "",
    val holderName: String = "",
    val postalCode: String = "",
    val addressNumber: String = "",
    val phone: String = "",
    val addressComplement: String = "",
    val mobilePhone: String = "",
    val errors: Set<CardFormError> = emptySet(),
)

enum class CardFormError {
    NumberInvalid,
    ExpiryInvalid,
    CvvInvalid,
    HolderNameRequired,
    PostalCodeInvalid,
    AddressNumberRequired,
    PhoneInvalid,
}

sealed interface PaymentIntent {
    data class UpdateCpfCnpj(val value: String) : PaymentIntent
    data class SelectBillingType(val value: BillingType) : PaymentIntent
    data class UpdateCardNumber(val value: String) : PaymentIntent
    data class UpdateCardExpiry(val value: String) : PaymentIntent
    data class UpdateCardCvv(val value: String) : PaymentIntent
    data class UpdateCardHolderName(val value: String) : PaymentIntent
    data class UpdateCardPostalCode(val value: String) : PaymentIntent
    data class UpdateCardAddressNumber(val value: String) : PaymentIntent
    data class UpdateCardAddressComplement(val value: String) : PaymentIntent
    data class UpdateCardPhone(val value: String) : PaymentIntent
    data class UpdateCardMobilePhone(val value: String) : PaymentIntent
    data object Submit : PaymentIntent

    /** "Já paguei · confirmar" — nunca marca sucesso, só antecipa a checagem do poll. */
    data object ConfirmPayment : PaymentIntent

    /** Reenvia `create()` com o mesmo requestId — não existe endpoint de Pix isolado. */
    data object RegeneratePix : PaymentIntent

    /** Seta do topo com checkout pendente: abre a confirmação em vez de sair direto. */
    data object RequestBack : PaymentIntent
    data object DismissBackConfirmation : PaymentIntent
}

sealed interface PaymentEffect {
    /** Só emitido depois de `receipts()` trazer um recibo real (VUL-105 já processou o webhook). */
    data object NavigateToPlanActive : PaymentEffect
}
