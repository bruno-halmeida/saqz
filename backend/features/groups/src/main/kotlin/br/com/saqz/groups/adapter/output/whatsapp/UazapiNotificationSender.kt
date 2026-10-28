package br.com.saqz.groups.adapter.output.whatsapp

import br.com.saqz.groups.application.communication.*
import com.uazapi.sdk.UazapiClient
import com.uazapi.sdk.exception.RateLimitException
import com.uazapi.sdk.exception.UazapiApiException
import com.uazapi.sdk.exception.UazapiConnectionException
import com.uazapi.sdk.message.MenuMessage
import com.uazapi.sdk.message.MenuType
import com.uazapi.sdk.message.TextMessage

class UazapiNotificationSender(private val client: UazapiClient) : NotificationWhatsAppSender {
    override fun send(message: WhatsAppNotification): WhatsAppDelivery {
        if (!Regex("\\+?[1-9][0-9]{7,14}").matches(message.phone)) return WhatsAppDelivery.Failed
        val phone = message.phone.removePrefix("+")
        val track = message.trackId ?: "notification-${message.notificationId}"
        return try {
            val button = message.button
            if (button == null) client.messages().sendText(TextMessage.builder(phone, message.body)
                .trackSource("saqz").trackId(track).linkPreview(false).asyncSend(false).build())
            else client.messages().sendMenu(MenuMessage.builder(phone, MenuType.BUTTON, message.body, listOf("${button.label}|${button.url}"))
                .trackSource("saqz").trackId(track).asyncSend(false).build())
            // A segunda mensagem é só o copia e cola. Falha nela não reenvia a primeira (fica no log do Uazapi).
            message.followUp?.let { code ->
                runCatching { client.messages().sendText(TextMessage.builder(phone, code).trackSource("saqz").trackId("$track-pix")
                    .linkPreview(false).asyncSend(false).build()) }
            }
            WhatsAppDelivery.Accepted
        } catch (error: RateLimitException) {
            WhatsAppDelivery.Retry((error.retryAfterSeconds() ?: 60).coerceAtLeast(60))
        } catch (error: UazapiApiException) {
            if (error.statusCode() >= 500 || error.statusCode() in RETRYABLE_4XX) WhatsAppDelivery.Retry() else WhatsAppDelivery.Failed
        } catch (_: UazapiConnectionException) {
            WhatsAppDelivery.Retry()
        }
    }
}

/** 408 e 401/403 (token da instância errado ou trocado) tentam de novo: a mensagem sai quando o token é corrigido. */
internal val RETRYABLE_4XX = setOf(401, 403, 408)
