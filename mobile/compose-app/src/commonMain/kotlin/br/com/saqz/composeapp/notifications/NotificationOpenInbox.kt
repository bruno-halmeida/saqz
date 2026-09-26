package br.com.saqz.composeapp.notifications

import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.NativeGroupLinkPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Guarda o último toque em push até a navegação consumir: com jogo abre o jogo, sem jogo a central. */
internal class NotificationOpenInbox(private val links: NativeGroupLinkPort) {
    private val mutablePending = MutableStateFlow<GroupLinkEvent.NotificationOpen?>(null)
    val pending = mutablePending.asStateFlow()
    private var subscription: GroupCancelable? = null

    fun start() {
        if (subscription != null) return
        subscription = links.start(object : GroupLinkEventListener {
            override fun onEvent(event: GroupLinkEvent) {
                if (event !is GroupLinkEvent.NotificationOpen) return
                mutablePending.value = event
            }
        })
    }

    fun stop() {
        subscription?.cancel()
        subscription = null
    }

    fun consume() {
        mutablePending.value = null
    }
}
