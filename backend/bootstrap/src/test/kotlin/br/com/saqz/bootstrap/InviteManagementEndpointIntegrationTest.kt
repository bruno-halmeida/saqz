package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.input.http.AccessInviteManagementController
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteLinkFactory
import br.com.saqz.groups.application.invite.InviteToken
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.SecureTokenGenerator
import br.com.saqz.groups.application.invite.manage.ExpireInvite
import br.com.saqz.groups.application.invite.manage.InviteManagementRepository
import br.com.saqz.groups.application.invite.manage.RotateInvite
import br.com.saqz.groups.application.invite.manage.RotateInviteCommand
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
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
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InviteManagementEndpointIntegrationTest.InviteTestConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class InviteManagementEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var read: RecordingInviteGroupReadRepository
    @Autowired private lateinit var repository: RecordingHttpInviteRepository
    @Autowired private lateinit var links: ConfigurableHttpInviteLinkFactory
    @Autowired private lateinit var objectMapper: ObjectMapper
    private val groupId = UUID.randomUUID()

    @BeforeEach
    fun reset() {
        read.role = GroupRole.OWNER
        repository.reset()
        links.failure = null
    }

    @Test
    fun `owner rotates invite and receives only the URL`() {
        val response = rotate(groupId)
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals(setOf("inviteUrl"), body.propertyNames().asSequence().toSet())
        assertEquals(InviteTestConfiguration.INVITE_URL.toString(), body["inviteUrl"].stringValue())
        assertEquals(groupId, repository.rotations.single().groupId)
        assertEquals(InviteTestConfiguration.USER_ID, repository.rotations.single().createdByUserId)
    }

    @Test
    fun `owner rotate invite link is opaque and contains only invite code`() {
        val response = rotate(groupId)
        val inviteUrl = json(response)["inviteUrl"].stringValue()
        val uri = URI(inviteUrl)
        val queryKeys = uri.query.split("&").map { it.substringBefore("=") }.toSet()
        val code = uri.query.substringAfter("saqz_invite=")

        assertEquals(setOf("saqz_invite"), queryKeys)
        assertEquals(43, code.length)
        assertFalse(inviteUrl.contains(groupId.toString()))
        assertFalse(inviteUrl.contains(InviteTestConfiguration.USER_ID.toString()))
        assertFalse(inviteUrl.contains("OWNER"))
        assertFalse(inviteUrl.contains("email"))
    }

    @Test
    fun `admin rotates invite`() {
        read.role = GroupRole.ADMIN

        assertEquals(200, rotate(groupId).statusCode())
        assertEquals(1, repository.rotations.size)
    }

    @Test
    fun `athlete cannot rotate invite`() {
        read.role = GroupRole.ATHLETE

        assertProblem(rotate(groupId), 403, "ACCESS_FORBIDDEN")
        assertTrue(repository.rotations.isEmpty())
    }

    @Test
    fun `nonmember rotation returns group not found`() {
        read.role = null

        assertProblem(rotate(groupId), 404, "GROUP_NOT_FOUND")
        assertTrue(repository.rotations.isEmpty())
    }

    @Test
    fun `link failure returns generic problem without exposing invite code`(output: CapturedOutput) {
        links.failure = IllegalStateException("failed for ${InviteTestConfiguration.INVITE_CODE.value}")

        val response = rotate(groupId)
        val captured = response.body() + output.out + output.err

        assertEquals(500, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertFalse(captured.contains(InviteTestConfiguration.INVITE_CODE.value))
        assertTrue(repository.rotations.isEmpty())
    }

    @Test
    fun `owner expiration is repeatable and returns empty 204`() {
        val first = expire(groupId)
        val second = expire(groupId)

        assertEquals(204, first.statusCode())
        assertEquals("", first.body())
        assertEquals(204, second.statusCode())
        assertEquals(listOf(groupId, groupId), repository.expirations)
    }

    @Test
    fun `admin expires invite`() {
        read.role = GroupRole.ADMIN

        assertEquals(204, expire(groupId).statusCode())
        assertEquals(listOf(groupId), repository.expirations)
    }

    @Test
    fun `athlete cannot expire invite`() {
        read.role = GroupRole.ATHLETE

        assertProblem(expire(groupId), 403, "ACCESS_FORBIDDEN")
        assertTrue(repository.expirations.isEmpty())
    }

    @Test
    fun `nonmember expiration returns group not found`() {
        read.role = null

        assertProblem(expire(groupId), 404, "GROUP_NOT_FOUND")
        assertTrue(repository.expirations.isEmpty())
    }

    @Test
    fun `malformed group id follows non enumeration contract on both routes`() {
        assertProblem(rotate("not-a-uuid"), 404, "GROUP_NOT_FOUND")
        assertProblem(expire("not-a-uuid"), 404, "GROUP_NOT_FOUND")
        assertTrue(repository.rotations.isEmpty())
        assertTrue(repository.expirations.isEmpty())
    }

    private fun rotate(id: Any): HttpResponse<String> = send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/groups/$id/invite"))
            .header("Authorization", "Bearer invite-token")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build(),
    )

    private fun expire(id: Any): HttpResponse<String> = send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/groups/$id/invite"))
            .header("Authorization", "Bearer invite-token")
            .DELETE()
            .build(),
    )

    private fun send(request: HttpRequest) = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())
    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertEquals(code, json(response)["code"].stringValue())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class InviteTestConfiguration {
        @Bean @Primary fun inviteVerifier() = InviteVerifier()
        @Bean fun inviteSessionRepository() = InviteSessionRepository()
        @Bean fun inviteBootstrap(repository: InviteSessionRepository) = BootstrapSession(repository)
        @Bean fun inviteGroupReadRepository() = RecordingInviteGroupReadRepository()
        @Bean fun httpInviteRepository() = RecordingHttpInviteRepository()
        @Bean fun httpInviteLinks() = ConfigurableHttpInviteLinkFactory()
        @Bean fun inviteTokenGenerator() = SecureTokenGenerator { INVITE_TOKEN }
        @Bean fun inviteTransaction() = object : TransactionRunner {
            override fun <T> inTransaction(block: () -> T): T = block()
        }
        @Bean fun rotateInvite(
            transaction: TransactionRunner,
            read: RecordingInviteGroupReadRepository,
            repository: RecordingHttpInviteRepository,
            generator: SecureTokenGenerator,
            links: ConfigurableHttpInviteLinkFactory,
        ) = RotateInvite(transaction, read, repository, GroupAccessPolicy(), generator, links)
        @Bean fun expireInvite(
            transaction: TransactionRunner,
            read: RecordingInviteGroupReadRepository,
            repository: RecordingHttpInviteRepository,
        ) = ExpireInvite(transaction, read, repository, GroupAccessPolicy())
        @Bean fun accessInviteManagementController(
            bootstrap: BootstrapSession,
            rotate: RotateInvite,
            expire: ExpireInvite,
        ) = AccessInviteManagementController(verifiedGroupActorResolver(bootstrap), rotate, expire)

        companion object {
            val USER_ID: UUID = UUID.randomUUID()
            private val RAW_CODE = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 })
            val INVITE_CODE = InviteCode.from(RAW_CODE)
            val INVITE_TOKEN = InviteToken(INVITE_CODE, InviteTokenDigest.from(ByteArray(32) { 9 }))
            val INVITE_URL: URI = URI("https://join.saqz.app/?saqz_invite=$RAW_CODE")
        }
    }

    class InviteVerifier : VerifyRequestIdentity {
        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
            RequestIdentity("invite-subject", "invite@example.test", true, "Invite Person"),
        )
    }

    class InviteSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            UserAccount(InviteTestConfiguration.USER_ID, command.subject, command.email, command.displayName),
            emptyList(),
        )
    }

    class RecordingInviteGroupReadRepository : GroupReadRepository {
        var role: GroupRole? = GroupRole.OWNER
        override fun find(key: GroupReadKey) = GroupReadSnapshot(
            key.groupId,
            AccessName.from("Training Group"),
            IanaTimeZone.from("UTC"),
            role,
            1,
        )
    }

    class RecordingHttpInviteRepository : InviteManagementRepository {
        val rotations = mutableListOf<RotateInviteCommand>()
        val expirations = mutableListOf<UUID>()
        fun reset() {
            rotations.clear()
            expirations.clear()
        }
        override fun rotate(command: RotateInviteCommand) { rotations += command }
        override fun expire(groupId: UUID) { expirations += groupId }
    }

    class ConfigurableHttpInviteLinkFactory : InviteLinkFactory {
        var failure: RuntimeException? = null
        override fun create(code: InviteCode): URI {
            failure?.let { throw it }
            return InviteTestConfiguration.INVITE_URL
        }
    }
}
