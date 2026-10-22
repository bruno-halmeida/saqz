package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.communication.WhatsAppNumberCheck
import br.com.saqz.groups.application.ratelimit.PerUserRateLimiter
import br.com.saqz.groups.application.ratelimit.UserRateLimitedException
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** `hasWhatsApp` nulo = sem resposta (WhatsApp desligado, falha, demora ou cota do usuário): o app não avisa nada. */
data class WhatsAppNumberCheckResponse(val hasWhatsApp: Boolean?)

/**
 * Aviso do cadastro "Esse número não tem WhatsApp" (DEB-095). Só informa: o cadastro nunca depende desta resposta.
 * Exige sessão, como o resto de `/api`, e tem cota por usuário: cada consulta gasta a instância da Uazapi e,
 * sem teto, uma conta qualquer varreria números alheios para saber quais têm WhatsApp.
 */
@RestController
class WhatsAppNumberCheckController(
    private val check: WhatsAppNumberCheck?,
    private val limiter: PerUserRateLimiter,
) {
    @GetMapping("/api/whatsapp/number-check")
    fun check(@AuthenticationPrincipal identity: RequestIdentity, @RequestParam phone: String): WhatsAppNumberCheckResponse {
        val verifier = check ?: return WhatsAppNumberCheckResponse(null)
        return try {
            limiter.acquire(identity.subject)
            WhatsAppNumberCheckResponse(verifier.hasWhatsApp(phone.trim()))
        } catch (_: UserRateLimitedException) {
            WhatsAppNumberCheckResponse(null)
        }
    }

    companion object {
        /** Dez por minuto cobre quem digita e corrige o número algumas vezes; não cobre varredura. */
        const val CHECKS_PER_MINUTE = 10
    }
}
