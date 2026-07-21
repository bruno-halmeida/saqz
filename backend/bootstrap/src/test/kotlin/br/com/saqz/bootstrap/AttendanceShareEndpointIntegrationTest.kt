package br.com.saqz.bootstrap

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.groups.adapter.input.http.AttendanceShareController
import br.com.saqz.groups.application.attendance.share.*
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.GameStatus
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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AttendanceShareEndpointIntegrationTest.AttendanceShareTestConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class AttendanceShareEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var rotateAttendanceRepository: RecordingRotateAttendanceRepository
    @Autowired private lateinit var resolveAttendanceRepository: RecordingResolveAttendanceRepository
    @Autowired private lateinit var snapshotRepository: RecordingSnapshotRepository
    @Autowired private lateinit var links: RecordingAttendanceLinkFactory
    @Autowired private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun reset() {
        rotateAttendanceRepository.reset()
        resolveAttendanceRepository.reset()
        snapshotRepository.reset()
        links.failure = null
    }

    @Test
    fun `owner rotates attendance link and receives only the URL`() {
        val response = rotate(AttendanceShareTestConfiguration.GROUP_ID, AttendanceShareTestConfiguration.GAME_ID)
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals(setOf("url"), body.propertyNames().asSequence().toSet())
        assertEquals(AttendanceShareTestConfiguration.LINK_URL.toString(), body["url"].stringValue())
        assertFalse(response.body().contains(AttendanceShareTestConfiguration.USER_ID.toString()))
    }

    @Test
    fun `athlete cannot rotate attendance link`() {
        rotateAttendanceRepository.rotatableTarget = AttendanceLinkRotatableTarget(
            AttendanceShareTestConfiguration.GROUP_ID,
            AttendanceShareTestConfiguration.GAME_ID,
            GroupRole.ATHLETE,
            GameStatus.PUBLISHED,
            AttendanceShareTestConfiguration.NOW.plusSeconds(60),
        )

        assertProblem(rotate(AttendanceShareTestConfiguration.GROUP_ID, AttendanceShareTestConfiguration.GAME_ID), 403, "ACCESS_FORBIDDEN")
    }

    @Test
    fun `malformed attendance ids map to game not found`() {
        assertProblem(rotate("not-a-group", "not-a-game"), 404, "GAME_NOT_FOUND")
    }

    @Test
    fun `link factory failure returns generic problem without raw capability`(output: CapturedOutput) {
        links.failure = IllegalStateException("failed for ${AttendanceShareTestConfiguration.CODE.value}")

        val response = rotate(AttendanceShareTestConfiguration.GROUP_ID, AttendanceShareTestConfiguration.GAME_ID)
        val captured = response.body() + output.out + output.err

        assertEquals(500, response.statusCode())
        assertFalse(captured.contains(AttendanceShareTestConfiguration.CODE.value))
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
    fun `missing malformed and frozen capabilities share equivalent public problems`() {
        resolveAttendanceRepository.resolvableTarget = null
        val missing = resolve(AttendanceShareTestConfiguration.CODE.value)
        val malformed = resolve("malformed")
        resolveAttendanceRepository.resolvableTarget = AttendanceLinkResolvableTarget(
            AttendanceShareTestConfiguration.GROUP_ID,
            AttendanceShareTestConfiguration.GAME_ID,
            GameStatus.CANCELLED,
            AttendanceShareTestConfiguration.NOW.plusSeconds(60),
        )
        val frozen = resolve(AttendanceShareTestConfiguration.CODE.value)

        listOf(missing, malformed, frozen).forEach { assertProblem(it, 404, "ATTENDANCE_LINK_INVALID_OR_EXPIRED") }
        assertEquals(normalizeCorrelation(missing.body()), normalizeCorrelation(malformed.body()))
        assertEquals(normalizeCorrelation(malformed.body()), normalizeCorrelation(frozen.body()))
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
    fun `snapshot owner receives exact nominal sections without timestamp or ids`() {
        val response = snapshot(AttendanceShareTestConfiguration.GROUP_ID, AttendanceShareTestConfiguration.GAME_ID)
        val body = json(response)

        assertEquals(200, response.statusCode())
        assertEquals("Treino de quinta", body["title"].stringValue())
        assertEquals("America/Sao_Paulo", body["timeZone"].stringValue())
        assertTrue(body.has("confirmed"))
        assertTrue(body.has("waitlisted"))
        assertTrue(body.has("declined"))
        assertFalse(body.has("timestamp"))
        assertFalse(body.toString().contains("userId"))
        assertFalse(body.toString().contains("phone"))
    }

    @Test
    fun `athlete snapshot request is forbidden and inaccessible game stays hidden`() {
        snapshotRepository.access = AttendanceShareSnapshotAccess(GroupRole.ATHLETE)
        assertProblem(snapshot(AttendanceShareTestConfiguration.GROUP_ID, AttendanceShareTestConfiguration.GAME_ID), 403, "ACCESS_FORBIDDEN")
        snapshotRepository.access = null
        assertProblem(snapshot(AttendanceShareTestConfiguration.GROUP_ID, AttendanceShareTestConfiguration.GAME_ID), 404, "GAME_NOT_FOUND")
    }

    @Test
    fun `empty snapshot sections serialize deterministically`() {
        snapshotRepository.snapshot = AttendanceShareSnapshot(
            title = "Treino vazio",
            startsAt = AttendanceShareTestConfiguration.NOW,
            timeZone = "UTC",
            venue = "Arena",
            capacity = 12,
            confirmed = emptyList(),
            waitlisted = emptyList(),
            declined = emptyList(),
        )

        val body = json(snapshot(AttendanceShareTestConfiguration.GROUP_ID, AttendanceShareTestConfiguration.GAME_ID))

        assertEquals(0, body["confirmed"].size())
        assertEquals(0, body["waitlisted"].size())
        assertEquals(0, body["declined"].size())
    }

    private fun rotate(groupId: Any, gameId: Any): HttpResponse<String> = send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/groups/$groupId/games/$gameId/attendance-link"))
            .header("Authorization", "Bearer attendance-token")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build(),
    )

    private fun resolve(code: String): HttpResponse<String> = send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/attendance-links/resolve"))
            .header("Authorization", "Bearer attendance-token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(mapOf("code" to code))))
            .build(),
    )

    private fun snapshot(groupId: Any, gameId: Any): HttpResponse<String> = send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/groups/$groupId/games/$gameId/attendance-share"))
            .header("Authorization", "Bearer attendance-token")
            .GET()
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
        @Bean fun rotateAttendanceRepository() = RecordingRotateAttendanceRepository()
        @Bean fun resolveAttendanceRepository() = RecordingResolveAttendanceRepository()
        @Bean fun snapshotAttendanceRepository() = RecordingSnapshotRepository()
        @Bean fun attendanceLinkFactory() = RecordingAttendanceLinkFactory()
        @Bean fun attendanceTransaction() = object : TransactionRunner {
            override fun <T> inTransaction(block: () -> T): T = block()
        }
        @Bean fun rotateAttendanceLink(
            transaction: TransactionRunner,
            rotateAttendanceRepository: RecordingRotateAttendanceRepository,
            attendanceLinkFactory: RecordingAttendanceLinkFactory,
        ) = RotateAttendanceLink(
            transaction,
            rotateAttendanceRepository,
            GroupAccessPolicy(),
            AttendanceLinkTokenGenerator { TOKEN },
            attendanceLinkFactory,
            Clock.fixed(NOW, ZoneOffset.UTC),
        )
        @Bean fun resolveAttendanceLink(
            transaction: TransactionRunner,
            resolveAttendanceRepository: RecordingResolveAttendanceRepository,
        ) = ResolveAttendanceLink(transaction, resolveAttendanceRepository, Clock.fixed(NOW, ZoneOffset.UTC))
        @Bean fun readAttendanceShareSnapshot(
            transaction: TransactionRunner,
            snapshotAttendanceRepository: RecordingSnapshotRepository,
        ) = ReadAttendanceShareSnapshot(transaction, snapshotAttendanceRepository, GroupAccessPolicy())
        @Bean fun attendanceShareController(
            bootstrap: BootstrapSession,
            rotateAttendanceLink: RotateAttendanceLink,
            resolveAttendanceLink: ResolveAttendanceLink,
            readAttendanceShareSnapshot: ReadAttendanceShareSnapshot,
        ) = AttendanceShareController(
            verifiedGroupActorResolver(bootstrap),
            rotateAttendanceLink,
            resolveAttendanceLink,
            readAttendanceShareSnapshot,
        )

        companion object {
            val NOW: Instant = Instant.parse("2026-07-21T18:00:00Z")
            val USER_ID: UUID = UUID.randomUUID()
            val GROUP_ID: UUID = UUID.randomUUID()
            val GAME_ID: UUID = UUID.randomUUID()
            val CODE: AttendanceLinkCode = AttendanceLinkCode.from("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA")
            val TOKEN: AttendanceLinkToken = AttendanceLinkToken(CODE, AttendanceLinkTokenDigest.from(ByteArray(32) { 7 }))
            val LINK_URL: URI = URI("https://join.saqz.app/?saqz_attendance=${CODE.value}")
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

    class RecordingRotateAttendanceRepository : AttendanceLinkRepository {
        var rotatableTarget = AttendanceLinkRotatableTarget(
            AttendanceShareTestConfiguration.GROUP_ID,
            AttendanceShareTestConfiguration.GAME_ID,
            GroupRole.OWNER,
            GameStatus.PUBLISHED,
            AttendanceShareTestConfiguration.NOW.plusSeconds(60),
        )
        fun reset() {
            rotatableTarget = AttendanceLinkRotatableTarget(
                AttendanceShareTestConfiguration.GROUP_ID,
                AttendanceShareTestConfiguration.GAME_ID,
                GroupRole.OWNER,
                GameStatus.PUBLISHED,
                AttendanceShareTestConfiguration.NOW.plusSeconds(60),
            )
        }

        override fun lockRotatableTarget(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceLinkRotatableTarget? = rotatableTarget
        override fun rotate(command: RotateAttendanceLinkCommand) = Unit
        override fun lockAttemptWindow(userId: UUID, initializedAt: Instant): AttendanceLinkAttemptWindow = error("unused")
        override fun findResolvableTarget(actorId: UUID, digest: AttendanceLinkTokenDigest): AttendanceLinkResolvableTarget? = error("unused")
        override fun recordInvalidAttempt(command: RecordInvalidAttendanceLinkAttempt) = error("unused")
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

        override fun lockRotatableTarget(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceLinkRotatableTarget? = error("unused")
        override fun rotate(command: RotateAttendanceLinkCommand) = error("unused")
        override fun lockAttemptWindow(userId: UUID, initializedAt: Instant): AttendanceLinkAttemptWindow = window
        override fun findResolvableTarget(actorId: UUID, digest: AttendanceLinkTokenDigest): AttendanceLinkResolvableTarget? {
            failure?.let { throw it }
            return resolvableTarget
        }
        override fun recordInvalidAttempt(command: RecordInvalidAttendanceLinkAttempt) {
            window = AttendanceLinkAttemptWindow(command.windowStartedAt, command.invalidCount)
        }
    }

    class RecordingAttendanceLinkFactory : AttendanceLinkFactory {
        var failure: RuntimeException? = null
        override fun create(code: AttendanceLinkCode): URI {
            failure?.let { throw it }
            return AttendanceShareTestConfiguration.LINK_URL
        }
    }

    class RecordingSnapshotRepository : AttendanceShareSnapshotRepository {
        var access: AttendanceShareSnapshotAccess? = AttendanceShareSnapshotAccess(GroupRole.OWNER)
        var snapshot = AttendanceShareSnapshot(
            title = "Treino de quinta",
            startsAt = AttendanceShareTestConfiguration.NOW,
            timeZone = "America/Sao_Paulo",
            venue = "Arena Central",
            capacity = 12,
            confirmed = listOf(AttendanceShareSnapshotPerson("Ana")),
            waitlisted = listOf(AttendanceShareSnapshotPerson("Bruno", 1)),
            declined = listOf(AttendanceShareSnapshotPerson("Carla")),
        )

        fun reset() {
            access = AttendanceShareSnapshotAccess(GroupRole.OWNER)
            snapshot = AttendanceShareSnapshot(
                title = "Treino de quinta",
                startsAt = AttendanceShareTestConfiguration.NOW,
                timeZone = "America/Sao_Paulo",
                venue = "Arena Central",
                capacity = 12,
                confirmed = listOf(AttendanceShareSnapshotPerson("Ana")),
                waitlisted = listOf(AttendanceShareSnapshotPerson("Bruno", 1)),
                declined = listOf(AttendanceShareSnapshotPerson("Carla")),
            )
        }

        override fun findSnapshotAccess(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceShareSnapshotAccess? = access
        override fun readSnapshot(groupId: UUID, gameId: UUID): AttendanceShareSnapshot = snapshot
    }
}
