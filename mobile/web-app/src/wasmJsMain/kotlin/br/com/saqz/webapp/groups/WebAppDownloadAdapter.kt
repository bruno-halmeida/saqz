package br.com.saqz.webapp.groups

import br.com.saqz.groups.domain.communication.AppDownloadPort
import kotlinx.browser.window

internal const val GOOGLE_PLAY_URL = "https://play.google.com/store/apps/details?id=app.saqz"

// O mesmo endereço de `appStore` em links-page/index.html.
internal const val APP_STORE_URL = "https://apps.apple.com/br/app/id6812743525"

/**
 * A loja do app para o aparelho do navegador: Google Play no Android, App Store no iPhone (vazia
 * quando não configurada, então sem oferta) e nada no computador.
 */
internal fun appDownloadUrlFor(userAgent: String, appStoreUrl: String = APP_STORE_URL): String? = when {
    "Android" in userAgent -> GOOGLE_PLAY_URL
    "iPhone" in userAgent || "iPad" in userAgent || "iPod" in userAgent -> appStoreUrl.ifBlank { null }
    else -> null
}

/** Oferta do app depois da primeira resposta, pela loja do aparelho (só o web app oferece). */
internal class WebAppDownloadAdapter(
    private val userAgent: () -> String = { window.navigator.userAgent },
) : AppDownloadPort {
    override fun appDownloadUrl(): String? = appDownloadUrlFor(userAgent())

    override fun openAppDownload(url: String) {
        runCatching { window.open(url, "_blank", "noopener") }
    }
}
