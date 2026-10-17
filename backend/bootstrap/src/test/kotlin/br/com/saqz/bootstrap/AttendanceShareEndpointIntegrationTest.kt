package br.com.saqz.bootstrap

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.groups.adapter.input.http.AttendanceShareController
import br.com.saqz.groups.application.attendance.share.*
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.game.GameStatus
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
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AttendanceShareEndpointIntegrationTest.AttendanceShareTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class AttendanceShareEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var resolveAttendanceRepository: RecordingResolveAttendanceRepository
    @Autowired private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun reset() {
        resolveAttendanceRepository.reset()
    }

    @Test
    fun `valid capability resolution returns only group and game`() {
        val response = resolve(AttendanceShareTestConfiguration.CODE.value)
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals(setOf("groupId", "gameId"), body.propertyNames().asSequence().toSet())
        assertEquals(AttendanceShareTestConfiguration.GROUP_ID.toString(), body["groupId"].stringValue())
        assertEquals(AttendanceShareTestConfiguration.GAME_ID.toString(), body["gameId"].stringValue())
    }

    @Test
    fun `incomplete athlete resolution includes registration requirement for the same group`() {
        resolveAttendanceRepository.resolvableTarget = AttendanceLinkResolvableTarget(
            AttendanceShareTestConfiguration.GROUP_ID, AttendanceShareTestConfiguration.GAME_ID,
            GameStatus.PUBLISHED, AttendanceShareTestConfiguration.NOW.plusSeconds(60), registrationRequired = true,
        )
        val response = resolve(AttendanceShareTestConfiguration.CODE.value)
        assertEquals(200, response.statusCode())
        assertEquals(true, json(response)["registrationRequired"].booleanValue())
        assertEquals(AttendanceShareTestConfiguration.GROUP_ID.toString(), json(response)["groupId"].stringValue())
    }

    @Test
    fun `missing and malformed capabilities share equivalent public problems without reason`() {
        resolveAttendanceRepository.resolvableTarget = null
        val missing = resolve(AttendanceShareTestConfiguration.CODE.value)
        val malformed = resolve("malformed")

        listOf(missing, malformed).forEach {
            assertProblem(it, 404, "ATTENDANCE_LINK_INVALID_OR_EXPIRED")
            assertFalse(json(it).has("reason"))
        }
        assertEquals(normalizeCorrelation(missing.body()), normalizeCorrelation(malformed.body()))
    }

    @Test
    fun `closed games keep the same status and code and add only the reason`() {
        val expected = mapOf(
            GameStatus.CANCELLED to "GAME_CANCELLED",
            GameStatus.DRAFT to "GAME_UNPUBLISHED",
            GameStatus.COMPLETED to "GAME_STARTED",
        )
        expected.forEach { (status, reason) ->
            resolveAttendanceRepository.resolvableTarget = AttendanceLinkResolvableTarget(
                AttendanceShareTestConfiguration.GROUP_ID,
                AttendanceShareTestConfiguration.GAME_ID,
                status,
                AttendanceShareTestConfiguration.NOW.plusSeconds(60),
            )
            val response = resolve(AttendanceShareTestConfiguration.CODE.value)

            assertProblem(response, 404, "ATTENDANCE_LINK_INVALID_OR_EXPIRED")
            assertEquals(reason, json(response)["reason"].stringValue(), status.name)
        }
    }

    @Test
    fun `started published game returns the started reason`() {
        resolveAttendanceRepository.resolvableTarget = AttendanceLinkResolvableTarget(
            AttendanceShareTestConfiguration.GROUP_ID,
            AttendanceShareTestConfiguration.GAME_ID,
            GameStatus.PUBLISHED,
            AttendanceShareTestConfiguration.NOW.minusSeconds(3600),
            startsAt = AttendanceShareTestConfiguration.NOW,
        )
        val response = resolve(AttendanceShareTestConfiguration.CODE.value)

        assertProblem(response, 404, "ATTENDANCE_LINK_INVALID_OR_EXPIRED")
        assertEquals("GAME_STARTED", json(response)["reason"].stringValue())
    }

    @Test
    fun `eleventh invalid resolution attempt is rate limited`() {
        resolveAttendanceRepository.resolvableTarget = null
        repeat(10) { resolve(AttendanceShareTestConfiguration.CODE.value) }

        val response = resolve(AttendanceShareTestConfiguration.CODE.value)

        assertProblem(response, 429, "ATTENDANCE_LINK_ATTEMPT_LIMIT")
        assertEquals(600, json(response)["retryAfterSeconds"].asInt())
        assertEquals("600", response.headers().firstValue("Retry-After").orElse(""))
    }

    @Test
    fun `temporary resolution failure is retryable`() {
        resolveAttendanceRepository.failure = IllegalStateException("temporary")

        assertProblem(resolve(AttendanceShareTestConfiguration.CODE.value), 503, "ATTENDANCE_LINK_UNAVAILABLE")
    }

    @Test
    fun `the share link and share snapshot routes were removed`() {
        val ids = "${AttendanceShareTestConfiguration.GROUP_ID}/games/${AttendanceShareTestConfiguration.GAME_ID}"

        assertEquals(404, removed("POST", "/api/groups/$ids/attendance-link").statusCode())
        assertEquals(404, removed("GET", "/api/groups/$ids/attendance-share").statusCode())
    }

    private fun resolve(code: String): HttpResponse<String> = send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/attendance-links/resolve"))
            .header("Authorization", "Bearer attendance-token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(mapOf("code" to code))))
            .build(),
    )

    private fun removed(method: String, path: String): HttpResponse<String> = send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .header("Authorization", "Bearer attendance-token")
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build(),
    )

    private fun send(request: HttpRequest) = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    private fun json(response: HttpResponse<String>) = objectMapper.readTree(response.body())
    private fun normalizeCorrelation(body: String) = body.replace(Regex("\"correlationId\":\"[^\"]+\""), "\"correlationId\":\"normalized\"")

    private fun assertProblem(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode())
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").get())
        assertEquals(code, json(response)["code"].stringValue())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class AttendanceShareTestConfiguration {
        @Bean @Primary fun attendanceVerifier() = AttendanceVerifier()
        @Bean fun attendanceSessionRepository() = AttendanceSessionRepository()
        @Bean fun attendanceBootstrap(repository: AttendanceSessionRepository) = BootstrapSession(repository)
        @Bean fun resolveAttendanceRepository() = RecordingResolveAttendanceRepository()
        @Bean fun attendanceTransaction() = object : TransactionRunner {
            override fun <T> inTransaction(block: () -> T): T = block()
        }
        @Bean fun resolveAttendanceLink(
            transaction: TransactionRunner,
            resolveAttendanceRepository: RecordingResolveAttendanceRepository,
        ) = ResolveAttendanceLink(transaction, resolveAttendanceRepository, Clock.fixed(NOW, ZoneOffset.UTC))
        @Bean fun attendanceShareController(
            bootstrap: BootstrapSession,
            resolveAttendanceLink: ResolveAttendanceLink,
        ) = AttendanceShareController(verifiedGroupActorResolver(bootstrap), resolveAttendanceLink)

        companion object {
            val NOW: Instant = Instant.parse("2026-07-21T18:00:00Z")
            val USER_ID: UUID = UUID.randomUUID()
            val GROUP_ID: UUID = UUID.randomUUID()
            val GAME_ID: UUID = UUID.randomUUID()
            val CODE: AttendanceLinkCode = AttendanceLinkCode.from("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA")
        }
    }

    class AttendanceVerifier : VerifyRequestIdentity {
        override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
            RequestIdentity("attendance-subject", "attendance@example.test", true, "Attendance Person"),
        )
    }

    class AttendanceSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert) = SessionView(
            UserAccount(AttendanceShareTestConfiguration.USER_ID, command.subject, command.email, command.displayName),
            emptyList(),
        )
    }

    class RecordingResolveAttendanceRepository : AttendanceLinkRepository {
        var window = AttendanceLinkAttemptWindow(AttendanceShareTestConfiguration.NOW, 0)
        var resolvableTarget: AttendanceLinkResolvableTarget? = AttendanceLinkResolvableTarget(
            AttendanceShareTestConfiguration.GROUP_ID,
            AttendanceShareTestConfiguration.GAME_ID,
            GameStatus.PUBLISHED,
            AttendanceShareTestConfiguration.NOW.plusSeconds(60),
        )
        var failure: RuntimeException? = null

        fun reset() {
            window = AttendanceLinkAttemptWindow(AttendanceShareTestConfiguration.NOW, 0)
            resolvableTarget = AttendanceLinkResolvableTarget(
                AttendanceShareTestConfiguration.GROUP_ID,
                AttendanceShareTestConfiguration.GAME_ID,
                GameStatus.PUBLISHED,
                AttendanceShareTestConfiguration.NOW.plusSeconds(60),
            )
            failure = null
        }

        override fun lockAttemptWindow(userId: UUID, initializedAt: Instant): AttendanceLinkAttemptWindow = window
        override fun findResolvableTarget(actorId: UUID, digest: AttendanceLinkTokenDigest): AttendanceLinkResolvableTarget? {
            failure?.let { throw it }
            return resolvableTarget
        }
        override fun recordInvalidAttempt(command: RecordInvalidAttendanceLinkAttempt) {
            window = AttendanceLinkAttemptWindow(command.windowStartedAt, command.invalidCount)
        }
    }
}
