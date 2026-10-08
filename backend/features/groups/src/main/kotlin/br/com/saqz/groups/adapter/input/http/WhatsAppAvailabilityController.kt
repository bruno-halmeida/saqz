package br.com.saqz.groups.adapter.input.http

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class WhatsAppAvailabilityResponse(val enabled: Boolean)

/**
 * Se o WhatsApp do Saqz está ligado neste servidor (`saqz.notifications.whatsapp.enabled`). O app
 * só mostra o vínculo com o grupo do WhatsApp e a aba de WhatsApp das preferências com `true`:
 * ligar e desligar fica aqui, sem versão nova do app.
 */
@RestController
class WhatsAppAvailabilityController(private val enabled: Boolean) {
    @GetMapping("/api/whatsapp/availability")
    fun availability() = WhatsAppAvailabilityResponse(enabled)
}
