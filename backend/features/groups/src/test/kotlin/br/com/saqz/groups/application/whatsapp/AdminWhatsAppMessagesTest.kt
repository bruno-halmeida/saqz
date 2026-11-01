package br.com.saqz.groups.application.whatsapp

import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.communication.WhatsAppNotification
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminWhatsAppMessagesTest {
    private val chat = InboxChat(PRIVATE, null, "5511988887777", Instant.EPOCH, null, InboxMessageKind.TEXT)
    private val sent = mutableListOf<WhatsAppNotification>()
    private var delivery: WhatsAppDelivery = WhatsAppDelivery.Accepted
    private var found: InboxChat? = chat
    private var tracked = false
    private var failChat = false
    private var failTrack = false
    private var disconnected = false
    private var chatsRead = 0
    private val inbox = object : AdminWhatsAppInbox {
        override fun chats(): InboxChatPage {
            chatsRead++
            return InboxChatPage(listOf(chat.copy(chatId = GROUP, phone = null), chat.copy(chatId = UNLINKED, phone = null), chat), true)
        }
        override fun privateChat(chatId: String): InboxChat? {
            if (failChat) throw DirectoryError.Unavailable("x")
            return found
        }
        override fun messages(chatId: String, offset: Int, limit: Int) = InboxMessagePage(emptyList(), false, null)
        override fun trackExists(trackId: String): Boolean {
            if (failTrack) throw DirectoryError.Unavailable("x")
            return tracked
        }
    }
    private val directory = object : WhatsAppGroupDirectory {
        override fun instanceStatus(): String {
            if (disconnected) throw DirectoryError.Disconnected
            return "5511900000000"
        }
        override fun inviteInfo(inviteCode: String) = error("unused")
        override fun join(inviteCode: String) = error("unused")
        override fun groupInfo(jid: String) = error("unused")
        override fun isMember(jid: String) = error("unused")
    }
    private val link = SaqzGroupLink(UUID.randomUUID(), "Vôlei", false)
    private val service = AdminWhatsAppMessages(inbox, directory, { mapOf(GROUP to link) }) { sent += it; delivery }

    @Test
    fun `overview separates groups from privates and links saqz groups`() {
        val overview = service.overview()
        assertTrue(overview.connected)
        assertTrue(overview.truncated)
        assertEquals(listOf(GROUP, UNLINKED), overview.groups.map { it.chat.chatId })
        assertEquals(link, overview.groups[0].saqzGroup)
        assertNull(overview.groups[1].saqzGroup)
        assertEquals(listOf(PRIVATE), overview.privates.map { it.chat.chatId })
    }

    @Test
    fun `disconnected is a state, with empty lists and no read`() {
        disconnected = true
        val overview = service.overview()
        assertFalse(overview.connected)
        assertTrue(overview.groups.isEmpty() && overview.privates.isEmpty())
        assertEquals(0, chatsRead)
    }

    @Test
    fun `invalid input is refused before touching anything`() {
        assertEquals(ReplyResult.Invalid, service.reply(PRIVATE, "oi", "nao-e-uuid"))
        assertEquals(ReplyResult.Invalid, service.reply(PRIVATE, "   \n ", REQUEST))
        assertEquals(ReplyResult.Invalid, service.reply(PRIVATE, "a".repeat(2001), REQUEST))
        assertEquals(ReplyResult.Invalid, service.reply("abc", "oi", REQUEST))
        assertEquals(ReplyResult.Invalid, service.reply(null, "oi", REQUEST))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `group is refused by the server without touching the sender`() {
        assertEquals(ReplyResult.GroupReadOnly, service.reply(GROUP, "oi", REQUEST))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `unknown chat is not started`() {
        found = null
        assertEquals(ReplyResult.ChatNotFound, service.reply(PRIVATE, "oi", REQUEST))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `resend with a known track does not duplicate`() {
        tracked = true
        assertEquals(ReplyResult.Sent, service.reply(PRIVATE, "oi", REQUEST))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `provider failure on checks does not send`() {
        failChat = true
        assertEquals(ReplyResult.Unavailable, service.reply(PRIVATE, "oi", REQUEST))
        failChat = false
        failTrack = true
        assertEquals(ReplyResult.Unavailable, service.reply(PRIVATE, "oi", REQUEST))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `sends to the chat phone, trimmed, keeping line breaks and the track`() {
        assertEquals(ReplyResult.Sent, service.reply(PRIVATE, "  oi\n\ntudo bem?  ", REQUEST))
        val message = sent.single()
        assertEquals("5511988887777", message.phone)
        assertEquals("oi\n\ntudo bem?", message.body)
        assertEquals("adm-reply-$REQUEST", message.trackId)
    }

    @Test
    fun `2000 characters are accepted and delivery maps to the result`() {
        assertEquals(ReplyResult.Sent, service.reply(PRIVATE, "a".repeat(2000), REQUEST))
        delivery = WhatsAppDelivery.Retry()
        assertEquals(ReplyResult.Unconfirmed, service.reply(PRIVATE, "oi", REQUEST))
        delivery = WhatsAppDelivery.Failed
        assertEquals(ReplyResult.Failed, service.reply(PRIVATE, "oi", REQUEST))
    }

    private companion object {
        const val PRIVATE = "5511988887777@s.whatsapp.net"
        const val GROUP = "120363000000000000@g.us"
        const val UNLINKED = "120363000000000001@g.us"
        const val REQUEST = "9c8b7a6d-5e4f-4321-9876-abcdef012345"
    }
}
