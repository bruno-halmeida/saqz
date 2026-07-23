package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.input.http.AccessSessionController
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.CompleteSessionProfile
import br.com.saqz.access.application.session.ProfileCompletion
import br.com.saqz.access.application.session.SessionMembership
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.PhoneNumber
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tools.jackson.databind.ObjectMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SessionEndpointIntegrationTest.SessionTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class SessionEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var verifier: SessionVerifier

    @Autowired
    private lateinit var repository: RecordingSessionRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun reset() {
        verifier.principal = identity()
        repository.reset()
    }

    @Test
    fun `valid principal returns exact persisted session fields`() {
        val response = putSession()
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals("session@example.test", body["user"]["email"].stringValue())
        assertEquals("Session Person", body["user"]["displayName"].stringValue())
        assertTrue(body["memberships"].isEmpty)
    }

    @Test
    fun `missing email claim returns nullable email without losing identity`() {
        verifier.principal = identity(email = null)

        val body = json(putSession())

        assertTrue(body["user"]["email"].isNull)
        assertEquals("Session Person", body["user"]["displayName"].stringValue())
    }

    @Test
    fun `same principal returns the same internal user on retry`() {
        val first = json(putSession())
        val second = json(putSession())

        assertEquals(first, second)
        assertEquals(2, repository.commands.size)
    }

    @Test
    fun `GET session semantics are removed`() {
        val response = send(HttpRequest.newBuilder(uri()).header("Authorization", "Bearer session-token").GET().build())

        assertTrue(response.statusCode() == 404 || response.statusCode() == 405)
    }

    @Test
    fun `false email verification returns 403 without write`() {
        verifier.principal = identity(emailVerified = false)

        val response = putSession()

        assertProblem(response, 403, "EMAIL_NOT_VERIFIED")
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `missing email verification returns 403 without write`() {
        verifier.principal = identity(emailVerified = null)

        val response = putSession()

        assertProblem(response, 403, "EMAIL_NOT_VERIFIED")
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `missing display name returns field validation without write`() {
        verifier.principal = identity(displayName = null)

        val response = putSession()

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertTrue(json(response)["fieldErrors"].has("displayName"))
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `blank display name returns field validation without write`() {
        verifier.principal = identity(displayName = "   ")

        val response = putSession()

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `request body cannot override token identity mirrors`() {
        val response = putSession("""{"email":"attacker@example.test","displayName":"Attacker"}""")

        assertEquals(200, response.statusCode())
        assertEquals("session@example.test", repository.commands.single().email)
        assertEquals("Session Person", repository.commands.single().displayName.value)
        assertFalse(response.body().contains("attacker@example.test"))
    }

    @Test
    fun `session response includes current group names and roles`() {
        val groupId = UUID.randomUUID()
        repository.memberships = listOf(
            SessionMembership(groupId, AccessName.from("Current Group"), "ADMIN"),
        )

        val membership = json(putSession())["memberships"][0]

        assertEquals(groupId.toString(), membership["groupId"].stringValue())
        assertEquals("Current Group", membership["groupName"].stringValue())
        assertEquals("ADMIN", membership["role"].stringValue())
    }

    @Test
    fun `changed mirrors keep user ID and memberships stable`() {
        repository.memberships = listOf(
            SessionMembership(UUID.randomUUID(), AccessName.from("Stable Group"), "ATHLETE"),
        )
        val first = json(putSession())
        verifier.principal = identity(email = "changed@example.test", displayName = "Changed Person")
        val second = json(putSession())

        assertEquals(first["user"]["id"], second["user"]["id"])
        assertEquals(first["memberships"], second["memberships"])
        assertEquals("changed@example.test", second["user"]["email"].stringValue())
        assertEquals("Changed Person", second["user"]["displayName"].stringValue())
    }

    @Test
    fun `repository failure returns generic correlated 500 without details`() {
        repository.failure = IllegalStateException("database-password-fixture")

        val response = putSession()

        assertEquals(500, response.statusCode())
        assertFalse(response.body().contains("database-password-fixture"))
        assertEquals(correlationId(response), correlationHeader(response))
    }

    @Test
    fun `session response never exposes Firebase UID`() {
        val response = putSession()

        assertFalse(response.body().contains("subject-session"))
        assertFalse(response.body().contains("firebaseSubject"))
        assertFalse(response.body().contains("subject"))
    }

    @Test
    fun `unverified problem correlation header equals body correlation ID`() {
        verifier.principal = identity(emailVerified = false)

        val response = putSession()

        assertEquals(correlationId(response), correlationHeader(response))
    }

    @Test
    fun `session response phoneRequired is true when no phone is on file`() {
        val body = json(putSession())

        assertTrue(body["user"]["phone"].isNull)
        assertTrue(body["user"]["phoneRequired"].booleanValue())
    }

    @Test
    fun `valid phone is persisted and reflected as no longer required`() {
        putSession()

        val response = patchProfile("""{"phone":"+5511911112222"}""")
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals("+5511911112222", body["user"]["phone"].stringValue())
        assertFalse(body["user"]["phoneRequired"].booleanValue())
        assertEquals(PhoneNumber.from("+5511911112222"), repository.profileCommands.single().phone)
    }

    @Test
    fun `masked BR phone input is normalized before persistence`() {
        putSession()

        val body = json(patchProfile("""{"phone":"(11) 91111-2222"}"""))

        assertEquals("+5511911112222", body["user"]["phone"].stringValue())
    }

    @Test
    fun `landline shaped phone returns field validation without write`() {
        putSession()

        val response = patchProfile("""{"phone":"+551133334444"}""")

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertTrue(json(response)["fieldErrors"].has("phone"))
        assertTrue(repository.profileCommands.isEmpty())
    }

    @Test
    fun `invalid phone problem never echoes the submitted value`() {
        putSession()

        val response = patchProfile("""{"phone":"not-a-phone"}""")

        assertFalse(response.body().contains("not-a-phone"))
    }

    @Test
    fun `repeat submission of the same phone is an idempotent overwrite`() {
        putSession()

        val first = json(patchProfile("""{"phone":"+5511911112222"}"""))
        val second = json(patchProfile("""{"phone":"+5511911112222"}"""))

        assertEquals(first["user"]["phone"], second["user"]["phone"])
        assertEquals(2, repository.profileCommands.size)
    }

    @Test
    fun `profile update scopes to the authenticated principal only`() {
        putSession()

        patchProfile("""{"phone":"+5511911112222"}""")

        assertEquals("subject-session", repository.profileCommands.single().subject)
    }

    private fun patchProfile(body: String): HttpResponse<String> =
        send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/session/profile"))
                .header("Authorization", "Bearer session-token")
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build(),
        )

    private fun identity(
        email: String? = "session@example.test",
        emailVerified: Boolean? = true,
        displayName: String? = "Session Person",
    ) = RequestIdentity("subject-session", email, emailVerified, displayName)

    private fun putSession(body: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(uri())
            .header("Authorization", "Bearer session-token")
        if (body == null) {
            builder.PUT(HttpRequest.BodyPublishers.noBody())
        } else {
            builder.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body))
        }
        return send(builder.build())
    }

    private fun uri() = URI("http://127.0.0.1:$port/api/session")

    private fun send(request: HttpRequest): HttpResponse<String> =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())

    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())

    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertEquals(code, json(response)["code"].stringValue())
    }

    private fun correlationId(response: HttpResponse<String>): String = json(response)["correlationId"].stringValue()

    private fun correlationHeader(response: HttpResponse<String>): String =
        response.headers().firstValue("X-Correlation-ID").orElse("")

    @TestConfiguration(proxyBeanMethods = false)
    class SessionTestConfiguration {
        @Bean
        @Primary
        fun sessionVerifier() = SessionVerifier()

        @Bean
        fun recordingSessionRepository() = RecordingSessionRepository()

        @Bean
        fun bootstrapSession(repository: RecordingSessionRepository) = BootstrapSession(repository)

        @Bean
        fun completeSessionProfile(repository: RecordingSessionRepository) = CompleteSessionProfile(repository)

        @Bean
        fun accessSessionController(useCase: BootstrapSession, profile: CompleteSessionProfile) =
            AccessSessionController(useCase, profile)
    }

    class SessionVerifier : VerifyRequestIdentity {
        lateinit var principal: RequestIdentity

        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(principal)
    }

    class RecordingSessionRepository : SessionRepository {
        val commands = mutableListOf<SessionUpsert>()
        val profileCommands = mutableListOf<ProfileCompletion>()
        private val ids = mutableMapOf<String, UUID>()
        private val phones = mutableMapOf<String, PhoneNumber>()
        private val names = mutableMapOf<String, AccessName>()
        var memberships: List<SessionMembership> = emptyList()
        var failure: RuntimeException? = null

        fun reset() {
            commands.clear()
            profileCommands.clear()
            ids.clear()
            phones.clear()
            names.clear()
            memberships = emptyList()
            failure = null
        }

        override fun upsertAndLoad(command: SessionUpsert): SessionView {
            failure?.let { throw it }
            commands += command
            val id = ids.getOrPut(command.subject) { UUID.randomUUID() }
            names[command.subject] = command.displayName
            return SessionView(
                UserAccount(id, command.subject, command.email, command.displayName, phones[command.subject]),
                memberships,
            )
        }

        override fun updateProfile(command: ProfileCompletion): SessionView {
            failure?.let { throw it }
            profileCommands += command
            val id = ids.getOrPut(command.subject) { UUID.randomUUID() }
            phones[command.subject] = command.phone
            command.displayName?.let { names[command.subject] = it }
            return SessionView(
                UserAccount(id, command.subject, "session@example.test", names.getValue(command.subject), command.phone),
                memberships,
            )
        }
    }
}
