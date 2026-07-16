package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.input.http.AccessGroupSettingsController
import br.com.saqz.access.application.group.create.TransactionRunner
import br.com.saqz.access.application.group.read.GroupReadKey
import br.com.saqz.access.application.group.read.GroupReadRepository
import br.com.saqz.access.application.group.read.GroupReadSnapshot
import br.com.saqz.access.application.group.settings.GroupSettingsRepository
import br.com.saqz.access.application.group.settings.SettingsWriteResult
import br.com.saqz.access.application.group.settings.StoredGroupSettings
import br.com.saqz.access.application.group.settings.UpdateGroupSettings
import br.com.saqz.access.application.group.settings.UpdateGroupSettingsCommand
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.GroupAccessPolicy
import br.com.saqz.access.domain.GroupRole
import br.com.saqz.access.domain.IanaTimeZone
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
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GroupSettingsEndpointIntegrationTest.SettingsTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class GroupSettingsEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var read: RecordingSettingsReadRepository
    @Autowired private lateinit var write: RecordingSettingsWriteRepository
    @Autowired private lateinit var objectMapper: ObjectMapper
    private val groupId = UUID.randomUUID()

    @BeforeEach
    fun reset() {
        read.snapshot = snapshot(GroupRole.OWNER)
        write.commands.clear()
    }

    @Test
    fun `owner updates settings and receives the new ETag`() {
        val response = put()
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals("\"8\"", response.headers().firstValue("ETag").orElse(""))
        assertEquals("New Group", body["name"].stringValue())
        assertEquals("Europe/Lisbon", body["timeZone"].stringValue())
        assertEquals("OWNER", body["role"].stringValue())
        assertEquals(7, write.commands.single().expectedVersion)
    }

    @Test
    fun `admin can update settings`() {
        read.snapshot = snapshot(GroupRole.ADMIN)

        val response = put()

        assertEquals(200, response.statusCode())
        assertEquals("ADMIN", json(response)["role"].stringValue())
    }

    @Test
    fun `athlete receives access forbidden without write`() {
        read.snapshot = snapshot(GroupRole.ATHLETE)

        assertProblem(put(), 403, "ACCESS_FORBIDDEN")
        assertTrue(write.commands.isEmpty())
    }

    @Test
    fun `nonmember receives group not found without write`() {
        read.snapshot = snapshot(role = null)

        assertProblem(put(), 404, "GROUP_NOT_FOUND")
        assertTrue(write.commands.isEmpty())
    }

    @Test
    fun `stale ETag returns version conflict without write`() {
        assertProblem(put(etag = "\"6\""), 409, "VERSION_CONFLICT")
        assertTrue(write.commands.isEmpty())
    }

    @Test
    fun `missing If-Match returns stable field problem`() {
        val response = put(etag = null)

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertTrue(json(response)["fieldErrors"].has("ifMatch"))
        assertTrue(write.commands.isEmpty())
    }

    @Test
    fun `malformed If-Match returns stable field problem`() {
        val response = put(etag = "7")

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertTrue(json(response)["fieldErrors"].has("ifMatch"))
    }

    @Test
    fun `invalid name returns name field problem`() {
        val response = put(name = " ")

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertEquals(setOf("name"), json(response)["fieldErrors"].propertyNames().asSequence().toSet())
    }

    @Test
    fun `invalid timezone returns timeZone field problem`() {
        val response = put(timeZone = "Mars/Olympus")

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertEquals(setOf("timeZone"), json(response)["fieldErrors"].propertyNames().asSequence().toSet())
    }

    @Test
    fun `payload owner user and role cannot override authenticated context`() {
        val attacker = UUID.randomUUID()
        val response = putRaw(
            """{"name":"New Group","timeZone":"Europe/Lisbon","ownerUserId":"$attacker","userId":"$attacker","role":"ATHLETE"}""",
            "\"7\"",
        )

        assertEquals(200, response.statusCode())
        assertEquals(groupId, write.commands.single().groupId)
        assertFalse(response.body().contains(attacker.toString()))
        assertEquals("OWNER", json(response)["role"].stringValue())
    }

    private fun snapshot(role: GroupRole?) = GroupReadSnapshot(
        groupId, AccessName.from("Old Group"), IanaTimeZone.from("UTC"), role, 7,
    )

    private fun put(
        etag: String? = "\"7\"",
        name: String = "New Group",
        timeZone: String = "Europe/Lisbon",
    ) = putRaw(objectMapper.writeValueAsString(mapOf("name" to name, "timeZone" to timeZone)), etag)

    private fun putRaw(body: String, etag: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/groups/$groupId/settings"))
            .header("Authorization", "Bearer settings-token")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
        if (etag != null) builder.header("If-Match", etag)
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())
    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertEquals(code, json(response)["code"].stringValue())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class SettingsTestConfiguration {
        @Bean @Primary fun settingsVerifier() = SettingsVerifier()
        @Bean fun settingsSessionRepository() = SettingsSessionRepository()
        @Bean fun settingsBootstrap(repository: SettingsSessionRepository) = BootstrapSession(repository)
        @Bean fun settingsReadRepository() = RecordingSettingsReadRepository()
        @Bean fun settingsWriteRepository() = RecordingSettingsWriteRepository()
        @Bean fun settingsTransaction() = object : TransactionRunner {
            override fun <T> inTransaction(block: () -> T): T = block()
        }
        @Bean fun updateGroupSettings(
            transaction: TransactionRunner,
            read: RecordingSettingsReadRepository,
            write: RecordingSettingsWriteRepository,
        ) = UpdateGroupSettings(transaction, read, write, GroupAccessPolicy())
        @Bean fun accessGroupSettingsController(bootstrap: BootstrapSession, useCase: UpdateGroupSettings) =
            AccessGroupSettingsController(bootstrap, useCase)

        companion object { val USER_ID: UUID = UUID.randomUUID() }
    }

    class SettingsVerifier : VerifyRequestIdentity {
        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
            RequestIdentity("settings-subject", "settings@example.test", true, "Settings Person"),
        )
    }

    class SettingsSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            UserAccount(SettingsTestConfiguration.USER_ID, command.subject, command.email, command.displayName),
            emptyList(),
        )
    }

    class RecordingSettingsReadRepository : GroupReadRepository {
        var snapshot: GroupReadSnapshot? = null
        override fun find(key: GroupReadKey) = snapshot
    }

    class RecordingSettingsWriteRepository : GroupSettingsRepository {
        val commands = mutableListOf<UpdateGroupSettingsCommand>()
        override fun update(command: UpdateGroupSettingsCommand): SettingsWriteResult {
            commands += command
            return SettingsWriteResult.Updated(
                StoredGroupSettings(command.groupId, command.name, command.timeZone, command.expectedVersion + 1),
            )
        }
    }
}
