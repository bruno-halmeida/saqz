package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.input.http.ContentModerationController
import br.com.saqz.groups.adapter.input.http.GroupCommunicationController
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGroupCommunicationRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.moderation.JdbcContentModerationRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.groups.application.moderation.ContentModerationService
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
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals

/** O contrato HTTP que o app usa para denunciar e bloquear (Apple 1.2). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContentModerationEndpointIntegrationTest.Configuration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.flyway.enabled=false", "saqz.firebase.emulator.enabled=true"])
class ContentModerationEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var json: ObjectMapper
    private val owner = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val stranger = UUID.randomUUID()
    private val group = UUID.randomUUID()

    @BeforeEach fun setup() {
        val jdbc = JdbcClient.create(dataSource)
        for ((id, name) in listOf(owner to "Dona", member to "Atleta", stranger to "Estranha")) {
            jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, :name, now(), now())")
                .param("id", id).param("subject", id.toString()).param("name", name).update()
        }
        jdbc.sql("INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) VALUES (:id, :owner, :key, 'Grupo', 'UTC', now(), now())")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        jdbc.sql("INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES (:group, :user, 'ADMIN', now(), now())")
            .param("group", group).param("user", owner).update()
        jdbc.sql("INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES (:group, :user, 'ATHLETE', now(), now())")
            .param("group", group).param("user", member).update()
    }

    @Test fun `report a notice, block its author and see it disappear`() {
        val notices = "/api/groups/$group/messages?channel=NOTICE"
        val posted = request("POST", notices, """{"requestId":"${UUID.randomUUID()}","body":"Treino amanhã"}""", owner)
        assertEquals(200, posted.statusCode(), posted.body())
        val noticeId = json.readTree(posted.body())["id"].stringValue()

        val report = """{"groupId":"$group","targetType":"MESSAGE","targetId":"$noticeId","reason":"OFFENSIVE","details":"Ofensivo"}"""
        assertEquals(401, request("POST", "/api/reports", report, actor = null).statusCode())
        assertEquals(404, request("POST", "/api/reports", report, stranger).statusCode())
        assertEquals(204, request("POST", "/api/reports", report, member).statusCode())
        assertEquals(422, request("POST", "/api/reports", report.replace("OFFENSIVE", "BLOCKED"), member).statusCode())
        assertEquals(422, request("POST", "/api/reports", report.replace("MESSAGE", "POST"), member).statusCode())

        val block = """{"groupId":"$group"}"""
        assertEquals(404, request("PUT", "/api/me/blocks/$stranger", block, member).statusCode())
        assertEquals(422, request("PUT", "/api/me/blocks/$member", block, member).statusCode())
        assertEquals(204, request("PUT", "/api/me/blocks/$owner", block, member).statusCode())
        val blocks = json.readTree(request("GET", "/api/me/blocks", actor = member).body())
        assertEquals(owner.toString(), blocks[0]["userId"].stringValue())
        assertEquals("Dona", blocks[0]["displayName"].stringValue())
        assertEquals(0, json.readTree(request("GET", notices, actor = member).body())["items"].size())
        assertEquals(1, json.readTree(request("GET", notices, actor = owner).body())["items"].size())

        assertEquals(204, request("DELETE", "/api/me/blocks/$owner", actor = member).statusCode())
        assertEquals(204, request("DELETE", "/api/me/blocks/$owner", actor = member).statusCode())
        assertEquals(1, json.readTree(request("GET", notices, actor = member).body())["items"].size())
        assertEquals(2, JdbcClient.create(dataSource).sql("SELECT count(*) FROM content_reports").query(Int::class.java).single())
    }

    @Test fun `objectionable notice comes back as a body field error`() {
        val response = request(
            "POST", "/api/groups/$group/messages?channel=NOTICE",
            """{"requestId":"${UUID.randomUUID()}","body":"Seu filho da puta"}""", owner,
        )
        assertEquals(422, response.statusCode())
        assertEquals("objectionable", json.readTree(response.body())["fieldErrors"]["body"][0].stringValue())
    }

    private fun request(method: String, path: String, body: String? = null, actor: UUID?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        if (actor != null) builder.header("Authorization", "Bearer $actor")
        if (body != null) builder.header("Content-Type", "application/json")
        val publisher = body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
        return HttpClient.newHttpClient().send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Configuration {
        private val actors = VerifiedGroupActorResolver { UUID.fromString(it.subject) }

        @Bean fun moderationTestDataSource(): DataSource = TestPostgres.migrated("classpath:db/migration").dataSource
        @Bean @Primary fun moderationVerifier() = object : VerifyRequestIdentity {
            override fun execute(token: RawIdentityToken) =
                TokenVerification.Verified(RequestIdentity(token.value, emailVerified = true, displayName = "Test Person"))
        }
        @Bean fun moderationTestController(dataSource: DataSource) = ContentModerationController(
            actors, ContentModerationService(JdbcTransactionRunner(dataSource), JdbcContentModerationRepository(dataSource)),
        )
        @Bean fun moderationCommunicationController(dataSource: DataSource) = GroupCommunicationController(
            actors,
            GroupCommunicationService(JdbcTransactionRunner(dataSource), JdbcGroupReadRepository(dataSource), JdbcGroupCommunicationRepository(dataSource)),
        )
    }
}
