package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import java.util.UUID

interface AsaasGateway {
    fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String): String
    fun createSubscription(
        asaasCustomerId: String,
        plan: Plan,
        cycle: SubscriptionCycle,
        valueCents: Long,
        billingType: AsaasBillingType,
        idempotencyKey: String,
    ): String
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
