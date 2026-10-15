package br.com.saqz.subscriptions.application

/**
 * A Asaas não aceita a forma de pagamento para assinaturas nesta conta (ex.: Pix sem chave Pix
 * cadastrada). O adapter traduz a falha do provedor para esta exceção de porta.
 */
class PaymentMethodUnavailableException(
    val billingType: AsaasBillingType,
    val asaasCode: String,
    cause: Throwable,
) : RuntimeException("Payment method unavailable ($billingType): $asaasCode", cause)
