package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.game.recurrence.GameIdFactory
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeries
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeriesResult
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule
import br.com.saqz.groups.domain.game.recurrence.WeeklySlotRule
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
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcOccurrenceMaterializationRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeAll fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    @BeforeEach fun resetDatabase() {
        flyway().clean()
        flyway().migrate()
    }

    @AfterAll fun stopDatabase() = postgres.stop()

    @Test fun `materializer inserts bounded draft occurrence snapshots`() {
        val fixture = fixture()
        val result = assertIs<MaterializeWeeklySeriesResult.Success>(fixture.materializer.execute(fixture.rule, DATE))

        assertEquals(12, result.inserted)
        assertEquals(12, int("SELECT count(*) FROM games WHERE series_id = '${fixture.rule.seriesId}'"))
        assertEquals(12, int("SELECT count(*) FROM games WHERE status = 'DRAFT'"))
    }

    @Test fun `retry inserts no duplicate occurrence identities`() {
        val fixture = fixture()
        fixture.materializer.execute(fixture.rule, DATE)
        val retry = assertIs<MaterializeWeeklySeriesResult.Success>(fixture.materializer.execute(fixture.rule, DATE))

        assertEquals(0, retry.inserted)
        assertEquals(12, int("SELECT count(*) FROM games"))
    }

    @Test fun `later read replenishes only missing tail of rolling horizon`() {
        val fixture = fixture()
        fixture.materializer.execute(fixture.rule, DATE)
        val replenished = assertIs<MaterializeWeeklySeriesResult.Success>(
            fixture.materializer.execute(fixture.rule, DATE.plusWeeks(4)),
        )

        assertEquals(4, replenished.inserted)
        assertEquals(16, int("SELECT count(*) FROM games"))
    }

    @Test fun `multiple same-day slots remain distinct and bounded`() {
        val first = SlotSeed(UUID.randomUUID(), DayOfWeek.WEDNESDAY, LocalTime.of(19, 0))
        val second = SlotSeed(UUID.randomUUID(), DayOfWeek.WEDNESDAY, LocalTime.of(21, 0))
        val fixture = fixture(listOf(first, second))
        val result = assertIs<MaterializeWeeklySeriesResult.Success>(fixture.materializer.execute(fixture.rule, DATE))

        assertEquals(24, result.inserted)
        assertEquals(2, int("SELECT count(*) FROM games WHERE local_date = DATE '$DATE'"))
    }

    @Test fun `gap keeps scheduled local identity and stores advanced resolved instant`() {
        val gapDate = LocalDate.of(2026, 3, 8)
        val gapSlot = SlotSeed(UUID.randomUUID(), DayOfWeek.SUNDAY, LocalTime.of(2, 30))
        val fixture = fixture(listOf(gapSlot), zone = "America/New_York", start = gapDate)
        fixture.materializer.execute(fixture.rule, gapDate)

        assertEquals("02:30:00", string("SELECT local_time::text FROM games WHERE local_date = DATE '$gapDate'"))
        assertEquals(
            Instant.parse("2026-03-08T07:30:00Z"),
            instant("SELECT starts_at FROM games WHERE local_date = DATE '$gapDate'"),
        )
    }

    @Test fun `venue changes cannot rewrite materialized game snapshot`() {
        val fixture = fixture()
        fixture.materializer.execute(fixture.rule, DATE)
        execute("UPDATE group_venues SET name = 'Arena Renamed' WHERE id = '${fixture.venueId}'")

        assertEquals("Arena Central", string("SELECT venue_name FROM games ORDER BY local_date LIMIT 1"))
    }

    private fun fixture(
        slots: List<SlotSeed> = listOf(SlotSeed(UUID.randomUUID(), DayOfWeek.WEDNESDAY, LocalTime.of(19, 30))),
        zone: String = "America/Sao_Paulo",
        start: LocalDate = DATE,
    ): Fixture {
        val owner = UUID.randomUUID()
        val group = UUID.randomUUID()
        val venue = UUID.randomUUID()
        val lineage = UUID.randomUUID()
        val revision = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$owner', 'owner-$owner', true, 'Owner', now(), now())",
        )
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, created_at, updated_at) " +
                "VALUES ('$group', '$owner', '${UUID.randomUUID()}', 'Group', '$zone', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now())",
        )
        execute(
            "INSERT INTO group_venues (id, group_id, name, address, court, created_at, updated_at) " +
                "VALUES ('$venue', '$group', 'Arena Central', 'Rua das Flores 100', 'Quadra 2', now(), now())",
        )
        execute(
            "INSERT INTO game_series (id, lineage_id, group_id, revision_number, zone_id, local_start_date, created_at, updated_at) " +
                "VALUES ('$revision', '$lineage', '$group', 1, '$zone', DATE '$start', now(), now())",
        )
        slots.forEach { slot ->
            execute(
                "INSERT INTO game_series_slots (series_revision_id, group_id, slot_key, title, weekday, local_time, duration_minutes, " +
                    "venue_id, venue_name, venue_address, venue_court, capacity, confirmation_lead_minutes, game_fee_cents, created_at) VALUES " +
                    "('$revision', '$group', '${slot.key}', 'Treino semanal', ${slot.day.value}, TIME '${slot.time}', 90, '$venue', " +
                    "'Arena Central', 'Rua das Flores 100', 'Quadra 2', 24, 180, 2500, now())",
            )
        }
        val rules = slots.map { slot ->
            WeeklySlotRule(
                slot.key,
                slot.day,
                slot.time,
                90,
                GameVenueSnapshot(venue, "Arena Central", "Rua das Flores 100", "Quadra 2"),
                24,
                180,
                2500,
                "Treino semanal",
            )
        }
        val rule = WeeklySeriesRule(group, lineage, revision, zone, start, slots = rules)
        val materializer = MaterializeWeeklySeries(
            object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
            JdbcOccurrenceMaterializationRepository(dataSource),
            GameIdFactory(UUID::randomUUID),
            Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC),
        )
        return Fixture(materializer, rule, venue)
    }

    private fun flyway() = Flyway.configure()
        .dataSource(dataSource)
        .locations(*allGroupFeatureMigrationLocations())
        .cleanDisabled(false)
        .load()

    private fun execute(sql: String) { connection().use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun int(sql: String): Int = query(sql) { it.getInt(1) }
    private fun string(sql: String): String = query(sql) { it.getString(1) }
    private fun instant(sql: String): Instant = query(sql) { it.getTimestamp(1).toInstant() }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result -> check(result.next()); read(result) }
        }
    }
    private fun connection(): Connection = dataSource.connection

    private data class SlotSeed(val key: UUID, val day: DayOfWeek, val time: LocalTime)
    private data class Fixture(val materializer: MaterializeWeeklySeries, val rule: WeeklySeriesRule, val venueId: UUID)
    private companion object { val DATE: LocalDate = LocalDate.of(2026, 1, 7) }
}
