package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.input.http.GroupCommunicationController
import br.com.saqz.groups.adapter.input.http.AthleteController
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRosterRepository
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteStatsRepository
import br.com.saqz.groups.application.athlete.ListAthletes
import br.com.saqz.groups.application.athlete.UpdateOwnAthleteProfile
import br.com.saqz.groups.application.athlete.UpdateAthlete
import br.com.saqz.groups.application.athlete.RemoveAthlete
import br.com.saqz.groups.application.athlete.GetOwnAthleteProfile
import br.com.saqz.groups.application.athlete.GetAthleteStats
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGroupCommunicationRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.GroupCommunicationService
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
import kotlin.test.assertTrue
import tools.jackson.databind.ObjectMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GroupCommunicationEndpointIntegrationTest.Configuration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.flyway.enabled=false", "saqz.firebase.emulator.enabled=true"])
class GroupCommunicationEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var json: ObjectMapper
    private val owner = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val stranger = UUID.randomUUID()
    private val group = UUID.randomUUID()

    @BeforeEach fun setup() {
        val jdbc = JdbcClient.create(dataSource)
        for (id in listOf(owner, member, stranger)) {
            jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, 'Test Person', now(), now())")
                .param("id", id).param("subject", id.toString()).update()
        }
        jdbc.sql("INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) VALUES (:id, :owner, :key, 'Test Group', 'UTC', now(), now())")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        for (id in listOf(owner, member)) jdbc.sql("INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES (:group, :user, 'ATHLETE', now(), now())")
            .param("group", group).param("user", id).update()
    }
    @Test fun `authenticated message API persists author context and handles retry validation and permissions`() {
        val path = "/api/groups/$group/messages?channel=CHAT"
        assertEquals(401, request("GET", path, actor = null).statusCode())
        assertEquals(404, request("GET", path, actor = stranger).statusCode())
        val key = UUID.randomUUID()
        val payload = """{"requestId":"$key","body":"Olá grupo"}"""
        val posted = request("POST", path, payload, member)
        assertEquals(200, posted.statusCode())
        val message = json.readTree(posted.body())
        assertEquals(member.toString(), message["authorId"].stringValue())
        assertEquals(group.toString(), message["groupId"].stringValue())
        assertEquals("Olá grupo", message["body"].stringValue())
        assertEquals(message["id"], json.readTree(request("POST", path, payload, member).body())["id"])
        assertEquals(1, json.readTree(request("GET", path).body())["items"].size())
        assertEquals(409, request("POST", path, """{"requestId":"$key","body":"Outro texto"}""", member).statusCode())
        assertEquals(422, request("POST", path, """{"requestId":"bad","body":"Olá"}""", member).statusCode())
        assertEquals(422, request("POST", path, """{"requestId":"${UUID.randomUUID()}","body":" "}""", member).statusCode())
        assertEquals(403, request("POST", "/api/groups/$group/messages?channel=NOTICE", payload, member).statusCode())
        assertEquals(200, request("POST", "/api/groups/$group/messages?channel=NOTICE", payload).statusCode())
        assertEquals(422, request("GET", "$path&before=-1").statusCode())
    }
    @Test fun `notification preferences and read endpoints are private and persist`() {
        val preferences = """{"notices":false,"messages":true,"reminders":true}"""
        assertEquals(200, request("PUT", "/api/me/notification-preferences", preferences, member).statusCode())
        assertEquals(false, json.readTree(request("GET", "/api/me/notification-preferences", actor = member).body())["notices"].booleanValue())
        assertEquals(true, json.readTree(request("GET", "/api/me/notification-preferences", actor = owner).body())["notices"].booleanValue())
        assertEquals(422, request("PUT", "/api/me/notification-preferences", "{}", member).statusCode())
        val payload = """{"requestId":"${UUID.randomUUID()}","body":"Chat privado"}"""
        assertEquals(200, request("POST", "/api/groups/$group/messages?channel=CHAT", payload).statusCode())
        val notification = json.readTree(request("GET", "/api/me/notifications", actor = member).body())["items"][0]
        assertEquals(false, notification["read"].booleanValue())
        val sequence = notification["sequence"].longValue()
        assertEquals(204, request("PUT", "/api/me/notifications/$sequence/read", actor = stranger).statusCode())
        assertEquals(false, json.readTree(request("GET", "/api/me/notifications", actor = member).body())["items"][0]["read"].booleanValue())
        assertEquals(204, request("PUT", "/api/me/notifications/$sequence/read", actor = member).statusCode())
        assertTrue(json.readTree(request("GET", "/api/me/notifications", actor = member).body())["items"][0]["read"].booleanValue())
        assertEquals(0, json.readTree(request("GET", "/api/me/notifications", actor = stranger).body())["items"].size())
    }
    @Test fun `reminder endpoint rejects nonmanager and invalid game without sending anything`() {
        val path = "/api/groups/$group/games/${UUID.randomUUID()}/notify-pending"
        val payload = """{"requestId":"${UUID.randomUUID()}"}"""
        assertEquals(403, request("POST", path, payload, member).statusCode())
        assertEquals(422, request("POST", path, payload).statusCode())
        assertEquals(0, json.readTree(request("GET", "/api/me/notifications", actor = member).body())["items"].size())
    }
    @Test fun `HTTP departure revokes roster profile and communication access using persisted membership`() {
        val path = "/api/groups/$group/memberships/me"
        assertEquals(200, request("GET", "/api/groups/$group/athletes", actor = member).statusCode())
        assertEquals(1, json.readTree(request("GET", "/api/athletes/me", actor = member).body())["memberships"].size())
        assertEquals(401, request("DELETE", path, actor = null).statusCode())
        assertEquals(200, request("GET", "/api/groups/$group/athletes", actor = member).statusCode())
        assertEquals(403, request("DELETE", path, actor = owner).statusCode())
        assertEquals(204, request("DELETE", path, actor = member).statusCode())
        assertEquals(204, request("DELETE", path, actor = member).statusCode())
        assertEquals(404, request("GET", "/api/groups/$group/athletes", actor = member).statusCode())
        assertEquals(0, json.readTree(request("GET", "/api/athletes/me", actor = member).body())["memberships"].size())
        assertEquals(404, request("GET", "/api/groups/$group/messages?channel=CHAT", actor = member).statusCode())
        assertEquals(200, request("GET", "/api/groups/$group/athletes", actor = owner).statusCode())
        val count = JdbcClient.create(dataSource).sql("SELECT count(*) FROM group_memberships WHERE group_id=:group")
            .param("group", group).query(Int::class.java).single()
        assertEquals(1, count)
    }

    private fun request(method: String, path: String, body: String? = null, actor: UUID? = owner): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        if (actor != null) builder.header("Authorization", "Bearer $actor")
        if (body != null) builder.header("Content-Type", "application/json")
        return HttpClient.newHttpClient().send(builder.method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
    }
    @TestConfiguration(proxyBeanMethods = false)
    class Configuration {
        @Bean fun departureTestController(dataSource: DataSource): AthleteController {
            val transaction = JdbcTransactionRunner(dataSource)
            val groups = JdbcGroupReadRepository(dataSource)
            val athletes = JdbcAthleteRepository(dataSource)
            val roster = JdbcAthleteRosterRepository(dataSource)
            val access = GroupAccessPolicy()
            return AthleteController(
                VerifiedGroupActorResolver { UUID.fromString(it.subject) },
                ListAthletes(groups, roster, access),
                UpdateOwnAthleteProfile(transaction, groups, athletes),
                UpdateAthlete(transaction, groups, athletes, access),
                RemoveAthlete(transaction, groups, athletes, access),
                GetOwnAthleteProfile(roster),
                GetAthleteStats(groups, athletes, JdbcAthleteStatsRepository(dataSource)),
            )
        }
        @Bean fun communicationTestDataSource(): DataSource = TestPostgres.migrated("classpath:db/migration").dataSource
        @Bean @Primary fun communicationVerifier() = object : VerifyRequestIdentity {
            override fun execute(token: RawIdentityToken) = TokenVerification.Verified(RequestIdentity(token.value, emailVerified = true, displayName = "Test Person"))
        }
        @Bean fun communicationTestController(dataSource: DataSource) = GroupCommunicationController(
            VerifiedGroupActorResolver { UUID.fromString(it.subject) },
            GroupCommunicationService(JdbcTransactionRunner(dataSource), JdbcGroupReadRepository(dataSource), JdbcGroupCommunicationRepository(dataSource)),
        )
    }
}
