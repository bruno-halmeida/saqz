package br.com.saqz.subscriptions.domain.subscription

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

enum class Plan { Titular, Organizador, Ilimitado }

enum class SubscriptionCycle { Monthly, Annual }

enum class BillingType { Pix, CreditCard }

enum class SubscriptionStatus { Active, PastDue, Canceled }

data class PlanDetails(
    val id: Plan,
    val name: String,
    val monthlyPriceCents: Long,
    val annualPriceCents: Long,
    val maxGroups: Int?,
    val maxAthletes: Int?,
    val multiAdmin: Boolean,
    val reports: Boolean,
    val whatsappSla: Boolean,
)

sealed interface CouponValidation {
    data class Applied(
        val code: String,
        val planId: Plan,
        val cycle: SubscriptionCycle,
        val discountPercent: Int,
        val listPriceCents: Long,
        val finalPriceCents: Long,
        val validUntil: String?,
    ) : CouponValidation

    data object NotFound : CouponValidation

    data object Expired : CouponValidation
}

data class SubscriptionUsage(val groupsUsed: Int, val groupsLimit: Int?)

data class MySubscription(
    val status: SubscriptionStatus,
    /** Regra do backend (`Subscription.isEntitlingAt`): pode criar grupo se também houver vaga. */
    val entitled: Boolean,
    val plan: Plan,
    val cycle: SubscriptionCycle,
    val pendingPlan: Plan?,
    val pendingPlanEffectiveAt: String?,
    val currentPeriodEnd: String,
    val paymentMethod: BillingType?,
    val usage: SubscriptionUsage,
    val readOnly: Boolean,
    val pastDueSince: String?,
    val canceledAt: String?,
    // VUL-196: binding pronto para quando o endpoint de leitura passar a devolver os dois —
    // hoje o backend não expõe, então ambos chegam nulos e a UI cai no rótulo genérico.
    val cardLast4: String? = null,
    val cardBrand: String? = null,
)

/** Dados de cartão: só em memória (`PaymentState`), nunca em `SavedStateHandle` — PCI (VUL-196). */
data class CreditCardInfo(
    val holderName: String,
    val number: String,
    val expiryMonth: String,
    val expiryYear: String,
    val ccv: String,
)

/**
 * `name`/`email`/`cpfCnpj` não duplicam campo de UI: [CreateSubscriptionCommand.name]/`email`/
 * `cpfCnpj` já carregam a mesma informação (sessão + CPF/CNPJ do formulário), então o
 * `PaymentViewModel` os reaproveita ao montar este bloco em vez de pedir de novo ao portador.
 */
data class CreditCardHolderInfo(
    val name: String,
    val email: String,
    val cpfCnpj: String,
    val postalCode: String,
    val addressNumber: String,
    val phone: String,
    val addressComplement: String? = null,
    val mobilePhone: String? = null,
)

data class CreateSubscriptionCommand(
    val requestId: String,
    val planId: Plan,
    val cycle: SubscriptionCycle,
    val billingType: BillingType,
    val name: String,
    val email: String,
    val cpfCnpj: String,
    val couponCode: String? = null,
    val creditCard: CreditCardInfo? = null,
    val creditCardHolderInfo: CreditCardHolderInfo? = null,
)

data class CreatedSubscription(
    val ownerUserId: String,
    val planId: Plan,
    val cycle: SubscriptionCycle,
    val status: SubscriptionStatus,
    val asaasSubscriptionId: String,
    val currentPeriodEnd: String,
    val billingType: BillingType,
    val pixCopyPaste: String?,
    val invoiceUrl: String?,
)

data class ChangePlanCommand(val requestId: String, val targetPlanId: Plan)

data class ChangePlanResult(
    val planId: Plan,
    val pendingPlanId: Plan?,
    val pendingPlanEffectiveAt: String?,
    val pendingUpgradePlanId: Plan?,
    val status: SubscriptionStatus,
    val chargedCents: Long?,
    val pixCopyPaste: String?,
    val invoiceUrl: String?,
)

data class CanceledSubscription(
    val status: SubscriptionStatus,
    val canceledAt: String,
    val currentPeriodEnd: String,
)

data class Receipt(
    val asaasEventId: String,
    val asaasPaymentId: String?,
    val valueCents: Long?,
    val confirmedAt: String?,
    val processedAt: String,
)

sealed interface SubscriptionError : SaqzError {
    data class Validation(val error: DataError.Validation) : SubscriptionError
    data object NotFound : SubscriptionError // 404 SUBSCRIPTION_NOT_FOUND
    data object Conflict : SubscriptionError // 409 SUBSCRIPTION_CONFLICT

    /** 409 SUBSCRIPTION_PENDING_CHECKOUT_MISMATCH — checkout pendente para OUTRO plano/ciclo/forma. */
    data object PendingCheckoutMismatch : SubscriptionError
    data object CouponNotFound : SubscriptionError // 404 COUPON_NOT_FOUND (create flow)
    data object CouponExpired : SubscriptionError // 410 COUPON_EXPIRED
    data object CouponAlreadyRedeemed : SubscriptionError // 409 COUPON_ALREADY_REDEEMED
    data object DowngradeBlocked : SubscriptionError // 409 DOWNGRADE_BLOCKED

    /** 402 card_declined — `reason` é o código Asaas (não exibir), `message` já vem em PT-BR. */
    data class CardDeclined(val reason: String, val message: String) : SubscriptionError
    data class Data(val error: DataError) : SubscriptionError
}

interface SubscriptionGateway {
    suspend fun plans(): SaqzResult<List<PlanDetails>, SubscriptionError>

    suspend fun validateCoupon(
        code: String,
        planId: Plan,
        cycle: SubscriptionCycle = SubscriptionCycle.Monthly,
    ): SaqzResult<CouponValidation, SubscriptionError>

    suspend fun mySubscription(): SaqzResult<MySubscription, SubscriptionError>

    suspend fun create(command: CreateSubscriptionCommand): SaqzResult<CreatedSubscription, SubscriptionError>

    suspend fun changePlan(command: ChangePlanCommand): SaqzResult<ChangePlanResult, SubscriptionError>

    suspend fun cancel(): SaqzResult<CanceledSubscription, SubscriptionError>

    /**
     * Uma página de recibos, do mais recente para o mais antigo. Sem tipo de página: quem
     * chama infere "tem mais?" comparando `resultado.size == limit` — página cheia pode ter
     * continuação, página curta acabou. `limit`/`offset` são obrigatórios de propósito
     * (VUL-120): o backend tem default próprio, e um cliente que omite volta a depender dele.
     */
    suspend fun receipts(limit: Int, offset: Int): SaqzResult<List<Receipt>, SubscriptionError>
}
