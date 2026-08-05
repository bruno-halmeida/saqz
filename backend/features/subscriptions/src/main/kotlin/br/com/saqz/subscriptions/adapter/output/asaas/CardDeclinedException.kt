package br.com.saqz.subscriptions.adapter.output.asaas

/**
 * Cartão recusado na criação da assinatura (~linha 69 de [HttpAsaasGateway]). Extends
 * [AsaasException] so the existing 4xx idempotency-release path (`isDefinitiveClientRejection`)
 * keeps working unchanged — only [br.com.saqz.subscriptions.application.CreateSubscription] needs
 * to know this is a decline, not a generic Asaas failure.
 */
class CardDeclinedException(
    val asaasCode: String,
    val asaasDescription: String,
    cause: AsaasException,
) : AsaasException(
    statusCode = cause.statusCode,
    message = "Card declined ($asaasCode): $asaasDescription",
    cause = cause,
    errorCode = asaasCode,
    errorDescription = asaasDescription,
)
