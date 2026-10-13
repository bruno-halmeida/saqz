package br.com.saqz.webapp

import br.com.saqz.webapp.interop.jsConfigJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class WebFirebaseConfig(
    val apiKey: String,
    val authDomain: String,
    val projectId: String,
    val appId: String? = null,
    val authEmulatorUrl: String? = null,
)

/** Lido de `window.SAQZ_WEB_CONFIG` (saqz-config.js): faz o papel do BuildConfig do Android. */
@Serializable
internal data class WebConfig(
    val environment: String,
    val apiBaseUrl: String,
    val linksHost: String = "links.saqz.app",
    val appVersion: String = "web",
    /** Página de assinatura do site (Asaas). Relativa ao origin: em produção vira saqz.app/assinar/. */
    val checkoutUrl: String = "/assinar/",
    val firebase: WebFirebaseConfig,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun read(): WebConfig {
            val raw = jsConfigJson() ?: error("window.SAQZ_WEB_CONFIG ausente: saqz-config.js não carregou")
            return json.decodeFromString(serializer(), raw)
        }
    }
}
