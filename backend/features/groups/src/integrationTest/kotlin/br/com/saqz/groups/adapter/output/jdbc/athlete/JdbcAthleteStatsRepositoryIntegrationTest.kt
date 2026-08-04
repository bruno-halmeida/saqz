package br.com.saqz.groups.adapter.output.jdbc.athlete

import br.com.saqz.groups.application.athlete.AthleteStatsAggregate
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
class JdbcAthleteStatsRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcAthleteStatsRepository

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).load().migrate()
        repository = JdbcAthleteStatsRepository(dataSource)
    }

    @AfterAll
    fun stopDatabase() = postgres.stop()

    @BeforeEach
    fun clearData() = execute("TRUNCATE game_attendance, games, group_memberships, access_groups, access_users CASCADE")

    @Test
    fun `stats count registered attendance by group and classify absences`() {
        val owner = insertUser("stats-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("stats-member", "Member Person")
        insertMembership(group, member)
        val confirmed = insertGame(group, startOffsetMinutes = 0)
        val declined = insertGame(group, startOffsetMinutes = 60)
        val waitlisted = insertGame(group, startOffsetMinutes = 120)
        insertAttendance(confirmed, group, member, "CONFIRMED")
        insertAttendance(declined, group, member, "DECLINED")
        insertAttendance(waitlisted, group, member, "WAITLISTED", waitlistSequence = 1)

        assertEquals(AthleteStatsAggregate(3, 2, 1), repository.find(group, member))
    }

    @Test
    fun `stats return null for a user without a membership in the group`() {
        val owner = insertUser("stats-missing-owner", "Owner Person")
        val group = insertGroup(owner)

        assertNull(repository.find(group, UUID.randomUUID()))
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

    private fun insertMembership(group: UUID, user: UUID) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
            "VALUES ('$group', '$user', 'ATHLETE', now(), now())",
    )

    private fun insertGame(group: UUID, startOffsetMinutes: Int): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, " +
                "confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Treino', DATE '2026-08-12', TIME '19:30', 'America/Sao_Paulo', " +
                "TIMESTAMPTZ '2026-08-12 22:30Z' + INTERVAL '$startOffsetMinutes minutes', 90, " +
                "TIMESTAMPTZ '2026-08-11 22:30Z', 'Arena', 'Rua Central 100', " +
                "12, 'PUBLISHED', now(), now())",
        )
        return id
    }

    private fun insertAttendance(
        game: UUID,
        group: UUID,
        member: UUID,
        status: String,
        waitlistSequence: Int? = null,
    ) = execute(
        "INSERT INTO game_attendance (game_id, group_id, member_user_id, status, waitlist_sequence, responded_at, " +
            "updated_at, version, member_display_name) VALUES ('$game', '$group', '$member', '$status', " +
            "${waitlistSequence ?: "NULL"}, now(), now(), 1, 'Member Person')",
    )

    private fun execute(sql: String) { connection().use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun connection(): Connection = dataSource.connection
}
