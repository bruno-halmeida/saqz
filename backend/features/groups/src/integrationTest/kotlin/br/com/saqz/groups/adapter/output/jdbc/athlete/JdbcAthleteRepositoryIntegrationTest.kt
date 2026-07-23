package br.com.saqz.groups.adapter.output.jdbc.athlete

import br.com.saqz.groups.application.athlete.UpdateAthleteCommand
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.GroupRole
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
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAthleteRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcAthleteRepository

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).load().migrate()
        repository = JdbcAthleteRepository(dataSource)
    }

    @AfterAll
    fun stopDatabase() = postgres.stop()

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE group_charges, game_attendance, attendance_events, games, group_memberships, access_groups, access_users CASCADE")
    }

    @Test
    fun `find returns the persisted athlete with every attribute`() {
        val owner = insertUser("find-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("find-member", "Member Person")
        insertMembership(group, member, position = "PONTA", membershipType = "MENSALISTA", active = false)

        val athlete = repository.find(group, member)

        assertEquals(member, athlete?.userId)
        assertEquals("Member Person", athlete?.displayName?.value)
        assertEquals(GroupRole.ATHLETE, athlete?.role)
        assertEquals(AthletePosition.PONTA, athlete?.position)
        assertEquals(AthleteMembershipType.MENSALISTA, athlete?.membershipType)
        assertEquals(false, athlete?.active)
    }

    @Test
    fun `find resolves the owner backfilled row as OWNER`() {
        val owner = insertUser("owner-role-owner", "Owner Person")
        val group = insertGroup(owner)
        insertMembership(group, owner, role = "ADMIN")

        assertEquals(GroupRole.OWNER, repository.find(group, owner)?.role)
    }

    @Test
    fun `find returns null for a user with no membership in the group`() {
        val owner = insertUser("find-null-owner", "Owner Person")
        val group = insertGroup(owner)
        val stranger = insertUser("find-null-stranger", "Stranger")

        assertNull(repository.find(group, stranger))
    }

    @Test
    fun `updatePosition sets a new position and returns the updated row`() {
        val owner = insertUser("update-position-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("update-position-member", "Member Person")
        insertMembership(group, member)

        val updated = repository.updatePosition(group, member, AthletePosition.LEVANTADOR)

        assertEquals(AthletePosition.LEVANTADOR, updated.position)
        assertEquals("LEVANTADOR", text("SELECT position FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
    }

    @Test
    fun `updatePosition clears an existing position when set to null`() {
        val owner = insertUser("clear-position-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("clear-position-member", "Member Person")
        insertMembership(group, member, position = "CENTRAL")

        val updated = repository.updatePosition(group, member, null)

        assertNull(updated.position)
        assertNull(textOrNull("SELECT position FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
    }

    @Test
    fun `update changes position type and active together and returns full row`() {
        val owner = insertUser("update-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("update-member", "Member Person")
        insertMembership(group, member)

        val updated = repository.update(
            UpdateAthleteCommand(group, member, AthletePosition.OPOSTO, AthleteMembershipType.MENSALISTA, false),
        )

        assertEquals(AthletePosition.OPOSTO, updated.position)
        assertEquals(AthleteMembershipType.MENSALISTA, updated.membershipType)
        assertEquals(false, updated.active)
        assertEquals("OPOSTO", text("SELECT position FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
        assertEquals("MENSALISTA", text("SELECT membership_type FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
        assertEquals(false, bool("SELECT active FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
    }

    @Test
    fun `update on the owner's backfilled row still resolves role OWNER`() {
        val owner = insertUser("update-owner-role-owner", "Owner Person")
        val group = insertGroup(owner)
        insertMembership(group, owner, role = "ADMIN")

        val updated = repository.update(
            UpdateAthleteCommand(group, owner, AthletePosition.LIBERO, AthleteMembershipType.AVULSO, true),
        )

        assertEquals(GroupRole.OWNER, updated.role)
    }

    @Test
    fun `remove deletes exactly one membership row`() {
        val owner = insertUser("remove-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("remove-member", "Member Person")
        insertMembership(group, member)

        repository.remove(group, member)

        assertEquals(0, number("SELECT count(*) FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
    }

    @Test
    fun `removal survives attendance and charge history with their name snapshots`() {
        val owner = insertUser("survive-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("survive-member", "Member Person")
        insertMembership(group, member)
        val game = insertGame(group)
        insertAttendance(game, group, member, "Member Person")
        insertCharge(group, member, "Member Person")

        repository.remove(group, member)

        assertEquals(1, number("SELECT count(*) FROM game_attendance WHERE game_id = '$game' AND member_user_id = '$member'"))
        assertEquals("Member Person", text("SELECT member_display_name FROM game_attendance WHERE game_id = '$game' AND member_user_id = '$member'"))
        assertEquals(1, number("SELECT count(*) FROM group_charges WHERE group_id = '$group' AND member_user_id = '$member'"))
        assertEquals("Member Person", text("SELECT member_display_name FROM group_charges WHERE group_id = '$group' AND member_user_id = '$member'"))
    }

    @Test
    fun `re-invited removed user gets a fresh avulso row with no carried-over attributes`() {
        val owner = insertUser("reinvite-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("reinvite-member", "Member Person")
        insertMembership(group, member, position = "CENTRAL", membershipType = "MENSALISTA", active = false)

        repository.remove(group, member)
        execute("INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES ('$group', '$member', 'ATHLETE', now(), now())")

        assertNull(textOrNull("SELECT position FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
        assertEquals("AVULSO", text("SELECT membership_type FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
        assertTrue(bool("SELECT active FROM group_memberships WHERE group_id = '$group' AND user_id = '$member'"))
    }

    private fun insertUser(subject: String, name: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject-${UUID.randomUUID()}', true, '$name', now(), now())",
        )
        return id
    }

    private fun insertGroup(owner: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES ('$id', '$owner', '${UUID.randomUUID()}', 'Training Group', 'America/Sao_Paulo', now(), now())",
        )
        return id
    }

    private fun insertMembership(
        group: UUID,
        user: UUID,
        role: String = "ATHLETE",
        position: String? = null,
        membershipType: String = "AVULSO",
        active: Boolean = true,
    ) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, position, membership_type, active, created_at, updated_at) " +
            "VALUES ('$group', '$user', '$role', ${position?.let { "'$it'" } ?: "NULL"}, '$membershipType', $active, now(), now())",
    )

    private fun insertGame(group: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Treino', DATE '2026-08-12', TIME '19:30', 'America/Sao_Paulo', TIMESTAMPTZ '2026-08-12 22:30Z', 90, TIMESTAMPTZ '2026-08-11 22:30Z', 'Arena', 'Rua Central 100', 12, 'PUBLISHED', now(), now())",
        )
        return id
    }

    private fun insertAttendance(game: UUID, group: UUID, member: UUID, displayName: String) = execute(
        "INSERT INTO game_attendance (game_id, group_id, member_user_id, status, waitlist_sequence, responded_at, updated_at, version, member_display_name) VALUES " +
            "('$game', '$group', '$member', 'CONFIRMED', NULL, now(), now(), 1, '$displayName')",
    )

    private fun insertCharge(group: UUID, member: UUID, displayName: String) = execute(
        "INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date, created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name) VALUES " +
            "('${UUID.randomUUID()}', '$group', '$member', 'MONTHLY', DATE '2026-08-01', 5000, DATE '2026-08-10', '$member', '$member', now(), now(), '$displayName')",
    )

    private fun execute(sql: String) { connection().use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun text(sql: String): String = connection().use { it.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getString(1) } } }
    private fun textOrNull(sql: String): String? = connection().use { it.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getString(1) } } }
    private fun bool(sql: String): Boolean = connection().use { it.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getBoolean(1) } } }
    private fun number(sql: String): Int = connection().use { it.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getInt(1) } } }
    private fun connection(): Connection = dataSource.connection
}
