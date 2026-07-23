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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AthleteAttributesMigrationIntegrationTest {
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
    fun `new membership defaults to avulso active with no position`() {
        val group = completeGroup("defaults")
        val member = member(group, "defaults-member")

        assertEquals("AVULSO", string("SELECT membership_type FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
        assertEquals(true, bool("SELECT active FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
        assertNull(stringOrNull("SELECT position FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
    }

    @Test
    fun `membership rejects unknown position`() {
        val group = completeGroup("bad-position")
        val member = member(group, "bad-position-member")
        assertFails { execute("UPDATE group_memberships SET position = 'GOALKEEPER' WHERE group_id = '$group' AND user_id = '$member'") }
    }

    @Test
    fun `membership accepts every listed position`() {
        val group = completeGroup("positions")
        val member = member(group, "positions-member")
        for (position in listOf("LIBERO", "PONTA", "CENTRAL", "OPOSTO", "LEVANTADOR")) {
            execute("UPDATE group_memberships SET position = '$position' WHERE group_id = '$group' AND user_id = '$member'")
            assertEquals(position, string("SELECT position FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
        }
    }

    @Test
    fun `membership rejects unknown membership type`() {
        val group = completeGroup("bad-type")
        val member = member(group, "bad-type-member")
        assertFails { execute("UPDATE group_memberships SET membership_type = 'VIP' WHERE group_id = '$group' AND user_id = '$member'") }
    }

    @Test
    fun `v8 to v9 upgrade defaults existing membership rows`() {
        flyway().clean()
        flyway("8").migrate()
        val group = completeGroup("upgrade")
        val member = member(group, "upgrade-member")

        flyway().migrate()

        assertEquals("AVULSO", string("SELECT membership_type FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
        assertEquals(true, bool("SELECT active FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
        assertNull(stringOrNull("SELECT position FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
    }

    @Test
    fun `v8 to v9 upgrade backfills a missing owner membership row as admin`() {
        flyway().clean()
        flyway("8").migrate()
        val group = completeGroup("owner-backfill")
        val owner = uuid("SELECT owner_user_id FROM access_groups WHERE id = '$group'")
        assertEquals(0, int("SELECT count(*) FROM group_memberships WHERE group_id = '$group' AND user_id = '$owner'"))

        flyway().migrate()

        assertEquals(1, int("SELECT count(*) FROM group_memberships WHERE group_id = '$group' AND user_id = '$owner'"))
        assertEquals("ADMIN", string("SELECT role FROM group_memberships WHERE group_id = '$group' AND user_id = '$owner'"))
    }

    @Test
    fun `v8 to v9 upgrade leaves an existing owner membership row untouched`() {
        flyway().clean()
        flyway("8").migrate()
        val group = completeGroup("owner-existing")
        val owner = uuid("SELECT owner_user_id FROM access_groups WHERE id = '$group'")
        execute("INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES ('$group', '$owner', 'ATHLETE', now(), now())")

        flyway().migrate()

        assertEquals(1, int("SELECT count(*) FROM group_memberships WHERE group_id = '$group' AND user_id = '$owner'"))
        assertEquals("ATHLETE", string("SELECT role FROM group_memberships WHERE group_id = '$group' AND user_id = '$owner'"))
    }

    @Test
    fun `game attendance member fk references access users not group memberships`() {
        val group = completeGroup("fk-repoint")
        val member = member(group, "fk-repoint-member")
        val game = game(group)
        attendance(game, group, member, displayName = "Member")

        execute("DELETE FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'")

        assertEquals(1, int("SELECT count(*) FROM game_attendance WHERE game_id = '$game' AND member_user_id = '$member'"))
    }

    @Test
    fun `attendance events member fk references access users not group memberships`() {
        val group = completeGroup("events-fk-repoint")
        val member = member(group, "events-fk-repoint-member")
        val game = game(group)
        attendance(game, group, member, displayName = "Member")
        attendanceEvent(game, group, member)

        execute("DELETE FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'")

        assertEquals(1, int("SELECT count(*) FROM game_attendance WHERE game_id = '$game' AND member_user_id = '$member'"))
        assertEquals(1, int("SELECT count(*) FROM attendance_events WHERE game_id = '$game' AND member_user_id = '$member'"))
    }

    @Test
    fun `attendance events still requires an existing access user`() {
        val group = completeGroup("events-fk-still-enforced")
        val actor = member(group, "events-fk-still-enforced-actor")
        val game = game(group)
        assertFails {
            execute(
                "INSERT INTO attendance_events (id, game_id, group_id, member_user_id, actor_user_id, source, new_status, occurred_at) VALUES " +
                    "('${UUID.randomUUID()}', '$game', '$group', '${UUID.randomUUID()}', '$actor', 'SELF', 'CONFIRMED', now())",
            )
        }
    }

    @Test
    fun `game attendance still requires an existing access user`() {
        val group = completeGroup("fk-still-enforced")
        val game = game(group)
        assertFails {
            execute(
                "INSERT INTO game_attendance (game_id, group_id, member_user_id, status, waitlist_sequence, responded_at, updated_at, version) VALUES " +
                    "('$game', '$group', '${UUID.randomUUID()}', 'CONFIRMED', NULL, now(), now(), 1)",
            )
        }
    }

    @Test
    fun `game attendance member display name is required and cannot be blank`() {
        val group = completeGroup("attendance-name-required")
        val member = member(group, "attendance-name-member")
        val game = game(group)
        assertFails {
            execute(
                "INSERT INTO game_attendance (game_id, group_id, member_user_id, status, waitlist_sequence, responded_at, updated_at, version, member_display_name) VALUES " +
                    "('$game', '$group', '$member', 'CONFIRMED', NULL, now(), now(), 1, NULL)",
            )
        }
        assertFails {
            execute(
                "INSERT INTO game_attendance (game_id, group_id, member_user_id, status, waitlist_sequence, responded_at, updated_at, version, member_display_name) VALUES " +
                    "('$game', '$group', '$member', 'CONFIRMED', NULL, now(), now(), 1, '  ')",
            )
        }
    }

    @Test
    fun `v8 to v9 upgrade backfills attendance member display name from access users`() {
        flyway().clean()
        flyway("8").migrate()
        val group = completeGroup("attendance-backfill")
        val member = member(group, "attendance-backfill-member", displayName = "Fulano de Tal")
        val game = game(group)
        attendance(game, group, member)

        flyway().migrate()

        assertEquals("Fulano de Tal", string("SELECT member_display_name FROM game_attendance WHERE game_id = '$game' AND member_user_id = '$member'"))
    }

    @Test
    fun `group charges member display name is required`() {
        val group = completeGroup("charge-name-required")
        val member = member(group, "charge-name-member")
        assertFails {
            execute(
                "INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date, created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name) VALUES " +
                    "('${UUID.randomUUID()}', '$group', '$member', 'MONTHLY', DATE '2026-08-01', 5000, DATE '2026-08-10', '$member', '$member', now(), now(), NULL)",
            )
        }
        assertFails {
            execute(
                "INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date, created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name) VALUES " +
                    "('${UUID.randomUUID()}', '$group', '$member', 'MONTHLY', DATE '2026-08-01', 5000, DATE '2026-08-10', '$member', '$member', now(), now(), '  ')",
            )
        }
    }

    @Test
    fun `v8 to v9 upgrade backfills charge member display name from access users`() {
        flyway().clean()
        flyway("8").migrate()
        val group = completeGroup("charge-backfill")
        val member = member(group, "charge-backfill-member", displayName = "Ciclana Souza")
        val chargeId = UUID.randomUUID()
        execute(
            "INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date, created_by_user_id, changed_by_user_id, created_at, updated_at) VALUES " +
                "('$chargeId', '$group', '$member', 'MONTHLY', DATE '2026-08-01', 5000, DATE '2026-08-10', '$member', '$member', now(), now())",
        )

        flyway().migrate()

        assertEquals("Ciclana Souza", string("SELECT member_display_name FROM group_charges WHERE id = '$chargeId'"))
    }

    private fun flyway(target: String? = null): Flyway {
        val configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations(*allGroupFeatureMigrationLocations())
            .cleanDisabled(false)
        if (target != null) configuration.target(target)
        return configuration.load()
    }

    private fun completeGroup(subject: String): UUID {
        val owner = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$owner', '$subject-${UUID.randomUUID()}', true, 'Owner', now(), now())",
        )
        val group = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, created_at, updated_at) " +
                "VALUES ('$group', '$owner', '${UUID.randomUUID()}', 'Complete Group', 'America/Sao_Paulo', " +
                "'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now())",
        )
        return group
    }

    private fun member(group: UUID, subject: String, displayName: String = "Member"): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject-${UUID.randomUUID()}', true, '$displayName', now(), now())",
        )
        execute("INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES ('$group', '$id', 'ATHLETE', now(), now())")
        return id
    }

    private fun game(group: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Treino', DATE '2026-08-12', TIME '19:30', 'America/Sao_Paulo', TIMESTAMPTZ '2026-08-12 22:30Z', 90, TIMESTAMPTZ '2026-08-11 22:30Z', 'Arena', 'Rua Central 100', 12, 'PUBLISHED', now(), now())",
        )
        return id
    }

    private fun attendance(game: UUID, group: UUID, member: UUID, displayName: String? = null) {
        val column = if (displayName != null) ", member_display_name" else ""
        val value = if (displayName != null) ", '$displayName'" else ""
        execute(
            "INSERT INTO game_attendance (game_id, group_id, member_user_id, status, waitlist_sequence, responded_at, updated_at, version$column) VALUES " +
                "('$game', '$group', '$member', 'CONFIRMED', NULL, now(), now(), 1$value)",
        )
    }

    private fun attendanceEvent(game: UUID, group: UUID, member: UUID) {
        execute(
            "INSERT INTO attendance_events (id, game_id, group_id, member_user_id, actor_user_id, source, new_status, occurred_at) VALUES " +
                "('${UUID.randomUUID()}', '$game', '$group', '$member', '$member', 'SELF', 'CONFIRMED', now())",
        )
    }

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun int(sql: String): Int = query(sql) { it.getInt(1) }
    private fun string(sql: String): String = query(sql) { it.getString(1) }
    private fun stringOrNull(sql: String): String? = query(sql) { it.getString(1) }
    private fun bool(sql: String): Boolean = query(sql) { it.getBoolean(1) }
    private fun uuid(sql: String): UUID = query(sql) { it.getObject(1, UUID::class.java) }

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
}
