package br.com.saqz.subscriptions.adapter.output.asaas

/**
 * Credenciais e endpoint do Asaas. A chave nunca mora em código — vem de
 * `SAQZ_ASAAS_API_KEY` / `saqz.asaas.api-key` (mesmo padrão de env do bootstrap).
 */
data class AsaasClientSettings(
    val baseUrl: String,
    val apiKey: String,
    val userAgent: String = DEFAULT_USER_AGENT,
) {
    init {
        require(baseUrl.isNotBlank()) { "Asaas base URL must not be blank" }
        require(apiKey.isNotBlank()) { "Asaas API key must not be blank" }
    }

    companion object {
        const val DEFAULT_USER_AGENT = "saqz-backend"
        const val DEFAULT_SANDBOX_BASE_URL = "https://api-sandbox.asaas.com/v3"

        fun fromProperties(lookup: (String) -> String?): AsaasClientSettings {
            val apiKey = lookup("SAQZ_ASAAS_API_KEY")
                ?: lookup("saqz.asaas.api-key")
                ?: error("Asaas API key is required (SAQZ_ASAAS_API_KEY or saqz.asaas.api-key)")
            val baseUrl = lookup("SAQZ_ASAAS_BASE_URL")
                ?: lookup("saqz.asaas.base-url")
                ?: DEFAULT_SANDBOX_BASE_URL
            val userAgent = lookup("SAQZ_ASAAS_USER_AGENT")
                ?: lookup("saqz.asaas.user-agent")
                ?: DEFAULT_USER_AGENT
            return AsaasClientSettings(
                baseUrl = baseUrl.trimEnd('/'),
                apiKey = apiKey,
                userAgent = userAgent,
            )
        }
    }
}
