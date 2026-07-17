package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.input.http.AccessInviteRedemptionController
import br.com.saqz.access.application.group.create.TransactionRunner
import br.com.saqz.access.application.invite.InviteCode
import br.com.saqz.access.application.invite.InviteTokenDigest
import br.com.saqz.access.application.invite.redeem.InviteAttemptWindow
import br.com.saqz.access.application.invite.redeem.InviteRedemptionRepository
import br.com.saqz.access.application.invite.redeem.RecordInvalidInviteAttempt
import br.com.saqz.access.application.invite.redeem.RedeemInvite
import br.com.saqz.access.application.invite.redeem.RedeemMembershipCommand
import br.com.saqz.access.application.invite.redeem.RedeemableInvite
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.access.domain.GroupRole
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InviteRedemptionEndpointIntegrationTest.RedemptionTestConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class InviteRedemptionEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var repository: RecordingHttpRedemptionRepository
    @Autowired private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun reset() = repository.reset()

    @Test
    fun `valid invite creates athlete and returns group and role only`() {
        val response = redeem(RedemptionTestConfiguration.RAW_CODE)
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals(setOf("groupId", "role"), body.propertyNames().asSequence().toSet())
        assertEquals(RedemptionTestConfiguration.GROUP_ID.toString(), body["groupId"].stringValue())
        assertEquals("ATHLETE", body["role"].stringValue())
    }

    @Test
    fun `owner redemption preserves owner response`() {
        repository.roles[RedemptionTestConfiguration.USER_ID] = GroupRole.OWNER

        assertEquals("OWNER", json(redeem(RedemptionTestConfiguration.RAW_CODE))["role"].stringValue())
    }

    @Test
    fun `admin redemption preserves admin response`() {
        repository.roles[RedemptionTestConfiguration.USER_ID] = GroupRole.ADMIN

        assertEquals("ADMIN", json(redeem(RedemptionTestConfiguration.RAW_CODE))["role"].stringValue())
    }

    @Test
    fun `athlete retry returns the same success without duplicate membership`() {
        val first = redeem(RedemptionTestConfiguration.RAW_CODE)
        val second = redeem(RedemptionTestConfiguration.RAW_CODE)

        assertEquals(200, first.statusCode())
        assertEquals(json(first), json(second))
        assertEquals(1, repository.roles.size)
    }

    @Test
    fun `missing malformed and rotated codes have equivalent public problems`() {
        repository.target = null

        val missing = redeemRaw("{}")
        val malformed = redeem("malformed")
        val rotated = redeem(validCode(3))

        listOf(missing, malformed, rotated).forEach { assertProblem(it, 404, "INVITE_INVALID_OR_EXPIRED") }
        assertEquals(normalizeCorrelation(missing.body()), normalizeCorrelation(malformed.body()))
        assertEquals(normalizeCorrelation(malformed.body()), normalizeCorrelation(rotated.body()))
    }

    @Test
    fun `invalid problem exposes neither code nor group`() {
        repository.target = null
        val rawCode = validCode(4)

        val response = redeem(rawCode)

        assertFalse(response.body().contains(rawCode))
        assertFalse(response.body().contains(RedemptionTestConfiguration.GROUP_ID.toString()))
    }

    @Test
    fun `tenth invalid attempt still returns invite invalid`() {
        repository.target = null

        val responses = List(10) { redeem(validCode(5)) }

        assertTrue(responses.all { it.statusCode() == 404 })
        assertEquals(10, repository.windows.getValue(RedemptionTestConfiguration.USER_ID).invalidCount)
    }

    @Test
    fun `eleventh attempt returns rate limit with retry seconds`() {
        repository.target = null
        repeat(10) { redeem(validCode(6)) }

        val response = redeem(validCode(6))

        assertProblem(response, 429, "INVITE_ATTEMPT_LIMIT")
        assertEquals(600, json(response)["retryAfterSeconds"].asInt())
    }

    @Test
    fun `rate limit response does not expose invite or group`() {
        repository.target = null
        val rawCode = validCode(7)
        repeat(10) { redeem(rawCode) }

        val response = redeem(rawCode)

        assertFalse(response.body().contains(rawCode))
        assertFalse(response.body().contains(RedemptionTestConfiguration.GROUP_ID.toString()))
    }

    @Test
    fun `repository failure returns generic problem and redacts raw code`(output: CapturedOutput) {
        repository.failure = IllegalStateException("failed ${RedemptionTestConfiguration.RAW_CODE}")

        val response = redeem(RedemptionTestConfiguration.RAW_CODE)
        val captured = response.body() + output.out + output.err

        assertEquals(500, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertFalse(captured.contains(RedemptionTestConfiguration.RAW_CODE))
    }

    @Test
    fun `invite code is accepted only from JSON body`() {
        val response = redeemRaw("{\"code\":\"${RedemptionTestConfiguration.RAW_CODE}\",\"groupId\":\"${UUID.randomUUID()}\",\"role\":\"OWNER\"}")

        assertEquals(200, response.statusCode())
        assertEquals(RedemptionTestConfiguration.GROUP_ID.toString(), json(response)["groupId"].stringValue())
        assertEquals("ATHLETE", json(response)["role"].stringValue())
    }

    private fun redeem(code: String) = redeemRaw(objectMapper.writeValueAsString(mapOf("code" to code)))

    private fun redeemRaw(body: String): HttpResponse<String> = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/invites/redeem"))
            .header("Authorization", "Bearer redeem-token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun validCode(seed: Int): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32) { seed.toByte() })

    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())
    private fun normalizeCorrelation(body: String) = body.replace(Regex("\"correlationId\":\"[^\"]+\""), "\"correlationId\":\"normalized\"")
    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertEquals(code, json(response)["code"].stringValue())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class RedemptionTestConfiguration {
        @Bean @Primary fun redemptionVerifier() = RedemptionVerifier()
        @Bean fun redemptionSessionRepository() = RedemptionSessionRepository()
        @Bean fun redemptionBootstrap(repository: RedemptionSessionRepository) = BootstrapSession(repository)
        @Bean fun httpRedemptionRepository() = RecordingHttpRedemptionRepository()
        @Bean fun redemptionTransaction() = object : TransactionRunner {
            override fun <T> inTransaction(block: () -> T): T = block()
        }
        @Bean fun redeemInvite(
            transaction: TransactionRunner,
            repository: RecordingHttpRedemptionRepository,
        ) = RedeemInvite(transaction, repository, Clock.fixed(NOW, ZoneOffset.UTC))
        @Bean fun accessInviteRedemptionController(
            bootstrap: BootstrapSession,
            redeemInvite: RedeemInvite,
        ) = AccessInviteRedemptionController(bootstrap, redeemInvite)

        companion object {
            val NOW: Instant = Instant.parse("2026-07-16T18:00:00Z")
            val USER_ID: UUID = UUID.randomUUID()
            val GROUP_ID: UUID = UUID.randomUUID()
            val RAW_CODE: String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 2 })
        }
    }

    class RedemptionVerifier : VerifyRequestIdentity {
        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
            RequestIdentity("redeem-subject", "redeem@example.test", true, "Redeem Person"),
        )
    }

    class RedemptionSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            UserAccount(RedemptionTestConfiguration.USER_ID, command.subject, command.email, command.displayName),
            emptyList(),
        )
    }

    class RecordingHttpRedemptionRepository : InviteRedemptionRepository {
        var target: RedeemableInvite? = RedeemableInvite(RedemptionTestConfiguration.GROUP_ID)
        var failure: RuntimeException? = null
        val windows = mutableMapOf<UUID, InviteAttemptWindow>()
        val roles = mutableMapOf<UUID, GroupRole>()

        fun reset() {
            target = RedeemableInvite(RedemptionTestConfiguration.GROUP_ID)
            failure = null
            windows.clear()
            roles.clear()
        }

        override fun lockAttemptWindow(userId: UUID, initializedAt: Instant): InviteAttemptWindow {
            failure?.let { throw it }
            return windows.getOrPut(userId) { InviteAttemptWindow(initializedAt, 0) }
        }

        override fun findInvite(digest: InviteTokenDigest): RedeemableInvite? = target

        override fun recordInvalidAttempt(command: RecordInvalidInviteAttempt) {
            windows[command.userId] = InviteAttemptWindow(command.windowStartedAt, command.invalidCount)
        }

        override fun redeemMembership(command: RedeemMembershipCommand): GroupRole =
            roles.getOrPut(command.userId) { GroupRole.ATHLETE }
    }
}
