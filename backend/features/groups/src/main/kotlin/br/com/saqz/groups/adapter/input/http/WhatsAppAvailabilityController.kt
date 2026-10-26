package br.com.saqz.groups.adapter.input.http

import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.client.CLIENT_APP_REQUEST_ATTRIBUTE
import br.com.saqz.sharedkernel.client.ClientApp
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class WhatsAppAvailabilityResponse(val enabled: Boolean)

/**
 * Se o WhatsApp do Saqz está ligado para quem pergunta. O app só mostra o vínculo com o grupo do
 * WhatsApp e a aba de WhatsApp das preferências com `true`. Desde a DEB-119 vale a integração no
 * servidor (`saqz.notifications.whatsapp.enabled`) **e** a flag `whatsapp`; os apps que ainda não leem
 * `/api/features` continuam perguntando aqui.
 */
@RestController
class WhatsAppAvailabilityController(private val enabled: (subject: String?, client: ClientApp) -> Boolean) {
    constructor(enabled: Boolean) : this({ _, _ -> enabled })

    @GetMapping("/api/whatsapp/availability")
    fun availability(@AuthenticationPrincipal identity: RequestIdentity?, request: HttpServletRequest?) =
        WhatsAppAvailabilityResponse(enabled(identity?.subject, ClientApp.fromRequestAttribute(request?.getAttribute(CLIENT_APP_REQUEST_ATTRIBUTE))))
}
