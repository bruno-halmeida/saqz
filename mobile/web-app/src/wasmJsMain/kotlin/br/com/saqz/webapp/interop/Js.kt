package br.com.saqz.webapp.interop

import kotlinx.browser.localStorage

@JsFun("() => JSON.stringify(window.SAQZ_WEB_CONFIG || null)")
internal external fun jsConfigJson(): String?

@JsFun("() => window.matchMedia('(prefers-reduced-motion: reduce)').matches")
internal external fun jsPrefersReducedMotion(): Boolean

/** `localStorage` com as falhas engolidas: o Safari em modo privado lança ao gravar. */
internal object WebStorage {
    fun read(key: String): String? = runCatching { localStorage.getItem(key) }.getOrNull()

    fun write(key: String, value: String?): Boolean = runCatching {
        if (value == null) localStorage.removeItem(key) else localStorage.setItem(key, value)
    }.isSuccess
}
