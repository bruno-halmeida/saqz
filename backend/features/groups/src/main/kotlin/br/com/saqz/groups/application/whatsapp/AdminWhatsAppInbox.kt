package br.com.saqz.groups.application.whatsapp

import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.communication.WhatsAppNotification
import br.com.saqz.groups.application.communication.WhatsAppNumberCheck
import java.time.Instant
import java.util.UUID

enum class InboxMessageKind { TEXT, AUDIO, IMAGE, VIDEO, STICKER, DOCUMENT, CONTACT, REACTION, OTHER }

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

/**
 * O que a tela precisa para desenhar uma mídia antes de baixá-la (DEB-166). Nunca o link, a chave ou o caminho do arquivo:
 * o link que a Uazapi devolve abre sem token.
 */
data class InboxMedia(
    val seconds: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
    val animated: Boolean = false,
    /** Áudio: intensidade de 0 a 100 por trecho, como o WhatsApp desenha. */
    val waveform: List<Int> = emptyList(),
)

data class InboxMessage(
    val id: String,
    val fromMe: Boolean,
    val at: Instant,
    val senderName: String?,
    val senderPhone: String?,
    val text: String?,
    val kind: InboxMessageKind,
    val media: InboxMedia? = null,
)

/** Reação a outra mensagem da conversa. `emoji` nulo é a reação retirada. */
data class InboxReaction(
    val targetId: String,
    val emoji: String?,
    val fromMe: Boolean,
    val at: Instant,
    val senderName: String?,
    val senderPhone: String?,
)

data class InboxMessagePage(
    val messages: List<InboxMessage>,
    val hasMore: Boolean,
    val nextOffset: Int?,
    val reactions: List<InboxReaction> = emptyList(),
)

/** Onde está e de que tipo é uma mensagem. */
data class InboxMessageRef(val chatId: String, val kind: InboxMessageKind, val sizeBytes: Long?)

/** Arquivo de uma mensagem, já baixado. */
class InboxMediaFile(val bytes: ByteArray, val mimeType: String)

sealed interface MediaResult {
    class Found(val file: InboxMediaFile, val kind: InboxMessageKind) : MediaResult
    data object Invalid : MediaResult
    /** Id que a Uazapi não tem ou que é de outra conversa. */
    data object NotFound : MediaResult
    /** Texto, documento, contato, ou arquivo de tipo que o adm não abre. */
    data object NotMedia : MediaResult
    data class TooLarge(val sizeBytes: Long) : MediaResult
    /** O WhatsApp não tem mais o arquivo. */
    data object Gone : MediaResult
    data object Unavailable : MediaResult
}

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

    /** Conversa e tipo de uma mensagem pelo id; nulo se a Uazapi não a tiver. */
    fun locate(messageId: String): InboxMessageRef?

    /**
     * Baixa o arquivo, sempre sem transcrição; áudio volta em MP3, que o Safari toca. Nulo se o WhatsApp não tiver mais
     * o arquivo.
     */
    fun download(messageId: String, kind: InboxMessageKind): InboxMediaFile?
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

    /**
     * Arquivo de foto, vídeo, áudio ou figurinha de uma conversa (DEB-166). A busca por id da Uazapi ignora a conversa, então
     * a conversa da mensagem é conferida aqui. Só sai tipo que o navegador abre como mídia; o resto é [MediaResult.NotMedia].
     */
    fun media(chatId: String?, messageId: String?): MediaResult {
        if (chatId == null || !validChat(chatId) || messageId == null || !MESSAGE_ID.matches(messageId)) return MediaResult.Invalid
        return try {
            val ref = inbox.locate(messageId)
            when {
                ref == null || ref.chatId != chatId -> MediaResult.NotFound
                ref.kind !in MEDIA_TYPES -> MediaResult.NotMedia
                ref.sizeBytes != null && ref.sizeBytes > MAX_MEDIA_BYTES -> MediaResult.TooLarge(ref.sizeBytes)
                else -> {
                    val file = inbox.download(messageId, ref.kind) ?: return MediaResult.Gone
                    val type = file.mimeType.substringBefore(";").trim().lowercase()
                    when {
                        type !in MEDIA_TYPES.getValue(ref.kind) -> MediaResult.NotMedia
                        file.bytes.size > MAX_MEDIA_BYTES -> MediaResult.TooLarge(file.bytes.size.toLong())
                        file.bytes.isEmpty() -> MediaResult.Gone
                        else -> MediaResult.Found(InboxMediaFile(file.bytes, type), ref.kind)
                    }
                }
            }
        } catch (_: DirectoryError.Unavailable) {
            MediaResult.Unavailable
        }
    }

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

        /**
         * 14 MiB: o arquivo chega em base64 dentro do JSON da Uazapi, e o Jackson do SDK recusa texto acima de 20 milhões de
         * caracteres. Foto, figurinha, áudio e quase todo vídeo do WhatsApp cabem.
         */
        const val MAX_MEDIA_BYTES = 14L * 1024 * 1024
        private val MESSAGE_ID = Regex("^[A-Za-z0-9]{8,64}$")
        private val MEDIA_TYPES = mapOf(
            InboxMessageKind.IMAGE to setOf("image/jpeg", "image/png", "image/webp", "image/gif"),
            InboxMessageKind.STICKER to setOf("image/webp", "image/png"),
            InboxMessageKind.VIDEO to setOf("video/mp4"),
            InboxMessageKind.AUDIO to setOf("audio/mpeg", "audio/ogg", "audio/mp4", "audio/aac"),
        )
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
