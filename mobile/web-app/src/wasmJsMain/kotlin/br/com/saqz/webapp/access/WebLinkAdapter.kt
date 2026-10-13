package br.com.saqz.webapp.access

import br.com.saqz.access.domain.port.AppOnboardingCodeListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.InviteCodeListener
import br.com.saqz.access.domain.port.NativeLinkPort
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.NativeGroupLinkPort
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.window

/**
 * Links do Saqz no navegador: o convite, o onboarding e a presença chegam na URL com que a página abriu
 * (a página de links manda para `/app/?saqz_invite=…`). Espelho de `AndroidLinkAdapter`: o evento fica
 * pendente até alguém escutar, e o código de onboarding vive num canal separado do convite.
 *
 * Depois de consumir um código a barra de endereço é limpa (`history.replaceState`): sem isso, um reload
 * repetiria o convite. [replaceAddress] recebe o endereço relativo já sem os parâmetros `saqz_*`.
 */
internal class WebLinkAdapter(
    allowedHosts: Set<String>,
    linksHost: String = DEFAULT_LINKS_HOST,
    private val replaceAddress: (String) -> Unit = ::replaceBrowserAddress,
) : NativeLinkPort, NativeGroupLinkPort {
    private val parser = WebLinkParser(allowedHosts, linksHost)
    private var accessListener: InviteCodeListener? = null
    private var onboardingListener: AppOnboardingCodeListener? = null
    private val groupListeners = mutableSetOf<GroupLinkEventListener>()
    private var pendingAccessCode: String? = null
    private var pendingOnboardingCode: String? = null
    private var pendingGroupEvent: GroupLinkEvent? = null

    override fun start(listener: InviteCodeListener): Cancelable {
        accessListener = listener
        pendingAccessCode?.let(listener::onInviteCode)
        pendingAccessCode = null
        return object : Cancelable {
            override fun cancel() {
                if (accessListener === listener) accessListener = null
            }
        }
    }

    override fun startAppOnboarding(listener: AppOnboardingCodeListener): Cancelable {
        onboardingListener = listener
        pendingOnboardingCode?.let(listener::onAppOnboardingCode)
        pendingOnboardingCode = null
        return object : Cancelable {
            override fun cancel() {
                if (onboardingListener === listener) onboardingListener = null
            }
        }
    }

    override fun start(listener: GroupLinkEventListener): GroupCancelable {
        groupListeners += listener
        pendingGroupEvent?.let(listener::onEvent)
        return object : GroupCancelable {
            override fun cancel() {
                groupListeners -= listener
            }
        }
    }

    /** Chamado com `window.location.href` na largada do app. */
    fun onColdStart(url: String?) {
        val onboarding = parser.directOnboardingCode(url)
        val event = parser.directEvent(url)
        acceptOnboarding(onboarding)
        accept(event)
        if (onboarding != null || event != null) parser.cleanedAddress(url)?.let(replaceAddress)
    }

    private fun acceptOnboarding(code: String?) {
        val accepted = code ?: return
        val current = onboardingListener
        if (current == null) pendingOnboardingCode = accepted else current.onAppOnboardingCode(accepted)
    }

    private fun accept(event: GroupLinkEvent?) {
        val accepted = event ?: return
        if (accepted is GroupLinkEvent.Invite) {
            val current = accessListener
            if (current == null) pendingAccessCode = accepted.code else current.onInviteCode(accepted.code)
        }
        if (groupListeners.isEmpty()) {
            pendingGroupEvent = accepted
        } else {
            pendingGroupEvent = null
            groupListeners.forEach { it.onEvent(accepted) }
        }
    }

    companion object {
        /** Mesmo padrão de `WebConfig.linksHost`. */
        const val DEFAULT_LINKS_HOST = "links.saqz.app"
    }
}

/** `replaceState` mantém a mesma entrada do histórico; engolido em contextos que o proíbem (iframe sandbox). */
@OptIn(ExperimentalWasmJsInterop::class)
private fun replaceBrowserAddress(address: String) {
    runCatching { window.history.replaceState(null, "", address) }
}
