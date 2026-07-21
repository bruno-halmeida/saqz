package br.com.saqz.groups.adapter.output.jdbc.attendance.share

import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.share.RotateAttendanceLinkCommand
import br.com.saqz.groups.application.attendance.share.AttendanceLinkTokenDigest
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.GameStatus
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
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAttendanceLinkRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
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
    fun `owner target lock returns role lifecycle and deadline`() {
        val fixture = fixture("owner-target")

        val target = transaction.inTransaction {
            repository.lockRotatableTarget(fixture.owner, fixture.group, fixture.game)
        }

        requireNotNull(target)
        assertEquals(GroupRole.OWNER, target.actorRole)
        assertEquals(GameStatus.PUBLISHED, target.status)
        assertEquals(fixture.deadline, target.confirmationDeadline)
    }

    @Test
    fun `admin target lock returns organizer role`() {
        val fixture = fixture("admin-target")

        val target = transaction.inTransaction {
            repository.lockRotatableTarget(fixture.admin, fixture.group, fixture.game)
        }

        assertEquals(GroupRole.ADMIN, target?.actorRole)
    }

    @Test
    fun `athlete target lock remains visible for later authorization`() {
        val fixture = fixture("athlete-target")

        val target = transaction.inTransaction {
            repository.lockRotatableTarget(fixture.athlete, fixture.group, fixture.game)
        }

        assertEquals(GroupRole.ATHLETE, target?.actorRole)
    }

    @Test
    fun `non member target lock is indistinguishable from missing game`() {
        val fixture = fixture("hidden-target")
        val outsider = insertUser("hidden-target-outsider")

        val hidden = transaction.inTransaction {
            repository.lockRotatableTarget(outsider, fixture.group, fixture.game)
        }
        val missing = transaction.inTransaction {
            repository.lockRotatableTarget(fixture.owner, fixture.group, UUID.randomUUID())
        }

        assertNull(hidden)
        assertNull(missing)
    }

    @Test
    fun `first rotation stores only the digest`() {
        val fixture = fixture("first-rotation")
        val digest = digest(1)

        rotate(fixture.owner, fixture, digest)

        assertContentEquals(digest.toByteArray(), link(fixture.game)?.digest)
        assertEquals(1, linkCount(fixture.game))
    }

    @Test
    fun `rotation replaces previous digest and preserves creator and created timestamp`() {
        val fixture = fixture("replace-rotation")
        rotate(fixture.owner, fixture, digest(1))
        execute(
            "UPDATE game_attendance_links SET created_by_user_id='${fixture.admin}', created_at=TIMESTAMPTZ '2024-07-21 18:00:00Z', updated_at=TIMESTAMPTZ '2024-07-21 18:00:00Z' WHERE game_id='${fixture.game}'",
        )

        rotate(fixture.owner, fixture, digest(2))

        val stored = requireNotNull(link(fixture.game))
        assertContentEquals(digest(2).toByteArray(), stored.digest)
        assertEquals(fixture.admin, stored.creator)
        assertEquals(Instant.parse("2024-07-21T18:00:00Z"), stored.createdAt)
        assertFalse(stored.updatedAt.isBefore(stored.createdAt))
    }

    @Test
    fun `simultaneous rotations leave exactly one committed digest`() {
        val fixture = fixture("concurrent-rotation")
        val firstLocked = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondAttempted = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit {
                transaction.inTransaction {
                    repository.lockRotatableTarget(fixture.owner, fixture.group, fixture.game)
                    repository.rotate(RotateAttendanceLinkCommand(fixture.group, fixture.game, digest(1), fixture.owner))
                    firstLocked.countDown()
                    releaseFirst.await()
                }
            }
            firstLocked.await()
            val second = pool.submit {
                transaction.inTransaction {
                    secondAttempted.countDown()
                    repository.lockRotatableTarget(fixture.owner, fixture.group, fixture.game)
                    repository.rotate(RotateAttendanceLinkCommand(fixture.group, fixture.game, digest(2), fixture.owner))
                    secondFinished.countDown()
                }
            }
            secondAttempted.await()
            assertFalse(secondFinished.await(200, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            first.get()
            second.get()

            assertEquals(1, linkCount(fixture.game))
            assertContentEquals(digest(2).toByteArray(), requireNotNull(link(fixture.game)).digest)
        } finally {
            releaseFirst.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `transaction rollback keeps the prior active digest`() {
        val fixture = fixture("rollback")
        val original = digest(1)
        rotate(fixture.owner, fixture, original)

        assertFailsWith<IllegalStateException> {
            transaction.inTransaction {
                repository.lockRotatableTarget(fixture.owner, fixture.group, fixture.game)
                repository.rotate(RotateAttendanceLinkCommand(fixture.group, fixture.game, digest(2), fixture.owner))
                error("rollback")
            }
        }

        assertContentEquals(original.toByteArray(), requireNotNull(link(fixture.game)).digest)
    }

    @Test
    fun `rotation without a locked visible target is rejected`() {
        val fixture = fixture("missing-target")

        transaction.inTransaction {
            repository.lockRotatableTarget(fixture.owner, fixture.group, fixture.game)
        }

        assertFailsWith<RuntimeException> {
            transaction.inTransaction {
                repository.rotate(RotateAttendanceLinkCommand(UUID.randomUUID(), fixture.game, digest(3), fixture.owner))
            }
        }
    }

    @Test
    fun `rotation never persists the raw capability string`() {
        val fixture = fixture("privacy")
        val rawCode = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmno0123456789_"
        rotate(fixture.owner, fixture, digest(4))

        assertFalse(columns("game_attendance_links").contains("code"))
        assertFalse(dumpTable("game_attendance_links").contains(rawCode))
    }

    private fun fixture(subject: String): Fixture {
        val owner = insertUser("$subject-owner")
        val admin = insertUser("$subject-admin")
        val athlete = insertUser("$subject-athlete")
        val group = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, created_at, updated_at) VALUES " +
                "('$group', '$owner', '${UUID.randomUUID()}', 'Training Group', 'America/Sao_Paulo', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now())",
        )
        insertMembership(group, admin, "ADMIN")
        insertMembership(group, athlete, "ATHLETE")
        val game = UUID.randomUUID()
        val deadline = Instant.parse("2026-08-11T22:30:00Z")
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$game', '$group', 'Treino', DATE '2026-08-12', TIME '19:30', 'America/Sao_Paulo', TIMESTAMPTZ '2026-08-12 22:30Z', 90, TIMESTAMPTZ '$deadline', 'Arena', 'Rua Central 100', 12, 'PUBLISHED', now(), now())",
        )
        return Fixture(owner, admin, athlete, group, game, deadline)
    }

    private fun rotate(actorId: UUID, fixture: Fixture, digest: AttendanceLinkTokenDigest) {
        transaction.inTransaction {
            requireNotNull(repository.lockRotatableTarget(actorId, fixture.group, fixture.game))
            repository.rotate(RotateAttendanceLinkCommand(fixture.group, fixture.game, digest, actorId))
        }
    }

    private fun insertUser(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES " +
                "('$id', '$subject-${UUID.randomUUID()}', true, 'User', now(), now())",
        )
        return id
    }

    private fun insertMembership(group: UUID, user: UUID, role: String) {
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES " +
                "('$group', '$user', '$role', now(), now())",
        )
    }

    private fun digest(seed: Int) = AttendanceLinkTokenDigest.from(ByteArray(32) { index -> (seed + index).toByte() })

    private fun link(game: UUID): StoredLink? = connection().use { connection ->
        connection.prepareStatement(
            "SELECT token_digest, created_by_user_id, created_at, updated_at FROM game_attendance_links WHERE game_id = ?",
        ).use { statement ->
            statement.setObject(1, game)
            statement.executeQuery().use { result ->
                if (result.next()) {
                    StoredLink(
                        digest = result.getBytes(1),
                        creator = result.getObject(2, UUID::class.java),
                        createdAt = result.getTimestamp(3).toInstant(),
                        updatedAt = result.getTimestamp(4).toInstant(),
                    )
                } else {
                    null
                }
            }
        }
    }

    private fun linkCount(game: UUID): Int = int("SELECT count(*) FROM game_attendance_links WHERE game_id = '$game'")

    private fun columns(table: String): Set<String> = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT column_name FROM information_schema.columns WHERE table_name = '$table'",
            ).use { result ->
                buildSet {
                    while (result.next()) add(result.getString(1))
                }
            }
        }
    }

    private fun dumpTable(table: String): String = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM $table").use { result ->
                buildString {
                    while (result.next()) {
                        append(result.getString(1))
                        append(result.getString(2))
                        append(result.getString(3))
                        append(result.getString(4))
                    }
                }
            }
        }
    }

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun int(sql: String): Int = query(sql) { it.getInt(1) }

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                read(result)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection

    private data class Fixture(
        val owner: UUID,
        val admin: UUID,
        val athlete: UUID,
        val group: UUID,
        val game: UUID,
        val deadline: Instant,
    )

    private data class StoredLink(
        val digest: ByteArray,
        val creator: UUID,
        val createdAt: Instant,
        val updatedAt: Instant,
    )
}
