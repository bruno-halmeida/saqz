package br.com.saqz.groups.adapter.output.jdbc.attendance.share

import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.share.AttendanceLinkAttemptWindow
import br.com.saqz.groups.application.attendance.share.AttendanceLinkCode
import br.com.saqz.groups.application.attendance.share.AttendanceLinkTokenDigest
import br.com.saqz.groups.application.attendance.share.RecordInvalidAttendanceLinkAttempt
import br.com.saqz.groups.application.attendance.share.ResolveAttendanceLink
import br.com.saqz.groups.application.attendance.share.ResolveAttendanceLinkResult
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.groups.testing.startAndAwaitJdbc
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAttendanceLinkResolutionIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private val now = Instant.parse("2026-07-21T18:00:00Z")
    private val code = AttendanceLinkCode.from(
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 11 }),
    )
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcAttendanceLinkRepository
    private lateinit var transaction: JdbcTransactionRunner

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).load().migrate()
        repository = JdbcAttendanceLinkRepository(dataSource)
        transaction = JdbcTransactionRunner(dataSource)
    }

    @AfterAll
    fun stopDatabase() = postgres.stop()

    @BeforeEach
    fun clearData() {
        execute(
            "TRUNCATE game_attendance_links, attendance_link_resolution_limits, attendance_events, game_attendance, games, group_memberships, access_groups, access_users CASCADE",
        )
    }

    @Test
    fun `locking a new user attempt window initializes zero count`() {
        val user = insertUser("window-new")

        val window = transaction.inTransaction { repository.lockAttemptWindow(user, now) }

        assertEquals(AttendanceLinkAttemptWindow(now, 0), window)
    }

    @Test
    fun `recording invalid attempt persists exact window state`() {
        val user = insertUser("record-invalid")

        transaction.inTransaction {
            repository.lockAttemptWindow(user, now)
            repository.recordInvalidAttempt(RecordInvalidAttendanceLinkAttempt(user, now.minusSeconds(30), 7))
        }

        assertEquals(AttendanceLinkAttemptWindow(now.minusSeconds(30), 7), storedWindow(user))
    }

    @Test
    fun `current member resolves active published capability`() {
        val fixture = fixture("member-resolve")
        insertMembership(fixture.group, fixture.member, "ATHLETE")
        insertLink(fixture, code)

        val resolved = transaction.inTransaction {
            repository.findResolvableTarget(fixture.member, AttendanceLinkTokenDigest.sha256(code))
        }

        requireNotNull(resolved)
        assertEquals(fixture.group, resolved.groupId)
        assertEquals(fixture.game, resolved.gameId)
    }

    @Test
    fun `owner resolves without a membership row`() {
        val fixture = fixture("owner-resolve")
        insertLink(fixture, code)

        val resolved = transaction.inTransaction {
            repository.findResolvableTarget(fixture.owner, AttendanceLinkTokenDigest.sha256(code))
        }

        assertEquals(fixture.game, resolved?.gameId)
    }

    @Test
    fun `unknown and non member capabilities remain hidden`() {
        val fixture = fixture("hidden-resolve")
        insertMembership(fixture.group, fixture.member, "ATHLETE")
        insertLink(fixture, code)
        val outsider = insertUser("hidden-outsider")
        val unknown = AttendanceLinkTokenDigest.from(ByteArray(32) { 99.toByte() })

        val hidden = transaction.inTransaction {
            repository.findResolvableTarget(outsider, AttendanceLinkTokenDigest.sha256(code))
        }
        val missing = transaction.inTransaction { repository.findResolvableTarget(fixture.member, unknown) }

        assertNull(hidden)
        assertNull(missing)
    }

    @Test
    fun `window expiry resets the next resolution series`() {
        val fixture = fixture("window-reset")
        insertMembership(fixture.group, fixture.member, "ATHLETE")
        insertLink(fixture, code)
        insertWindow(fixture.member, now.minusSeconds(601), 10)

        val result = useCase().execute(fixture.member, code.value)

        assertEquals(ResolveAttendanceLinkResult.Success(fixture.group, fixture.game), result)
        assertEquals(10, storedWindow(fixture.member)?.invalidCount)
    }

    @Test
    fun `ten invalid attempts are allowed and the eleventh is limited`() {
        val actor = insertUser("limit-eleven")
        val useCase = useCase()

        repeat(10) { assertEquals(ResolveAttendanceLinkResult.InvalidOrExpired, useCase.execute(actor, code.value)) }
        val limited = useCase.execute(actor, code.value)

        assertEquals(ResolveAttendanceLinkResult.AttemptLimit(600), limited)
        assertEquals(10, storedWindow(actor)?.invalidCount)
    }

    @Test
    fun `successful resolution does not reset the invalid window`() {
        val fixture = fixture("success-no-reset")
        insertMembership(fixture.group, fixture.member, "ATHLETE")
        insertLink(fixture, code)
        insertWindow(fixture.member, now.minusSeconds(30), 4)

        val result = useCase().execute(fixture.member, code.value)

        assertEquals(ResolveAttendanceLinkResult.Success(fixture.group, fixture.game), result)
        assertEquals(4, storedWindow(fixture.member)?.invalidCount)
    }

    @Test
    fun `concurrent invalid boundary permits tenth and limits eleventh`() {
        val actor = insertUser("parallel-limit")
        insertWindow(actor, now, 9)

        val results = concurrent(List(2) { Callable { useCase().execute(actor, code.value) } })

        assertEquals(1, results.count { it == ResolveAttendanceLinkResult.InvalidOrExpired })
        assertEquals(1, results.count { it == ResolveAttendanceLinkResult.AttemptLimit(600) })
        assertEquals(10, storedWindow(actor)?.invalidCount)
    }

    private fun useCase() = ResolveAttendanceLink(
        transaction,
        repository,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun fixture(prefix: String): Fixture {
        val owner = insertUser("$prefix-owner")
        val member = insertUser("$prefix-member")
        val group = insertGroup(owner)
        val game = insertGame(group)
        return Fixture(owner, member, group, game)
    }

    private fun <T> concurrent(calls: List<Callable<T>>): List<T> {
        val ready = CountDownLatch(calls.size)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(calls.size)
        return try {
            val futures = calls.map { call ->
                pool.submit<T> {
                    ready.countDown()
                    start.await()
                    call.call()
                }
            }
            ready.await()
            start.countDown()
            futures.map { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun insertUser(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES " +
                "('$id', '$subject-${UUID.randomUUID()}', true, 'Access Person', now(), now())",
        )
        return id
    }

    private fun insertGroup(owner: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, created_at, updated_at) VALUES " +
                "('$id', '$owner', '${UUID.randomUUID()}', 'Training Group', 'UTC', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now())",
        )
        return id
    }

    private fun insertGame(group: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Treino', DATE '2026-08-12', TIME '19:30', 'UTC', TIMESTAMPTZ '2026-08-12 19:30:00Z', 90, TIMESTAMPTZ '2026-08-11 19:30:00Z', 'Arena', 'Rua Central', 12, 'PUBLISHED', now(), now())",
        )
        return id
    }

    private fun insertMembership(group: UUID, user: UUID, role: String) {
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES " +
                "('$group', '$user', '$role', now(), now())",
        )
    }

    private fun insertLink(fixture: Fixture, attendanceCode: AttendanceLinkCode) {
        connection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO game_attendance_links (game_id, group_id, token_digest, created_by_user_id, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())",
            ).use { statement ->
                statement.setObject(1, fixture.game)
                statement.setObject(2, fixture.group)
                statement.setBytes(3, AttendanceLinkTokenDigest.sha256(attendanceCode).toByteArray())
                statement.setObject(4, fixture.owner)
                statement.executeUpdate()
            }
        }
    }

    private fun insertWindow(user: UUID, startedAt: Instant, count: Int) {
        connection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO attendance_link_resolution_limits (user_id, window_started_at, invalid_count) VALUES (?, ?, ?)",
            ).use { statement ->
                statement.setObject(1, user)
                statement.setTimestamp(2, java.sql.Timestamp.from(startedAt))
                statement.setInt(3, count)
                statement.executeUpdate()
            }
        }
    }

    private fun storedWindow(user: UUID): AttendanceLinkAttemptWindow? = connection().use { connection ->
        connection.prepareStatement(
            "SELECT window_started_at, invalid_count FROM attendance_link_resolution_limits WHERE user_id = ?",
        ).use { statement ->
            statement.setObject(1, user)
            statement.executeQuery().use { result ->
                if (result.next()) AttendanceLinkAttemptWindow(result.getTimestamp(1).toInstant(), result.getInt(2)) else null
            }
        }
    }

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun connection(): Connection = dataSource.connection

    private data class Fixture(
        val owner: UUID,
        val member: UUID,
        val group: UUID,
        val game: UUID,
    )
}
