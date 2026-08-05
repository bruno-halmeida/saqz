package br.com.saqz.subscriptions.application

/**
 * Persiste o token de cartão devolvido pelo Asaas na criação (VUL-194) — nunca o PAN/CVV, que
 * nunca chegam a este ponto. Serve para trocar cartão / gerar nova cobrança sem redigitar; a
 * Asaas cobra sozinha as renovações usando o `asaasSubscriptionId`, não este token.
 */
fun interface CreditCardTokenStore {
    fun save(asaasSubscriptionId: String, token: String, lastFourDigits: String?, brand: String?)
}
