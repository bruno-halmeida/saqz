package br.com.saqz.groups.softdelete

import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRosterRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeManagementRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcExpenseRepository
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcGameOccurrenceRepository
import br.com.saqz.groups.adapter.output.jdbc.group.delete.JdbcGroupDeletionRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.application.athlete.AthleteRosterFilter
import br.com.saqz.groups.application.delete.DeleteGroupResult
import br.com.saqz.groups.application.read.GroupReadKey
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupSoftDeleteIntegrationTest {
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
    fun `V21 adds nullable deleted_at and active-owner partial index`() {
        val group = fixture().group

        assertTrue(boolean("SELECT deleted_at IS NULL FROM access_groups WHERE id = '$group'"))
        assertEquals(
            1,
            int(
                "SELECT count(*) FROM pg_indexes " +
                    "WHERE tablename = 'access_groups' AND indexname = 'ix_access_groups_active_owner'",
            ),
        )
    }

    @Test
    fun `owner deletion is soft and idempotent`() {
        val fixture = fixture()
        val repository = JdbcGroupDeletionRepository(dataSource)

        assertSame(DeleteGroupResult.Success, repository.softDelete(fixture.owner, fixture.group))
        assertNotNull(timestamp("SELECT deleted_at FROM access_groups WHERE id = '${fixture.group}'"))
        assertSame(DeleteGroupResult.GroupNotFound, repository.softDelete(fixture.owner, fixture.group))
    }

    @Test
    fun `athlete and admin who do not own the group are forbidden`() {
        val fixture = fixture()
        val repository = JdbcGroupDeletionRepository(dataSource)

        assertSame(DeleteGroupResult.AccessForbidden, repository.softDelete(fixture.admin, fixture.group))
        assertSame(DeleteGroupResult.AccessForbidden, repository.softDelete(fixture.athlete, fixture.group))
        assertTrue(boolean("SELECT deleted_at IS NULL FROM access_groups WHERE id = '${fixture.group}'"))
    }

    @Test
    fun `deleted group is absent from detail`() {
        val fixture = fixture()
        delete(fixture.group)

        assertNull(JdbcGroupReadRepository(dataSource).find(GroupReadKey(fixture.owner, fixture.group)))
    }

    @Test
    fun `deleted group is absent from athlete roster`() {
        val fixture = fixture()
        delete(fixture.group)

        assertTrue(JdbcAthleteRosterRepository(dataSource).list(fixture.group, AthleteRosterFilter()).isEmpty())
    }

    @Test
    fun `deleted group is absent from own athlete memberships`() {
        val fixture = fixture()
        delete(fixture.group)

        val profile = JdbcAthleteRosterRepository(dataSource).findOwnProfile(fixture.athlete)

        assertNotNull(profile)
        assertTrue(profile.memberships.none { it.groupId == fixture.group })
    }

    @Test
    fun `deleted group is absent from games`() {
        val fixture = fixture()
        val repository = JdbcGameOccurrenceRepository(dataSource)
        assertEquals(1, repository.list(fixture.group).size)

        delete(fixture.group)

        assertTrue(repository.list(fixture.group).isEmpty())
        assertNull(repository.find(fixture.group, fixture.game))
    }

    @Test
    fun `deleted group is absent from charges`() {
        val fixture = fixture()
        val repository = JdbcChargeManagementRepository(dataSource)
        assertEquals(1, repository.list(fixture.group).size)

        delete(fixture.group)

        assertTrue(repository.list(fixture.group).isEmpty())
    }

    @Test
    fun `deleted group is absent from expenses`() {
        val fixture = fixture()
        val repository = JdbcExpenseRepository(dataSource)
        assertEquals(1, repository.list(fixture.group).size)

        delete(fixture.group)

        assertTrue(repository.list(fixture.group).isEmpty())
    }

    private fun fixture(): Fixture {
        val owner = user("owner")
        val admin = user("admin")
        val athlete = user("athlete")
        val group = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, " +
                "modality, composition, created_at, updated_at) VALUES " +
                "('$group', '$owner', '${UUID.randomUUID()}', 'Training Group', 'UTC', 'COMPLETE', " +
                "'COURT_VOLLEYBALL', 'MIXED', now(), now())",
        )
        membership(group, admin, "ADMIN")
        membership(group, athlete, "ATHLETE")
        val game = UUID.randomUUID()
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, " +
                "confirmation_deadline, venue_name, venue_address, capacity, game_fee_cents, status, created_at, updated_at) VALUES " +
                "('$game', '$group', 'Training', DATE '2026-08-12', TIME '19:30', 'UTC', " +
                "TIMESTAMPTZ '2026-08-12 19:30Z', 90, TIMESTAMPTZ '2026-08-11 19:30Z', 'Arena', 'Central Street', " +
                "24, 2500, 'PUBLISHED', now(), now())",
        )
        execute(
            "INSERT INTO group_charges (id, group_id, member_user_id, kind, game_id, amount_cents, due_date, " +
                "created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name) VALUES " +
                "('${UUID.randomUUID()}', '$group', '$athlete', 'GAME', '$game', 2500, DATE '2026-08-12', " +
                "'$owner', '$owner', now(), now(), 'Athlete Person')",
        )
        execute(
            "INSERT INTO group_expenses (id, group_id, description, amount_cents, expense_date, category, " +
                "created_by_user_id, changed_by_user_id, created_at, updated_at) VALUES " +
                "('${UUID.randomUUID()}', '$group', 'Court rental', 5000, DATE '2026-08-12', 'VENUE', " +
                "'$owner', '$owner', now(), now())",
        )
        return Fixture(owner, admin, athlete, group, game)
    }

    private fun user(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject-${UUID.randomUUID()}', true, '${subject.replaceFirstChar(Char::uppercase)} Person', now(), now())",
        )
        return id
    }

    private fun membership(group: UUID, user: UUID, role: String) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
            "VALUES ('$group', '$user', '$role', now(), now())",
    )

    private fun delete(group: UUID) = execute("UPDATE access_groups SET deleted_at = now() WHERE id = '$group'")

    private fun flyway() = Flyway.configure()
        .dataSource(dataSource)
        .locations(*allGroupFeatureMigrationLocations())
        .cleanDisabled(false)
        .load()

    private fun execute(sql: String) {
        connection().use { it.createStatement().use { statement -> statement.execute(sql) } }
    }

    private fun int(sql: String): Int = query(sql) { it.getInt(1) }

    private fun boolean(sql: String): Boolean = query(sql) { it.getBoolean(1) }

    private fun timestamp(sql: String) = query(sql) { it.getTimestamp(1) }

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
    )
}
