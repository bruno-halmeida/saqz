package br.com.saqz.webapp.access

import br.com.saqz.access.domain.port.AppOnboardingCodeListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.InviteCodeListener
import br.com.saqz.access.domain.port.NativeLinkPort
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.NativeGroupLinkPort

// STUB: substituir pelo port de links (leitura de saqz_invite / saqz_onboarding / attendance na URL),
// espelhando AndroidLinkAdapter.kt.
internal class WebLinkAdapter(private val allowedHosts: Set<String>) : NativeLinkPort, NativeGroupLinkPort {
    fun onColdStart(url: String?) = Unit

    override fun start(listener: InviteCodeListener): Cancelable = object : Cancelable { override fun cancel() = Unit }

    override fun startAppOnboarding(listener: AppOnboardingCodeListener): Cancelable =
        object : Cancelable { override fun cancel() = Unit }

    override fun start(listener: GroupLinkEventListener): GroupCancelable =
        object : GroupCancelable { override fun cancel() = Unit }
}
