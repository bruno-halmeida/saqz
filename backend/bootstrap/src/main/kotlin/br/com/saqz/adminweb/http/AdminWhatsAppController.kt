package br.com.saqz.adminweb.http

import br.com.saqz.access.adapter.output.jdbc.flags.FeatureJson
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.groups.application.whatsapp.AdminWhatsAppMessages
import br.com.saqz.groups.application.whatsapp.DirectoryError
import br.com.saqz.groups.application.whatsapp.InboxConversation
import br.com.saqz.groups.application.whatsapp.InboxMessagePage
import br.com.saqz.groups.application.whatsapp.ReplyResult
import br.com.saqz.sharedkernel.RequestIdentity
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class AdminWhatsAppReplyBody(val chatId: String? = null, val text: String? = null, val requestId: String? = null)

data class AdminWhatsAppGroupLinkResponse(val id: UUID, val name: String, val broken: Boolean)

data class AdminWhatsAppConversationResponse(
    val chatId: String,
    val name: String?,
    val phone: String?,
    val lastMessageAt: Instant,
    val preview: String?,
    val previewKind: String,
    val saqzGroup: AdminWhatsAppGroupLinkResponse?,
)

data class AdminWhatsAppConversationsResponse(
    val connected: Boolean,
    val phone: String?,
    val truncated: Boolean,
    val groups: List<AdminWhatsAppConversationResponse>,
    val privates: List<AdminWhatsAppConversationResponse>,
)

data class AdminWhatsAppMessageResponse(
    val id: String,
    val fromMe: Boolean,
    val at: Instant,
    val senderName: String?,
    val senderPhone: String?,
    val text: String?,
    val kind: String,
)

data class AdminWhatsAppMessagesResponse(val messages: List<AdminWhatsAppMessageResponse>, val hasMore: Boolean, val nextOffset: Int?)

/**
 * Aba Mensagens do adm (DEB-160): leitura ao vivo da Uazapi e resposta a conversas privadas. Só existe com
 * o WhatsApp ligado; quem não é admin para na guarda do /admin. O log nunca leva texto de mensagem.
 */
@RestController
class AdminWhatsAppController(private val messages: AdminWhatsAppMessages, private val admins: PlatformAdminLookup) {
    private val log = LoggerFactory.getLogger("br.com.saqz.admin.whatsapp")

    @GetMapping("/admin/whatsapp/conversations")
    fun conversations(@AuthenticationPrincipal identity: RequestIdentity): ResponseEntity<*> = admin(identity) {
        try {
            val overview = messages.overview()
            json(200, AdminWhatsAppConversationsResponse(overview.connected, overview.phone, overview.truncated,
                overview.groups.map(::conversation), overview.privates.map(::conversation)))
        } catch (_: DirectoryError) {
            error(503, "WHATSAPP_UNAVAILABLE")
        }
    }

    @GetMapping("/admin/whatsapp/messages")
    fun messages(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestParam(required = false) chatId: String?,
        @RequestParam(defaultValue = "0") offset: String,
        @RequestParam(defaultValue = "50") limit: String,
    ): ResponseEntity<*> = admin(identity) { actor ->
        val start = offset.toIntOrNull()?.takeIf { it >= 0 }
        val size = limit.toIntOrNull()?.takeIf { it in 1..100 }
        if (chatId == null || !AdminWhatsAppMessages.validChat(chatId) || start == null || size == null) {
            return@admin error(400, "INVALID_INPUT")
        }
        try {
            val page = messages.messages(chatId, start, size)
            record("conversation", actor, chatId, "ok")
            json(200, page(page))
        } catch (_: DirectoryError) {
            record("conversation", actor, chatId, "unavailable")
            error(503, "WHATSAPP_UNAVAILABLE")
        }
    }

    @PostMapping("/admin/whatsapp/messages")
    fun reply(@AuthenticationPrincipal identity: RequestIdentity, @RequestBody raw: String): ResponseEntity<*> =
        admin(identity) { actor ->
            val body = try {
                FeatureJson.read(raw, AdminWhatsAppReplyBody::class.java)
            } catch (_: Exception) {
                return@admin error(400, "INVALID_INPUT")
            }
            val result = messages.reply(body.chatId, body.text, body.requestId)
            if (body.chatId != null && AdminWhatsAppMessages.validChat(body.chatId)) {
                record("reply", actor, body.chatId, result::class.simpleName.orEmpty().lowercase())
            }
            when (result) {
                ReplyResult.Sent -> json(200, mapOf("status" to "SENT"))
                ReplyResult.Invalid -> error(400, "INVALID_INPUT")
                ReplyResult.GroupReadOnly -> error(422, "GROUP_READ_ONLY")
                ReplyResult.ChatNotFound -> error(404, "CHAT_NOT_FOUND")
                ReplyResult.Unconfirmed -> error(503, "SEND_UNCONFIRMED")
                ReplyResult.Failed -> error(502, "SEND_FAILED")
                ReplyResult.Unavailable -> error(503, "WHATSAPP_UNAVAILABLE")
            }
        }

    private fun conversation(item: InboxConversation) = AdminWhatsAppConversationResponse(
        item.chat.chatId, item.chat.name, item.chat.phone, item.chat.lastMessageAt, item.chat.preview, item.chat.previewKind.name,
        item.saqzGroup?.let { AdminWhatsAppGroupLinkResponse(it.id, it.name, it.broken) },
    )

    private fun page(page: InboxMessagePage) = AdminWhatsAppMessagesResponse(
        page.messages.map { AdminWhatsAppMessageResponse(it.id, it.fromMe, it.at, it.senderName, it.senderPhone, it.text, it.kind.name) },
        page.hasMore, page.nextOffset,
    )

    private fun record(action: String, actor: UUID, chatId: String, result: String) =
        log.info("admin_whatsapp action={} admin={} chat={} result={}", action, actor, logChat(chatId), result)

    private fun admin(identity: RequestIdentity, block: (UUID) -> ResponseEntity<*>): ResponseEntity<*> {
        val actor = admins.findBySubject(identity.subject)?.userId ?: return error(403, "UNAUTHORIZED")
        return block(actor)
    }

    private fun json(status: Int, body: Any): ResponseEntity<*> =
        ResponseEntity.status(status).header("Cache-Control", "no-store").body(body)

    private fun error(status: Int, code: String) = json(status, mapOf("error" to code))
}

/** Privada: só os 4 últimos dígitos. Grupo: JID inteiro. */
internal fun logChat(chatId: String): String =
    if (AdminWhatsAppMessages.isGroup(chatId)) chatId else chatId.substringBefore("@").takeLast(4)
