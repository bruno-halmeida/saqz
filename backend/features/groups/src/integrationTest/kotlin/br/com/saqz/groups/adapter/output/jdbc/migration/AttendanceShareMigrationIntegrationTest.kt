package br.com.saqz.groups.adapter.output.jdbc.migration

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
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttendanceShareMigrationIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    @BeforeEach
    fun resetDatabase() {
        flyway().clean()
        flyway().migrate()
    }

    @AfterAll
    fun stopDatabase() = postgres.stop()

    @Test
    fun `v7 creates attendance link and limit tables`() {
        assertEquals(
            2,
            int(
                "SELECT count(*) FROM information_schema.tables WHERE table_name IN " +
                    "('game_attendance_links', 'attendance_link_resolution_limits')",
            ),
        )
    }

    @Test
    fun `v6 to v7 upgrade preserves existing attendance data`() {
        flyway().clean()
        flyway("6").migrate()
        val fixture = fixture("upgrade")
        insertAttendance(fixture)

        flyway().migrate()

        assertEquals(1, int("SELECT count(*) FROM game_attendance WHERE game_id = '${fixture.game}'"))
        assertEquals(1, int("SELECT count(*) FROM attendance_events WHERE game_id = '${fixture.game}'"))
        assertEquals(0, int("SELECT count(*) FROM game_attendance_links"))
    }

    @Test
    fun `attendance link row stores only digest timestamps and constrained references`() {
        val fixture = fixture("stored-row")
        val digest = digest(1)
        insertAttendanceLink(fixture, digest)

        assertContentEquals(digest, bytes("SELECT token_digest FROM game_attendance_links WHERE game_id = '${fixture.game}'"))
        assertEquals(fixture.group, uuid("SELECT group_id FROM game_attendance_links WHERE game_id = '${fixture.game}'"))
        assertEquals(fixture.owner, uuid("SELECT created_by_user_id FROM game_attendance_links WHERE game_id = '${fixture.game}'"))
        assertFalse(columns("game_attendance_links").contains("code"))
        assertFalse(columns("game_attendance_links").contains("raw_code"))
    }

    @Test
    fun `one game has at most one active attendance link row`() {
        val fixture = fixture("one-row")
        insertAttendanceLink(fixture, digest(1))

        assertFails { insertAttendanceLink(fixture, digest(2)) }
    }

    @Test
    fun `attendance link digests are globally unique`() {
        val first = fixture("first-link")
        val second = fixture("second-link")
        val shared = digest(3)
        insertAttendanceLink(first, shared)

        assertFails { insertAttendanceLink(second, shared) }
    }

    @Test
    fun `attendance link digest must contain exactly 32 bytes`() {
        val fixture = fixture("digest-size")

        assertFails { insertAttendanceLink(fixture, ByteArray(31)) }
        assertFails { insertAttendanceLink(fixture, ByteArray(33)) }
    }

    @Test
    fun `attendance link group and game identity must match`() {
        val first = fixture("first-identity")
        val second = fixture("second-identity")

        assertFails {
            execute(
                "INSERT INTO game_attendance_links (game_id, group_id, token_digest, created_by_user_id, created_at, updated_at) VALUES " +
                    "('${first.game}', '${second.group}', decode('${digestHex(4)}', 'hex'), '${first.owner}', now(), now())",
            )
        }
    }

    @Test
    fun `attendance link creator must reference an existing user`() {
        val fixture = fixture("creator-ref")
        val missingCreator = UUID.randomUUID()

        assertFails {
            execute(
                "INSERT INTO game_attendance_links (game_id, group_id, token_digest, created_by_user_id, created_at, updated_at) VALUES " +
                    "('${fixture.game}', '${fixture.group}', decode('${digestHex(5)}', 'hex'), '$missingCreator', now(), now())",
            )
        }
    }

    @Test
    fun `attendance link timestamps must move forward`() {
        val fixture = fixture("time-order")

        assertFails {
            execute(
                "INSERT INTO game_attendance_links (game_id, group_id, token_digest, created_by_user_id, created_at, updated_at) VALUES " +
                    "('${fixture.game}', '${fixture.group}', decode('${digestHex(6)}', 'hex'), '${fixture.owner}', now(), now() - interval '1 second')",
            )
        }
    }

    @Test
    fun `attendance link resolution limits allow counts only inside the ten attempt window bounds`() {
        val user = insertUser("limit-bounds")
        insertLimit(user, 0)
        execute("DELETE FROM attendance_link_resolution_limits WHERE user_id = '$user'")
        insertLimit(user, 10)

        assertFails { insertLimit(insertUser("limit-eleven"), 11) }
    }

    @Test
    fun `attendance link resolution limits keep one row per user`() {
        val user = insertUser("limit-unique")
        insertLimit(user, 1)

        assertFails { insertLimit(user, 2) }
    }

    @Test
    fun `attendance link resolution limits require an existing authenticated user`() {
        assertFails { insertLimit(UUID.randomUUID(), 1) }
    }

    private fun flyway(target: String? = null): Flyway {
        val configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations(*allGroupFeatureMigrationLocations())
            .cleanDisabled(false)
        if (target != null) configuration.target(target)
        return configuration.load()
    }

    private fun fixture(subject: String): Fixture {
        val owner = insertUser("$subject-owner")
        val member = insertUser("$subject-member")
        val group = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, created_at, updated_at) VALUES " +
                "('$group', '$owner', '${UUID.randomUUID()}', 'Attendance Group', 'America/Sao_Paulo', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now())",
        )
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES " +
                "('$group', '$member', 'ATHLETE', now(), now())",
        )
        val game = UUID.randomUUID()
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$game', '$group', 'Treino', DATE '2026-08-12', TIME '19:30', 'America/Sao_Paulo', TIMESTAMPTZ '2026-08-12 22:30Z', 90, TIMESTAMPTZ '2026-08-11 22:30Z', 'Arena', 'Rua Central 100', 12, 'PUBLISHED', now(), now())",
        )
        return Fixture(owner, member, group, game)
    }

    private fun insertAttendance(fixture: Fixture) {
        execute(
            "INSERT INTO game_attendance (game_id, group_id, member_user_id, status, waitlist_sequence, responded_at, updated_at, version) VALUES " +
                "('${fixture.game}', '${fixture.group}', '${fixture.member}', 'CONFIRMED', NULL, now(), now(), 1)",
        )
        execute(
            "INSERT INTO attendance_events (id, game_id, group_id, member_user_id, actor_user_id, source, old_status, new_status, reason, occurred_at) VALUES " +
                "('${UUID.randomUUID()}', '${fixture.game}', '${fixture.group}', '${fixture.member}', '${fixture.owner}', 'SELF', NULL, 'CONFIRMED', NULL, now())",
        )
    }

    private fun insertAttendanceLink(fixture: Fixture, digest: ByteArray) {
        connection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO game_attendance_links (game_id, group_id, token_digest, created_by_user_id, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())",
            ).use { statement ->
                statement.setObject(1, fixture.game)
                statement.setObject(2, fixture.group)
                statement.setBytes(3, digest)
                statement.setObject(4, fixture.owner)
                statement.executeUpdate()
            }
        }
    }

    private fun insertLimit(user: UUID, count: Int) {
        execute(
            "INSERT INTO attendance_link_resolution_limits (user_id, window_started_at, invalid_count) VALUES " +
                "('$user', TIMESTAMPTZ '2026-07-21 18:00:00Z', $count)",
        )
    }

    private fun insertUser(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES " +
                "('$id', '$subject-${UUID.randomUUID()}', true, 'User', now(), now())",
        )
        return id
    }

    private fun digest(seed: Int): ByteArray = ByteArray(32) { index -> (seed + index).toByte() }
    private fun digestHex(seed: Int): String = digest(seed).joinToString(separator = "") { "%02x".format(it) }

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

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun int(sql: String): Int = query(sql) { it.getInt(1) }
    private fun uuid(sql: String): UUID = query(sql) { it.getObject(1, UUID::class.java) }
    private fun bytes(sql: String): ByteArray = query(sql) { it.getBytes(1) }

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                read(result)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection
    private fun assertFails(block: () -> Unit) {
        assertFailsWith<Exception> { block() }
    }

    private data class Fixture(val owner: UUID, val member: UUID, val group: UUID, val game: UUID)
}
