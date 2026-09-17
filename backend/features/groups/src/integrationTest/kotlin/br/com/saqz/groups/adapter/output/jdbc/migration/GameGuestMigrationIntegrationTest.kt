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

// VUL-240: V82 acrescenta guest_seq em game_attendance/attendance_events/group_charges para
// abrir espaço à 2ª linha do mesmo membro (o convidado). Estes testes cobrem só o schema.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GameGuestMigrationIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.empty(owner = this).dataSource
    }

    @Test
    fun primaryKeyAllowsGuestRowsForTheSameMember() {
        flyway().migrate()
        val fixture = fixture()

        execute(
            """
            INSERT INTO game_attendance (
                game_id, group_id, member_user_id, status, guest_seq, responded_at, updated_at, member_display_name
            ) VALUES (
                '${fixture.game}', '${fixture.group}', '${fixture.member}', 'CONFIRMED', 0, now(), now(), 'Member'
            )
            """.trimIndent(),
        )
        execute(
            """
            INSERT INTO game_attendance (
                game_id, group_id, member_user_id, status, guest_seq, waitlist_sequence,
                responded_at, updated_at, member_display_name
            ) VALUES (
                '${fixture.game}', '${fixture.group}', '${fixture.member}', 'WAITLISTED', 1, 1, now(), now(), 'Rafa'
            )
            """.trimIndent(),
        )

        assertEquals(2, int("SELECT count(*) FROM game_attendance"))
        assertFailsWith<Exception> {
            execute(
                """
                INSERT INTO game_attendance (
                    game_id, group_id, member_user_id, status, guest_seq, waitlist_sequence,
                    responded_at, updated_at, member_display_name
                ) VALUES (
                    '${fixture.game}', '${fixture.group}', '${fixture.member}', 'WAITLISTED', 1, 2, now(), now(), 'Rafa'
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun gameChargeIsUniquePerGuest() {
        flyway().migrate()
        val fixture = fixture()

        execute(charge(fixture, guestSeq = 0, guestName = null))
        execute(charge(fixture, guestSeq = 1, guestName = "Rafa"))

        assertEquals(2, int("SELECT count(*) FROM group_charges"))
        assertFailsWith<Exception> {
            execute(charge(fixture, guestSeq = 1, guestName = "Rafa"))
        }
    }

    @Test
    fun guestNameIsRequiredOnlyForGuests() {
        flyway().migrate()
        val fixture = fixture()

        assertFailsWith<Exception> { execute(charge(fixture, guestSeq = 1, guestName = null)) }
        assertFailsWith<Exception> { execute(charge(fixture, guestSeq = 0, guestName = "Rafa")) }
    }

    private fun charge(fixture: Fixture, guestSeq: Int, guestName: String?): String {
        val guestNameLiteral = guestName?.let { "'$it'" } ?: "NULL"
        return """
            INSERT INTO group_charges (
                id, group_id, member_user_id, kind, game_id, amount_cents, due_date, status,
                created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name,
                guest_seq, guest_display_name
            ) VALUES (
                '${UUID.randomUUID()}', '${fixture.group}', '${fixture.member}', 'GAME', '${fixture.game}', 2500,
                DATE '2026-08-10', 'PENDING', '${fixture.owner}', '${fixture.owner}', now(), now(), 'Member',
                $guestSeq, $guestNameLiteral
            )
        """.trimIndent()
    }

    private fun flyway(): Flyway = Flyway.configure()
        .dataSource(dataSource)
        .locations(*allGroupFeatureMigrationLocations())
        .cleanDisabled(false)
        .load()

    private fun fixture(): Fixture {
        val owner = user("guest-owner")
        val group = UUID.randomUUID()
        execute(
            """
            INSERT INTO access_groups (
                id, owner_user_id, creation_key, name, time_zone, profile_status,
                modality, composition, created_at, updated_at
            ) VALUES (
                '$group', '$owner', '${UUID.randomUUID()}', 'Guest Group', 'America/Sao_Paulo',
                'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now()
            )
            """.trimIndent(),
        )
        val member = user("guest-member")
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$group', '$member', 'ATHLETE', now(), now())",
        )
        val game = UUID.randomUUID()
        execute(
            """
            INSERT INTO games (
                id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at
            ) VALUES (
                '$game', '$group', 'Treino', DATE '2026-08-12', TIME '19:30', 'America/Sao_Paulo',
                TIMESTAMPTZ '2026-08-12 22:30Z', 90, TIMESTAMPTZ '2026-08-11 22:30Z',
                'Arena', 'Rua Central 100', 12, 'PUBLISHED', now(), now()
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

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun int(sql: String): Int = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                result.getInt(1)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection

    private data class Fixture(val owner: UUID, val group: UUID, val member: UUID, val game: UUID)
}
