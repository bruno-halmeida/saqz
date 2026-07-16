package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.input.http.AccessMembershipController
import br.com.saqz.access.application.group.create.TransactionRunner
import br.com.saqz.access.application.group.read.GroupReadKey
import br.com.saqz.access.application.group.read.GroupReadRepository
import br.com.saqz.access.application.group.read.GroupReadSnapshot
import br.com.saqz.access.application.membership.AccessMembership
import br.com.saqz.access.application.membership.ChangeMemberRole
import br.com.saqz.access.application.membership.ChangeMemberRoleCommand
import br.com.saqz.access.application.membership.ListAccessMemberships
import br.com.saqz.access.application.membership.MembershipRepository
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.GroupAccessPolicy
import br.com.saqz.access.domain.GroupRole
import br.com.saqz.access.domain.IanaTimeZone
import br.com.saqz.access.domain.PersistedMembershipRole
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
@Import(MembershipEndpointIntegrationTest.MembershipTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class MembershipEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var read: RecordingMembershipGroupReadRepository
    @Autowired private lateinit var memberships: RecordingHttpMembershipRepository
    @Autowired private lateinit var objectMapper: ObjectMapper
    private val groupId = UUID.randomUUID()
    private val owner = member(MembershipTestConfiguration.USER_ID, "Owner Person", GroupRole.OWNER)
    private val admin = member(UUID.randomUUID(), "Admin Person", GroupRole.ADMIN)
    private val athlete = member(UUID.randomUUID(), "Athlete Person", GroupRole.ATHLETE)

    @BeforeEach
    fun reset() {
        read.role = GroupRole.OWNER
        memberships.reset(listOf(owner, admin, athlete))
    }

    @Test
    fun `owner lists minimal memberships`() {
        val response = getMemberships(groupId)
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals(3, body.size())
        assertEquals(setOf("userId", "displayName", "role"), body[0].propertyNames().asSequence().toSet())
        assertTrue(response.body().contains("Owner Person"))
    }

    @Test
    fun `admin cannot list memberships`() {
        read.role = GroupRole.ADMIN
        assertProblem(getMemberships(groupId), 403, "ACCESS_FORBIDDEN")
    }

    @Test
    fun `athlete cannot list memberships`() {
        read.role = GroupRole.ATHLETE
        assertProblem(getMemberships(groupId), 403, "ACCESS_FORBIDDEN")
    }

    @Test
    fun `nonmember list returns group not found`() {
        read.role = null
        assertProblem(getMemberships(groupId), 404, "GROUP_NOT_FOUND")
    }

    @Test
    fun `malformed list group id follows non enumeration contract`() {
        assertProblem(getMemberships("not-a-uuid"), 404, "GROUP_NOT_FOUND")
    }

    @Test
    fun `owner promotes athlete`() {
        val response = putRole(groupId, athlete.userId, "ADMIN")

        assertEquals(200, response.statusCode())
        assertEquals("ADMIN", json(response)["role"].stringValue())
        assertEquals(PersistedMembershipRole.ADMIN, memberships.commands.single().role)
    }

    @Test
    fun `owner demotes admin`() {
        val response = putRole(groupId, admin.userId, "ATHLETE")

        assertEquals(200, response.statusCode())
        assertEquals("ATHLETE", json(response)["role"].stringValue())
    }

    @Test
    fun `admin cannot change member role`() {
        read.role = GroupRole.ADMIN
        assertProblem(putRole(groupId, athlete.userId, "ADMIN"), 403, "ACCESS_FORBIDDEN")
        assertTrue(memberships.commands.isEmpty())
    }

    @Test
    fun `nonmember role change returns group not found`() {
        read.role = null
        assertProblem(putRole(groupId, athlete.userId, "ADMIN"), 404, "GROUP_NOT_FOUND")
    }

    @Test
    fun `missing target returns group not found`() {
        assertProblem(putRole(groupId, UUID.randomUUID(), "ADMIN"), 404, "GROUP_NOT_FOUND")
    }

    @Test
    fun `owner role is rejected as field validation`() {
        val response = putRole(groupId, athlete.userId, "OWNER")

        assertProblem(response, 400, "VALIDATION_FAILED")
        assertTrue(json(response)["fieldErrors"].has("role"))
        assertTrue(memberships.commands.isEmpty())
    }

    private fun getMemberships(id: Any): HttpResponse<String> = send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/groups/$id/memberships"))
            .header("Authorization", "Bearer membership-token")
            .GET()
            .build(),
    )

    private fun putRole(group: UUID, user: UUID, role: String): HttpResponse<String> = send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/groups/$group/memberships/$user/role"))
            .header("Authorization", "Bearer membership-token")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(mapOf("role" to role))))
            .build(),
    )

    private fun send(request: HttpRequest) = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())
    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertEquals(code, json(response)["code"].stringValue())
    }
    private fun member(id: UUID, name: String, role: GroupRole) = AccessMembership(id, AccessName.from(name), role)

    @TestConfiguration(proxyBeanMethods = false)
    class MembershipTestConfiguration {
        @Bean @Primary fun membershipVerifier() = MembershipVerifier()
        @Bean fun membershipSessionRepository() = MembershipSessionRepository()
        @Bean fun membershipBootstrap(repository: MembershipSessionRepository) = BootstrapSession(repository)
        @Bean fun membershipGroupReadRepository() = RecordingMembershipGroupReadRepository()
        @Bean fun httpMembershipRepository() = RecordingHttpMembershipRepository()
        @Bean fun membershipTransaction() = object : TransactionRunner {
            override fun <T> inTransaction(block: () -> T): T = block()
        }
        @Bean fun listAccessMemberships(read: RecordingMembershipGroupReadRepository, repository: RecordingHttpMembershipRepository) =
            ListAccessMemberships(read, repository, GroupAccessPolicy())
        @Bean fun changeMemberRole(transaction: TransactionRunner, read: RecordingMembershipGroupReadRepository, repository: RecordingHttpMembershipRepository) =
            ChangeMemberRole(transaction, read, repository, GroupAccessPolicy())
        @Bean fun accessMembershipController(bootstrap: BootstrapSession, list: ListAccessMemberships, change: ChangeMemberRole) =
            AccessMembershipController(bootstrap, list, change)
        companion object { val USER_ID: UUID = UUID.randomUUID() }
    }

    class MembershipVerifier : VerifyRequestIdentity {
        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
            RequestIdentity("membership-subject", "membership@example.test", true, "Membership Person"),
        )
    }

    class MembershipSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            UserAccount(MembershipTestConfiguration.USER_ID, command.subject, command.email, command.displayName),
            emptyList(),
        )
    }

    class RecordingMembershipGroupReadRepository : GroupReadRepository {
        var role: GroupRole? = GroupRole.OWNER
        override fun find(key: GroupReadKey) = GroupReadSnapshot(
            key.groupId, AccessName.from("Training Group"), IanaTimeZone.from("UTC"), role, 1,
        )
    }

    class RecordingHttpMembershipRepository : MembershipRepository {
        private val members = linkedMapOf<UUID, AccessMembership>()
        val commands = mutableListOf<ChangeMemberRoleCommand>()
        fun reset(values: List<AccessMembership>) {
            members.clear()
            values.associateByTo(members, AccessMembership::userId)
            commands.clear()
        }
        override fun list(groupId: UUID) = members.values.toList()
        override fun find(groupId: UUID, userId: UUID) = members[userId]
        override fun change(command: ChangeMemberRoleCommand): AccessMembership {
            commands += command
            val changed = members.getValue(command.userId).copy(role = GroupRole.valueOf(command.role.name))
            members[command.userId] = changed
            return changed
        }
    }
}
