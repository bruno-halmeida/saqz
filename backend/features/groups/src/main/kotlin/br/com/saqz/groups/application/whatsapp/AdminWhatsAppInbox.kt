package br.com.saqz.groups.application.whatsapp

import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.communication.WhatsAppNotification
import java.time.Instant
import java.util.UUID

enum class InboxMessageKind { TEXT, AUDIO, IMAGE, VIDEO, STICKER, DOCUMENT, CONTACT, OTHER }

/** Lista fechada de campos: nada do JSON da Uazapi passa desta porta. */
data class InboxChat(
    val chatId: String,
    val name: String?,
    val phone: String?,
    val lastMessageAt: Instant,
    val preview: String?,
    val previewKind: InboxMessageKind,
)

data class InboxChatPage(val chats: List<InboxChat>, val truncated: Boolean)

data class InboxMessage(
    val id: String,
    val fromMe: Boolean,
    val at: Instant,
    val senderName: String?,
    val senderPhone: String?,
    val text: String?,
    val kind: InboxMessageKind,
)

data class InboxMessagePage(val messages: List<InboxMessage>, val hasMore: Boolean, val nextOffset: Int?)

/**
 * Leitura ao vivo das mensagens do número do Saqz (DEB-160). Falha do provedor vira
 * [DirectoryError.Unavailable]. Só lê: nunca marca conversa ou mensagem como lida.
 */
interface AdminWhatsAppInbox {
    /** Até 1.000 conversas com mensagem, da mais recente para a mais antiga. */
    fun chats(): InboxChatPage

    /** Conversa privada já usada; nulo se não existir, for grupo ou nunca tiver tido mensagem. */
    fun privateChat(chatId: String): InboxChat?

    /** Uma página, da mais antiga para a mais nova; `hasMore`/`nextOffset` são os da Uazapi. */
    fun messages(chatId: String, offset: Int, limit: Int): InboxMessagePage

    /** Procura mensagem já enviada com `track_source=saqz` e este `track_id`. */
    fun trackExists(trackId: String): Boolean
}

data class SaqzGroupLink(val id: UUID, val name: String, val broken: Boolean)

/** Vínculos ativos por JID: `enabled=false` e grupo apagado contam como sem vínculo. */
fun interface AdminWhatsAppBindings {
    fun activeByJid(): Map<String, SaqzGroupLink>
}

data class InboxConversation(val chat: InboxChat, val saqzGroup: SaqzGroupLink?)

data class InboxOverview(
    val connected: Boolean,
    val phone: String?,
    val truncated: Boolean,
    val groups: List<InboxConversation>,
    val privates: List<InboxConversation>,
)

sealed interface ReplyResult {
    data object Sent : ReplyResult
    data object Invalid : ReplyResult
    data object GroupReadOnly : ReplyResult
    data object ChatNotFound : ReplyResult
    data object Unconfirmed : ReplyResult
    data object Failed : ReplyResult
    data object Unavailable : ReplyResult
}

/** Regras da aba Mensagens do adm. O adm responde a quem já escreveu; grupo é somente leitura. */
class AdminWhatsAppMessages(
    private val inbox: AdminWhatsAppInbox,
    private val directory: WhatsAppGroupDirectory,
    private val bindings: AdminWhatsAppBindings,
    private val sender: NotificationWhatsAppSender,
) {
    /** Desconectado não é erro: a tela mostra o estado próprio, sem ler a lista. */
    fun overview(): InboxOverview {
        val phone = try {
            directory.instanceStatus()
        } catch (_: DirectoryError.Disconnected) {
            return InboxOverview(false, null, false, emptyList(), emptyList())
        }
        val page = inbox.chats()
        val links = bindings.activeByJid()
        val (groups, privates) = page.chats.partition { isGroup(it.chatId) }
        return InboxOverview(
            connected = true,
            phone = phone,
            truncated = page.truncated,
            groups = groups.map { InboxConversation(it, links[it.chatId]) },
            privates = privates.map { InboxConversation(it, null) },
        )
    }

    fun messages(chatId: String, offset: Int, limit: Int): InboxMessagePage = inbox.messages(chatId, offset, limit)

    fun reply(chatId: String?, rawText: String?, requestId: String?): ReplyResult {
        val text = rawText?.trim().orEmpty()
        if (requestId == null || !UUID_FORMAT.matches(requestId) || text.isEmpty() || text.length > MAX_REPLY) return ReplyResult.Invalid
        if (chatId == null) return ReplyResult.Invalid
        if (chatId.endsWith(GROUP_SUFFIX)) return ReplyResult.GroupReadOnly
        if (!PRIVATE_CHAT.matches(chatId)) return ReplyResult.Invalid
        val trackId = "adm-reply-${requestId.lowercase()}"
        try {
            if (inbox.privateChat(chatId) == null) return ReplyResult.ChatNotFound
            if (inbox.trackExists(trackId)) return ReplyResult.Sent
        } catch (_: DirectoryError.Unavailable) {
            return ReplyResult.Unavailable
        }
        val delivery = sender.send(WhatsAppNotification(0, chatId.substringBefore("@"), text, trackId = trackId))
        return when (delivery) {
            WhatsAppDelivery.Accepted -> ReplyResult.Sent
            is WhatsAppDelivery.Retry -> ReplyResult.Unconfirmed
            WhatsAppDelivery.Failed -> ReplyResult.Failed
        }
    }

    companion object {
        const val MAX_REPLY = 2000
        private const val GROUP_SUFFIX = "@g.us"
        private val PRIVATE_CHAT = Regex("^[1-9][0-9]{7,14}@s\\.whatsapp\\.net$")
        private val GROUP_CHAT = Regex("^[0-9-]+@g\\.us$")
        private val UUID_FORMAT = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun isGroup(chatId: String) = GROUP_CHAT.matches(chatId)
        fun isPrivate(chatId: String) = PRIVATE_CHAT.matches(chatId)
        fun validChat(chatId: String) = isGroup(chatId) || isPrivate(chatId)
    }
}
