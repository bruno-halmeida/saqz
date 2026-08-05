package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import java.util.UUID

interface AsaasGateway {
    fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String): String

    /**
     * [creditCard]/[creditCardHolderInfo]/[remoteIp] only apply when [billingType] is
     * `CREDIT_CARD` — null otherwise. Throws [br.com.saqz.subscriptions.adapter.output.asaas.CardDeclinedException]
     * (not a generic Asaas error) when Asaas rejects the card itself.
     */
    fun createSubscription(
        asaasCustomerId: String,
        plan: Plan,
        cycle: SubscriptionCycle,
        valueCents: Long,
        billingType: AsaasBillingType,
        idempotencyKey: String,
        creditCard: CreditCardDetails? = null,
        creditCardHolderInfo: CreditCardHolderInfo? = null,
        remoteIp: String? = null,
    ): AsaasSubscriptionCreation
    fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long)

    /** Stops future Asaas billing; local access still follows currentPeriodEnd / grace. */
    fun cancelSubscription(asaasSubscriptionId: String)

    fun createOneOffCharge(
        asaasCustomerId: String,
        valueCents: Long,
        description: String,
        idempotencyKey: String,
    ): String
    fun regeneratePixPayload(asaasChargeId: String): String

    /** Newest payment id for a subscription, if Asaas already generated one. */
    fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String): String?

    /** Hosted invoice / checkout URL for a payment (credit card path). */
    fun findPaymentInvoiceUrl(asaasPaymentId: String): String?

    /**
     * Estado atual da cobranca no Asaas. E a unica forma de responder "ja foi pago?" sem
     * depender do webhook ter chegado — webhook e push e so acontece uma vez.
     */
    fun findPayment(asaasPaymentId: String): AsaasPaymentSnapshot?
}

/** Recorte de `GET /payments/{id}` que a recuperacao de checkout precisa. */
data class AsaasPaymentSnapshot(
    val id: String,
    val status: String?,
    val invoiceUrl: String?,
)

/** Dados de cartao do checkout direto (VUL-194) — nunca logados, nunca persistidos como estao. */
data class CreditCardDetails(
    val holderName: String,
    val number: String,
    val expiryMonth: String,
    val expiryYear: String,
    val ccv: String,
)

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

/** Recorte do `creditCard` da resposta de criacao — token/last4/brand, nunca o PAN. */
data class AsaasCreditCardInfo(
    val token: String?,
    val lastFourDigits: String?,
    val brand: String?,
)

data class AsaasSubscriptionCreation(
    val asaasSubscriptionId: String,
    /** Null quando billingType nao e CREDIT_CARD, ou quando o id veio de recuperacao/reconciliacao. */
    val creditCard: AsaasCreditCardInfo? = null,
)
