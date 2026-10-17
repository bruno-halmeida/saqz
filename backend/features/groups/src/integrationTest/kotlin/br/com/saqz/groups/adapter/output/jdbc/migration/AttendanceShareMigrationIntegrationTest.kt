package br.com.saqz.groups.adapter.output.jdbc.migration

import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttendanceShareMigrationIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    }

    @Test
    fun `v136 drops the game attendance links table and keeps the reminder links and the limits`() {
        assertEquals(
            0,
            int("SELECT count(*) FROM information_schema.tables WHERE table_name = 'game_attendance_links'"),
        )
        assertEquals(
            2,
            int(
                "SELECT count(*) FROM information_schema.tables WHERE table_name IN " +
                    "('notification_attendance_links', 'attendance_link_resolution_limits')",
            ),
        )
    }

    @Test
    fun `v133 to v136 upgrade preserves attendance data and reminder links`() {
        flyway().clean()
        flyway("133").migrate()
        val fixture = fixture("upgrade")
        insertAttendance(fixture)
        val message = UUID.randomUUID()
        execute(
            "INSERT INTO group_messages (id, group_id, author_id, author_name, channel, body, request_id, game_id) VALUES " +
                "('$message', '${fixture.group}', '${fixture.owner}', 'Organizador', 'REMINDER', 'Bora?', '${UUID.randomUUID()}', '${fixture.game}')",
        )
        execute("INSERT INTO notification_attendance_links (message_id) VALUES ('$message')")

        flyway().migrate()

        assertEquals(1, int("SELECT count(*) FROM game_attendance WHERE game_id = '${fixture.game}'"))
        assertEquals(1, int("SELECT count(*) FROM attendance_events WHERE game_id = '${fixture.game}'"))
        assertEquals(1, int("SELECT count(*) FROM notification_attendance_links WHERE message_id = '$message'"))
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
            "INSERT INTO game_attendance (game_id, group_id, member_user_id, status, waitlist_sequence, responded_at, updated_at, version, member_display_name) VALUES " +
                "('${fixture.game}', '${fixture.group}', '${fixture.member}', 'CONFIRMED', NULL, now(), now(), 1, 'Atleta')",
        )
        execute(
            "INSERT INTO attendance_events (id, game_id, group_id, member_user_id, actor_user_id, source, old_status, new_status, reason, occurred_at) VALUES " +
                "('${UUID.randomUUID()}', '${fixture.game}', '${fixture.group}', '${fixture.member}', '${fixture.owner}', 'SELF', NULL, 'CONFIRMED', NULL, now())",
        )
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
    private fun assertFails(block: () -> Unit) {
        assertFailsWith<Exception> { block() }
    }

    private data class Fixture(val owner: UUID, val member: UUID, val group: UUID, val game: UUID)
}
