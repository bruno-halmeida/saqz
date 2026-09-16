package br.com.saqz.groups.adapter.output.whatsapp

import br.com.saqz.groups.application.communication.NotificationWhatsAppGroupSender
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import com.uazapi.sdk.UazapiClient
import com.uazapi.sdk.exception.RateLimitException
import com.uazapi.sdk.exception.UazapiApiException
import com.uazapi.sdk.exception.UazapiConnectionException
import com.uazapi.sdk.message.TextMessage
import java.util.UUID

/**
 * Entrega ao grupo via `/send/text`. O destino é o JID do grupo (`...@g.us`), então não há
 * regex de telefone: JID não é número. A classificação de erro é idêntica à do sender DM.
 */
class UazapiGroupNotificationSender(private val client: UazapiClient) : NotificationWhatsAppGroupSender {
    override fun send(jid: String, messageId: UUID, body: String): WhatsAppDelivery = try {
        client.messages().sendText(TextMessage.builder(jid, body)
            .trackSource("saqz").trackId("group-$messageId")
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
