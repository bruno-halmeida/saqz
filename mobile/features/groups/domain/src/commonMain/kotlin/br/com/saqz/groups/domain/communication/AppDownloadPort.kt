package br.com.saqz.groups.domain.communication

/**
 * Oferta do app nativo a quem respondeu pelo navegador ("quer lembrete no celular?"). Só o web app
 * tem: a loja que serve ao aparelho atual, ou nada quando não há loja para oferecer (iPhone enquanto
 * a App Store não estiver liberada, computador). Android e iOS passam `null`.
 */
interface AppDownloadPort {
    /** URL da loja do app para este aparelho; `null` quando não há o que oferecer. */
    fun appDownloadUrl(): String?

    /** Abre [url] (a devolvida por [appDownloadUrl]) numa aba nova. */
    fun openAppDownload(url: String)
}
