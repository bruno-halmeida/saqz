package br.com.saqz.bootstrap

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.adminweb.http.AdminWhatsAppController
import br.com.saqz.adminweb.http.logChat
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.whatsapp.AdminWhatsAppInbox
import br.com.saqz.groups.application.whatsapp.AdminWhatsAppMessages
import br.com.saqz.groups.application.whatsapp.DirectoryError
import br.com.saqz.groups.application.whatsapp.InboxChat
import br.com.saqz.groups.application.whatsapp.InboxChatPage
import br.com.saqz.groups.application.whatsapp.InboxMessage
import br.com.saqz.groups.application.whatsapp.InboxMessageKind
import br.com.saqz.groups.application.whatsapp.InboxMessagePage
import br.com.saqz.groups.application.whatsapp.SaqzGroupLink
import br.com.saqz.groups.application.whatsapp.WhatsAppGroupDirectory
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val PRIVATE = "5511988887777@s.whatsapp.net"
private const val GROUP = "120363000000000000@g.us"
private const val REQUEST = "9c8b7a6d-5e4f-4321-9876-abcdef012345"

private fun admin(subject: String) = if (subject == "admin-subject") PlatformAdminView(UUID.randomUUID(), "admin@saqz.test", "Ana Admin") else null

private fun verifier() = VerifyRequestIdentity {
    when (it.value) {
        "admin-token" -> TokenVerification.Verified(RequestIdentity("admin-subject", "admin@saqz.test", true))
        "user-token" -> TokenVerification.Verified(RequestIdentity("user-subject", "user@saqz.test", true))
        else -> TokenVerification.Rejected
    }
}

private fun call(port: Int, method: String, path: String, token: String, body: String? = null): HttpResponse<String> {
    val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).header("Authorization", "Bearer $token")
        .header("Content-Type", "application/json")
        .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()).build()
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdminWhatsAppEndpointIntegrationTest.Config::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class AdminWhatsAppEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0

    private val messagesPath = "/admin/whatsapp/messages?chatId=${URLEncoder.encode(PRIVATE, Charsets.UTF_8)}"

    @Test
    fun `quem nao e admin e barrado nas tres rotas`() {
        assertEquals(403, call(port, "GET", "/admin/whatsapp/conversations", "user-token").statusCode())
        assertEquals(403, call(port, "GET", messagesPath, "user-token").statusCode())
        assertEquals(403, call(port, "POST", "/admin/whatsapp/messages", "user-token", reply(PRIVATE)).statusCode())
    }

    @Test
    fun `conversas saem so com a lista fechada de campos`() {
        val response = call(port, "GET", "/admin/whatsapp/conversations", "admin-token")
        assertEquals(200, response.statusCode())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        assertTrue(response.body().contains("\"connected\":true"))
        assertTrue(response.body().contains("\"saqzGroup\":{"))
        assertTrue(response.body().contains("\"previewKind\":\"TEXT\""))
        assertTrue(response.body().contains("\"lastMessageAt\":\"1970-01-01T00:00:01Z\""))
    }

    @Test
    fun `mensagens validam parametros`() {
        assertEquals(200, call(port, "GET", messagesPath, "admin-token").statusCode())
        assertEquals(400, call(port, "GET", "/admin/whatsapp/messages?chatId=abc", "admin-token").statusCode())
        assertEquals(400, call(port, "GET", "/admin/whatsapp/messages", "admin-token").statusCode())
        assertEquals(400, call(port, "GET", "$messagesPath&limit=0", "admin-token").statusCode())
        assertEquals(400, call(port, "GET", "$messagesPath&limit=101", "admin-token").statusCode())
        assertEquals(400, call(port, "GET", "$messagesPath&offset=-1", "admin-token").statusCode())
        assertEquals(400, call(port, "GET", "$messagesPath&offset=x", "admin-token").statusCode())
    }

    @Test
    fun `resposta devolve os codigos combinados`() {
        val sent = call(port, "POST", "/admin/whatsapp/messages", "admin-token", reply(PRIVATE))
        assertEquals(200, sent.statusCode())
        assertTrue(sent.body().contains("SENT"))
        val group = call(port, "POST", "/admin/whatsapp/messages", "admin-token", reply(GROUP))
        assertEquals(422, group.statusCode())
        assertTrue(group.body().contains("GROUP_READ_ONLY"))
        assertEquals(400, call(port, "POST", "/admin/whatsapp/messages", "admin-token", """{"chatId":"$PRIVATE","text":"oi"}""").statusCode())
    }

    @Test
    fun `json malformado responde 400 INVALID_INPUT`() {
        val response = call(port, "POST", "/admin/whatsapp/messages", "admin-token", "{nao e json")
        assertEquals(400, response.statusCode())
        assertTrue(response.body().contains("INVALID_INPUT"))
    }

    @Test
    fun `log mostra so os quatro ultimos digitos da privada`() {
        assertEquals("7777", logChat(PRIVATE))
        assertEquals(GROUP, logChat(GROUP))
    }

    private fun reply(chatId: String) = """{"chatId":"$chatId","text":"oi","requestId":"$REQUEST"}"""

    private class Inbox : AdminWhatsAppInbox {
        private val chat = InboxChat(PRIVATE, null, "5511988887777", Instant.ofEpochSecond(1), "oi", InboxMessageKind.TEXT)
        override fun chats() = InboxChatPage(listOf(chat, chat.copy(chatId = GROUP, phone = null)), false)
        override fun privateChat(chatId: String) = chat
        override fun messages(chatId: String, offset: Int, limit: Int) = InboxMessagePage(
            listOf(InboxMessage("1", false, Instant.ofEpochSecond(1), "Rita", "5511988887777", "oi", InboxMessageKind.TEXT)), false, null)
        override fun trackExists(trackId: String) = false
    }

    private class Directory : WhatsAppGroupDirectory {
        override fun instanceStatus() = "5511900000000"
        override fun inviteInfo(inviteCode: String) = throw DirectoryError.InvalidInvite
        override fun join(inviteCode: String) = throw DirectoryError.InvalidInvite
        override fun groupInfo(jid: String) = throw DirectoryError.NotInGroup
        override fun isMember(jid: String) = false
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean @Primary fun adminWhatsAppVerifier(): VerifyRequestIdentity = verifier()
        @Bean fun adminWhatsAppLookup(): PlatformAdminLookup = PlatformAdminLookup(::admin)
        @Bean fun adminWhatsAppController(lookup: PlatformAdminLookup) = AdminWhatsAppController(
            AdminWhatsAppMessages(Inbox(), Directory(), { mapOf(GROUP to SaqzGroupLink(UUID.randomUUID(), "Vôlei", false)) }) { WhatsAppDelivery.Accepted },
            lookup,
        )
    }
}

/** Sem o WhatsApp ligado o controller não existe: o adm recebe 404 e mostra o estado próprio. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdminWhatsAppDisabledEndpointIntegrationTest.Config::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class AdminWhatsAppDisabledEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0

    @Test
    fun `whatsapp desligado responde 404`() {
        val response = call(port, "GET", "/admin/whatsapp/conversations", "admin-token")
        assertEquals(404, response.statusCode())
        assertFalse(response.body().contains("connected"))
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean @Primary fun adminWhatsAppOffVerifier(): VerifyRequestIdentity = verifier()
        @Bean fun adminWhatsAppOffLookup(): PlatformAdminLookup = PlatformAdminLookup(::admin)
    }
}
