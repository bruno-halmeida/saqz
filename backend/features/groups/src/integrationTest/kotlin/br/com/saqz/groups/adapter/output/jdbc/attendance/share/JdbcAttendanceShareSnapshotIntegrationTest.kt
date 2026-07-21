package br.com.saqz.groups.adapter.output.jdbc.attendance.share

import br.com.saqz.groups.application.attendance.share.AttendanceShareSnapshotAccess
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
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAttendanceShareSnapshotIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcAttendanceLinkRepository

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).load().migrate()
        repository = JdbcAttendanceLinkRepository(dataSource)
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
    fun `snapshot access preserves owner admin athlete and hidden roles`() {
        val fixture = fixture("roles")
        val outsider = insertUser("roles-outsider")

        assertEquals(AttendanceShareSnapshotAccess(br.com.saqz.groups.domain.GroupRole.OWNER), repository.findSnapshotAccess(fixture.owner, fixture.group, fixture.game))
        assertEquals(AttendanceShareSnapshotAccess(br.com.saqz.groups.domain.GroupRole.ADMIN), repository.findSnapshotAccess(fixture.admin, fixture.group, fixture.game))
        assertEquals(AttendanceShareSnapshotAccess(br.com.saqz.groups.domain.GroupRole.ATHLETE), repository.findSnapshotAccess(fixture.athlete, fixture.group, fixture.game))
        assertNull(repository.findSnapshotAccess(outsider, fixture.group, fixture.game))
    }

    @Test
    fun `snapshot read orders sections deterministically and omits no response members`() {
        val fixture = fixture("snapshot-order")
        val confirmedBruno = insertUser("snapshot-order-confirmed", "Bruno")
        insertMembership(fixture.group, confirmedBruno, "ATHLETE")
        insertAttendance(fixture.game, fixture.group, confirmedBruno, "CONFIRMED", null, "Bruno")
        insertAttendance(fixture.game, fixture.group, fixture.admin, "CONFIRMED", null, "Ana")
        val waitlistLeader = insertUser("snapshot-order-wait-1", "Bianca")
        insertMembership(fixture.group, waitlistLeader, "ATHLETE")
        insertAttendance(fixture.game, fixture.group, waitlistLeader, "WAITLISTED", 1, "Bianca")
        insertAttendance(fixture.game, fixture.group, fixture.athlete, "WAITLISTED", 2, "Carlos")
        val declined = insertUser("snapshot-order-declined", "Zeca")
        insertMembership(fixture.group, declined, "ATHLETE")
        insertAttendance(fixture.game, fixture.group, declined, "DECLINED", null, "Zeca")

        val snapshot = repository.readSnapshot(fixture.group, fixture.game)

        assertEquals(listOf("Ana", "Bruno"), snapshot.confirmed.map { it.displayName })
        assertEquals(listOf("Bianca" to 1L, "Carlos" to 2L), snapshot.waitlisted.map { it.displayName to it.waitlistPosition })
        assertEquals(listOf("Zeca"), snapshot.declined.map { it.displayName })
        assertEquals(0, snapshot.confirmed.count { it.displayName == "No Response" })
    }

    @Test
    fun `snapshot preserves duplicate and diacritic names`() {
        val fixture = fixture("snapshot-names")
        val duplicateOne = insertUser("snapshot-names-1", "João")
        val duplicateTwo = insertUser("snapshot-names-2", "João")
        val diacritic = insertUser("snapshot-names-3", "Álvaro")
        insertMembership(fixture.group, duplicateOne, "ATHLETE")
        insertMembership(fixture.group, duplicateTwo, "ATHLETE")
        insertMembership(fixture.group, diacritic, "ATHLETE")
        insertAttendance(fixture.game, fixture.group, duplicateOne, "CONFIRMED", null, "João")
        insertAttendance(fixture.game, fixture.group, duplicateTwo, "CONFIRMED", null, "João")
        insertAttendance(fixture.game, fixture.group, diacritic, "DECLINED", null, "Álvaro")

        val snapshot = repository.readSnapshot(fixture.group, fixture.game)

        assertEquals(listOf("João", "João"), snapshot.confirmed.map { it.displayName })
        assertEquals(listOf("Álvaro"), snapshot.declined.map { it.displayName })
    }

    @Test
    fun `snapshot returns empty sections when nobody responded`() {
        val fixture = fixture("snapshot-empty")

        val snapshot = repository.readSnapshot(fixture.group, fixture.game)

        assertEquals(emptyList(), snapshot.confirmed)
        assertEquals(emptyList(), snapshot.waitlisted)
        assertEquals(emptyList(), snapshot.declined)
        assertEquals("Treino", snapshot.title)
    }

    private fun fixture(prefix: String): Fixture {
        val owner = insertUser("$prefix-owner", "Owner")
        val admin = insertUser("$prefix-admin", "Admin")
        val athlete = insertUser("$prefix-athlete", "Athlete")
        val group = insertGroup(owner)
        insertMembership(group, admin, "ADMIN")
        insertMembership(group, athlete, "ATHLETE")
        val game = insertGame(group)
        return Fixture(owner, admin, athlete, group, game)
    }

    private fun insertUser(subject: String, displayName: String = "Access Person"): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES " +
                "('$id', '$subject-${UUID.randomUUID()}', true, '$displayName', now(), now())",
        )
        return id
    }

    private fun insertGroup(owner: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, created_at, updated_at) VALUES " +
                "('$id', '$owner', '${UUID.randomUUID()}', 'Training Group', 'America/Sao_Paulo', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now())",
        )
        return id
    }

    private fun insertGame(group: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Treino', DATE '2026-08-12', TIME '19:30', 'America/Sao_Paulo', TIMESTAMPTZ '2026-08-12 22:30Z', 90, TIMESTAMPTZ '2026-08-11 22:30Z', 'Arena', 'Rua Central', 12, 'PUBLISHED', now(), now())",
        )
        return id
    }

    private fun insertMembership(group: UUID, user: UUID, role: String) {
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES " +
                "('$group', '$user', '$role', now(), now())",
        )
    }

    private fun insertAttendance(game: UUID, group: UUID, member: UUID, status: String, waitlistSequence: Long?, displayName: String) {
        execute("UPDATE access_users SET display_name = '$displayName' WHERE id = '$member'")
        connection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO game_attendance (game_id, group_id, member_user_id, status, waitlist_sequence, responded_at, updated_at, version) VALUES (?, ?, ?, ?, ?, now(), now(), 1)",
            ).use { statement ->
                statement.setObject(1, game)
                statement.setObject(2, group)
                statement.setObject(3, member)
                statement.setString(4, status)
                statement.setObject(5, waitlistSequence)
                statement.executeUpdate()
            }
        }
    }

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun connection(): Connection = dataSource.connection

    private data class Fixture(
        val owner: UUID,
        val admin: UUID,
        val athlete: UUID,
        val group: UUID,
        val game: UUID,
    )
}
