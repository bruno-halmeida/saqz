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
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GroupReportEndpointIntegrationTest.Fixture::class)
@ActiveProfiles("test")
class GroupReportEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var ds: DataSource
    @Autowired private lateinit var mapper: ObjectMapper
    private lateinit var owner: String
    private val client = HttpClient.newHttpClient()

    @BeforeEach
    fun reset() { owner = UUID.randomUUID().toString() }

    @Test
    fun `trial and smaller plans see the entry locked and get SUBSCRIPTION_REQUIRED from the report`() {
        val group = createGroup()

        assertFalse(availability(group))
        assertSubscriptionRequired(request("GET", "/api/groups/$group/reports"))
        assertSubscriptionRequired(request("GET", "/api/groups/$group/reports/export?kind=LEDGER"))

        subscribe("ORGANIZADOR")
        assertFalse(availability(group))
        assertSubscriptionRequired(request("GET", "/api/groups/$group/reports?period=YEAR"))
    }

    @Test
    fun `unlimited owner gets the report with attendance and finance for the chosen period`() {
        val group = createGroup()
        subscribe("ILIMITADO")
        execute(
            "INSERT INTO group_expenses (id, group_id, description, amount_cents, expense_date, category, status, direction, created_by_user_id, changed_by_user_id, version, created_at, updated_at) " +
                "SELECT '${UUID.randomUUID()}', '$group', 'Racha recebido', 2000, DATE '2026-09-05', 'RACHA', 'ACTIVE', 'IN', owner_user_id, owner_user_id, 1, now(), now() FROM access_groups WHERE id = '$group'",
        )
        execute(
            "INSERT INTO group_expenses (id, group_id, description, amount_cents, expense_date, category, status, direction, created_by_user_id, changed_by_user_id, version, created_at, updated_at) " +
                "SELECT '${UUID.randomUUID()}', '$group', 'Bola', 500, DATE '2026-08-30', 'VENUE', 'ACTIVE', 'OUT', owner_user_id, owner_user_id, 1, now(), now() FROM access_groups WHERE id = '$group'",
        )

        assertTrue(availability(group))

        val current = mapper.readTree(request("GET", "/api/groups/$group/reports").body())
        assertEquals("CURRENT_MONTH", current["period"].stringValue())
        assertEquals("2026-09-01", current["from"].stringValue())
        assertEquals("2026-10-01", current["toExclusive"].stringValue())
        assertEquals(0, current["attendance"]["games"].intValue())
        assertTrue(current["attendance"]["mostPresent"].isArray)
        assertEquals(2000, current["finance"]["inCents"].longValue())
        assertEquals(0, current["finance"]["outCents"].longValue())
        assertEquals(2000, current["finance"]["balanceCents"].longValue())

        val previous = mapper.readTree(request("GET", "/api/groups/$group/reports?period=PREVIOUS_MONTH").body())
        assertEquals("2026-08-01", previous["from"].stringValue())
        assertEquals(500, previous["finance"]["outCents"].longValue())
        assertEquals(-500, previous["finance"]["balanceCents"].longValue())

        val year = mapper.readTree(request("GET", "/api/groups/$group/reports?period=YEAR").body())
        assertEquals(1500, year["finance"]["balanceCents"].longValue())

        val export = mapper.readTree(request("GET", "/api/groups/$group/reports/export?kind=LEDGER").body())
        assertEquals("saqz-lancamentos-2026-09-01-2026-09-30.csv", export["fileName"].stringValue())
        assertTrue(export["csv"].stringValue().contains("05/09/2026;Entrada;Racha recebido;RACHA;;20,00"))
        val attendance = mapper.readTree(request("GET", "/api/groups/$group/reports/export?kind=ATTENDANCE").body())
        assertEquals("saqz-presenca-2026-09-01-2026-09-30.csv", attendance["fileName"].stringValue())
    }

    @Test
    fun `the owner plan decides for an admin, athletes and outsiders are refused`() {
        val group = createGroup()
        subscribe("ILIMITADO")
        val admin = enroll(group, "ADMIN")
        val athlete = enroll(group, "ATHLETE")

        assertEquals(200, request("GET", "/api/groups/$group/reports", actor = admin).statusCode())
        assertTrue(availability(group, actor = admin))
        assertEquals(403, request("GET", "/api/groups/$group/reports", actor = athlete).statusCode())
        assertEquals(403, request("GET", "/api/groups/$group/reports/availability", actor = athlete).statusCode())
        assertEquals(404, request("GET", "/api/groups/$group/reports", actor = UUID.randomUUID().toString()).statusCode())
        assertEquals(401, request("GET", "/api/groups/$group/reports", actor = null).statusCode())
        assertEquals(400, request("GET", "/api/groups/$group/reports?period=DECADE").statusCode())
        assertEquals(400, request("GET", "/api/groups/$group/reports/export?kind=XLS").statusCode())
    }

    @Test
    fun `admin of a group whose owner has no unlimited plan stays locked`() {
        val group = createGroup()
        val admin = enroll(group, "ADMIN")
        subscribe("ILIMITADO", subject = admin)

        assertFalse(availability(group, actor = admin))
        assertSubscriptionRequired(request("GET", "/api/groups/$group/reports", actor = admin))
    }

    private fun availability(group: UUID, actor: String = owner): Boolean {
        val response = request("GET", "/api/groups/$group/reports/availability", actor = actor)
        assertEquals(200, response.statusCode(), response.body())
        return mapper.readTree(response.body())["enabled"].booleanValue()
    }

    private fun assertSubscriptionRequired(response: HttpResponse<String>) {
        assertEquals(403, response.statusCode(), response.body())
        assertEquals("SUBSCRIPTION_REQUIRED", mapper.readTree(response.body())["code"].stringValue())
    }

    private fun subscribe(plan: String, subject: String = owner) {
        JdbcClient.create(ds).sql(
            """INSERT INTO subscriptions (owner_user_id,plan,cycle,status,asaas_customer_id,asaas_subscription_id,current_period_end,first_confirmed_at,created_at,updated_at)
               SELECT id,'$plan','MONTHLY','ACTIVE','customer',:sub,now()+interval '30 days',now(),now(),now() FROM access_users WHERE firebase_subject=:subject""",
        ).param("sub", UUID.randomUUID().toString()).param("subject", subject).update()
    }

    private fun enroll(group: UUID, role: String): String {
        val subject = UUID.randomUUID().toString()
        request("GET", "/subscriptions/trial", actor = subject)
        execute(
            "INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at) SELECT '$group',id,'$role',now(),now() FROM access_users WHERE firebase_subject='$subject'",
        )
        return subject
    }

    private fun createGroup(): UUID {
        val response = request(
            "POST", "/api/groups",
            """{"requestId":"${UUID.randomUUID()}","name":"Relatórios","modality":"COURT_VOLLEYBALL","composition":"MIXED","timeZone":"America/Sao_Paulo"}""",
        )
        assertEquals(201, response.statusCode(), response.body())
        return UUID.fromString(mapper.readTree(response.body())["id"].stringValue())
    }

    private fun execute(sql: String) {
        JdbcClient.create(ds).sql(sql).update()
    }

    private fun request(method: String, path: String, body: String? = null, actor: String? = owner): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .header("Content-Type", "application/json")
            .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        actor?.let { builder.header("Authorization", "Bearer $it") }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    class FixedClock : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
        override fun instant(): Instant = Instant.parse("2026-09-12T12:00:00Z")
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Fixture {
        @Bean @Primary fun reportClock(): Clock = FixedClock()
        @Bean @Primary fun verifier() = VerifyRequestIdentity {
            TokenVerification.Verified(RequestIdentity(it.value, "${it.value}@example.test", true, "Relator"))
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
            registry.add("saqz.links.domain") { "https://join.test" }
            registry.add("saqz.password-reset.secret") { "segredo-de-teste-com-trinta-e-dois" }
        }
    }
}
