package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.communication.WhatsAppNumberCheck
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** `hasWhatsApp` nulo = sem resposta (WhatsApp desligado, falha ou demora): o app não avisa nada. */
data class WhatsAppNumberCheckResponse(val hasWhatsApp: Boolean?)

/**
 * Aviso do cadastro "Esse número não tem WhatsApp" (DEB-095). Só informa: o cadastro nunca depende desta resposta.
 * Exige sessão, como o resto de `/api`, porque cada consulta gasta a instância da Uazapi.
 */
@RestController
class WhatsAppNumberCheckController(private val check: WhatsAppNumberCheck?) {
    @GetMapping("/api/whatsapp/number-check")
    fun check(@RequestParam phone: String) = WhatsAppNumberCheckResponse(check?.hasWhatsApp(phone.trim()))
}
