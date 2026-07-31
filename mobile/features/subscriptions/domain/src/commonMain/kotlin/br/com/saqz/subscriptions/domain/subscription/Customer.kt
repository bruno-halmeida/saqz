package br.com.saqz.subscriptions.domain.subscription

/**
 * Nome/e-mail de quem está pagando. Contrato próprio de `subscriptions:domain`, e não
 * `access:domain.SessionGateway` direto — `<x>:presentation` só pode depender de `<x>:domain`
 * (AGENTS.md §1 "Regras de dependência": nenhuma feature depende de outra). A implementação
 * real (que de fato lê a sessão) mora em `:compose-app`, o único módulo que enxerga as duas
 * features, e é lá que este contrato é bindado no grafo Koin.
 */
data class CustomerInfo(val displayName: String, val email: String?)

interface CustomerInfoProvider {
    suspend fun current(): CustomerInfo?
}
