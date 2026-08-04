package br.com.saqz.bootstrap

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.groups.adapter.input.http.AccessGroupListController
import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.application.read.GroupSummariesReadRepository
import br.com.saqz.groups.application.read.GroupSummaryReadModel
import br.com.saqz.groups.application.read.ListGroups
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
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
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GroupListEndpointIntegrationTest.GroupListTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class GroupListEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var repository: RecordingGroupSummariesReadRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun reset() {
        repository.summaries = emptyList()
        repository.actors.clear()
    }

    @Test
    fun `member receives every accessible group with summary fields`() {
        val ownedId = UUID.randomUUID()
        val joinedId = UUID.randomUUID()
        repository.summaries = listOf(
            summary(ownedId, "Beach Crew", GroupRole.OWNER, memberCount = 12),
            summary(joinedId, "Training Club", GroupRole.ATHLETE, memberCount = 24),
        )

        val response = listGroups()
        val body = json(response)

        assertEquals(200, response.statusCode())
        val groups = body["groups"]
        assertEquals(2, groups.size())
        assertEquals(ownedId.toString(), groups[0]["id"].stringValue())
        assertEquals("Beach Crew", groups[0]["name"].stringValue())
        assertEquals("OWNER", groups[0]["role"].stringValue())
        assertEquals(12, groups[0]["memberCount"].intValue())
        assertEquals("America/Sao_Paulo", groups[0]["timeZone"].stringValue())
        assertEquals("COMPLETE", groups[0]["profileStatus"].stringValue())
        assertEquals("COURT_VOLLEYBALL", groups[0]["modality"].stringValue())
        assertEquals("São Paulo", groups[0]["city"].stringValue())
        assertEquals("ATHLETE", groups[1]["role"].stringValue())
        assertEquals(listOf(GroupListTestConfiguration.USER_ID), repository.actors)
    }

    @Test
    fun `user without groups receives an empty list`() {
        val response = listGroups()

        assertEquals(200, response.statusCode())
        assertEquals(0, json(response)["groups"].size())
    }

    @Test
    fun `missing bearer returns authentication problem without repository read`() {
        val response = listGroups(bearer = null)

        assertEquals(401, response.statusCode())
        assertEquals("AUTHENTICATION_REQUIRED", json(response)["code"].stringValue())
        assertTrue(repository.actors.isEmpty())
    }

    private fun summary(
        id: UUID,
        name: String,
        role: GroupRole,
        memberCount: Int,
    ) = GroupSummaryReadModel(
        id = id,
        name = AccessName.from(name),
        timeZone = IanaTimeZone.from("America/Sao_Paulo"),
        role = role,
        profileStatus = GroupProfileStatus.COMPLETE,
        modality = GroupModality.COURT_VOLLEYBALL,
        city = "São Paulo",
        memberCount = memberCount,
    )

    private fun listGroups(bearer: String? = "group-list-token"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/groups")).GET()
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())

    @TestConfiguration(proxyBeanMethods = false)
    class GroupListTestConfiguration {
        @Bean
        @Primary
        fun groupListVerifier() = GroupListVerifier()

        @Bean
        fun groupListSessionRepository() = GroupListSessionRepository()

        @Bean
        fun groupListBootstrapSession(repository: GroupListSessionRepository) = BootstrapSession(repository)

        @Bean
        fun recordingGroupSummariesReadRepository() = RecordingGroupSummariesReadRepository()

        @Bean
        fun listGroups(repository: RecordingGroupSummariesReadRepository) = ListGroups(repository)

        @Bean
        fun accessGroupListController(bootstrap: BootstrapSession, listGroups: ListGroups) =
            AccessGroupListController(verifiedGroupActorResolver(bootstrap), listGroups)

        companion object {
            val USER_ID: UUID = UUID.randomUUID()
        }
    }

    class GroupListVerifier : VerifyRequestIdentity {
        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
            RequestIdentity("group-list-subject", "list@example.test", true, "List Person"),
        )
    }

    class GroupListSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            UserAccount(
                GroupListTestConfiguration.USER_ID,
                command.subject,
                command.email,
                command.displayName,
            ),
            emptyList(),
        )
    }

    class RecordingGroupSummariesReadRepository : GroupSummariesReadRepository {
        var summaries: List<GroupSummaryReadModel> = emptyList()
        val actors = mutableListOf<UUID>()

        override fun findAllFor(actorUserId: UUID): List<GroupSummaryReadModel> {
            actors += actorUserId
            return summaries
        }
    }
}
