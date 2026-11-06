package br.com.saqz.groups.adapter.output.whatsapp

import br.com.saqz.groups.application.whatsapp.AdminWhatsAppInbox
import br.com.saqz.groups.application.whatsapp.AdminWhatsAppMessages
import br.com.saqz.groups.application.whatsapp.DirectoryError
import br.com.saqz.groups.application.whatsapp.InboxChat
import br.com.saqz.groups.application.whatsapp.InboxChatPage
import br.com.saqz.groups.application.whatsapp.InboxMedia
import br.com.saqz.groups.application.whatsapp.InboxMediaFile
import br.com.saqz.groups.application.whatsapp.InboxMessage
import br.com.saqz.groups.application.whatsapp.InboxMessageKind
import br.com.saqz.groups.application.whatsapp.InboxMessagePage
import br.com.saqz.groups.application.whatsapp.InboxMessageRef
import br.com.saqz.groups.application.whatsapp.InboxReaction
import com.fasterxml.jackson.databind.JsonNode
import com.uazapi.sdk.UazapiClient
import com.uazapi.sdk.chat.ChatQuery
import com.uazapi.sdk.exception.UazapiApiException
import com.uazapi.sdk.exception.UazapiException
import com.uazapi.sdk.message.DownloadRequest
import com.uazapi.sdk.message.MessageQuery
import java.time.Instant
import java.util.Base64

/**
 * Único ponto que lê conversas e mensagens da Uazapi. Copia só os campos da porta: `owner`, `lead_*`,
 * `chatbot_summary`, `sendPayload` e `fileURL` nunca saem daqui. O corpo de erro da Uazapi também não
 * entra na exceção, porque pode ecoar conteúdo. Do download só saem os bytes e o tipo: o `fileURL` abre sem
 * token e a `transcription` (cache da instância, mesmo pedindo sem) é descartada.
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
        val all = list(body, "messages")
        return InboxMessagePage(
            messages = all.filter { text(it, "messageType") !in HIDDEN_TYPES }
                .mapNotNull(::message).sortedBy { it.at },
            hasMore = body.path("hasMore").asBoolean(false),
            nextOffset = body.get("nextOffset")?.takeIf { it.isNumber }?.asInt(),
            reactions = all.filter { text(it, "messageType") == REACTION }.mapNotNull(::reaction).sortedBy { it.at },
        )
    }

    override fun locate(messageId: String): InboxMessageRef? {
        val body = call { client.messages().findMessages(MessageQuery.builder().id(messageId).limit(1).build()) }
        val node = list(body, "messages").firstOrNull { text(it, "messageid") == messageId } ?: return null
        val chatId = text(node, "chatid") ?: return null
        return InboxMessageRef(chatId, kind(text(node, "messageType")), long(node.path("content"), "fileLength"))
    }

    override fun download(messageId: String, kind: InboxMessageKind): InboxMediaFile? {
        val request = DownloadRequest.builder(messageId).returnBase64(true).returnLink(false).transcribe(false)
            .generateMp3(kind == InboxMessageKind.AUDIO).build()
        val body = try {
            call { client.messages().downloadMedia(request) }
        } catch (error: DirectoryError.Unavailable) {
            if (error.reason in GONE_STATUSES) return null
            throw error
        }
        val data = text(body, "base64Data")?.substringAfter("base64,") ?: return null
        val type = text(body, "mimetype") ?: return null
        val bytes = try {
            Base64.getMimeDecoder().decode(data)
        } catch (_: IllegalArgumentException) {
            throw DirectoryError.Unavailable("invalid media")
        }
        return InboxMediaFile(bytes, type).takeIf { bytes.isNotEmpty() }
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
        val kind = kind(text(node, "messageType"))
        return InboxMessage(
            id = text(node, "messageid") ?: return null,
            fromMe = node.path("fromMe").asBoolean(false),
            at = instant(node, "messageTimestamp") ?: return null,
            senderName = text(node, "senderName"),
            senderPhone = senderPhone(node),
            text = text(node, "text"),
            kind = kind,
            media = if (kind in MEDIA_KINDS) media(node.path("content"), kind) else null,
        )
    }

    /** Só números e duração: `URL`, `directPath`, `mediaKey` e os hashes do arquivo ficam aqui. */
    private fun media(content: JsonNode, kind: InboxMessageKind) = InboxMedia(
        seconds = long(content, "seconds")?.toInt(),
        width = long(content, "width")?.toInt(),
        height = long(content, "height")?.toInt(),
        sizeBytes = long(content, "fileLength"),
        animated = content.path("isAnimated").asBoolean(false),
        waveform = if (kind == InboxMessageKind.AUDIO) waveform(text(content, "waveform")) else emptyList(),
    )

    private fun waveform(encoded: String?): List<Int> {
        if (encoded == null) return emptyList()
        val bytes = try {
            Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }
        return bytes.take(MAX_WAVEFORM).map { (it.toInt() and 0xFF).coerceAtMost(100) }
    }

    /** A Uazapi manda o id da mensagem alvo em `reaction`; versões antigas, só em `content.key.ID`. */
    private fun reaction(node: JsonNode): InboxReaction? {
        val target = text(node, "reaction") ?: text(node.path("content").path("key"), "ID") ?: return null
        return InboxReaction(
            targetId = target,
            emoji = text(node, "text"),
            fromMe = node.path("fromMe").asBoolean(false),
            at = instant(node, "messageTimestamp") ?: return null,
            senderName = text(node, "senderName"),
            senderPhone = senderPhone(node),
        )
    }

    private fun senderPhone(node: JsonNode) = text(node, "sender_pn")?.let(::phone)?.takeIf { it.isNotEmpty() }

    private fun long(node: JsonNode, field: String): Long? =
        node.get(field)?.takeIf { it.isNumber || (it.isTextual && it.asText().all(Char::isDigit) && it.asText().isNotEmpty()) }
            ?.asLong()?.takeIf { it >= 0 }

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
        REACTION -> InboxMessageKind.REACTION
        else -> InboxMessageKind.OTHER
    }

    private companion object {
        const val LATEST_FIRST = "-wa_lastMsgTimestamp"
        const val MAX_CHATS = 1000
        const val MAX_WAVEFORM = 128
        const val TRACK_SOURCE = "saqz"
        const val REACTION = "ReactionMessage"
        val HIDDEN_TYPES = setOf(REACTION, "PinInChatMessage")
        val MEDIA_KINDS = setOf(InboxMessageKind.IMAGE, InboxMessageKind.VIDEO, InboxMessageKind.AUDIO, InboxMessageKind.STICKER)
        /** Mensagem que a Uazapi não acha mais ou arquivo que o WhatsApp já apagou. */
        val GONE_STATUSES = setOf("HTTP 404", "HTTP 410")
    }
}
