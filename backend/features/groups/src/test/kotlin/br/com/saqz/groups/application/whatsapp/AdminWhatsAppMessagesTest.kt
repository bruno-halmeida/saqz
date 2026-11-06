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
    private var located: InboxMessageRef? = InboxMessageRef(PRIVATE, InboxMessageKind.IMAGE, 3)
    private var file: InboxMediaFile? = InboxMediaFile(byteArrayOf(1, 2, 3), "image/jpeg")
    private var failMedia = false
    private val locatedIds = mutableListOf<String>()
    private val downloads = mutableListOf<Pair<String, InboxMessageKind>>()
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
        override fun locate(messageId: String): InboxMessageRef? {
            locatedIds += messageId
            if (failMedia) throw DirectoryError.Unavailable("x")
            return located
        }
        override fun download(messageId: String, kind: InboxMessageKind): InboxMediaFile? {
            downloads += messageId to kind
            return file
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
    private val carla = AdminContactTarget(CARLA, "+5511988887777", doNotContact = false)
    private var target: AdminContactTarget? = carla
    private var hasWhatsApp: Boolean? = true
    private val recorded = mutableListOf<Triple<UUID, UUID, String>>()
    private val askedPhones = mutableListOf<Collection<String>>()
    private val targets = object : AdminContactTargets {
        override fun find(userId: UUID) = target?.takeIf { it.userId == userId }
        override fun byPhone(phones: Collection<String>): Map<String, SaqzAccountLink> {
            askedPhones += phones
            return phones.filter { it == "+5511988887777" }.associateWith { SaqzAccountLink(CARLA, "Carla Menezes") }
        }
        override fun recordSent(userId: UUID, adminId: UUID, requestId: UUID, text: String) {
            recorded += Triple(userId, requestId, text)
        }
    }
    private val service = AdminWhatsAppMessages(inbox, directory, { mapOf(GROUP to link) }, targets, { hasWhatsApp }) { sent += it; delivery }

    @Test
    fun `overview separates groups from privates and links saqz groups`() {
        val overview = service.overview()
        assertTrue(overview.connected)
        assertTrue(overview.truncated)
        assertEquals(listOf(GROUP, UNLINKED), overview.groups.map { it.chat.chatId })
        assertEquals(link, overview.groups[0].saqzGroup)
        assertNull(overview.groups[1].saqzGroup)
        assertEquals(listOf(PRIVATE), overview.privates.map { it.chat.chatId })
        assertEquals(SaqzAccountLink(CARLA, "Carla Menezes"), overview.privates.single().account)
        assertNull(overview.groups[0].account)
        assertEquals(listOf("+5511988887777"), askedPhones.single().toList())
    }

    @Test
    fun `chat phone reads as the account phone, putting back the ninth digit of old jids`() {
        assertEquals("+5511988887777", AdminWhatsAppMessages.accountPhone("5511988887777@s.whatsapp.net"))
        assertEquals("+5511988887777", AdminWhatsAppMessages.accountPhone("551188887777@s.whatsapp.net"))
        assertNull(AdminWhatsAppMessages.accountPhone("551133334444@s.whatsapp.net"))
        assertNull(AdminWhatsAppMessages.accountPhone("14155550100@s.whatsapp.net"))
        assertNull(AdminWhatsAppMessages.accountPhone(GROUP))
    }

    @Test
    fun `message to an account goes to the phone of the record and is noted in contacts`() {
        assertEquals(ContactSendResult.Sent, service.sendToUser(CARLA, "  Oi, Carla!\n\nTudo bem?  ", REQUEST, ADMIN))
        val message = sent.single()
        assertEquals("5511988887777", message.phone)
        assertEquals("Oi, Carla!\n\nTudo bem?", message.body)
        assertEquals("adm-contact-$REQUEST", message.trackId)
        assertEquals(listOf(Triple(CARLA, UUID.fromString(REQUEST), "Oi, Carla!\n\nTudo bem?")), recorded)
    }

    @Test
    fun `message to an account is refused before sending when the rules say so`() {
        assertEquals(ContactSendResult.Invalid, service.sendToUser(CARLA, "oi", "nao-e-uuid", ADMIN))
        assertEquals(ContactSendResult.Invalid, service.sendToUser(CARLA, "  ", REQUEST, ADMIN))
        assertEquals(ContactSendResult.Invalid, service.sendToUser(CARLA, "a".repeat(2001), REQUEST, ADMIN))
        assertEquals(ContactSendResult.NotFound, service.sendToUser(UUID.randomUUID(), "oi", REQUEST, ADMIN))
        target = carla.copy(doNotContact = true)
        assertEquals(ContactSendResult.DoNotContact, service.sendToUser(CARLA, "oi", REQUEST, ADMIN))
        target = carla.copy(phone = null)
        assertEquals(ContactSendResult.NoPhone, service.sendToUser(CARLA, "oi", REQUEST, ADMIN))
        target = carla.copy(phone = "+551133334444")
        assertEquals(ContactSendResult.NoPhone, service.sendToUser(CARLA, "oi", REQUEST, ADMIN))
        target = carla
        hasWhatsApp = false
        assertEquals(ContactSendResult.NotOnWhatsApp, service.sendToUser(CARLA, "oi", REQUEST, ADMIN))
        assertTrue(sent.isEmpty())
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `unknown answer from the number check does not block the message`() {
        hasWhatsApp = null
        assertEquals(ContactSendResult.Sent, service.sendToUser(CARLA, "oi", REQUEST, ADMIN))
        assertEquals(1, sent.size)
    }

    @Test
    fun `message to an account maps delivery and only notes what was accepted`() {
        delivery = WhatsAppDelivery.Retry()
        assertEquals(ContactSendResult.Unconfirmed, service.sendToUser(CARLA, "oi", REQUEST, ADMIN))
        delivery = WhatsAppDelivery.Failed
        assertEquals(ContactSendResult.Failed, service.sendToUser(CARLA, "oi", REQUEST, ADMIN))
        assertTrue(recorded.isEmpty())
        failTrack = true
        assertEquals(ContactSendResult.Unavailable, service.sendToUser(CARLA, "oi", REQUEST, ADMIN))
        assertEquals(2, sent.size)
    }

    @Test
    fun `resend of a message that already went out notes it without sending again`() {
        tracked = true
        assertEquals(ContactSendResult.Sent, service.sendToUser(CARLA, "oi", REQUEST, ADMIN))
        assertTrue(sent.isEmpty())
        assertEquals(1, recorded.size)
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

    @Test
    fun `media of the same chat comes back for photo, video, audio and sticker with the file type`() {
        val cases = listOf(
            InboxMessageKind.IMAGE to "image/jpeg", InboxMessageKind.VIDEO to "video/mp4",
            InboxMessageKind.AUDIO to "audio/ogg; codecs=opus", InboxMessageKind.STICKER to "image/webp",
        )
        for ((kind, type) in cases) {
            located = InboxMessageRef(GROUP, kind, 3)
            file = InboxMediaFile(byteArrayOf(9, 8, 7), type)
            val result = service.media(GROUP, MESSAGE)
            assertTrue(result is MediaResult.Found, kind.name)
            assertEquals(type.substringBefore(";"), result.file.mimeType)
            assertEquals(kind, result.kind)
            assertEquals(listOf(9, 8, 7), result.file.bytes.map(Byte::toInt))
            assertEquals(MESSAGE to kind, downloads.last())
        }
    }

    @Test
    fun `media refuses bad input before asking the provider`() {
        for ((chatId, id) in listOf(null to MESSAGE, "abc" to MESSAGE, PRIVATE to null, PRIVATE to "abc", PRIVATE to "../3AA025E50D",
            PRIVATE to "3AA0 25E50D", PRIVATE to "A".repeat(65))) {
            assertEquals(MediaResult.Invalid, service.media(chatId, id), "$chatId $id")
        }
        assertTrue(locatedIds.isEmpty())
    }

    @Test
    fun `media of another chat or of an unknown id is not found, without downloading`() {
        located = InboxMessageRef(GROUP, InboxMessageKind.IMAGE, 3)
        assertEquals(MediaResult.NotFound, service.media(PRIVATE, MESSAGE))
        located = null
        assertEquals(MediaResult.NotFound, service.media(PRIVATE, MESSAGE))
        assertTrue(downloads.isEmpty())
    }

    @Test
    fun `text, document and contact are not media, and an unexpected file type is refused`() {
        for (kind in listOf(InboxMessageKind.TEXT, InboxMessageKind.DOCUMENT, InboxMessageKind.CONTACT, InboxMessageKind.OTHER)) {
            located = InboxMessageRef(PRIVATE, kind, 3)
            assertEquals(MediaResult.NotMedia, service.media(PRIVATE, MESSAGE), kind.name)
        }
        assertTrue(downloads.isEmpty())
        located = InboxMessageRef(PRIVATE, InboxMessageKind.IMAGE, 3)
        file = InboxMediaFile(byteArrayOf(1), "text/html")
        assertEquals(MediaResult.NotMedia, service.media(PRIVATE, MESSAGE))
        located = InboxMessageRef(PRIVATE, InboxMessageKind.STICKER, 3)
        file = InboxMediaFile(byteArrayOf(1), "image/jpeg")
        assertEquals(MediaResult.NotMedia, service.media(PRIVATE, MESSAGE))
    }

    @Test
    fun `too large is refused before downloading, and also when the file is bigger than announced`() {
        located = InboxMessageRef(PRIVATE, InboxMessageKind.VIDEO, AdminWhatsAppMessages.MAX_MEDIA_BYTES + 1)
        assertEquals(MediaResult.TooLarge(AdminWhatsAppMessages.MAX_MEDIA_BYTES + 1), service.media(PRIVATE, MESSAGE))
        assertTrue(downloads.isEmpty())
        located = InboxMessageRef(PRIVATE, InboxMessageKind.VIDEO, AdminWhatsAppMessages.MAX_MEDIA_BYTES)
        file = InboxMediaFile(ByteArray(AdminWhatsAppMessages.MAX_MEDIA_BYTES.toInt()), "video/mp4")
        assertTrue(service.media(PRIVATE, MESSAGE) is MediaResult.Found)
        located = InboxMessageRef(PRIVATE, InboxMessageKind.VIDEO, null)
        file = InboxMediaFile(ByteArray(AdminWhatsAppMessages.MAX_MEDIA_BYTES.toInt() + 1), "video/mp4")
        assertEquals(MediaResult.TooLarge(AdminWhatsAppMessages.MAX_MEDIA_BYTES + 1), service.media(PRIVATE, MESSAGE))
    }

    @Test
    fun `file the whatsapp no longer has is gone, and provider failure is unavailable`() {
        file = null
        assertEquals(MediaResult.Gone, service.media(PRIVATE, MESSAGE))
        file = InboxMediaFile(ByteArray(0), "image/jpeg")
        assertEquals(MediaResult.Gone, service.media(PRIVATE, MESSAGE))
        failMedia = true
        assertEquals(MediaResult.Unavailable, service.media(PRIVATE, MESSAGE))
    }

    private companion object {
        const val PRIVATE = "5511988887777@s.whatsapp.net"
        const val GROUP = "120363000000000000@g.us"
        const val UNLINKED = "120363000000000001@g.us"
        const val REQUEST = "9c8b7a6d-5e4f-4321-9876-abcdef012345"
        const val MESSAGE = "3AA025E50D2F15D95D7E"
        val CARLA: UUID = UUID.fromString("4f2a91c7-8e13-4b60-a2d5-7c19e0b6d341")
        val ADMIN: UUID = UUID.fromString("0b6d3414-8e13-4b60-a2d5-7c19e0f2a91c")
    }
}
