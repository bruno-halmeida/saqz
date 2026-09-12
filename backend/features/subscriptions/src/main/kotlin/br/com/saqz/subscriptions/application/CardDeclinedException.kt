package br.com.saqz.subscriptions.application

/**
 * Cartão recusado pelo provedor de cobrança durante a criação da assinatura.
 * O adapter traduz a falha específica do provedor para esta exceção de porta.
 */
class CardDeclinedException(
    val asaasCode: String,
    val asaasDescription: String,
    cause: Throwable,
) : RuntimeException(
    "Card declined ($asaasCode): $asaasDescription",
    cause,
)
