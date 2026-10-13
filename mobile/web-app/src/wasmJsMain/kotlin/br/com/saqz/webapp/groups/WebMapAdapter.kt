package br.com.saqz.webapp.groups

import br.com.saqz.groups.domain.map.GroupMapCallback
import br.com.saqz.groups.domain.map.GroupMapPort
import kotlinx.browser.window

/** Abre o endereço do jogo numa aba nova; no celular o sistema oferece o app de mapas. */
internal class WebMapAdapter : GroupMapPort {
    override fun open(url: String, done: GroupMapCallback) {
        val opened = runCatching { window.open(url, "_blank", "noopener") }.getOrNull() != null
        done.complete(opened)
    }
}
