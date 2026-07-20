package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.input.http.AccessGroupReadController
import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.application.read.GetGroup
import br.com.saqz.groups.application.read.GroupFinanceDefaultsReadModel
import br.com.saqz.groups.application.read.GroupProfileReadModel
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupModality
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
@Import(GroupReadEndpointIntegrationTest.GroupReadTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class GroupReadEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var repository: RecordingGroupReadRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val groupId = UUID.randomUUID()

    @BeforeEach
    fun reset() {
        repository.failure = null
        repository.snapshot = snapshot(GroupRole.OWNER)
        repository.keys.clear()
    }

    @Test
    fun `owner receives exact group settings`() {
        val response = getGroup(groupId)
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals(groupId.toString(), body["id"].stringValue())
        assertEquals("Training Club", body["name"].stringValue())
        assertEquals("America/Sao_Paulo", body["timeZone"].stringValue())
        assertEquals("OWNER", body["role"].stringValue())
        assertEquals(7, body["version"].intValue())
        assertEquals(listOf(GroupReadKey(GroupReadTestConfiguration.USER_ID, groupId)), repository.keys)
    }

    @Test
    fun `admin receives the group`() {
        repository.snapshot = snapshot(GroupRole.ADMIN)

        val response = getGroup(groupId)

        assertEquals(200, response.statusCode())
        assertEquals("ADMIN", json(response)["role"].stringValue())
    }

    @Test
    fun `athlete receives the group`() {
        repository.snapshot = snapshot(GroupRole.ATHLETE)

        val response = getGroup(groupId)

        assertEquals(200, response.statusCode())
        assertEquals("ATHLETE", json(response)["role"].stringValue())
    }

    @Test
    fun `nonmember receives group not found`() {
        repository.snapshot = snapshot(role = null)

        assertProblem(getGroup(groupId), 404, "GROUP_NOT_FOUND")
    }

    @Test
    fun `missing and nonmember groups expose the same public problem`() {
        repository.snapshot = null
        val missingResponse = getGroup(groupId)
        repository.snapshot = snapshot(role = null)
        val nonmemberResponse = getGroup(groupId)
        val missing = json(missingResponse)
        val nonmember = json(nonmemberResponse)

        assertEquals(404, missingResponse.statusCode())
        assertEquals(404, nonmemberResponse.statusCode())
        assertEquals(missing["status"], nonmember["status"])
        assertEquals(missing["code"], nonmember["code"])
        assertEquals(missing.propertyNames().asSequence().toSet(), nonmember.propertyNames().asSequence().toSet())
    }

    @Test
    fun `ETag is the quoted body version`() {
        val response = getGroup(groupId)

        assertEquals("\"7\"", response.headers().firstValue("ETag").orElse(""))
        assertEquals(7, json(response)["version"].intValue())
    }

    @Test
    fun `member response includes profile status and non-financial defaults`() {
        repository.snapshot = snapshot(GroupRole.ATHLETE, profile = profile(), financeDefaults = finance())

        val body = json(getGroup(groupId))

        assertEquals("COMPLETE", body["profileStatus"].stringValue())
        assertEquals("COURT_VOLLEYBALL", body["profile"]["modality"].stringValue())
        assertEquals("MIXED", body["profile"]["composition"].stringValue())
        assertEquals("São Paulo", body["profile"]["city"].stringValue())
        assertEquals(18, body["profile"]["defaultCapacity"].intValue())
        assertTrue(body["financeDefaults"].isNull)
    }

    @Test
    fun `organizer response includes finance defaults`() {
        repository.snapshot = snapshot(GroupRole.ADMIN, profile = profile(), financeDefaults = finance())

        val body = json(getGroup(groupId))

        assertEquals(1500, body["financeDefaults"]["defaultGameFeeCents"].intValue())
        assertEquals(7000, body["financeDefaults"]["monthlyFeeCents"].intValue())
        assertEquals(10, body["financeDefaults"]["monthlyDueDay"].intValue())
    }

    @Test
    fun `legacy incomplete profile status is serialized`() {
        repository.snapshot = snapshot(
            GroupRole.OWNER,
            profileStatus = GroupProfileStatus.INCOMPLETE,
            profile = profile(modality = null),
        )

        val body = json(getGroup(groupId))

        assertEquals("INCOMPLETE", body["profileStatus"].stringValue())
        assertTrue(body["profile"]["modality"].isNull)
    }

    @Test
    fun `missing bearer returns authentication problem without repository read`() {
        val response = getGroup(groupId, bearer = null)

        assertProblem(response, 401, "AUTHENTICATION_REQUIRED")
        assertTrue(repository.keys.isEmpty())
    }

    @Test
    fun `repository failure returns generic correlated problem without details`() {
        repository.failure = IllegalStateException("read-database-password-fixture")

        val response = getGroup(groupId)

        assertEquals(500, response.statusCode())
        assertFalse(response.body().contains("read-database-password-fixture"))
        assertEquals(json(response)["correlationId"].stringValue(), correlationHeader(response))
    }

    private fun snapshot(
        role: GroupRole?,
        profileStatus: GroupProfileStatus = GroupProfileStatus.COMPLETE,
        profile: GroupProfileReadModel? = null,
        financeDefaults: GroupFinanceDefaultsReadModel? = null,
    ) = GroupReadSnapshot(
        groupId,
        AccessName.from("Training Club"),
        IanaTimeZone.from("America/Sao_Paulo"),
        role,
        7,
        profileStatus,
        profile,
        financeDefaults,
    )

    private fun profile(
        modality: GroupModality? = GroupModality.COURT_VOLLEYBALL,
        composition: GroupComposition? = GroupComposition.MIXED,
    ) = GroupProfileReadModel(
        modality = modality,
        composition = composition,
        description = "Training group",
        city = "São Paulo",
        level = null,
        customLevel = null,
        playStyle = null,
        customPlayStyle = null,
        defaultVenue = null,
        regularSlots = emptyList(),
        defaultCapacity = 18,
        defaultConfirmationLeadMinutes = 180,
    )

    private fun finance() = GroupFinanceDefaultsReadModel(
        defaultGameFeeCents = 1500,
        monthlyFeeCents = 7000,
        monthlyDueDay = 10,
    )

    private fun getGroup(id: UUID, bearer: String? = "group-read-token"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/groups/$id")).GET()
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())

    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertEquals(code, json(response)["code"].stringValue())
    }

    private fun correlationHeader(response: HttpResponse<String>) =
        response.headers().firstValue("X-Correlation-ID").orElse("")

    @TestConfiguration(proxyBeanMethods = false)
    class GroupReadTestConfiguration {
        @Bean
        @Primary
        fun groupReadVerifier() = GroupReadVerifier()

        @Bean
        fun groupReadSessionRepository() = GroupReadSessionRepository()

        @Bean
        fun groupReadBootstrapSession(repository: GroupReadSessionRepository) = BootstrapSession(repository)

        @Bean
        fun recordingGroupReadRepository() = RecordingGroupReadRepository()

        @Bean
        fun getGroup(repository: RecordingGroupReadRepository) = GetGroup(repository, GroupAccessPolicy())

        @Bean
        fun accessGroupReadController(bootstrap: BootstrapSession, getGroup: GetGroup) =
            AccessGroupReadController(verifiedGroupActorResolver(bootstrap), getGroup)

        companion object {
            val USER_ID: UUID = UUID.randomUUID()
        }
    }

    class GroupReadVerifier : VerifyRequestIdentity {
        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
            RequestIdentity("group-read-subject", "read@example.test", true, "Read Person"),
        )
    }

    class GroupReadSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            UserAccount(
                GroupReadTestConfiguration.USER_ID,
                command.subject,
                command.email,
                command.displayName,
            ),
            emptyList(),
        )
    }

    class RecordingGroupReadRepository : GroupReadRepository {
        var snapshot: GroupReadSnapshot? = null
        var failure: RuntimeException? = null
        val keys = mutableListOf<GroupReadKey>()

        override fun find(key: GroupReadKey): GroupReadSnapshot? {
            failure?.let { throw it }
            keys += key
            return snapshot
        }
    }
}
