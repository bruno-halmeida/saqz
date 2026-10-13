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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Convite por código curto, ponta a ponta contra o banco real: qualquer admin lê o código só pela API,
 * o token longo legado continua valendo até a troca, e preview/resgate aceitam os dois formatos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InviteShortCodeEndpointIntegrationTest.Configuration::class)
@ActiveProfiles("test")
class InviteShortCodeEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var json: ObjectMapper

    private val owner = "owner-${UUID.randomUUID()}"
    private val admin = "admin-${UUID.randomUUID()}"
    private val athlete = "athlete-${UUID.randomUUID()}"
    private val newcomer = "newcomer-${UUID.randomUUID()}"
    private val other = "other-${UUID.randomUUID()}"
    private val group = UUID.randomUUID()
    private val ids = mutableMapOf<String, UUID>()
    private val jdbc get() = JdbcClient.create(dataSource)

    @BeforeEach
    fun seed() {
        listOf(owner, admin, athlete, newcomer, other).forEach { ids[it] = seedUser(it) }
        seedGroup(group, ids.getValue(owner))
        membership(group, ids.getValue(admin), "ADMIN")
        membership(group, ids.getValue(athlete), "ATHLETE")
    }

    @Test
    fun `any admin reads the same short code and url from the api alone`() {
        val first = read(owner)
        val second = read(admin)

        assertEquals(200, first.statusCode(), first.body())
        val a = json.readTree(first.body())
        val b = json.readTree(second.body())
        assertTrue(Regex("[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}").matches(a["code"].stringValue()), a["code"].stringValue())
        assertEquals(a["code"].stringValue(), b["code"].stringValue())
        assertEquals("https://links.saqz.app/?saqz_invite=${a["code"].stringValue().replace("-", "")}", a["inviteUrl"].stringValue())
        assertEquals(a["inviteUrl"].stringValue(), b["inviteUrl"].stringValue())
        assertEquals(true, a["active"].booleanValue())
        // Leituras repetidas não trocam o código.
        assertEquals(a["code"].stringValue(), json.readTree(read(owner).body())["code"].stringValue())
    }

    @Test
    fun `athlete and strangers cannot read the code`() {
        assertEquals(403, read(athlete).statusCode())
        assertEquals(404, read(newcomer).statusCode())
        assertEquals(401, request("GET", "/api/groups/$group/invite", actor = null).statusCode())
    }

    @Test
    fun `rotation returns a new code and invalidates the previous one`() {
        val before = json.readTree(read(owner).body())["code"].stringValue()

        val rotated = request("POST", "/api/groups/$group/invite", actor = admin)
        assertEquals(200, rotated.statusCode(), rotated.body())
        val after = json.readTree(rotated.body())
        assertNotEquals(before, after["code"].stringValue())
        assertEquals(after["code"].stringValue(), json.readTree(read(owner).body())["code"].stringValue())

        assertEquals(404, preview(before, newcomer).statusCode())
        assertEquals(200, preview(after["code"].stringValue(), newcomer).statusCode())
    }

    @Test
    fun `redeems by code in any accepted spelling and by the link`() {
        val code = json.readTree(read(owner).body())["code"].stringValue()
        val spellings = listOf(code, code.lowercase(), code.replace("-", ""), " ${code.replace("-", " ").lowercase()} ")
        val people = listOf(newcomer, other)

        val joined = redeem(spellings[1], newcomer)
        assertEquals(200, joined.statusCode(), joined.body())
        assertEquals("JOINED", json.readTree(joined.body())["status"].stringValue())
        assertEquals(group.toString(), json.readTree(joined.body())["groupId"].stringValue())
        assertEquals(1, memberCount(newcomer))

        // Quem já é membro e usa outra grafia segue funcionando (idempotente).
        spellings.forEach { assertEquals(200, redeem(it, newcomer).statusCode(), it) }
        assertEquals(2, people.size)
        assertEquals(200, redeem(spellings[2], other).statusCode())
        assertEquals(1, memberCount(other))
    }

    @Test
    fun `legacy long token keeps working until the group rotates the code`() {
        val legacy = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { (it + 11).toByte() })
        seedLegacyInvite(group, ids.getValue(owner), legacy)

        // Antes de qualquer leitura: o token longo já enviado resgata.
        assertEquals(200, preview(legacy, newcomer).statusCode())

        // A primeira leitura de um admin ganha o código curto SEM invalidar o longo.
        val code = json.readTree(read(admin).body())["code"].stringValue()
        assertEquals(200, preview(legacy, newcomer).statusCode())
        assertEquals(200, preview(code, newcomer).statusCode())
        val joined = redeem(legacy, newcomer)
        assertEquals(200, joined.statusCode(), joined.body())
        assertEquals(1, memberCount(newcomer))

        // Trocar o código invalida o longo e o curto anterior.
        val rotated = json.readTree(request("POST", "/api/groups/$group/invite", actor = owner).body())
        assertEquals(404, preview(legacy, other).statusCode())
        assertEquals(404, preview(code, other).statusCode())
        assertEquals(404, redeem(legacy, other).statusCode())
        assertEquals(404, redeem(code, other).statusCode())
        assertEquals(200, redeem(rotated["code"].stringValue(), other).statusCode())
    }

    @Test
    fun `preview reflects whether the group requires approval`() {
        val code = json.readTree(read(owner).body())["code"].stringValue()

        assertEquals(false, json.readTree(preview(code, newcomer).body())["entryRequiresApproval"].booleanValue())

        jdbc.sql("UPDATE access_groups SET entry_requires_approval = true WHERE id = :id").param("id", group).update()

        assertEquals(true, json.readTree(preview(code, newcomer).body())["entryRequiresApproval"].booleanValue())
        val redeemed = json.readTree(redeem(code, newcomer).body())
        assertEquals("PENDING", redeemed["status"].stringValue())
        assertEquals(0, memberCount(newcomer))
    }

    @Test
    fun `invalid short codes are rejected like any invalid invite`() {
        listOf("ABCD-EFG0", "ABCD-EFG", "zzzz-zzzz-zz", "not a code").forEach {
            val response = preview(it, newcomer)
            assertEquals(404, response.statusCode(), "$it ${response.body()}")
            assertEquals("INVITE_INVALID", json.readTree(response.body())["code"].stringValue())
        }
    }

    @Test
    fun `invalid short code attempts count towards the same attempt limit`() {
        repeat(10) { assertEquals(404, redeem("2222-2222", newcomer).statusCode()) }

        val limited = redeem("2222-2222", newcomer)
        assertEquals(429, limited.statusCode(), limited.body())
        val code = json.readTree(read(owner).body())["code"].stringValue()
        assertEquals(429, redeem(code, newcomer).statusCode())
    }

    @Test
    fun `short code digest matches sha256 of the canonical code`() {
        val code = json.readTree(read(owner).body())["code"].stringValue()
        val stored = jdbc.sql("SELECT short_code_digest FROM group_invites WHERE group_id = :id").param("id", group)
            .query(ByteArray::class.java).single()

        assertEquals(
            InviteTokenDigest.sha256(InviteCode.from(code)).toByteArray().toList(),
            stored.toList(),
        )
    }

    private fun read(actor: String) = request("GET", "/api/groups/$group/invite", actor = actor)

    private fun preview(code: String, actor: String?) =
        request("POST", "/api/invites/preview", """{"code":"$code"}""", actor)

    private fun redeem(code: String, actor: String) =
        request("POST", "/api/invites/redeem", """{"code":"$code"}""", actor)

    private fun memberCount(user: String): Int = jdbc.sql(
        "SELECT count(*) FROM group_memberships WHERE group_id = :group AND user_id = :user",
    ).param("group", group).param("user", ids.getValue(user)).query(Int::class.java).single()

    private fun seedUser(subject: String): UUID {
        val id = UUID.randomUUID()
        jdbc.sql(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES (:id, :subject, true, 'Pessoa Teste', now(), now())",
        ).param("id", id).param("subject", subject).update()
        return id
    }

    private fun seedGroup(id: UUID, ownerId: UUID) {
        jdbc.sql(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES (:id, :owner, :key, 'Vôlei de Quinta', 'UTC', now(), now())",
        ).param("id", id).param("owner", ownerId).param("key", UUID.randomUUID()).update()
    }

    private fun membership(groupId: UUID, userId: UUID, role: String) {
        jdbc.sql(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES (:group, :user, '$role', now(), now())",
        ).param("group", groupId).param("user", userId).update()
    }

    private fun seedLegacyInvite(groupId: UUID, creator: UUID, token: String) {
        jdbc.sql(
            "INSERT INTO group_invites (group_id, token_digest, created_by_user_id, created_at, expires_at) " +
                "VALUES (:group, :digest, :creator, now(), NULL)",
        ).param("group", groupId)
            .param("digest", InviteTokenDigest.sha256(InviteCode.from(token)).toByteArray())
            .param("creator", creator).update()
    }

    private fun request(method: String, path: String, body: String? = null, actor: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        if (actor != null) builder.header("Authorization", "Bearer $actor")
        if (body != null) builder.header("Content-Type", "application/json")
        val publisher = body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
        return HttpClient.newHttpClient().send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Configuration {
        @Bean @Primary fun inviteShortCodeUnlimitedPlan() = object : SubscriptionLimits {
            override fun groupLimitFor(ownerId: UUID): Int? = null
            override fun athleteLimitFor(ownerId: UUID): Int? = null
        }

        @Bean @Primary fun inviteShortCodeTestVerifier() = object : VerifyRequestIdentity {
            override fun execute(token: RawIdentityToken) =
                TokenVerification.Verified(RequestIdentity(token.value, emailVerified = true, displayName = "Pessoa Teste"))
        }
    }

    companion object {
        // O banco vazio é migrado pelo Flyway real na subida do contexto, como em produção.
        private val database = TestPostgres.empty()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { database.jdbcUrl }
            registry.add("spring.datasource.username") { database.username }
            registry.add("spring.datasource.password") { database.password }
            registry.add("saqz.firebase.emulator.enabled") { "true" }
            registry.add("saqz.links.domain") { "https://links.saqz.app" }
            registry.add("saqz.password-reset.secret") { "segredo-de-teste-com-trinta-e-dois" }
        }
    }
}
