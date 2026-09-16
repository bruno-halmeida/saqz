package br.com.saqz.composeapp.notifications

import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.NativeGroupLinkPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Marks that a notification push was tapped; cleared when navigation opens the center. */
internal class NotificationOpenInbox(private val links: NativeGroupLinkPort) {
    private val mutablePending = MutableStateFlow(false)
    val pending = mutablePending.asStateFlow()
    private var subscription: GroupCancelable? = null

    fun start() {
        if (subscription != null) return
        subscription = links.start(object : GroupLinkEventListener {
            override fun onEvent(event: GroupLinkEvent) {
                if (event !is GroupLinkEvent.NotificationOpen) return
                mutablePending.value = true
            }
        })
    }

    fun stop() {
        subscription?.cancel()
        subscription = null
    }

    fun consume() {
        mutablePending.value = false
    }
}
