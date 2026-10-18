package br.com.saqz.groups.adapter.output.jdbc.athlete

import br.com.saqz.groups.application.athlete.AthleteStatsAggregate
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAthleteStatsRepositoryIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcAthleteStatsRepository

    @BeforeAll
    fun startDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations()).dataSource
        repository = JdbcAthleteStatsRepository(dataSource)
    }

    @BeforeEach
    fun clearData() = execute("TRUNCATE game_attendance, games, group_memberships, access_groups, access_users CASCADE")

    @Test
    fun `stats count only completed games the person confirmed and classify absences`() {
        val owner = insertUser("stats-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("stats-member", "Member Person")
        insertMembership(group, member)
        val confirmed = insertGame(group, startOffsetMinutes = 0, status = "COMPLETED")
        val declined = insertGame(group, startOffsetMinutes = 180, status = "COMPLETED")
        val waitlisted = insertGame(group, startOffsetMinutes = 360, status = "COMPLETED")
        insertAttendance(confirmed, group, member, "CONFIRMED")
        insertAttendance(declined, group, member, "DECLINED")
        insertAttendance(waitlisted, group, member, "WAITLISTED", waitlistSequence = 1)

        assertEquals(AthleteStatsAggregate(1, 2, 1), repository.find(group, member))
    }

    @Test
    fun `games ignore cancelled, not yet completed and guest rows and survive leaving the group`() {
        val owner = insertUser("stats-played-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("stats-played-member", "Member Person")
        insertMembership(group, member)
        val played = insertGame(group, startOffsetMinutes = 0, status = "COMPLETED")
        val cancelled = insertGame(group, startOffsetMinutes = 60, status = "CANCELLED")
        val upcoming = insertGame(group, startOffsetMinutes = 120, status = "PUBLISHED")
        insertAttendance(played, group, member, "CONFIRMED")
        insertAttendance(cancelled, group, member, "CONFIRMED")
        insertAttendance(upcoming, group, member, "CONFIRMED")
        execute(
            "INSERT INTO game_attendance (game_id, group_id, member_user_id, guest_seq, status, responded_at, " +
                "updated_at, version, member_display_name) VALUES ('$played', '$group', '$member', " +
                "1, 'CONFIRMED', now(), now(), 1, 'Member Person')",
        )
        execute("UPDATE group_memberships SET active = false WHERE group_id = '$group' AND user_id = '$member'")

        assertEquals(1, repository.find(group, member)?.games)
    }

    @Test
    fun `rate and absences look only at completed games, not future, published, draft or cancelled`() {
        val owner = insertUser("stats-rate-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("stats-rate-member", "Member Person")
        insertMembership(group, member)
        val completedConfirmed = insertGame(group, startOffsetMinutes = 0, status = "COMPLETED")
        val completedDeclined = insertGame(group, startOffsetMinutes = 180, status = "COMPLETED")
        val future = insertGame(group, startOffsetMinutes = 60 * 24 * 30, status = "PUBLISHED")
        val published = insertGame(group, startOffsetMinutes = 360, status = "PUBLISHED")
        val draft = insertGame(group, startOffsetMinutes = 540, status = "DRAFT")
        val cancelled = insertGame(group, startOffsetMinutes = 720, status = "CANCELLED")
        insertAttendance(completedConfirmed, group, member, "CONFIRMED")
        insertAttendance(completedDeclined, group, member, "DECLINED")
        listOf(future, published, draft, cancelled).forEach { insertAttendance(it, group, member, "DECLINED") }

        assertEquals(AthleteStatsAggregate(1, 2, 1), repository.find(group, member))
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

    private fun insertGame(group: UUID, startOffsetMinutes: Int, status: String = "PUBLISHED"): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, " +
                "confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Treino', DATE '2026-08-12', TIME '19:30', 'America/Sao_Paulo', " +
                "TIMESTAMPTZ '2026-08-12 22:30Z' + INTERVAL '$startOffsetMinutes minutes', 90, " +
                "TIMESTAMPTZ '2026-08-11 22:30Z', 'Arena', 'Rua Central 100', " +
                "12, '$status', now(), now())",
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
