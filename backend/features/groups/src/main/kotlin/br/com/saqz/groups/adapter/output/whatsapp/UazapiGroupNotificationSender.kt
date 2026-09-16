package br.com.saqz.groups.adapter.output.whatsapp

import br.com.saqz.groups.application.communication.NotificationWhatsAppGroupSender
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import com.uazapi.sdk.UazapiClient
import com.uazapi.sdk.exception.RateLimitException
import com.uazapi.sdk.exception.UazapiApiException
import com.uazapi.sdk.exception.UazapiConnectionException
import com.uazapi.sdk.message.MenuMessage
import com.uazapi.sdk.message.MenuType
import com.uazapi.sdk.message.TextMessage
import java.util.UUID

/**
 * Entrega ao grupo por `/send/text` e, quando há link (lembrete de presença), por `/send/menu`
 * com botão CTA de URL — a URL crua não aparece no texto. O destino é o JID do grupo
 * (`...@g.us`), então não há regex de telefone: JID não é número. A classificação de erro é
 * idêntica à do sender DM.
 */
class UazapiGroupNotificationSender(private val client: UazapiClient) : NotificationWhatsAppGroupSender {
    override fun send(jid: String, messageId: UUID, text: String, link: String?): WhatsAppDelivery =
        if (link == null) sendText(jid, messageId, text) else sendButton(jid, messageId, text, link)

    private fun sendButton(jid: String, messageId: UUID, text: String, link: String): WhatsAppDelivery {
        val result = deliver {
            client.messages().sendMenu(
                MenuMessage.builder(jid, MenuType.BUTTON, text, listOf("$BUTTON_LABEL|$link"))
                    .trackSource("saqz").trackId("group-$messageId").asyncSend(false).build(),
            )
        }
        // Botão recusado de forma permanente (provider/cliente sem suporte): não perder o
        // lembrete — reenvia como texto com a URL ao final.
        return if (result == WhatsAppDelivery.Failed) sendText(jid, messageId, "$text\n$TEXT_LINK_PREFIX$link") else result
    }

    private fun sendText(jid: String, messageId: UUID, body: String): WhatsAppDelivery = deliver {
        client.messages().sendText(
            TextMessage.builder(jid, body)
                .trackSource("saqz").trackId("group-$messageId")
                .linkPreview(false).asyncSend(false).build(),
        )
    }

    private inline fun deliver(block: () -> Unit): WhatsAppDelivery = try {
        block()
        WhatsAppDelivery.Accepted
    } catch (error: RateLimitException) {
        WhatsAppDelivery.Retry((error.retryAfterSeconds() ?: 60).coerceAtLeast(60))
    } catch (error: UazapiApiException) {
        if (error.statusCode() >= 500 || error.statusCode() == 408) WhatsAppDelivery.Retry() else WhatsAppDelivery.Failed
    } catch (_: UazapiConnectionException) {
        WhatsAppDelivery.Retry()
    }

    private companion object {
        const val BUTTON_LABEL = "Confirmar presença"
        const val TEXT_LINK_PREFIX = "Confirmar minha presença no Saqz: "
    }
}
