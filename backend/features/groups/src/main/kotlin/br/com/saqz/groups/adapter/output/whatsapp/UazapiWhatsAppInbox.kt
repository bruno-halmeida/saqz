package br.com.saqz.groups.adapter.output.whatsapp

import br.com.saqz.groups.application.whatsapp.AdminWhatsAppInbox
import br.com.saqz.groups.application.whatsapp.AdminWhatsAppMessages
import br.com.saqz.groups.application.whatsapp.DirectoryError
import br.com.saqz.groups.application.whatsapp.InboxChat
import br.com.saqz.groups.application.whatsapp.InboxChatPage
import br.com.saqz.groups.application.whatsapp.InboxMessage
import br.com.saqz.groups.application.whatsapp.InboxMessageKind
import br.com.saqz.groups.application.whatsapp.InboxMessagePage
import com.fasterxml.jackson.databind.JsonNode
import com.uazapi.sdk.UazapiClient
import com.uazapi.sdk.chat.ChatQuery
import com.uazapi.sdk.exception.UazapiApiException
import com.uazapi.sdk.exception.UazapiException
import com.uazapi.sdk.message.MessageQuery
import java.time.Instant

/**
 * Único ponto que lê conversas e mensagens da Uazapi. Copia só os campos da porta: `owner`, `lead_*`,
 * `chatbot_summary`, `sendPayload` e `fileURL` nunca saem daqui. O corpo de erro da Uazapi também não
 * entra na exceção, porque pode ecoar conteúdo.
 */
class UazapiWhatsAppInbox(private val client: UazapiClient) : AdminWhatsAppInbox {
    override fun chats(): InboxChatPage {
        val query = ChatQuery.builder().sort(LATEST_FIRST).limit(MAX_CHATS).offset(0).build()
        val body = call { client.chats().findChats(query) }
        val chats = list(body, "chats").mapNotNull(::chat)
            .filter { AdminWhatsAppMessages.validChat(it.chatId) }
            .sortedByDescending { it.lastMessageAt }
        return InboxChatPage(chats, body.path("pagination").path("hasMore").asBoolean(false))
    }

    override fun privateChat(chatId: String): InboxChat? {
        val body = call { client.chats().findChats(ChatQuery.builder().waChatid(chatId).limit(1).build()) }
        return list(body, "chats").mapNotNull(::chat).firstOrNull { it.chatId == chatId && AdminWhatsAppMessages.isPrivate(it.chatId) }
    }

    override fun messages(chatId: String, offset: Int, limit: Int): InboxMessagePage {
        val query = MessageQuery.builder().chatid(chatId).limit(limit).offset(offset).build()
        val body = call { client.messages().findMessages(query) }
        return InboxMessagePage(
            messages = list(body, "messages").filter { text(it, "messageType") !in HIDDEN_TYPES }
                .mapNotNull(::message).sortedBy { it.at },
            hasMore = body.path("hasMore").asBoolean(false),
            nextOffset = body.get("nextOffset")?.takeIf { it.isNumber }?.asInt(),
        )
    }

    override fun trackExists(trackId: String): Boolean {
        val query = MessageQuery.builder().trackSource(TRACK_SOURCE).trackId(trackId).limit(1).build()
        return list(call { client.messages().findMessages(query) }, "messages").isNotEmpty()
    }

    private fun call(request: () -> JsonNode?): JsonNode = try {
        request() ?: throw DirectoryError.Unavailable("empty response")
    } catch (error: UazapiApiException) {
        throw DirectoryError.Unavailable("HTTP ${error.statusCode()}")
    } catch (_: UazapiException) {
        throw DirectoryError.Unavailable("connection")
    }

    private fun chat(node: JsonNode): InboxChat? {
        val chatId = text(node, "wa_chatid") ?: return null
        val at = instant(node, "wa_lastMsgTimestamp") ?: return null
        return InboxChat(
            chatId = chatId,
            name = text(node, "wa_contactName") ?: text(node, "wa_name") ?: text(node, "name"),
            phone = chatId.takeIf(AdminWhatsAppMessages::isPrivate)?.let(::phone),
            lastMessageAt = at,
            preview = text(node, "wa_lastMessageTextVote"),
            previewKind = kind(text(node, "wa_lastMessageType")),
        )
    }

    private fun message(node: JsonNode): InboxMessage? {
        return InboxMessage(
            id = text(node, "messageid") ?: return null,
            fromMe = node.path("fromMe").asBoolean(false),
            at = instant(node, "messageTimestamp") ?: return null,
            senderName = text(node, "senderName"),
            senderPhone = text(node, "sender_pn")?.let(::phone)?.takeIf { it.isNotEmpty() },
            text = text(node, "text"),
            kind = kind(text(node, "messageType")),
        )
    }

    private fun list(body: JsonNode, field: String): List<JsonNode> = body.get(field)?.takeIf { it.isArray }?.toList().orEmpty()

    private fun text(node: JsonNode, field: String): String? =
        node.get(field)?.takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }

    private fun instant(node: JsonNode, field: String): Instant? =
        node.get(field)?.takeIf { it.isNumber }?.asLong()?.takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    private fun phone(jid: String) = jid.substringBefore("@").substringBefore(":").filter(Char::isDigit)

    private fun kind(type: String?) = when (type) {
        "Conversation", "ExtendedTextMessage", "NativeFlowMessage" -> InboxMessageKind.TEXT
        "AudioMessage" -> InboxMessageKind.AUDIO
        "ImageMessage" -> InboxMessageKind.IMAGE
        "VideoMessage" -> InboxMessageKind.VIDEO
        "StickerMessage" -> InboxMessageKind.STICKER
        "DocumentMessage" -> InboxMessageKind.DOCUMENT
        "ContactMessage" -> InboxMessageKind.CONTACT
        else -> InboxMessageKind.OTHER
    }

    private companion object {
        const val LATEST_FIRST = "-wa_lastMsgTimestamp"
        const val MAX_CHATS = 1000
        const val TRACK_SOURCE = "saqz"
        val HIDDEN_TYPES = setOf("ReactionMessage", "PinInChatMessage")
    }
}
