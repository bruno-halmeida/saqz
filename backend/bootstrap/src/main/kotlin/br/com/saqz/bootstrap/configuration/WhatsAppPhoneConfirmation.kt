package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.input.http.PhoneConfirmationController
import br.com.saqz.access.application.emailverification.PhoneConfirmationSender
import br.com.saqz.access.application.session.AppOnboardingCode
import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.communication.WhatsAppNotification
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * Reaproveita o sender da Uazapi das notificações. Sem fila: é o toque do usuário que
 * dispara, e quem não recebeu toca "Reenviar" de novo. Fora da thread do request porque
 * a Uazapi tem 30 s de timeout de leitura e o PATCH do perfil não pode esperar por ela.
 */
class WhatsAppPhoneConfirmation(
    private val whatsApp: NotificationWhatsAppSender,
    private val apiBaseUrl: String,
) : PhoneConfirmationSender {
    override fun send(phone: String, code: AppOnboardingCode) {
        val link = "${apiBaseUrl.trimEnd('/')}${PhoneConfirmationController.PATH_PREFIX}/${code.value}"
        val body = "Saqz: toque no link para confirmar sua conta.\n\n$link\n\nSe não foi você, ignore esta mensagem."
        // ponytail: pool comum do JDK; executor próprio quando o volume pedir.
        CompletableFuture.runAsync {
            val delivery = whatsApp.send(WhatsAppNotification(notificationId = 0, phone = phone, body = body))
            if (delivery != WhatsAppDelivery.Accepted) log.warn("account_confirmation_whatsapp_failed delivery={}", delivery)
        }.exceptionally { failure ->
            log.error("account_confirmation_whatsapp_failed", failure)
            null
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(WhatsAppPhoneConfirmation::class.java)
    }
}
