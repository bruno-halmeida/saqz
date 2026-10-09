package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.game.GameScheduleConflictWriteException
import br.com.saqz.groups.application.game.recurrence.GameIdFactory
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeries
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeriesResult
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule
import br.com.saqz.groups.domain.game.recurrence.WeeklySlotRule
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DelegatingDataSource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcOccurrenceMaterializationRepositoryIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    }

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

    @Test fun `one-off game at an occurrence start counts as the occurrence instead of failing the batch`() {
        val fixture = fixture()
        val oneOff = insertOneOffGame(fixture.rule.groupId)

        val result = assertIs<MaterializeWeeklySeriesResult.Success>(fixture.materializer.execute(fixture.rule, DATE))

        assertEquals(12, result.generated)
        assertEquals(11, result.inserted)
        assertEquals(12, int("SELECT count(*) FROM games"))
        assertEquals(1, int("SELECT count(*) FROM games WHERE local_date = DATE '$DATE'"))
        assertEquals("DRAFT", string("SELECT status::text FROM games WHERE id = '$oneOff'"))
        assertEquals(0, int("SELECT count(*) FROM games WHERE id = '$oneOff' AND series_id IS NOT NULL"))
    }

    @Test fun `start taken between the occupancy read and the insert is mapped to a game schedule conflict`() {
        val fixture = fixture()
        val racing = materializer(writingBeforeBatchInsert { insertOneOffGame(fixture.rule.groupId) })

        assertFailsWith<GameScheduleConflictWriteException> { racing.execute(fixture.rule, DATE) }
        assertEquals(1, int("SELECT count(*) FROM games"))
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
        return Fixture(materializer(dataSource), rule, venue)
    }

    private fun materializer(dataSource: DataSource) = MaterializeWeeklySeries(
        object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
        JdbcOccurrenceMaterializationRepository(dataSource),
        GameIdFactory(UUID::randomUUID),
        Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC),
    )

    /** Jogo avulso no horário da primeira ocorrência da série (quarta 07/01 às 19:30 em São Paulo). */
    private fun insertOneOffGame(group: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, " +
                "confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Jogo avulso', DATE '2026-01-07', TIME '19:30', " +
                "'America/Sao_Paulo', TIMESTAMPTZ '2026-01-07 22:30Z', 90, TIMESTAMPTZ '2026-01-07 19:30Z', " +
                "'Arena', 'Rua Central 100', 12, 'DRAFT', now(), now())",
        )
        return id
    }

    /**
     * Outro escritor grava um jogo entre a leitura dos horários ocupados e o INSERT em lote: a única
     * janela em que games_schedule_start_unique ainda dispara e precisa virar GameScheduleConflictWriteException.
     */
    private fun writingBeforeBatchInsert(write: () -> Unit): DataSource = object : DelegatingDataSource(dataSource) {
        override fun getConnection(): Connection {
            val real = super.getConnection()
            return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                if (method.name == "prepareStatement" && (args?.firstOrNull() as? String)?.contains("INSERT INTO games") == true) {
                    write()
                }
                try {
                    method.invoke(real, *(args ?: emptyArray()))
                } catch (failure: InvocationTargetException) {
                    throw failure.targetException
                }
            } as Connection
        }
    }

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
