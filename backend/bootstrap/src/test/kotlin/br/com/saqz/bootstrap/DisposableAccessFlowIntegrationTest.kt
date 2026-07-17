package br.com.saqz.bootstrap

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import java.sql.DriverManager
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@Tag("emulator")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DisposableAccessFlowIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcTemplate

    private val client = HttpClient.newHttpClient()
    private lateinit var state: Path
    private lateinit var process: Process
    private lateinit var ownerToken: String
    private lateinit var athleteToken: String

    @BeforeAll
    fun startDisposableIdentityFixture() {
        jdbc = JdbcTemplate(dataSource)
        state = Files.createTempDirectory("saqz-access-e2e-")
        process = ProcessBuilder(System.getProperty("session.fixture"))
            .redirectErrorStream(true)
            .redirectOutput(state.resolve("fixture.log").toFile())
            .apply {
                environment()["SAQZ_FIXTURE_STATE_DIR"] = state.toString()
                environment()["SAQZ_FIXTURE_HOLD"] = "true"
            }.start()
        awaitReady()
        ownerToken = state.resolve("id-token").readText().trim()
        athleteToken = createVerifiedIdentity("Athlete Fixture")
    }

    @AfterAll
    fun stopDisposableResources() {
        process.destroy()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), fixtureLog())
        assertTrue(Files.exists(state.resolve("account-deleted")), fixtureLog())
        assertTrue(Files.exists(state.resolve("port-bindable")), fixtureLog())
        state.resolve("pids").readLines().forEach { pid ->
            assertFalse(ProcessHandle.of(pid.toLong()).map(ProcessHandle::isAlive).orElse(false), pid)
        }
        postgres.stop()
        state.toFile().deleteRecursively()
    }

    @Test
    fun `two verified identities bootstrap as distinct internal users`() {
        val owner = session(ownerToken)
        val athlete = session(athleteToken)

        assertEquals(200, owner.statusCode())
        assertEquals(200, athlete.statusCode())
        assertNotEquals(jsonValue(owner.body(), "id"), jsonValue(athlete.body(), "id"))
        assertFalse(owner.body().contains(state.resolve("subject").readText().trim()))
    }

    @Test
    fun `verified identity creates group as its only owner`() {
        val group = createGroup(uniqueName())

        assertEquals(201, group.statusCode(), group.body())
        assertTrue(group.body().contains("\"role\":\"OWNER\""))
        assertEquals(0, jdbc.queryForObject("select count(*) from group_memberships where group_id = ?", Int::class.java, UUID.fromString(jsonValue(group.body(), "id"))))
    }

    @Test
    fun `owner generates opaque invite through real endpoint`() {
        val invite = rotateInvite(groupId(createGroup(uniqueName())))

        assertEquals(200, invite.statusCode(), invite.body())
        assertTrue(inviteCode(invite).length >= 22)
        assertFalse(invite.body().contains("groupId"))
    }

    @Test
    fun `rotating invite invalidates the previous capability`() {
        val groupId = groupId(createGroup(uniqueName()))
        val oldCode = inviteCode(rotateInvite(groupId))
        val newCode = inviteCode(rotateInvite(groupId))

        assertNotEquals(oldCode, newCode)
        assertEquals(404, redeem(oldCode).statusCode())
        assertEquals(200, redeem(newCode).statusCode())
    }

    @Test
    fun `second identity redeems invite as athlete`() {
        val code = inviteCode(rotateInvite(groupId(createGroup(uniqueName()))))
        val response = redeem(code)

        assertEquals(200, response.statusCode(), response.body())
        assertTrue(response.body().contains("\"role\":\"ATHLETE\""))
    }

    @Test
    fun `owner promotes redeemed athlete without changing ownership`() {
        val groupId = groupId(createGroup(uniqueName()))
        redeem(inviteCode(rotateInvite(groupId)))
        val athleteId = jsonValue(session(athleteToken).body(), "id")
        val response = request("PUT", "/api/groups/$groupId/memberships/$athleteId/role", ownerToken, "{\"role\":\"ADMIN\"}")

        assertEquals(200, response.statusCode(), response.body())
        assertTrue(response.body().contains("\"role\":\"ADMIN\""))
        assertEquals(UUID.fromString(jsonValue(session(ownerToken).body(), "id")), jdbc.queryForObject("select owner_user_id from access_groups where id = ?", UUID::class.java, groupId))
    }

    @Test
    fun `parallel redemption is idempotent and creates one membership`() {
        val groupId = groupId(createGroup(uniqueName()))
        val code = inviteCode(rotateInvite(groupId))
        val responses = listOf(
            CompletableFuture.supplyAsync { redeem(code) },
            CompletableFuture.supplyAsync { redeem(code) },
        ).map { it.get(30, TimeUnit.SECONDS) }

        assertEquals(listOf(200, 200), responses.map(HttpResponse<String>::statusCode))
        val athleteId = UUID.fromString(jsonValue(session(athleteToken).body(), "id"))
        assertEquals(1, jdbc.queryForObject("select count(*) from group_memberships where group_id = ? and user_id = ?", Int::class.java, groupId, athleteId))
    }

    @Test
    fun `invalid group request rolls back all durable rows`() {
        val name = uniqueName()
        val response = request("POST", "/api/groups", ownerToken, "{\"requestId\":\"${UUID.randomUUID()}\",\"name\":\"$name\",\"timeZone\":\"Invalid/Zone\"}")

        assertEquals(400, response.statusCode(), response.body())
        assertEquals(0, jdbc.queryForObject("select count(*) from access_groups where name = ?", Int::class.java, name))
    }

    private fun session(token: String) = request("PUT", "/api/session", token)
    private fun createGroup(name: String) = request("POST", "/api/groups", ownerToken, "{\"requestId\":\"${UUID.randomUUID()}\",\"name\":\"$name\",\"timeZone\":\"America/Sao_Paulo\"}")
    private fun rotateInvite(groupId: UUID) = request("POST", "/api/groups/$groupId/invite", ownerToken)
    private fun redeem(code: String) = request("POST", "/api/invites/redeem", athleteToken, "{\"code\":\"$code\"}")
    private fun groupId(response: HttpResponse<String>) = UUID.fromString(jsonValue(response.body(), "id"))
    private fun uniqueName() = "E2E ${UUID.randomUUID()}"

    private fun inviteCode(response: HttpResponse<String>): String {
        val url = jsonValue(response.body(), "inviteUrl")
        val query = URI(url).rawQuery.split('&').associate { part -> part.substringBefore('=') to part.substringAfter('=') }
        return URLDecoder.decode(query.getValue("saqz_invite"), StandardCharsets.UTF_8)
    }

    private fun request(method: String, path: String, token: String, body: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .header("Authorization", "Bearer $token")
        if (body != null) builder.header("Content-Type", "application/json")
        builder.method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun createVerifiedIdentity(displayName: String): String {
        val email = "athlete-${UUID.randomUUID()}@example.test"
        val signup = emulator("POST", "/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake", "{\"email\":\"$email\",\"password\":\"local-password-123\",\"returnSecureToken\":true}")
        val signupToken = jsonValue(signup, "idToken")
        emulator("POST", "/identitytoolkit.googleapis.com/v1/accounts:update?key=fake", "{\"idToken\":\"$signupToken\",\"displayName\":\"$displayName\",\"returnSecureToken\":false}")
        emulator("POST", "/identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=fake", "{\"requestType\":\"VERIFY_EMAIL\",\"idToken\":\"$signupToken\"}")
        val codes = emulator("GET", "/emulator/v1/projects/saqz-local/oobCodes")
        val code = Regex("\\{[^{}]*\\\"email\\\":\\\"${Regex.escape(email)}\\\"[^{}]*\\\"oobCode\\\":\\\"([^\\\"]+)\\\"")
            .find(codes)?.groupValues?.get(1) ?: error("Missing verification code for $email")
        emulator("POST", "/identitytoolkit.googleapis.com/v1/accounts:update?key=fake", "{\"oobCode\":\"$code\"}")
        val signin = emulator("POST", "/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake", "{\"email\":\"$email\",\"password\":\"local-password-123\",\"returnSecureToken\":true}")
        return jsonValue(signin, "idToken")
    }

    private fun emulator(method: String, path: String, body: String? = null): String {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:9099$path"))
            .apply { if (body != null) header("Content-Type", "application/json") }
            .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { response.body() }
        return response.body()
    }

    private fun jsonValue(body: String, field: String): String =
        Regex("\\\"$field\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(body)?.groupValues?.get(1)
            ?: error("Missing $field in $body")

    private fun awaitReady() {
        val deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos()
        while (System.nanoTime() < deadline) {
            if (Files.exists(state.resolve("ready"))) return
            if (!process.isAlive) error(fixtureLog())
            Thread.sleep(200)
        }
        error("Fixture did not become ready: ${fixtureLog()}")
    }

    private fun fixtureLog() = state.resolve("fixture.log").takeIf(Files::exists)?.readText().orEmpty()

    companion object {
        private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).apply {
            start()
            val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
            var ready = false
            while (!ready && System.nanoTime() < deadline) {
                ready = runCatching { DriverManager.getConnection(jdbcUrl, username, password).use { } }.isSuccess
                if (!ready) Thread.sleep(100)
            }
            check(ready) { "PostgreSQL port did not become JDBC-ready" }
        }

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("saqz.firebase.emulator.enabled") { "true" }
            registry.add("saqz.branch.domain") { "https://join.test" }
        }
    }
}
