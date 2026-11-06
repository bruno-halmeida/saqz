package br.com.saqz.groups.adapter.output.whatsapp

import br.com.saqz.groups.application.whatsapp.DirectoryError
import br.com.saqz.groups.application.whatsapp.InboxMessageKind
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import com.uazapi.sdk.UazapiClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UazapiWhatsAppInboxTest {
    private val mapper = ObjectMapper()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val requests = mutableListOf<Pair<String, String>>()
    private var status = 200
    private var response = "{}"
    private val client: UazapiClient

    init {
        server.createContext("/") { exchange ->
            requests += exchange.requestURI.path to exchange.requestBody.use { String(it.readAllBytes()) }
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        client = UazapiClient.builder().baseUrl("http://127.0.0.1:${server.address.port}").token("t")
            .connectTimeout(Duration.ofSeconds(1)).readTimeout(Duration.ofSeconds(1)).build()
    }

    private val inbox = UazapiWhatsAppInbox(client)

    @AfterEach fun close() { client.close(); server.stop(0) }

    @Test
    fun `chats asks for the latest 1000, drops never used chats and orders by latest`() {
        response = """{"chats":[
          {"wa_chatid":"$PRIVATE","wa_lastMsgTimestamp":1000,"wa_lastMessageTextVote":"oi","wa_lastMessageType":"Conversation","wa_name":"Rita",
           "owner":"SECRET","lead_name":"SECRET","chatbot_summary":"SECRET"},
          {"wa_chatid":"$GROUP","wa_lastMsgTimestamp":2000,"wa_lastMessageType":"AudioMessage","wa_contactName":"","name":"Vôlei"},
          {"wa_chatid":"5511000000000@s.whatsapp.net","wa_lastMsgTimestamp":0},
          {"wa_chatid":"status@broadcast","wa_lastMsgTimestamp":3000}
        ],"pagination":{"hasMore":true}}"""

        val page = inbox.chats()

        val sent = mapper.readTree(requests.single().second)
        assertEquals("/chat/find", requests.single().first)
        assertEquals("-wa_lastMsgTimestamp", sent["sort"].asText())
        assertEquals(1000, sent["limit"].asInt())
        assertTrue(page.truncated)
        assertEquals(listOf(GROUP, PRIVATE), page.chats.map { it.chatId })
        val group = page.chats[0]
        assertEquals("Vôlei", group.name)
        assertNull(group.phone)
        assertNull(group.preview)
        assertEquals(InboxMessageKind.AUDIO, group.previewKind)
        val private = page.chats[1]
        assertEquals("5511988887777", private.phone)
        assertEquals("oi", private.preview)
        assertEquals(Instant.ofEpochMilli(1000), private.lastMessageAt)
        assertFalse(page.toString().contains("SECRET"))
    }

    @Test
    fun `privateChat filters by wa_chatid and refuses other, group or unused chats`() {
        response = """{"chats":[{"wa_chatid":"$PRIVATE","wa_lastMsgTimestamp":5}]}"""
        assertEquals(PRIVATE, inbox.privateChat(PRIVATE)?.chatId)
        assertEquals(PRIVATE, mapper.readTree(requests.single().second)["wa_chatid"].asText())
        assertNull(inbox.privateChat("5511911112222@s.whatsapp.net"))
        response = """{"chats":[{"wa_chatid":"$PRIVATE","wa_lastMsgTimestamp":0}]}"""
        assertNull(inbox.privateChat(PRIVATE))
        response = """{"chats":[{"wa_chatid":"$GROUP","wa_lastMsgTimestamp":5}]}"""
        assertNull(inbox.privateChat(GROUP))
    }

    @Test
    fun `messages come oldest first, hide reactions and carry only the closed field list`() {
        response = """{"messages":[
          {"messageid":"3","fromMe":false,"messageTimestamp":3000,"messageType":"ImageMessage","text":"legenda","senderName":"Rita",
           "sender":"123@lid","sender_pn":"5511988887777@s.whatsapp.net","fileURL":"https://SECRET","sendPayload":{"x":"SECRET"}},
          {"messageid":"2","fromMe":false,"messageTimestamp":2000,"messageType":"ReactionMessage","text":"x"},
          {"messageid":"p","fromMe":false,"messageTimestamp":2500,"messageType":"PinInChatMessage"},
          {"messageid":"1","fromMe":true,"messageTimestamp":1000,"messageType":"ExtendedTextMessage","text":"oi","sender_pn":""}
        ],"hasMore":true,"nextOffset":50,"token":"SECRET","openai_apikey":"SECRET"}"""

        val page = inbox.messages(PRIVATE, 0, 50)

        val sent = mapper.readTree(requests.single().second)
        assertEquals("/message/find", requests.single().first)
        assertEquals(PRIVATE, sent["chatid"].asText())
        assertEquals(50, sent["limit"].asInt())
        assertEquals(listOf("1", "3"), page.messages.map { it.id })
        assertTrue(page.hasMore)
        assertEquals(50, page.nextOffset)
        assertEquals(InboxMessageKind.TEXT, page.messages[0].kind)
        assertNull(page.messages[0].senderPhone)
        assertEquals(InboxMessageKind.IMAGE, page.messages[1].kind)
        assertEquals("5511988887777", page.messages[1].senderPhone)
        assertFalse(page.toString().contains("SECRET"))
        assertFalse(page.toString().contains("@lid"))
    }

    @Test
    fun `every message type maps to its kind`() {
        val types = mapOf("Conversation" to "TEXT", "NativeFlowMessage" to "TEXT", "VideoMessage" to "VIDEO", "AudioMessage" to "AUDIO",
            "StickerMessage" to "STICKER", "DocumentMessage" to "DOCUMENT", "ContactMessage" to "CONTACT", "PollMessage" to "OTHER")
        response = types.keys.mapIndexed { i, t -> """{"messageid":"$i","messageTimestamp":${i + 1},"messageType":"$t"}""" }
            .joinToString(",", """{"messages":[""", "]}")
        assertEquals(types.values.toList(), inbox.messages(PRIVATE, 0, 50).messages.map { it.kind.name })
    }

    @Test
    fun `trackExists searches saqz track`() {
        response = """{"messages":[{"messageid":"1"}]}"""
        assertTrue(inbox.trackExists("adm-reply-1"))
        val sent = mapper.readTree(requests.single().second)
        assertEquals("saqz", sent["track_source"].asText())
        assertEquals("adm-reply-1", sent["track_id"].asText())
        response = """{"messages":[]}"""
        assertFalse(inbox.trackExists("adm-reply-2"))
    }

    @Test
    fun `provider failures become Unavailable without the response body`() {
        for (code in listOf(401, 403, 500)) {
            status = code
            response = """{"error":"SECRET"}"""
            val error = assertFailsWith<DirectoryError.Unavailable> { inbox.chats() }
            assertFalse(error.reason.contains("SECRET"))
        }
    }

    @Test
    fun `messages bring media numbers and reactions, never the file address`() {
        val wave = java.util.Base64.getEncoder().encodeToString(byteArrayOf(0, 50, 100, 200.toByte()))
        response = """{"messages":[
          {"messageid":"I1","fromMe":false,"messageTimestamp":1000,"messageType":"ImageMessage","text":"quadra",
           "content":{"URL":"https://SECRET","directPath":"/SECRET","mediaKey":"SECRET","fileSHA256":"SECRET","fileLength":2048,
                      "width":800,"height":600,"mimetype":"image/jpeg"}},
          {"messageid":"A1","fromMe":false,"messageTimestamp":2000,"messageType":"AudioMessage",
           "content":{"URL":"https://SECRET","seconds":14,"waveform":"$wave","PTT":true,"fileLength":"8800"}},
          {"messageid":"S1","fromMe":false,"messageTimestamp":2500,"messageType":"StickerMessage","content":{"isAnimated":true}},
          {"messageid":"T1","fromMe":false,"messageTimestamp":2600,"messageType":"Conversation","text":"oi","content":{"seconds":3}},
          {"messageid":"R2","fromMe":false,"messageTimestamp":4000,"messageType":"ReactionMessage","text":"","reaction":"I1",
           "senderName":"Ana","sender_pn":"5511900001111@s.whatsapp.net"},
          {"messageid":"R1","fromMe":false,"messageTimestamp":3000,"messageType":"ReactionMessage","text":"❤️","reaction":"I1",
           "senderName":"Ana","sender_pn":"5511900001111@s.whatsapp.net","sender":"999@lid"},
          {"messageid":"R3","fromMe":true,"messageTimestamp":3500,"messageType":"ReactionMessage","text":"👍",
           "content":{"key":{"ID":"A1","fromMe":false}}},
          {"messageid":"R4","fromMe":false,"messageTimestamp":3600,"messageType":"ReactionMessage","text":"👍"}
        ]}"""

        val page = inbox.messages(GROUP, 0, 50)

        assertEquals(listOf("I1", "A1", "S1", "T1"), page.messages.map { it.id })
        val image = page.messages[0].media!!
        assertEquals(800, image.width); assertEquals(600, image.height); assertEquals(2048L, image.sizeBytes)
        assertTrue(image.waveform.isEmpty())
        val audio = page.messages[1].media!!
        assertEquals(14, audio.seconds); assertEquals(8800L, audio.sizeBytes)
        assertEquals(listOf(0, 50, 100, 100), audio.waveform)
        assertTrue(page.messages[2].media!!.animated)
        assertNull(page.messages[3].media)
        assertEquals(3, page.reactions.size)
        val (heart, thumb, removed) = page.reactions
        assertEquals("I1", heart.targetId); assertEquals("❤️", heart.emoji); assertEquals("Ana", heart.senderName)
        assertEquals("5511900001111", heart.senderPhone); assertFalse(heart.fromMe)
        assertEquals("A1", thumb.targetId); assertTrue(thumb.fromMe)
        assertEquals("I1", removed.targetId); assertNull(removed.emoji)
        assertFalse(page.toString().contains("SECRET"))
        assertFalse(page.toString().contains("@lid"))
    }

    @Test
    fun `chat preview of a reaction is a reaction`() {
        response = """{"chats":[{"wa_chatid":"$PRIVATE","wa_lastMsgTimestamp":1,"wa_lastMessageType":"ReactionMessage","wa_lastMessageTextVote":"❤️"}]}"""
        assertEquals(InboxMessageKind.REACTION, inbox.chats().chats.single().previewKind)
    }

    @Test
    fun `locate searches by id and reads chat, kind and size`() {
        response = """{"messages":[{"messageid":"3AA025E50D","chatid":"$GROUP","messageType":"VideoMessage","content":{"fileLength":4096}}]}"""
        val ref = inbox.locate("3AA025E50D")
        val sent = mapper.readTree(requests.single().second)
        assertEquals("/message/find", requests.single().first)
        assertEquals("3AA025E50D", sent["id"].asText())
        assertEquals(GROUP, ref?.chatId)
        assertEquals(InboxMessageKind.VIDEO, ref?.kind)
        assertEquals(4096L, ref?.sizeBytes)
        response = """{"messages":[{"messageid":"OUTRO","chatid":"$GROUP","messageType":"VideoMessage"}]}"""
        assertNull(inbox.locate("3AA025E50D"))
        response = """{"messages":[]}"""
        assertNull(inbox.locate("3AA025E50D"))
    }

    @Test
    fun `download asks for base64 without transcription, mp3 only for audio, and keeps only bytes and type`() {
        response = """{"base64Data":"AQID","mimetype":"image/jpeg","fileURL":"https://SECRET","transcription":"SECRET","cached":true}"""
        val file = inbox.download("3AA025E50D", InboxMessageKind.IMAGE)!!
        assertEquals(listOf(1, 2, 3), file.bytes.map(Byte::toInt))
        assertEquals("image/jpeg", file.mimeType)
        val sent = mapper.readTree(requests.single().second)
        assertEquals("/message/download", requests.single().first)
        assertEquals("3AA025E50D", sent["id"].asText(), sent.toString())
        assertTrue(sent["return_base64"].asBoolean(), sent.toString())
        assertFalse(sent["transcribe"].asBoolean(true), sent.toString())
        assertFalse(sent.path("generate_mp3").asBoolean(false), sent.toString())

        response = """{"base64Data":"data:audio/mpeg;base64,BAU=","mimetype":"audio/mpeg"}"""
        assertEquals(listOf(4, 5), inbox.download("3AA025E50D", InboxMessageKind.AUDIO)!!.bytes.map(Byte::toInt))
        assertTrue(mapper.readTree(requests.last().second)["generate_mp3"].asBoolean())
    }

    @Test
    fun `download not found is gone, other failures are unavailable without the body`() {
        status = 404
        response = """{"error":"Message not found"}"""
        assertNull(inbox.download("3AA025E50D", InboxMessageKind.IMAGE))
        status = 200
        response = """{"mimetype":"image/jpeg"}"""
        assertNull(inbox.download("3AA025E50D", InboxMessageKind.IMAGE))
        response = """{"base64Data":"%%%","mimetype":"image/jpeg"}"""
        assertNull(inbox.download("3AA025E50D", InboxMessageKind.IMAGE))
        response = """{"base64Data":"A","mimetype":"image/jpeg"}"""
        assertFailsWith<DirectoryError.Unavailable> { inbox.download("3AA025E50D", InboxMessageKind.IMAGE) }
        for (code in listOf(401, 500)) {
            status = code
            response = """{"error":"SECRET"}"""
            val error = assertFailsWith<DirectoryError.Unavailable> { inbox.download("3AA025E50D", InboxMessageKind.IMAGE) }
            assertFalse(error.reason.contains("SECRET"))
        }
    }

    private companion object {
        const val PRIVATE = "5511988887777@s.whatsapp.net"
        const val GROUP = "120363000000000000@g.us"
    }
}
