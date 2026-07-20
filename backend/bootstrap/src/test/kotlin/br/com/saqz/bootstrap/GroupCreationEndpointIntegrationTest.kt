package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.input.http.AccessGroupController
import br.com.saqz.groups.application.create.CreateGroup
import br.com.saqz.groups.application.create.CreateGroupCommand
import br.com.saqz.groups.application.create.GroupCreationRepository
import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.application.create.StoredGroup
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GetGroup
import br.com.saqz.groups.application.read.GroupFinanceDefaultsReadModel
import br.com.saqz.groups.application.read.GroupProfileReadModel
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.application.read.GroupRegularSlotReadModel
import br.com.saqz.groups.application.read.GroupVenueReadModel
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
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
@Import(GroupCreationEndpointIntegrationTest.GroupTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class GroupCreationEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var verifier: GroupVerifier

    @Autowired
    private lateinit var repository: RecordingGroupRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun reset() {
        verifier.principal = identity()
        repository.reset()
    }

    @Test
    fun `verified synchronized user creates a group as owner`() {
        val requestId = UUID.randomUUID()

        val response = postGroup(requestId, "Training Club", "America/Sao_Paulo")
        val body = json(response)

        assertEquals(201, response.statusCode())
        assertEquals("Training Club", body["name"].stringValue())
        assertEquals("America/Sao_Paulo", body["timeZone"].stringValue())
        assertEquals("OWNER", body["role"].stringValue())
        assertEquals("COMPLETE", body["profileStatus"].stringValue())
        assertEquals(GroupTestConfiguration.USER_ID, repository.commands.single().ownerUserId)
        assertEquals(requestId, repository.commands.single().creationKey)
        assertEquals("Training Club", repository.commands.single().profile.name)
    }

    @Test
    fun `retry with one request id returns an equivalent response without duplication`() {
        val requestId = UUID.randomUUID()

        val first = postGroup(requestId, "Original Group", "UTC")
        val retry = postGroup(requestId, "Changed Group", "Europe/Lisbon")

        assertEquals(201, first.statusCode())
        assertEquals(json(first), json(retry))
        assertEquals(1, repository.groups.size)
        assertEquals(2, repository.commands.size)
    }

    @Test
    fun `invalid name returns a name field problem without mutation`() {
        val response = postGroup(UUID.randomUUID(), " ", "UTC")

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertEquals(setOf("name"), json(response)["fieldErrors"].propertyNames().asSequence().toSet())
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `invalid timezone returns a timeZone field problem without mutation`() {
        val response = postGroup(UUID.randomUUID(), "Training Club", "Mars/Olympus")

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertEquals(setOf("timeZone"), json(response)["fieldErrors"].propertyNames().asSequence().toSet())
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `invalid request id returns a requestId field problem without mutation`() {
        val response = postRaw("""{"requestId":"not-a-uuid","name":"Training Club","timeZone":"UTC"}""")

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertEquals(setOf("requestId"), json(response)["fieldErrors"].propertyNames().asSequence().toSet())
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `missing bearer token returns authentication problem without mutation`() {
        val response = postGroup(UUID.randomUUID(), "Training Club", "UTC", bearer = null)

        assertProblem(response, 401, "AUTHENTICATION_REQUIRED")
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `body owner and role fields cannot override the authenticated actor`() {
        val attackerId = UUID.randomUUID()
        val response = postRaw(
            """{"requestId":"${UUID.randomUUID()}","name":"Safe Group","modality":"COURT_VOLLEYBALL","composition":"MIXED","timeZone":"UTC","ownerUserId":"$attackerId","role":"ADMIN"}""",
        )

        assertEquals(201, response.statusCode())
        assertEquals(GroupTestConfiguration.USER_ID, repository.commands.single().ownerUserId)
        assertFalse(response.body().contains(attackerId.toString()))
        assertEquals("OWNER", json(response)["role"].stringValue())
    }

    @Test
    fun `unverified principal cannot create a group`() {
        verifier.principal = identity(emailVerified = false)

        val response = postGroup(UUID.randomUUID(), "Training Club", "UTC")

        assertProblem(response, 403, "EMAIL_NOT_VERIFIED")
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `repository failure returns generic correlated problem without details`() {
        repository.failure = IllegalStateException("group-database-password-fixture")

        val response = postGroup(UUID.randomUUID(), "Training Club", "UTC")

        assertEquals(500, response.statusCode())
        assertFalse(response.body().contains("group-database-password-fixture"))
        assertEquals(json(response)["correlationId"].stringValue(), correlationHeader(response))
    }

    @Test
    fun `complete registration returns every profile game and finance default`() {
        val venueId = UUID.randomUUID()
        val slotId = UUID.randomUUID()
        repository.nextVenueId = venueId
        repository.nextSlotIds = listOf(slotId)
        val response = postRaw(completeRequest(UUID.randomUUID()))
        val body = json(response)

        assertEquals(201, response.statusCode())
        assertEquals("PRIVATE", body["privacy"].stringValue())
        assertEquals("BRL", body["currency"].stringValue())
        assertEquals("BEACH_VOLLEYBALL", body["profile"]["modality"].stringValue())
        assertEquals("WOMEN", body["profile"]["composition"].stringValue())
        assertEquals("Arena Beach", body["profile"]["defaultVenue"]["name"].stringValue())
        assertEquals(venueId.toString(), body["profile"]["defaultVenue"]["id"].stringValue())
        assertEquals(slotId.toString(), body["profile"]["regularSlots"][0]["id"].stringValue())
        assertEquals(1500, body["financeDefaults"]["defaultGameFeeCents"].intValue())
        assertEquals(7000, body["financeDefaults"]["monthlyFeeCents"].intValue())
        assertEquals(10, body["financeDefaults"]["monthlyDueDay"].intValue())
    }

    @Test
    fun `optional collections may be omitted from registration`() {
        val response = postRaw(
            """{"requestId":"${UUID.randomUUID()}","name":"Minimal Group","modality":"FOOTVOLLEY","composition":"MIXED","timeZone":"UTC"}""",
        )

        assertEquals(201, response.statusCode())
        assertTrue(json(response)["profile"]["regularSlots"].isEmpty)
        assertTrue(json(response)["profile"]["defaultVenue"].isNull)
    }

    @Test
    fun `nested venue errors retain exact field paths`() {
        val response = postRaw(
            """{"requestId":"${UUID.randomUUID()}","name":"Group","modality":"BEACH_VOLLEYBALL","composition":"MIXED","defaultVenue":{"name":"","address":"x"},"timeZone":"UTC"}""",
        )

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertEquals(
            setOf("defaultVenue.name", "defaultVenue.address"),
            json(response)["fieldErrors"].propertyNames().asSequence().toSet(),
        )
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun `nested slot errors retain indexed field paths`() {
        val response = postRaw(
            """{"requestId":"${UUID.randomUUID()}","name":"Group","modality":"BEACH_VOLLEYBALL","composition":"MIXED","regularSlots":[{"durationMinutes":10}],"timeZone":"UTC"}""",
        )

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertEquals(
            setOf("regularSlots[0].weekday", "regularSlots[0].startTime", "regularSlots[0].durationMinutes"),
            json(response)["fieldErrors"].propertyNames().asSequence().toSet(),
        )
    }

    @Test
    fun `non-court registration rejects play style fields`() {
        val response = postRaw(
            """{"requestId":"${UUID.randomUUID()}","name":"Group","modality":"FOOTVOLLEY","composition":"MIXED","playStyle":"FIVE_ONE","timeZone":"UTC"}""",
        )

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertEquals(setOf("playStyle"), json(response)["fieldErrors"].propertyNames().asSequence().toSet())
    }

    @Test
    fun `preset level rejects obsolete custom text`() {
        val response = postRaw(
            """{"requestId":"${UUID.randomUUID()}","name":"Group","modality":"COURT_VOLLEYBALL","composition":"MIXED","level":"ADVANCED","customLevel":"Elite","timeZone":"UTC"}""",
        )

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertEquals(setOf("customLevel"), json(response)["fieldErrors"].propertyNames().asSequence().toSet())
    }

    @Test
    fun `missing timezone returns an exact field problem`() {
        val response = postRaw(
            """{"requestId":"${UUID.randomUUID()}","name":"Group","modality":"COURT_VOLLEYBALL","composition":"MIXED"}""",
        )

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertEquals(setOf("timeZone"), json(response)["fieldErrors"].propertyNames().asSequence().toSet())
    }

    @Test
    fun `replayed complete registration returns the original nested aggregate`() {
        val requestId = UUID.randomUUID()
        val first = postRaw(completeRequest(requestId))
        val replay = postRaw(completeRequest(requestId).replace("Training group", "Changed description"))

        assertEquals(json(first), json(replay))
        assertEquals("Training group", json(replay)["profile"]["description"].stringValue())
        assertEquals(1, repository.groups.size)
    }

    private fun completeRequest(requestId: UUID) =
        """{"requestId":"$requestId","name":"Beach Club","modality":"BEACH_VOLLEYBALL","composition":"WOMEN","description":"Training group","city":"Santos","level":"INTERMEDIATE","defaultVenue":{"name":"Arena Beach","address":"Rua Central 100","court":"Quadra 2"},"regularSlots":[{"weekday":"MONDAY","startTime":"20:00:00","durationMinutes":120}],"defaultCapacity":18,"defaultConfirmationLeadMinutes":180,"defaultGameFeeCents":1500,"monthlyFeeCents":7000,"monthlyDueDay":10,"timeZone":"America/Sao_Paulo"}"""

    private fun identity(emailVerified: Boolean? = true) =
        RequestIdentity("group-subject", "group@example.test", emailVerified, "Group Person")

    private fun postGroup(
        requestId: UUID,
        name: String,
        timeZone: String,
        bearer: String? = "group-token",
    ): HttpResponse<String> = postRaw(
        objectMapper.writeValueAsString(
            mapOf(
                "requestId" to requestId,
                "name" to name,
                "modality" to "COURT_VOLLEYBALL",
                "composition" to "MIXED",
                "timeZone" to timeZone,
            ),
        ),
        bearer,
    )

    private fun postRaw(body: String, bearer: String? = "group-token"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/groups"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
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
    class GroupTestConfiguration {
        @Bean
        @Primary
        fun groupVerifier() = GroupVerifier()

        @Bean
        fun groupSessionRepository() = GroupSessionRepository()

        @Bean
        fun groupBootstrapSession(repository: GroupSessionRepository) = BootstrapSession(repository)

        @Bean
        fun recordingGroupRepository() = RecordingGroupRepository()

        @Bean
        fun groupTransactionRunner() = object : TransactionRunner {
            override fun <T> inTransaction(block: () -> T): T = block()
        }

        @Bean
        fun createGroup(transaction: TransactionRunner, repository: RecordingGroupRepository) =
            CreateGroup(transaction, repository)

        @Bean
        fun getCreatedGroup(repository: RecordingGroupRepository) = GetGroup(repository, GroupAccessPolicy())

        @Bean
        fun accessGroupController(bootstrap: BootstrapSession, createGroup: CreateGroup, getCreatedGroup: GetGroup) =
            AccessGroupController(verifiedGroupActorResolver(bootstrap), createGroup, getCreatedGroup)

        companion object {
            val USER_ID: UUID = UUID.randomUUID()
        }
    }

    class GroupVerifier : VerifyRequestIdentity {
        lateinit var principal: RequestIdentity

        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(principal)
    }

    class GroupSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            user = UserAccount(
                GroupTestConfiguration.USER_ID,
                command.subject,
                command.email,
                command.displayName,
            ),
            memberships = emptyList(),
        )
    }

    class RecordingGroupRepository : GroupCreationRepository, GroupReadRepository {
        val commands = mutableListOf<CreateGroupCommand>()
        val groups = mutableMapOf<Pair<UUID, UUID>, StoredGroup>()
        var failure: RuntimeException? = null
        var nextVenueId: UUID = UUID.randomUUID()
        var nextSlotIds: List<UUID> = listOf(UUID.randomUUID())

        fun reset() {
            commands.clear()
            groups.clear()
            failure = null
            nextVenueId = UUID.randomUUID()
            nextSlotIds = listOf(UUID.randomUUID())
        }

        override fun create(command: CreateGroupCommand): StoredGroup {
            failure?.let { throw it }
            commands += command
            return groups.getOrPut(command.ownerUserId to command.creationKey) {
                StoredGroup(
                    UUID.randomUUID(),
                    command.ownerUserId,
                    command.creationKey,
                    AccessName.from(command.profile.name),
                    command.timeZone,
                    1,
                    GroupProfileStatus.COMPLETE,
                )
            }
        }

        override fun find(key: GroupReadKey): GroupReadSnapshot? {
            val stored = groups.values.firstOrNull { it.id == key.groupId } ?: return null
            val command = commands.first { it.ownerUserId == stored.ownerUserId && it.creationKey == stored.creationKey }
            val profile = command.profile
            return GroupReadSnapshot(
                stored.id,
                stored.name,
                stored.timeZone,
                if (key.actorUserId == stored.ownerUserId) GroupRole.OWNER else null,
                stored.version,
                stored.profileStatus,
                GroupProfileReadModel(
                    profile.modality,
                    profile.composition,
                    profile.description,
                    profile.city,
                    profile.level,
                    profile.customLevel,
                    profile.playStyle,
                    profile.customPlayStyle,
                    profile.defaultVenue?.let { GroupVenueReadModel(nextVenueId, it.name, it.address, it.court) },
                    profile.regularSlots.mapIndexed { index, slot ->
                        GroupRegularSlotReadModel(nextSlotIds[index], slot.weekday, slot.startTime, slot.durationMinutes)
                    },
                    profile.defaultCapacity,
                    profile.defaultConfirmationLeadMinutes,
                ),
                GroupFinanceDefaultsReadModel(
                    profile.defaultGameFeeCents,
                    profile.monthlyFeeCents,
                    profile.monthlyDueDay,
                ),
            )
        }
    }
}
