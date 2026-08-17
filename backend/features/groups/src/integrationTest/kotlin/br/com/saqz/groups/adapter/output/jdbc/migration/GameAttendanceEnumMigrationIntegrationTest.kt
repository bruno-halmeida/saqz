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
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GameAttendanceEnumMigrationIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.empty(owner = this).dataSource
    }

    @Test
    fun `v40 registers game and attendance enums and converts the closed-set columns`() {
        flyway().migrate()

        assertEquals(
            listOf(
                "attendance_source" to listOf("SELF", "ORGANIZER", "SYSTEM"),
                "attendance_status" to listOf("CONFIRMED", "DECLINED", "WAITLISTED"),
                "game_status" to listOf("DRAFT", "PUBLISHED", "CANCELLED", "COMPLETED"),
            ),
            enumCatalog(),
        )
        assertEquals("game_status", udt("games", "status"))
        assertEquals("attendance_status", udt("game_attendance", "status"))
        assertEquals("attendance_source", udt("attendance_events", "source"))
        assertEquals("attendance_status", udt("attendance_events", "old_status"))
        assertEquals("attendance_status", udt("attendance_events", "new_status"))
        assertTrue(
            queryStrings(
                """
                SELECT conname FROM pg_constraint
                WHERE conrelid IN ('games'::regclass, 'game_attendance'::regclass, 'attendance_events'::regclass)
                  AND contype = 'c'
                  AND conname IN (
                      'ck_games_status', 'ck_game_attendance_status',
                      'ck_attendance_events_source', 'ck_attendance_events_old_status',
                      'ck_attendance_events_new_status'
                  )
                """.trimIndent(),
            ).isEmpty(),
        )
        assertEquals(1, int("SELECT count(*) FROM pg_constraint WHERE conname = 'games_schedule_start_unique'"))
        assertEquals(1, int("SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_game_attendance_waitlist_sequence'"))
        assertEquals("DRAFT", defaultLabel("games", "status"))
    }

    @Test
    fun `v35 to v40 preserves game attendance and event labels including null old status`() {
        flyway("35").migrate()
        val fixture = fixture()
        execute(
            """
            INSERT INTO game_attendance (
                game_id, group_id, member_user_id, status, waitlist_sequence,
                responded_at, updated_at, version, member_display_name
            ) VALUES (
                '${fixture.game}', '${fixture.group}', '${fixture.member}', 'WAITLISTED', 1,
                now(), now(), 1, 'Member'
            )
            """.trimIndent(),
        )
        execute(
            """
            INSERT INTO attendance_events (
                id, game_id, group_id, member_user_id, actor_user_id,
                source, old_status, new_status, occurred_at
            ) VALUES (
                '${UUID.randomUUID()}', '${fixture.game}', '${fixture.group}', '${fixture.member}', '${fixture.owner}',
                'SELF', NULL, 'WAITLISTED', now()
            )
            """.trimIndent(),
        )

        flyway().migrate()

        assertEquals("PUBLISHED", string("SELECT status::text FROM games WHERE id = '${fixture.game}'"))
        assertEquals("WAITLISTED", string("SELECT status::text FROM game_attendance WHERE game_id = '${fixture.game}'"))
        assertEquals("SELF", string("SELECT source::text FROM attendance_events WHERE game_id = '${fixture.game}'"))
        assertEquals(null, nullableString("SELECT old_status::text FROM attendance_events WHERE game_id = '${fixture.game}'"))
        assertEquals("WAITLISTED", string("SELECT new_status::text FROM attendance_events WHERE game_id = '${fixture.game}'"))
        assertEquals("game_status", udt("games", "status"))
    }

    @Test
    fun `v40 keeps the draft status default`() {
        flyway().migrate()
        val fixture = fixture(status = null)
        assertEquals("DRAFT", string("SELECT status::text FROM games WHERE id = '${fixture.game}'"))
    }

    @Test
    fun `v40 rejects labels that are not in the enums`() {
        flyway().migrate()
        val fixture = fixture()
        assertFailsWith<Exception> {
            execute("UPDATE games SET status = 'MAYBE' WHERE id = '${fixture.game}'")
        }
        assertFailsWith<Exception> {
            execute(
                """
                INSERT INTO game_attendance (
                    game_id, group_id, member_user_id, status, responded_at, updated_at, version, member_display_name
                ) VALUES (
                    '${fixture.game}', '${fixture.group}', '${fixture.member}', 'MAYBE', now(), now(), 1, 'Member'
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `v40 restores waitlist sequence uniqueness`() {
        flyway().migrate()
        val first = fixture()
        execute(
            """
            INSERT INTO game_attendance (
                game_id, group_id, member_user_id, status, waitlist_sequence,
                responded_at, updated_at, version, member_display_name
            ) VALUES (
                '${first.game}', '${first.group}', '${first.member}', 'WAITLISTED', 1,
                now(), now(), 1, 'Member'
            )
            """.trimIndent(),
        )
        val other = user("enum-other")
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('${first.group}', '$other', 'ATHLETE', now(), now())",
        )
        assertFailsWith<Exception> {
            execute(
                """
                INSERT INTO game_attendance (
                    game_id, group_id, member_user_id, status, waitlist_sequence,
                    responded_at, updated_at, version, member_display_name
                ) VALUES (
                    '${first.game}', '${first.group}', '$other', 'WAITLISTED', 1,
                    now(), now(), 1, 'Other'
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `v40 restores the mutable schedule exclusion`() {
        flyway().migrate()
        val first = fixture()
        assertFailsWith<Exception> {
            execute(
                """
                INSERT INTO games (
                    id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                    confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at
                ) VALUES (
                    '${UUID.randomUUID()}', '${first.group}', 'Choque', DATE '2026-08-12', TIME '19:30',
                    'America/Sao_Paulo', TIMESTAMPTZ '2026-08-12 22:30Z', 90, TIMESTAMPTZ '2026-08-11 22:30Z',
                    'Arena', 'Rua Central 100', 12, 'DRAFT', now(), now()
                )
                """.trimIndent(),
            )
        }
    }

    private fun flyway(target: String? = null): Flyway {
        val configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations(*allGroupFeatureMigrationLocations())
            .cleanDisabled(false)
        if (target != null) configuration.target(target)
        return configuration.load()
    }

    private fun fixture(status: String? = "PUBLISHED"): Fixture {
        val owner = user("enum-owner")
        val group = UUID.randomUUID()
        execute(
            """
            INSERT INTO access_groups (
                id, owner_user_id, creation_key, name, time_zone, profile_status,
                modality, composition, created_at, updated_at
            ) VALUES (
                '$group', '$owner', '${UUID.randomUUID()}', 'Enum Group', 'America/Sao_Paulo',
                'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now()
            )
            """.trimIndent(),
        )
        val member = user("enum-member")
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$group', '$member', 'ATHLETE', now(), now())",
        )
        val game = UUID.randomUUID()
        val statusSql = status?.let { "'$it'" } ?: "DEFAULT"
        execute(
            """
            INSERT INTO games (
                id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at
            ) VALUES (
                '$game', '$group', 'Treino', DATE '2026-08-12', TIME '19:30', 'America/Sao_Paulo',
                TIMESTAMPTZ '2026-08-12 22:30Z', 90, TIMESTAMPTZ '2026-08-11 22:30Z',
                'Arena', 'Rua Central 100', 12, $statusSql, now(), now()
            )
            """.trimIndent(),
        )
        return Fixture(owner, group, member, game)
    }

    private fun user(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject-${UUID.randomUUID()}', true, 'User', now(), now())",
        )
        return id
    }

    private fun enumCatalog(): List<Pair<String, List<String>>> {
        val labels = mutableMapOf<String, MutableList<String>>()
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT t.typname, e.enumlabel
                    FROM pg_type t
                    JOIN pg_namespace n ON n.oid = t.typnamespace
                    JOIN pg_enum e ON e.enumtypid = t.oid
                    WHERE n.nspname = 'public'
                      AND t.typname IN ('game_status', 'attendance_status', 'attendance_source')
                    ORDER BY t.typname, e.enumsortorder
                    """.trimIndent(),
                ).use { result ->
                    while (result.next()) {
                        labels.getOrPut(result.getString("typname")) { mutableListOf() }
                            .add(result.getString("enumlabel"))
                    }
                }
            }
        }
        return labels.toSortedMap().map { it.key to it.value }
    }

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun string(sql: String): String = query(sql) { it.getString(1) }

    private fun nullableString(sql: String): String? = query(sql) { it.getString(1) }

    private fun int(sql: String): Int = query(sql) { it.getInt(1) }

    private fun udt(table: String, column: String): String =
        string("SELECT udt_name FROM information_schema.columns WHERE table_name = '$table' AND column_name = '$column'")

    private fun defaultLabel(table: String, column: String): String {
        val default = string(
            "SELECT column_default FROM information_schema.columns WHERE table_name = '$table' AND column_name = '$column'",
        )
        return default.substringAfter("'").substringBefore("'")
    }

    private fun queryStrings(sql: String): List<String> {
        val values = mutableListOf<String>()
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    while (result.next()) values += result.getString(1)
                }
            }
        }
        return values
    }

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                read(result)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection

    private data class Fixture(val owner: UUID, val group: UUID, val member: UUID, val game: UUID)
}
