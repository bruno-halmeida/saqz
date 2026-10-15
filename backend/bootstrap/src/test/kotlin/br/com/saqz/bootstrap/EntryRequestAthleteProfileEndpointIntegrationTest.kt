package br.com.saqz.bootstrap

import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
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
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cadastro de atleta gravado no pedido de entrada e aplicado quando o dono aprova (P1-12f), com Postgres de verdade. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EntryRequestAthleteProfileEndpointIntegrationTest.Configuration::class)
@ActiveProfiles("test")
class EntryRequestAthleteProfileEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var json: ObjectMapper
    private lateinit var owner: UUID
    private lateinit var applicant: UUID
    private lateinit var group: UUID
    private val code = InviteCode.from(Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 21 }))
    private val profile = """
        {"nickname":"Bia","position":"LEVANTADOR","secondaryPosition":"OPOSTO","level":"AVANCADO","preferredSide":null,"heightCm":178}
    """.trimIndent()

    @BeforeEach
    fun setup() {
        val jdbc = JdbcClient.create(dataSource)
        jdbc.sql("TRUNCATE game_attendance, games, group_invites, group_entry_requests, group_memberships, access_groups, invite_redemption_limits, access_users CASCADE").update()
        owner = sessionUser("owner")
        applicant = sessionUser("applicant")
        group = UUID.randomUUID()
        jdbc.sql(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, modality, composition, " +
                "entry_requires_approval, created_at, updated_at) " +
                "VALUES (:id, :owner, :key, 'Grupo', 'UTC', 'COURT_VOLLEYBALL', 'MIXED', true, now(), now())",
        ).param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        jdbc.sql(
            "INSERT INTO group_invites (group_id, token_digest, created_by_user_id, created_at, expires_at) " +
                "VALUES (:group, :digest, :owner, now(), :expires)",
        ).param("group", group).param("digest", InviteTokenDigest.sha256(code).toByteArray()).param("owner", owner)
            .param("expires", Timestamp.from(Instant.now().plusSeconds(3600))).update()
    }

    @Test
    fun `request entry, save the registration, get approved and see it on the athlete profile`() {
        assertEquals("PENDING", json.readTree(redeem("applicant").body())["status"].stringValue())

        val saved = put("applicant", "/api/groups/$group/entry-requests/me/athlete-profile", profile)
        assertEquals(204, saved.statusCode(), saved.body())
        // O pedido só guarda o cadastro: nada de vínculo antes da aprovação.
        assertEquals(0, count("SELECT count(*) FROM group_memberships WHERE user_id = '$applicant'"))

        val approved = send("owner", "POST", "/api/groups/$group/entry-requests/$applicant/approve")
        assertEquals(200, approved.statusCode(), approved.body())

        val membership = json.readTree(send("applicant", "GET", "/api/athletes/me").body())["memberships"][0]
        assertEquals(group.toString(), membership["groupId"].stringValue())
        assertEquals("Bia", membership["nickname"].stringValue())
        assertEquals("LEVANTADOR", membership["position"].stringValue())
        assertEquals("OPOSTO", membership["secondaryPosition"].stringValue())
        assertEquals("AVANCADO", membership["level"].stringValue())
        assertEquals(178, membership["heightCm"].intValue())
        assertEquals(0, count("SELECT count(*) FROM group_entry_requests"))
    }

    @Test
    fun `applicant reads its pending request with the group context and the saved registration`() {
        val path = "/api/groups/$group/entry-requests/me"
        assertEquals(404, send("applicant", "GET", path).statusCode())
        redeem("applicant")

        val before = json.readTree(send("applicant", "GET", path).body())
        assertEquals("Grupo", before["groupName"].stringValue())
        assertEquals("COURT_VOLLEYBALL", before["modality"].stringValue())
        assertTrue(before["athleteProfile"].isNull)

        put("applicant", "$path/athlete-profile", profile)
        val after = json.readTree(send("applicant", "GET", path).body())
        assertEquals("Bia", after["athleteProfile"]["nickname"].stringValue())
        assertEquals("LEVANTADOR", after["athleteProfile"]["position"].stringValue())
        assertEquals(178, after["athleteProfile"]["heightCm"].intValue())

        assertEquals(404, send("owner", "GET", path).statusCode())
    }

    @Test
    fun `saving again overwrites the stored registration`() {
        redeem("applicant")
        val path = "/api/groups/$group/entry-requests/me/athlete-profile"
        assertEquals(204, put("applicant", path, profile).statusCode())
        assertEquals(204, put("applicant", path, profile.replace("Bia", "Bia Lima")).statusCode())

        assertEquals("Bia Lima", JdbcClient.create(dataSource)
            .sql("SELECT nickname FROM group_entry_requests WHERE user_id = :user").param("user", applicant)
            .query(String::class.java).single())
    }

    @Test
    fun `approval without a stored registration keeps the old behavior`() {
        redeem("applicant")

        assertEquals(200, send("owner", "POST", "/api/groups/$group/entry-requests/$applicant/approve").statusCode())

        val membership = json.readTree(send("applicant", "GET", "/api/athletes/me").body())["memberships"][0]
        assertTrue(membership["nickname"].isNull)
        assertTrue(membership["position"].isNull)
    }

    @Test
    fun `rejecting the request discards the stored registration`() {
        redeem("applicant")
        put("applicant", "/api/groups/$group/entry-requests/me/athlete-profile", profile)

        assertEquals(204, send("owner", "DELETE", "/api/groups/$group/entry-requests/$applicant").statusCode())

        assertEquals(0, count("SELECT count(*) FROM group_entry_requests"))
        assertEquals(0, count("SELECT count(*) FROM group_memberships WHERE user_id = '$applicant'"))
    }

    @Test
    fun `invalid registration is rejected with the member endpoint's fields and stores nothing`() {
        redeem("applicant")
        val response = put(
            "applicant",
            "/api/groups/$group/entry-requests/me/athlete-profile",
            """{"nickname":"a","position":"LEVANTADOR","secondaryPosition":"LEVANTADOR","level":null,"preferredSide":"DIREITA","heightCm":50}""",
        )

        assertEquals(422, response.statusCode(), response.body())
        val fields = json.readTree(response.body())["fieldErrors"]
        assertTrue(fields.has("nickname") && fields.has("secondaryPosition") && fields.has("preferredSide") && fields.has("heightCm"))
        assertNull(JdbcClient.create(dataSource)
            .sql("SELECT nickname FROM group_entry_requests WHERE user_id = :user").param("user", applicant)
            .query(String::class.java).optional().orElse(null))
    }

    @Test
    fun `without a pending request it is not found, even for members and unknown groups`() {
        val path = "/api/groups/$group/entry-requests/me/athlete-profile"
        assertEquals(404, put("applicant", path, profile).statusCode())
        assertEquals(404, put("owner", path, profile).statusCode())
        assertEquals(404, put("applicant", "/api/groups/${UUID.randomUUID()}/entry-requests/me/athlete-profile", profile).statusCode())
        assertEquals(401, put(null, path, profile).statusCode())
    }

    private fun sessionUser(name: String): UUID {
        assertEquals(200, send(name, "PUT", "/api/session", "{}").statusCode())
        return JdbcClient.create(dataSource).sql("SELECT id FROM access_users WHERE firebase_subject = :subject")
            .param("subject", name).query(UUID::class.java).single()
    }

    private fun redeem(token: String) = send(token, "POST", "/api/invites/redeem", """{"code":"${code.value}"}""")

    private fun put(token: String?, path: String, body: String) = send(token, "PUT", path, body)

    private fun count(sql: String): Int = JdbcClient.create(dataSource).sql(sql).query(Int::class.java).single()

    private fun send(token: String?, method: String, path: String, body: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (body != null) builder.header("Content-Type", "application/json")
        val publisher = body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
        return HttpClient.newHttpClient().send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        // O contexto sobe com o Flyway do app, como em producao.
        private val database = TestPostgres.empty()

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { database.jdbcUrl }
            registry.add("spring.datasource.username") { database.username }
            registry.add("spring.datasource.password") { database.password }
            registry.add("saqz.firebase.emulator.enabled") { "true" }
            registry.add("saqz.links.domain") { "https://join.test" }
            registry.add("saqz.subscription.purchase-url") { "https://checkout.test/assinar/" }
            registry.add("saqz.password-reset.secret") { "segredo-de-teste-com-trinta-e-dois" }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Configuration {
        @Bean @Primary fun entryProfileVerifier() = object : VerifyRequestIdentity {
            override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
                RequestIdentity(token.value, "${token.value}@example.test", true, "${token.value} person"),
            )
        }

        @Bean @Primary fun entryProfileLimits() = object : SubscriptionLimits {
            override fun groupLimitFor(ownerId: UUID): Int? = null
            override fun athleteLimitFor(ownerId: UUID): Int? = null
        }
    }
}
