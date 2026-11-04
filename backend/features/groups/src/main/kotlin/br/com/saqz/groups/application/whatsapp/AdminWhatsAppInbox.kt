package br.com.saqz.groups.application.whatsapp

import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.communication.WhatsAppNotification
import br.com.saqz.groups.application.communication.WhatsAppNumberCheck
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

/** Conta do Saqz dona do telefone de uma conversa privada (DEB-164). */
data class SaqzAccountLink(val userId: UUID, val name: String?)

/** Para quem o adm pode escrever pelo número do Saqz: o telefone é sempre o do cadastro, nunca um digitado no adm. */
data class AdminContactTarget(val userId: UUID, val phone: String?, val doNotContact: Boolean)

/** Contas do Saqz do lado do adm (DEB-164). */
interface AdminContactTargets {
    /** Conta não apagada; null se não existir. `doNotContact` segue o registro de contato mais recente (GER-005). */
    fun find(userId: UUID): AdminContactTarget?

    /**
     * Telefone no formato do cadastro (`+55` e 11 dígitos) para a conta dona dele. Telefone de mais de uma conta fica de
     * fora: o nome mostrado no adm tem de ser o da pessoa certa.
     */
    fun byPhone(phones: Collection<String>): Map<String, SaqzAccountLink>

    /** Anota o envio em Contatos. O mesmo `requestId` não anota duas vezes; conta apagada não anota. */
    fun recordSent(userId: UUID, adminId: UUID, requestId: UUID, text: String)
}

data class InboxConversation(val chat: InboxChat, val saqzGroup: SaqzGroupLink?, val account: SaqzAccountLink? = null)

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

sealed interface ContactSendResult {
    data object Sent : ContactSendResult
    data object Invalid : ContactSendResult
    data object NotFound : ContactSendResult
    data object NoPhone : ContactSendResult
    data object DoNotContact : ContactSendResult
    data object NotOnWhatsApp : ContactSendResult
    data object Unconfirmed : ContactSendResult
    data object Failed : ContactSendResult
    data object Unavailable : ContactSendResult
}

/**
 * Regras da aba Mensagens do adm. Conversa existente aceita resposta; conversa nova só com conta do Saqz, pelo telefone
 * do cadastro ([sendToUser], DEB-164). Grupo é somente leitura.
 */
class AdminWhatsAppMessages(
    private val inbox: AdminWhatsAppInbox,
    private val directory: WhatsAppGroupDirectory,
    private val bindings: AdminWhatsAppBindings,
    private val targets: AdminContactTargets,
    private val numbers: WhatsAppNumberCheck,
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
        val phones = privates.associate { it.chatId to accountPhone(it.chatId) }
        val accounts = targets.byPhone(phones.values.filterNotNull().distinct())
        return InboxOverview(
            connected = true,
            phone = phone,
            truncated = page.truncated,
            groups = groups.map { InboxConversation(it, links[it.chatId]) },
            privates = privates.map { InboxConversation(it, null, phones[it.chatId]?.let(accounts::get)) },
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

    /**
     * Mensagem para uma conta do Saqz, com ou sem conversa aberta (DEB-164). Recusa quem pediu para não ser procurado e
     * número que a Uazapi diz não ter WhatsApp; dúvida na consulta não recusa. Enviada, fica anotada em Contatos.
     */
    fun sendToUser(userId: UUID, rawText: String?, requestId: String?, adminId: UUID): ContactSendResult {
        val text = rawText?.trim().orEmpty()
        if (requestId == null || !UUID_FORMAT.matches(requestId) || text.isEmpty() || text.length > MAX_REPLY) return ContactSendResult.Invalid
        val request = UUID.fromString(requestId)
        val target = targets.find(userId) ?: return ContactSendResult.NotFound
        if (target.doNotContact) return ContactSendResult.DoNotContact
        val phone = target.phone?.takeIf { ACCOUNT_PHONE.matches(it) }?.removePrefix("+") ?: return ContactSendResult.NoPhone
        val trackId = "adm-contact-$request"
        try {
            if (inbox.trackExists(trackId)) {
                targets.recordSent(userId, adminId, request, text)
                return ContactSendResult.Sent
            }
        } catch (_: DirectoryError.Unavailable) {
            return ContactSendResult.Unavailable
        }
        if (numbers.hasWhatsApp(phone) == false) return ContactSendResult.NotOnWhatsApp
        return when (sender.send(WhatsAppNotification(0, phone, text, trackId = trackId))) {
            WhatsAppDelivery.Accepted -> {
                targets.recordSent(userId, adminId, request, text)
                ContactSendResult.Sent
            }
            is WhatsAppDelivery.Retry -> ContactSendResult.Unconfirmed
            WhatsAppDelivery.Failed -> ContactSendResult.Failed
        }
    }

    companion object {
        const val MAX_REPLY = 2000
        private val ACCOUNT_PHONE = Regex("^\\+55[1-9][0-9]9[0-9]{8}$")

        /**
         * Telefone do cadastro (`+55`, DDD e 9 dígitos) de uma conversa privada. Conta antiga de celular no WhatsApp ainda
         * pode ter o JID sem o nono dígito (`55` + DDD + 8): ele volta aqui com o 9.
         */
        fun accountPhone(chatId: String): String? {
            if (!isPrivate(chatId)) return null
            val digits = chatId.substringBefore("@")
            if (!digits.startsWith("55")) return null
            val national = digits.removePrefix("55")
            val mobile = when {
                national.length == 11 && national[2] == '9' -> national
                national.length == 10 && national[2] in '6'..'9' -> national.take(2) + "9" + national.drop(2)
                else -> return null
            }
            return "+55$mobile".takeIf { ACCOUNT_PHONE.matches(it) }
        }
        private const val GROUP_SUFFIX = "@g.us"
        private val PRIVATE_CHAT = Regex("^[1-9][0-9]{7,14}@s\\.whatsapp\\.net$")
        private val GROUP_CHAT = Regex("^[0-9-]+@g\\.us$")
        private val UUID_FORMAT = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun isGroup(chatId: String) = GROUP_CHAT.matches(chatId)
        fun isPrivate(chatId: String) = PRIVATE_CHAT.matches(chatId)
        fun validChat(chatId: String) = isGroup(chatId) || isPrivate(chatId)
    }
}
