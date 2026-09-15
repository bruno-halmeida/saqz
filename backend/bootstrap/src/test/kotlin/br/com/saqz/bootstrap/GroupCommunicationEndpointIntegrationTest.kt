package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.input.http.GroupCommunicationController
import br.com.saqz.groups.adapter.input.http.AthleteController
import br.com.saqz.groups.adapter.input.http.GameController
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcGameOccurrenceRepository
import br.com.saqz.groups.adapter.output.jdbc.attendance.JdbcAttendanceCommandRepository
import br.com.saqz.groups.application.game.CreateGame
import br.com.saqz.groups.application.game.EditGame
import br.com.saqz.groups.application.game.ChangeGameLifecycle
import br.com.saqz.groups.application.game.ListGames
import br.com.saqz.groups.application.game.GetGame
import br.com.saqz.groups.application.game.GameSideEffectPort
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
    @Test fun `notification channels persist separately and legacy clients preserve WhatsApp opt in`() {
        val path = "/api/me/notification-preferences"
        val payload = """{"notices":false,"messages":true,"reminders":false,
            "push":{"notices":true,"messages":false,"reminders":true,"charges":false},
            "whatsapp":{"notices":true,"reminders":false,"charges":true}}"""
        assertEquals(401, request("PUT", path, payload, actor = null).statusCode())
        assertEquals(200, request("PUT", path, payload, member).statusCode())
        val saved = json.readTree(request("GET", path, actor = member).body())
        assertEquals(json.readTree(payload), saved)
        val other = json.readTree(request("GET", path, actor = stranger).body())
        assertEquals(false, other["whatsapp"]["notices"].booleanValue())
        assertEquals(200, request("PUT", path, """{"notices":true,"messages":true,"reminders":false}""", member).statusCode())
        val legacy = json.readTree(request("GET", path, actor = member).body())
        assertEquals(saved["whatsapp"], legacy["whatsapp"])
        assertEquals(false, legacy["push"]["charges"].booleanValue())
        assertEquals(422, request("PUT", path, "{}", member).statusCode())
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

    @Test fun `leaving revokes game list and detail while owner retains the persisted game`() {
        val games = "/api/groups/$group/games"
        val gameId = UUID.randomUUID()
        val payload = """{"requestId":"$gameId","title":"Treino de saída","venue":{"name":"Arena","address":"Rua Teste 10"},"localDate":"2026-10-12","localTime":"19:00","zoneId":"UTC","startsAt":"2026-10-12T19:00:00Z","durationMinutes":90,"capacity":12,"confirmationDeadline":"2026-10-12T17:00:00Z","useDefaultGameFee":true}"""
        val created = request("POST", games, payload)
        assertEquals(201, created.statusCode(), created.body())
        // Fixture publicada no banco; todos os checks de leitura e saída passam por HTTP real.
        JdbcClient.create(dataSource).sql("UPDATE games SET status='PUBLISHED' WHERE id=:id")
            .param("id", gameId).update()
        assertEquals(401, request("GET", games, actor = null).statusCode())
        assertEquals(404, request("GET", games, actor = stranger).statusCode())
        assertEquals(gameId.toString(), json.readTree(request("GET", games, actor = member).body())[0]["id"].stringValue())
        assertEquals(200, request("GET", "$games/$gameId", actor = member).statusCode())
        assertEquals(204, request("DELETE", "/api/groups/$group/memberships/me", actor = member).statusCode())
        assertEquals(404, request("GET", games, actor = member).statusCode())
        assertEquals(404, request("GET", "$games/$gameId", actor = member).statusCode())
        assertEquals(gameId.toString(), json.readTree(request("GET", games).body())[0]["id"].stringValue())
        assertEquals(200, request("GET", "$games/$gameId").statusCode())
    }

    private fun request(method: String, path: String, body: String? = null, actor: UUID? = owner): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        if (actor != null) builder.header("Authorization", "Bearer $actor")
        if (body != null) builder.header("Content-Type", "application/json")
        return HttpClient.newHttpClient().send(builder.method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
    }
    @TestConfiguration(proxyBeanMethods = false)
    class Configuration {
        @Bean fun departureGameController(dataSource: DataSource): GameController {
            val transaction = JdbcTransactionRunner(dataSource)
            val repository = JdbcGameOccurrenceRepository(dataSource)
            val attendance = JdbcAttendanceCommandRepository(dataSource)
            val unusedEffects = GameSideEffectPort { _, _, _ -> error("Lifecycle mutations are outside this read test") }
            return GameController(
                VerifiedGroupActorResolver { UUID.fromString(it.subject) },
                CreateGame(transaction, repository), EditGame(transaction, repository, unusedEffects),
                ChangeGameLifecycle(transaction, repository, unusedEffects),
                ListGames(repository, attendance), GetGame(repository, attendance),
            )
        }
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
