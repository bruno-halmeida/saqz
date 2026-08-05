package br.com.saqz.subscriptions.application

/**
 * Persiste o token de cartão devolvido pelo Asaas na criação (VUL-194) — nunca o PAN/CVV, que
 * nunca chegam a este ponto. Serve para trocar cartão / gerar nova cobrança sem redigitar; a
 * Asaas cobra sozinha as renovações usando o `asaasSubscriptionId`, não este token.
 *
 * `token` nulo limpa as colunas: uma reativação sem cartão (ex.: PIX) precisa apagar o token/last4/
 * brand de uma assinatura de cartão anterior que ocupava a mesma linha — senão a coluna some do
 * `asaasSubscriptionId` novo mas continua com o token órfão do antigo (achado do Codex no PR #179).
 */
fun interface CreditCardTokenStore {
    fun save(asaasSubscriptionId: String, token: String?, lastFourDigits: String?, brand: String?)
}
