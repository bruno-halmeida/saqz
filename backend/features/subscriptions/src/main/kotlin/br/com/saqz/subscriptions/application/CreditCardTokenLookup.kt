package br.com.saqz.subscriptions.application

/** Lê o token de cartão guardado por [CreditCardTokenStore]; nulo quando a assinatura não é de cartão. */
fun interface CreditCardTokenLookup {
    fun tokenFor(asaasSubscriptionId: String): String?
}
