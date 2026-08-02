package br.com.saqz.bootstrap

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.groups.adapter.input.http.AccessInvitePreviewController
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.preview.AnonymousInvitePreviewRateLimiter
import br.com.saqz.groups.application.invite.preview.PreviewInvite
import br.com.saqz.groups.application.invite.preview.PreviewInviteAttemptWindow
import br.com.saqz.groups.application.invite.preview.PreviewInviteCard
import br.com.saqz.groups.application.invite.preview.PreviewInviteRepository
import br.com.saqz.groups.application.invite.preview.PreviewNextGame
import br.com.saqz.groups.application.invite.preview.PreviewRegularSlot
import br.com.saqz.groups.application.invite.preview.PreviewableInvite
import br.com.saqz.groups.application.invite.preview.RecordInvalidPreviewInviteAttempt
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
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
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InvitePreviewEndpointIntegrationTest.PreviewTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class InvitePreviewEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var repository: RecordingPreviewRepository

    @Autowired
    private lateinit var anonymousRateLimiter: AnonymousInvitePreviewRateLimiter

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun reset() {
        repository.reset()
        anonymousRateLimiter.clear()
    }

    @Test
    fun `valid preview is public and returns the complete card without group id`() {
        val response = preview(PreviewTestConfiguration.RAW_CODE)
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals(
            setOf(
                "groupName",
                "city",
                "composition",
                "level",
                "memberCount",
                "regularSlots",
                "inviterName",
                "entryRequiresApproval",
                "expiresAt",
                "nextGame",
            ),
            body.propertyNames().asSequence().toSet(),
        )
        assertEquals("Preview Group", body["groupName"].stringValue())
        assertEquals("São Paulo", body["city"].stringValue())
        assertEquals("MIXED", body["composition"].stringValue())
        assertEquals("INTERMEDIATE", body["level"].stringValue())
        assertEquals(18, body["memberCount"].intValue())
        assertEquals("TUESDAY", body["regularSlots"][0]["weekday"].stringValue())
        assertEquals("19:30", body["regularSlots"][0]["startTime"].stringValue())
        assertEquals("Owner Display", body["inviterName"].stringValue())
        assertFalse(body["entryRequiresApproval"].booleanValue())
        assertTrue(body["expiresAt"].isNull)
        assertEquals("CERET", body["nextGame"]["venueName"].stringValue())
        assertEquals("Court 2", body["nextGame"]["court"].stringValue())
        assertFalse(body.has("groupId"))
    }

    @Test
    fun `malformed and unknown preview codes return the preview invalid problem`() {
        repository.target = null

        val response = preview("malformed")

        assertProblem(response, 404, "INVITE_INVALID")
    }

    @Test
    fun `anonymous invalid window returns retry seconds after thirty attempts`() {
        repository.target = null
        repeat(30) { assertProblem(preview("bad-$it"), 404, "INVITE_INVALID") }

        val response = preview("bad-final")

        assertProblem(response, 429, "INVITE_ATTEMPT_LIMIT")
        assertEquals(600, json(response)["retryAfterSeconds"].intValue())
        assertEquals("600", response.headers().firstValue("Retry-After").orElse(""))
    }

    @Test
    fun `another api path without bearer remains protected`() {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/session")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertProblem(response, 401, "AUTHENTICATION_REQUIRED")
    }

    @Test
    fun `present bearer uses authenticated ten attempt window`() {
        repository.target = null
        repeat(10) { assertProblem(preview("bad-$it", "Bearer preview-token"), 404, "INVITE_INVALID") }

        val response = preview("bad-final", "Bearer preview-token")

        assertProblem(response, 429, "INVITE_ATTEMPT_LIMIT")
        assertEquals(600, json(response)["retryAfterSeconds"].intValue())
        assertEquals(10, repository.windows.getValue(PreviewTestConfiguration.USER_ID).invalidCount)
    }

    private fun preview(code: String, authorization: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/invites/preview"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(mapOf("code" to code))))
        if (authorization != null) builder.header("Authorization", authorization)
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())

    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertEquals(code, json(response)["code"].stringValue())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class PreviewTestConfiguration {
        @Bean @Primary fun previewVerifier() = PreviewVerifier()
        @Bean fun previewSessionRepository() = PreviewSessionRepository()
        @Bean fun previewBootstrap(repository: PreviewSessionRepository) = BootstrapSession(repository)
        @Bean fun previewRepository() = RecordingPreviewRepository()
        @Bean fun previewTransaction() = object : TransactionRunner {
            override fun <T> inTransaction(block: () -> T): T = block()
        }
        @Bean fun previewAnonymousRateLimiter() = AnonymousInvitePreviewRateLimiter()
        @Bean fun previewInvite(
            transaction: TransactionRunner,
            repository: RecordingPreviewRepository,
            anonymousRateLimiter: AnonymousInvitePreviewRateLimiter,
        ) = PreviewInvite(transaction, repository, anonymousRateLimiter, Clock.fixed(NOW, ZoneOffset.UTC))
        @Bean fun accessInvitePreviewController(
            bootstrap: BootstrapSession,
            previewInvite: PreviewInvite,
        ) = AccessInvitePreviewController(verifiedGroupActorResolver(bootstrap), previewInvite)

        companion object {
            val NOW: Instant = Instant.parse("2026-07-16T18:00:00Z")
            val USER_ID: UUID = UUID.randomUUID()
            const val RAW_CODE: String = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        }
    }

    class PreviewVerifier : VerifyRequestIdentity {
        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
            RequestIdentity("preview-subject", "preview@example.test", true, "Preview User"),
        )
    }

    class PreviewSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            UserAccount(PreviewTestConfiguration.USER_ID, command.subject, command.email, command.displayName),
            emptyList(),
        )
    }

    class RecordingPreviewRepository : PreviewInviteRepository {
        var target: PreviewableInvite? = defaultInvite()
        val windows = mutableMapOf<UUID, PreviewInviteAttemptWindow>()

        fun reset() {
            target = defaultInvite()
            windows.clear()
        }

        override fun lockAttemptWindow(userId: UUID, initializedAt: Instant): PreviewInviteAttemptWindow =
            windows.getOrPut(userId) { PreviewInviteAttemptWindow(initializedAt, 0) }

        override fun recordInvalidAttempt(command: RecordInvalidPreviewInviteAttempt) {
            windows[command.userId] = PreviewInviteAttemptWindow(command.windowStartedAt, command.invalidCount)
        }

        override fun findInvite(digest: InviteTokenDigest, now: Instant): PreviewableInvite? = target

        private fun defaultInvite() = PreviewableInvite(
            groupDeleted = false,
            expiredAt = null,
            card = PreviewInviteCard(
                groupName = "Preview Group",
                city = "São Paulo",
                composition = GroupComposition.MIXED,
                level = GroupLevel.INTERMEDIATE,
                memberCount = 18,
                regularSlots = listOf(PreviewRegularSlot(DayOfWeek.TUESDAY, LocalTime.of(19, 30))),
                inviterName = "Owner Display",
                entryRequiresApproval = false,
                expiresAt = null,
                nextGame = PreviewNextGame(PreviewTestConfiguration.NOW.plusSeconds(3_600), "CERET", "Court 2"),
            ),
        )
    }
}
