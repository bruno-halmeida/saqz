package br.com.saqz.composeapp.receivables

import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.NativeGroupLinkPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Guarda o código do botão da hora do acerto (FIN-013) até haver sessão: deslogado, passa pelo login e depois abre o
 * pagamento, como o link de presença. Fica em memória: o código vale só para o toque que acabou de acontecer.
 */
internal class SettleLinkInbox(private val links: NativeGroupLinkPort) {
    private val mutablePending = MutableStateFlow<String?>(null)
    val pending = mutablePending.asStateFlow()
    private var subscription: GroupCancelable? = null

    fun start() {
        if (subscription != null) return
        subscription = links.start(object : GroupLinkEventListener {
            override fun onEvent(event: GroupLinkEvent) {
                if (event is GroupLinkEvent.Settle) mutablePending.value = event.code
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
