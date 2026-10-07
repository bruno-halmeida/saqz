package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcFinishedGamesRepositoryIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach fun resetDatabase() { dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource }

    @Test fun `published game that already ended becomes completed and the others stay`() {
        val group = group()
        val ended = game(group, "PUBLISHED", startsAt = NOW.minus(Duration.ofHours(3)), minutes = 120)
        val endsNow = game(group, "PUBLISHED", startsAt = NOW.minus(Duration.ofMinutes(90)), minutes = 90)
        val playing = game(group, "PUBLISHED", startsAt = NOW.minus(Duration.ofHours(1)), minutes = 120)
        val upcoming = game(group, "PUBLISHED", startsAt = NOW.plus(Duration.ofDays(1)), minutes = 120)
        val draft = game(group, "DRAFT", startsAt = NOW.minus(Duration.ofHours(5)), minutes = 120)
        val cancelled = game(group, "CANCELLED", startsAt = NOW.minus(Duration.ofHours(7)), minutes = 120)

        assertEquals(2, JdbcFinishedGamesRepository(dataSource).completeFinished(NOW))

        assertEquals("COMPLETED" to 2L, state(ended))
        assertEquals("COMPLETED" to 2L, state(endsNow))
        assertEquals("PUBLISHED" to 1L, state(playing))
        assertEquals("PUBLISHED" to 1L, state(upcoming))
        assertEquals("DRAFT" to 1L, state(draft))
        assertEquals("CANCELLED" to 1L, state(cancelled))
    }

    @Test fun `running again changes nothing`() {
        val ended = game(group(), "PUBLISHED", startsAt = NOW.minus(Duration.ofHours(3)), minutes = 120)
        val repository = JdbcFinishedGamesRepository(dataSource)

        assertEquals(1, repository.completeFinished(NOW))
        assertEquals(0, repository.completeFinished(NOW.plus(Duration.ofMinutes(5))))
        assertEquals("COMPLETED" to 2L, state(ended))
    }

    @Test fun `games of a deleted group are left alone`() {
        val group = group()
        val ended = game(group, "PUBLISHED", startsAt = NOW.minus(Duration.ofHours(3)), minutes = 120)
        execute("UPDATE access_groups SET deleted_at = now() WHERE id = '$group'")

        assertEquals(0, JdbcFinishedGamesRepository(dataSource).completeFinished(NOW))
        assertEquals("PUBLISHED" to 1L, state(ended))
    }

    private fun group(): UUID {
        val owner = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$owner', 'owner-$owner', true, 'Owner', now(), now())",
        )
        return UUID.randomUUID().also { group ->
            execute(
                "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, " +
                    "composition, default_capacity, default_confirmation_lead_minutes, created_at, updated_at) VALUES " +
                    "('$group', '$owner', '${UUID.randomUUID()}', 'Training Group', 'America/Sao_Paulo', 'COMPLETE', " +
                    "'COURT_VOLLEYBALL', 'MIXED', 12, 180, now(), now())",
            )
        }
    }

    private fun game(group: UUID, status: String, startsAt: Instant, minutes: Int): UUID = UUID.randomUUID().also { id ->
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, " +
                "confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Training Group', DATE '2026-10-07', TIME '20:00', 'America/Sao_Paulo', " +
                "TIMESTAMPTZ '$startsAt', $minutes, TIMESTAMPTZ '${startsAt.minus(Duration.ofHours(1))}', " +
                "'Arena Central', 'Rua das Flores 100', 12, '$status', now(), now())",
        )
    }

    private fun state(game: UUID): Pair<String, Long> = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT status::text, version FROM games WHERE id = '$game'").use { result ->
                check(result.next())
                result.getString(1) to result.getLong(2)
            }
        }
    }

    private fun execute(sql: String) { dataSource.connection.use { it.createStatement().use { statement -> statement.execute(sql) } } }

    private companion object { val NOW: Instant = Instant.parse("2026-10-07T23:00:00Z") }
}
