package br.com.saqz.bootstrap

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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OrganizerTrialEndpointIntegrationTest.Fixture::class)
@ActiveProfiles("test")
class OrganizerTrialEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var ds: DataSource
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var clock: TrialClock
    private val now = Instant.parse("2026-09-12T12:00:00Z")
    private lateinit var token: String
    private val client = HttpClient.newHttpClient()

    @BeforeEach
    fun reset() { token = UUID.randomUUID().toString(); clock.now = now }

    @Test
    fun `available lookup is free and returns exact offer and configured app link`() {
        val response = request("GET", "/subscriptions/trial")
        assertEquals(200, response.statusCode())
        val body = mapper.readTree(response.body())
        assertEquals("AVAILABLE", body["status"].stringValue())
        assertTrue(body["startedAt"].isNull)
        assertTrue(body["endsAt"].isNull)
        assertEquals(now.toString(), body["serverTime"].stringValue())
        assertTrue(body["canCreateGroup"].booleanValue())
        assertFalse(body["readOnly"].booleanValue())
        assertEquals(1, body["maxGroups"].intValue())
        assertEquals(25, body["maxAthletes"].intValue())
        assertEquals("https://join.test/?%24ios_nativelink=true", body["appUrl"].stringValue())
        assertEquals(404, request("GET", "/subscriptions/me").statusCode())
        assertEquals(0, jdbc().sql("SELECT count(*)::int FROM organizer_trials t JOIN access_users u ON t.owner_user_id=u.id WHERE u.firebase_subject=:token").param("token", token).query(Int::class.java).single())
    }

    @Test
    fun `group access exposes owner trial to members but never outsiders or anonymous`() {
        val group = createGroup()
        val body = mapper.readTree(request("GET", "/api/groups/$group/trial").body())
        assertEquals("ACTIVE", body["status"].stringValue())
        assertEquals(now.toString(), body["startedAt"].stringValue())
        assertEquals(now.plus(Duration.ofDays(14)).toString(), body["endsAt"].stringValue())
        assertTrue(body["isOwner"].booleanValue())
        assertFalse(body["canCreateGroup"].booleanValue())
        val member = UUID.randomUUID().toString()
        request("GET", "/subscriptions/trial", actor = member)
        jdbc().sql("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at) SELECT :group,id,'ATHLETE',now(),now() FROM access_users WHERE firebase_subject=:subject")
            .param("group", group).param("subject", member).update()
        val athlete = mapper.readTree(request("GET", "/api/groups/$group/trial", actor = member).body())
        assertEquals("ACTIVE", athlete["status"].stringValue())
        assertFalse(athlete["isOwner"].booleanValue())
        assertFalse(athlete["canCreateGroup"].booleanValue())
        assertEquals(body["endsAt"], athlete["endsAt"])
        val outsider = request("GET", "/api/groups/$group/trial", actor = UUID.randomUUID().toString())
        assertEquals(404, outsider.statusCode())
        assertFalse(outsider.body().contains("endsAt"))
        assertEquals(401, request("GET", "/api/groups/$group/trial", actor = null).statusCode())
        assertEquals(401, request("GET", "/subscriptions/trial", actor = null).statusCode())
        assertEquals(404, request("GET", "/api/groups/${UUID.randomUUID()}/trial").statusCode())
    }

    @Test
    fun `exact expiry is read only while paid subscription removes trial restrictions`() {
        val group = createGroup()
        clock.now = now.plus(Duration.ofDays(14))
        val expired = mapper.readTree(request("GET", "/api/groups/$group/trial").body())
        assertEquals("EXPIRED", expired["status"].stringValue())
        assertTrue(expired["readOnly"].booleanValue())
        assertFalse(expired["canCreateGroup"].booleanValue())
        jdbc().sql("""INSERT INTO subscriptions (owner_user_id,plan,cycle,status,asaas_customer_id,asaas_subscription_id,current_period_end,first_confirmed_at,created_at,updated_at)
            SELECT id,'ORGANIZADOR','MONTHLY','ACTIVE','customer',:sub,now()+interval '30 days',now(),now(),now() FROM access_users WHERE firebase_subject=:subject""")
            .param("sub", UUID.randomUUID().toString()).param("subject", token).update()
        val subscribed = mapper.readTree(request("GET", "/api/groups/$group/trial").body())
        assertEquals("SUBSCRIBED", subscribed["status"].stringValue())
        assertFalse(subscribed["readOnly"].booleanValue())
        assertTrue(subscribed["canCreateGroup"].booleanValue())
    }

    private fun createGroup(): UUID {
        val response = request("POST", "/api/groups", """{"requestId":"${UUID.randomUUID()}","name":"Trial Group","modality":"COURT_VOLLEYBALL","composition":"MIXED","timeZone":"America/Sao_Paulo"}""")
        assertEquals(201, response.statusCode(), response.body())
        return UUID.fromString(mapper.readTree(response.body())["id"].stringValue())
    }

    private fun jdbc() = JdbcClient.create(ds)
    private fun request(method: String, path: String, body: String? = null, actor: String? = token): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .header("Content-Type", "application/json")
            .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        actor?.let { builder.header("Authorization", "Bearer $it") }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    class TrialClock(var now: Instant = Instant.parse("2026-09-12T12:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Fixture {
        @Bean @Primary fun trialClock() = TrialClock()
        @Bean @Primary fun verifier() = VerifyRequestIdentity {
            TokenVerification.Verified(RequestIdentity(it.value, "${it.value}@example.test", true, "Trial User"))
        }
    }

    companion object {
        private val database = TestPostgres.empty()
        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { database.jdbcUrl }
            registry.add("spring.datasource.username") { database.username }
            registry.add("spring.datasource.password") { database.password }
            registry.add("saqz.firebase.emulator.enabled") { "true" }
            registry.add("saqz.branch.domain") { "https://join.test" }
            registry.add("saqz.password-reset.secret") { "segredo-de-teste-com-trinta-e-dois" }
        }
    }
}
