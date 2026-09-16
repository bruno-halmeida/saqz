package br.com.saqz.groups.adapter.output.whatsapp

import br.com.saqz.groups.application.whatsapp.DirectoryError
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.uazapi.sdk.UazapiClient
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UazapiGroupDirectoryTest {
    private val mapper = ObjectMapper()

    @Test
    fun `inviteInfo unwraps the group wrapper and normalizes full jid phone numbers`() {
        val server = Server()
        server.on("/group/inviteInfo") { 200 to groupJson() }
        try {
            val info = directory(server).inviteInfo("https://chat.whatsapp.com/AbCdEf123456")
            assertEquals(GROUP_JID, info.jid)
            assertEquals("Vôlei do CERET", info.name)
            assertEquals(listOf(ADMIN_PHONE), info.admins)
        } finally {
            server.close()
        }
        assertEquals(1, server.calls("/group/inviteInfo"))
    }

    @Test
    fun `groupInfo accepts an unwrapped group payload`() {
        val server = Server()
        server.on("/group/info") { 200 to flatGroupJson() }
        try {
            val info = directory(server).groupInfo(GROUP_JID)
            assertEquals("Flat", info.name)
            assertTrue(info.admins.isEmpty())
        } finally {
            server.close()
        }
    }

    @Test
    fun `a 500 that group does not exist becomes NotInGroup and never retries`() {
        val server = Server()
        server.on("/group/info") { 500 to "{\"error\":\"that group does not exist\"}" }
        try {
            assertFailsWith<DirectoryError.NotInGroup> { directory(server).groupInfo(GROUP_JID) }
        } finally {
            server.close()
        }
        assertEquals(1, server.calls("/group/info"))
    }

    @Test
    fun `groupInfo maps a 404 to NotInGroup`() {
        val server = Server()
        server.on("/group/info") { 404 to "{}" }
        try {
            assertFailsWith<DirectoryError.NotInGroup> { directory(server).groupInfo(GROUP_JID) }
        } finally {
            server.close()
        }
    }

    @Test
    fun `inviteInfo maps a transient 5xx without the missing-group payload to Unavailable`() {
        val server = Server()
        server.on("/group/inviteInfo") { 503 to "{\"error\":\"upstream down\"}" }
        try {
            assertFailsWith<DirectoryError.Unavailable> { directory(server).inviteInfo("abc") }
        } finally {
            server.close()
        }
    }

    @Test
    fun `inviteInfo maps a 4xx to InvalidInvite`() {
        val server = Server()
        server.on("/group/inviteInfo") { 400 to "{\"error\":\"invalid invite\"}" }
        try {
            assertFailsWith<DirectoryError.InvalidInvite> { directory(server).inviteInfo("abc") }
        } finally {
            server.close()
        }
    }

    @Test
    fun `join returns an empty group without throwing and the recheck confirms membership`() {
        val server = Server()
        server.on("/group/join") { 200 to "{\"group\":{\"JID\":\"\",\"Participants\":null},\"needs_refresh\":true}" }
        server.on("/group/info") { 200 to groupJson(participants = listOf(ADMIN_PHONE to true, INSTANCE_PHONE to false)) }
        server.on("/instance/status") { 200 to statusJson(connected = true) }
        try {
            val directory = directory(server)
            directory.join("AbCdEf123456")
            assertTrue(directory.isMember(GROUP_JID))
        } finally {
            server.close()
        }
        assertEquals(1, server.calls("/group/join"))
    }

    @Test
    fun `join maps the missing-group 500 to NotInGroup`() {
        val server = Server()
        server.on("/group/join") { 500 to "{\"error\":\"that group does not exist\"}" }
        try {
            assertFailsWith<DirectoryError.NotInGroup> { directory(server).join("abc") }
        } finally {
            server.close()
        }
        assertEquals(1, server.calls("/group/join"))
    }

    @Test
    fun `isMember is false when the instance is absent from participants`() {
        val server = Server()
        server.on("/instance/status") { 200 to statusJson(connected = true) }
        server.on("/group/info") { 200 to groupJson(participants = listOf(ADMIN_PHONE to true, MEMBER_PHONE to false)) }
        try {
            val directory = directory(server)
            assertFalse(directory.isMember(GROUP_JID))
            assertFalse(directory.isMember(GROUP_JID))
        } finally {
            server.close()
        }
        assertEquals(2, server.calls("/group/info"))
    }

    @Test
    fun `isMember is true when the instance phone is a participant`() {
        val server = Server()
        server.on("/instance/status") { 200 to statusJson(connected = true) }
        server.on("/group/info") { 200 to groupJson(participants = listOf(ADMIN_PHONE to true, INSTANCE_PHONE to false)) }
        try {
            assertTrue(directory(server).isMember(GROUP_JID))
        } finally {
            server.close()
        }
    }

    @Test
    fun `instanceStatus reads the connected instance phone in digits`() {
        val server = Server()
        server.on("/instance/status") { 200 to statusJson(connected = true) }
        try {
            assertEquals(INSTANCE_PHONE, directory(server).instanceStatus())
        } finally {
            server.close()
        }
    }

    @Test
    fun `instanceStatus throws Disconnected when the instance is not connected`() {
        val server = Server()
        server.on("/instance/status") { 200 to statusJson(connected = false) }
        try {
            assertFailsWith<DirectoryError.Disconnected> { directory(server).instanceStatus() }
        } finally {
            server.close()
        }
        assertEquals(1, server.calls("/instance/status"))
    }

    private fun directory(server: Server) = UazapiGroupDirectory(server.client())

    private fun groupJson(
        participants: List<Pair<String, Boolean>> = listOf(ADMIN_PHONE to true, MEMBER_PHONE to false),
    ): String {
        val group = mapper.createObjectNode()
        group.put("JID", GROUP_JID)
        group.put("Name", "Vôlei do CERET")
        group.put("OwnerJID", "123227628130444@lid")
        group.put("OwnerPN", "$ADMIN_PHONE@s.whatsapp.net")
        val array = group.putArray("Participants")
        participants.forEach { (phone, admin) ->
            val participant = array.addObject()
            participant.put("PhoneNumber", "$phone@s.whatsapp.net")
            participant.put("IsAdmin", admin)
            participant.put("LID", "x@lid")
        }
        group.put("IsEphemeral", true)
        group.put("DisappearingTimer", 0)
        val root = mapper.createObjectNode()
        root.set<JsonNode>("group", group)
        root.put("response", "ok")
        return mapper.writeValueAsString(root)
    }

    private fun flatGroupJson(): String {
        val group = mapper.createObjectNode()
        group.put("JID", GROUP_JID)
        group.put("Name", "Flat")
        group.putArray("Participants")
        return mapper.writeValueAsString(group)
    }

    private fun statusJson(connected: Boolean): String {
        val instance = mapper.createObjectNode()
        instance.put("id", "i")
        instance.put("name", "saqz-principal")
        instance.put("status", if (connected) "connected" else "disconnected")
        instance.put("profileName", "Saqz.app")
        instance.put("token", "t")
        val status = mapper.createObjectNode()
        status.put("connected", connected)
        status.put("loggedIn", connected)
        val jid = status.putObject("jid")
        jid.put("user", INSTANCE_PHONE)
        jid.put("server", "s.whatsapp.net")
        val root = mapper.createObjectNode()
        root.set<JsonNode>("instance", instance)
        root.set<JsonNode>("status", status)
        return mapper.writeValueAsString(root)
    }

    private class Server {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val responses = mutableMapOf<String, () -> Pair<Int, String>>()
        private val calls = mutableMapOf<String, Int>()

        init {
            server.createContext("/") { exchange -> dispatch(exchange) }
            server.start()
        }

        fun on(path: String, response: () -> Pair<Int, String>) {
            responses[path] = response
        }

        fun calls(path: String): Int = calls[path] ?: 0

        fun client(): UazapiClient = UazapiClient.builder()
            .baseUrl("http://127.0.0.1:${server.address.port}").token("test-instance-token")
            .connectTimeout(Duration.ofSeconds(1)).readTimeout(Duration.ofSeconds(1)).build()

        fun close() = server.stop(0)

        private fun dispatch(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            calls[path] = (calls[path] ?: 0) + 1
            exchange.requestBody.use { it.readBytes() }
            val (status, body) = responses[path]?.invoke() ?: (404 to "{}")
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private companion object {
        const val GROUP_JID = "120363000000000000@g.us"
        const val INSTANCE_PHONE = "551153040175"
        const val ADMIN_PHONE = "5511988887777"
        const val MEMBER_PHONE = "5511999990000"
    }
}
