package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.input.http.GroupWhatsAppBindingController
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.jdbc.whatsapp.JdbcGroupWhatsAppBindingRepository
import br.com.saqz.groups.application.whatsapp.DirectoryError
import br.com.saqz.groups.application.whatsapp.LinkGroupWhatsApp
import br.com.saqz.groups.application.whatsapp.ManageGroupWhatsAppBinding
import br.com.saqz.groups.application.whatsapp.WhatsAppGroupDirectory
import br.com.saqz.groups.application.whatsapp.WhatsAppGroupInfo
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tools.jackson.databind.ObjectMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GroupWhatsAppBindingEndpointIntegrationTest.Configuration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.flyway.enabled=false", "saqz.firebase.emulator.enabled=true"])
class GroupWhatsAppBindingEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var directory: TestGroupDirectory

    private val owner = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val group = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        val jdbc = JdbcClient.create(dataSource)
        seedUser(jdbc, owner, "owner", "+$OWNER_PHONE")
        seedUser(jdbc, member, "member", "+5511977776666")
        seedGroup(jdbc, group, owner)
        membership(jdbc, group, member)
        directory.reset(adminPhone = OWNER_PHONE)
    }

    @Test
    fun `manager links enables reads and toggles the channel`() {
        val path = bindingPath()
        assertEquals(401, request("GET", path, actor = null).statusCode())

        val linked = request("PUT", path, """{"inviteLink":"https://chat.whatsapp.com/AbCdEf123456"}""")
        assertEquals(200, linked.statusCode(), linked.body())
        val linkedBody = json.readTree(linked.body())
        assertEquals(directory.binding.jid, linkedBody["groupJid"].stringValue())
        assertEquals("Vôlei do CERET", linkedBody["groupName"].stringValue())
        assertEquals(true, linkedBody["enabled"].booleanValue())
        assertEquals("ACTIVE", linkedBody["status"].stringValue())

        val read = json.readTree(request("GET", path).body())
        assertEquals(true, read["bound"].booleanValue())
        assertEquals("ACTIVE", read["status"].stringValue())

        val disabled = json.readTree(request("PATCH", path, """{"enabled":false}""").body())
        assertEquals(false, disabled["enabled"]?.booleanValue() ?: false)
        assertEquals("DISABLED", disabled["status"].stringValue())
        assertEquals("DISABLED", json.readTree(request("GET", path).body())["status"].stringValue())

        val reenabled = json.readTree(request("PATCH", path, """{"enabled":true}""").body())
        assertEquals("ACTIVE", reenabled["status"].stringValue())
    }

    @Test
    fun `unbound group reads as not bound and cannot be patched`() {
        val fresh = UUID.randomUUID()
        seedGroup(JdbcClient.create(dataSource), fresh, owner)

        val read = json.readTree(request("GET", "/api/groups/$fresh/whatsapp-binding").body())
        assertEquals(false, read["bound"].booleanValue())
        assertNull(read["groupJid"])
        val patch = request("PATCH", "/api/groups/$fresh/whatsapp-binding", """{"enabled":true}""")
        assertEquals(404, patch.statusCode(), patch.body())
    }

    @Test
    fun `non manager is forbidden on every route`() {
        val path = bindingPath()
        assertEquals(403, request("GET", path, actor = member).statusCode())
        assertEquals(403, request("PUT", path, """{"inviteLink":"https://chat.whatsapp.com/AbCdEf123456"}""", member).statusCode())
        assertEquals(403, request("PATCH", path, """{"enabled":false}""", member).statusCode())
        assertTrue(directory.joined.isEmpty())
    }

    @Test
    fun `unknown group is not found`() {
        val path = "/api/groups/${UUID.randomUUID()}/whatsapp-binding"
        assertEquals(404, request("GET", path).statusCode())
        assertEquals(404, request("PUT", path, """{"inviteLink":"https://chat.whatsapp.com/AbCdEf123456"}""").statusCode())
    }

    @Test
    fun `link validates the invite payload`() {
        assertEquals(422, request("PUT", bindingPath(), "{}").statusCode())
        assertEquals(422, request("PUT", bindingPath(), """{"inviteLink":"   "}""").statusCode())
        assertEquals(422, request("PUT", bindingPath(), """{"inviteLink":"not a link"}""").statusCode())
        assertEquals(422, request("PATCH", bindingPath(), "{}").statusCode())
    }

    @Test
    fun `provider rejects the invite with unprocessable entity`() {
        directory.inviteFailure = DirectoryError.InvalidInvite

        assertEquals(422, request("PUT", bindingPath(), """{"inviteLink":"https://chat.whatsapp.com/AbCdEf123456"}""").statusCode())
    }

    @Test
    fun `invite without a resolvable admin is unprocessable`() {
        directory.reset(adminPhone = "5511900000000")

        val failure = request("PUT", bindingPath(), """{"inviteLink":"https://chat.whatsapp.com/AbCdEf123456"}""")

        assertEquals(422, failure.statusCode(), failure.body())
        assertTrue(directory.joined.isEmpty())
    }

    @Test
    fun `a jid already bound elsewhere is a conflict`() {
        val jdbc = JdbcClient.create(dataSource)
        val otherGroup = UUID.randomUUID()
        val otherOwner = UUID.randomUUID()
        seedUser(jdbc, otherOwner, "other", "+5511966665555")
        seedGroup(jdbc, otherGroup, otherOwner)
        insertBinding(jdbc, otherGroup, directory.binding.jid, otherOwner)

        assertEquals(409, request("PUT", bindingPath(), """{"inviteLink":"https://chat.whatsapp.com/AbCdEf123456"}""").statusCode())
        assertTrue(directory.joined.isEmpty())
    }

    @Test
    fun `a join not confirmed is a conflict`() {
        directory.member = false

        assertEquals(409, request("PUT", bindingPath(), """{"inviteLink":"https://chat.whatsapp.com/AbCdEf123456"}""").statusCode())
    }

    @Test
    fun `disconnected or unavailable provider is a bad gateway`() {
        directory.instanceFailure = DirectoryError.Disconnected
        assertEquals(502, request("PUT", bindingPath(), """{"inviteLink":"https://chat.whatsapp.com/AbCdEf123456"}""").statusCode())

        directory.reset(adminPhone = OWNER_PHONE)
        directory.inviteFailure = DirectoryError.Unavailable("timeout")
        assertEquals(502, request("PUT", bindingPath(), """{"inviteLink":"https://chat.whatsapp.com/AbCdEf123456"}""").statusCode())
    }

    private fun bindingPath() = "/api/groups/$group/whatsapp-binding"

    private fun seedUser(jdbc: JdbcClient, id: UUID, subject: String, phone: String?) {
        jdbc.sql(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, phone, created_at, updated_at) " +
                "VALUES (:id, :subject, true, 'Test Person', :phone, now(), now())",
        ).param("id", id).param("subject", "$subject-${UUID.randomUUID()}").param("phone", phone).update()
    }

    private fun seedGroup(jdbc: JdbcClient, id: UUID, ownerId: UUID) {
        jdbc.sql(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES (:id, :owner, :key, 'Test Group', 'UTC', now(), now())",
        ).param("id", id).param("owner", ownerId).param("key", UUID.randomUUID()).update()
    }

    private fun membership(jdbc: JdbcClient, groupId: UUID, userId: UUID) {
        jdbc.sql(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES (:group, :user, 'ATHLETE', now(), now())",
        ).param("group", groupId).param("user", userId).update()
    }

    private fun insertBinding(jdbc: JdbcClient, groupId: UUID, jid: String, creator: UUID) {
        jdbc.sql(
            "INSERT INTO group_whatsapp_bindings (group_id, whatsapp_jid, invite_code, group_name, instance_jid, " +
                "enabled, created_by, created_at, updated_at) " +
                "VALUES (:group, :jid, 'OtherInvite1', 'Other Group', '551153040175', true, :creator, now(), now())",
        ).param("group", groupId).param("jid", jid).param("creator", creator).update()
    }

    private fun request(method: String, path: String, body: String? = null, actor: UUID? = owner): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        if (actor != null) builder.header("Authorization", "Bearer $actor")
        if (body != null) builder.header("Content-Type", "application/json")
        val publisher = body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
        return HttpClient.newHttpClient().send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Configuration {
        @Bean fun groupWhatsAppTestDataSource(): DataSource = TestPostgres.migrated("classpath:db/migration").dataSource

        @Bean fun groupWhatsAppTestDirectory() = TestGroupDirectory()

        @Bean fun groupWhatsAppTestController(
            dataSource: DataSource,
            directory: TestGroupDirectory,
        ): GroupWhatsAppBindingController {
            val groups = JdbcGroupReadRepository(dataSource)
            val bindings = JdbcGroupWhatsAppBindingRepository(dataSource)
            return GroupWhatsAppBindingController(
                VerifiedGroupActorResolver { UUID.fromString(it.subject) },
                LinkGroupWhatsApp(JdbcTransactionRunner(dataSource), groups, bindings, directory),
                ManageGroupWhatsAppBinding(groups, bindings),
            )
        }

        @Bean @Primary fun groupWhatsAppTestVerifier() = object : VerifyRequestIdentity {
            override fun execute(token: RawIdentityToken) =
                TokenVerification.Verified(RequestIdentity(token.value, emailVerified = true, displayName = "Test Person"))
        }
    }

    private companion object {
        const val OWNER_PHONE = "5511988887777"
    }
}

class TestGroupDirectory : WhatsAppGroupDirectory {
    var instanceJid: String = "551153040175"
    var instanceFailure: DirectoryError? = null
    var inviteFailure: DirectoryError? = null
    var infoFailure: DirectoryError? = null
    var member: Boolean = true
    var binding: WhatsAppGroupInfo = WhatsAppGroupInfo("120363000000000000@g.us", "Vôlei do CERET", emptyList())
    val joined = mutableListOf<String>()

    fun reset(adminPhone: String) {
        instanceJid = "551153040175"
        instanceFailure = null
        inviteFailure = null
        infoFailure = null
        member = true
        binding = WhatsAppGroupInfo(
            jid = "120363${UUID.randomUUID().toString().replace("-", "").take(15)}@g.us",
            name = "Vôlei do CERET",
            admins = listOf(adminPhone),
        )
        joined.clear()
    }

    override fun instanceStatus(): String {
        instanceFailure?.let { throw it }
        return instanceJid
    }

    override fun inviteInfo(inviteCode: String): WhatsAppGroupInfo {
        inviteFailure?.let { throw it }
        return binding
    }

    override fun join(inviteCode: String) {
        joined += inviteCode
    }

    override fun groupInfo(jid: String): WhatsAppGroupInfo {
        infoFailure?.let { throw it }
        return binding
    }

    override fun isMember(jid: String): Boolean = member
}
