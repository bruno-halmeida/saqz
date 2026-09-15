package br.com.saqz.groups.adapter.output.whatsapp

import br.com.saqz.groups.application.communication.*
import com.uazapi.sdk.UazapiClient
import com.uazapi.sdk.exception.RateLimitException
import com.uazapi.sdk.exception.UazapiApiException
import com.uazapi.sdk.exception.UazapiConnectionException
import com.uazapi.sdk.message.TextMessage

class UazapiNotificationSender(private val client: UazapiClient) : NotificationWhatsAppSender {
    override fun send(message: WhatsAppNotification): WhatsAppDelivery {
        if (!Regex("\\+?[1-9][0-9]{7,14}").matches(message.phone)) return WhatsAppDelivery.Failed
        return try {
            client.messages().sendText(TextMessage.builder(message.phone.removePrefix("+"), message.body)
                .trackSource("saqz").trackId("notification-${message.notificationId}")
                .linkPreview(false).asyncSend(false).build())
            WhatsAppDelivery.Accepted
        } catch (error: RateLimitException) {
            WhatsAppDelivery.Retry((error.retryAfterSeconds() ?: 60).coerceAtLeast(60))
        } catch (error: UazapiApiException) {
            if (error.statusCode() >= 500 || error.statusCode() == 408) WhatsAppDelivery.Retry() else WhatsAppDelivery.Failed
        } catch (_: UazapiConnectionException) {
            WhatsAppDelivery.Retry()
        }
    }
}
