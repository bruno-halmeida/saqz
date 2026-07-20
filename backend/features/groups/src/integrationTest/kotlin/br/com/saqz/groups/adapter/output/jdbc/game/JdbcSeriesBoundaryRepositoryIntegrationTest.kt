package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.game.recurrence.GameIdFactory
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeries
import br.com.saqz.groups.application.game.series.ApplySeriesBoundary
import br.com.saqz.groups.application.game.series.OnlyThisBoundaryCommand
import br.com.saqz.groups.application.game.series.SeriesBoundaryAction
import br.com.saqz.groups.application.game.series.SeriesBoundaryResult
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.game.GameSnapshot
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
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSeriesBoundaryRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeAll fun startDatabase() { postgres.startAndAwaitJdbc(); dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password) }
    @BeforeEach fun resetDatabase() { flyway().clean(); flyway().migrate() }
    @AfterAll fun stopDatabase() = postgres.stop()

    @Test fun `only this edit detaches selected identity and leaves sibling unchanged`() {
        val fixture = fixture(); val selected = game(fixture, DATE); val sibling = game(fixture, DATE.plusWeeks(1))
        val replacement = snapshot(DATE, "Treino alterado")
        assertEquals(SeriesBoundaryResult.Applied, repository().applyOnlyThis(OnlyThisBoundaryCommand(fixture.group, selected, 1, DATE.minusDays(1), SeriesBoundaryAction.EDIT, replacement)))
        assertEquals("Treino alterado", string("SELECT title FROM games WHERE id='$selected'"))
        assertTrue(bool("SELECT detached_from_series FROM games WHERE id='$selected'"))
        assertEquals("Treino semanal", string("SELECT title FROM games WHERE id='$sibling'"))
        assertEquals(2, int("SELECT count(*) FROM games WHERE series_id='${fixture.lineage}'"))
    }

    @Test fun `only this cancel preserves stable occurrence identity`() {
        val fixture = fixture(); val selected = game(fixture, DATE)
        assertEquals(SeriesBoundaryResult.Applied, repository().applyOnlyThis(OnlyThisBoundaryCommand(fixture.group, selected, 1, DATE.minusDays(1), SeriesBoundaryAction.CANCEL)))
        assertEquals("CANCELLED", string("SELECT status FROM games WHERE id='$selected'"))
        assertTrue(bool("SELECT detached_from_series FROM games WHERE id='$selected'"))
        assertEquals(fixture.lineage, uuid("SELECT series_id FROM games WHERE id='$selected'"))
    }

    @Test fun `only this rejects past and completed occurrences without writes`() {
        val fixture = fixture(); val past = game(fixture, DATE); val completed = game(fixture, DATE.plusWeeks(1), "COMPLETED")
        val repo = repository()
        assertEquals(SeriesBoundaryResult.InvalidBoundary, repo.applyOnlyThis(OnlyThisBoundaryCommand(fixture.group, past, 1, DATE.plusDays(1), SeriesBoundaryAction.CANCEL)))
        assertEquals(SeriesBoundaryResult.InvalidBoundary, repo.applyOnlyThis(OnlyThisBoundaryCommand(fixture.group, completed, 1, DATE, SeriesBoundaryAction.CANCEL)))
        assertEquals("PUBLISHED", string("SELECT status FROM games WHERE id='$past'"))
        assertEquals("COMPLETED", string("SELECT status FROM games WHERE id='$completed'"))
    }

    @Test fun `future edit closes old revision and creates one successor`() {
        val fixture = fixture(); game(fixture, DATE); game(fixture, DATE.plusWeeks(1))
        val successor = successor(fixture, "Treino novo")
        assertEquals(SeriesBoundaryResult.Applied, apply(repository(), fixture, successor))
        assertEquals(DATE.minusDays(1), date("SELECT active_through_date FROM game_series WHERE id='${fixture.revision}'"))
        assertEquals(1, int("SELECT count(*) FROM game_series WHERE previous_revision_id='${fixture.revision}'"))
        assertEquals(12, int("SELECT count(*) FROM games WHERE series_revision_id='${successor.revisionId}'"))
        assertEquals(12, int("SELECT count(*) FROM games WHERE series_id='${fixture.lineage}'"))
        assertEquals("Treino novo", string("SELECT title FROM games WHERE series_revision_id='${successor.revisionId}' ORDER BY local_date LIMIT 1"))
    }

    @Test fun `future edit preserves past and completed snapshots`() {
        val fixture = fixture(start = DATE.minusWeeks(2)); val past = game(fixture, DATE.minusWeeks(1)); val completed = game(fixture, DATE.plusWeeks(1), "COMPLETED"); game(fixture, DATE)
        val successor = successor(fixture, "Treino novo")
        apply(repository(), fixture, successor)
        assertEquals(fixture.revision, uuid("SELECT series_revision_id FROM games WHERE id='$past'"))
        assertEquals("Treino semanal", string("SELECT title FROM games WHERE id='$past'"))
        assertEquals(fixture.revision, uuid("SELECT series_revision_id FROM games WHERE id='$completed'"))
        assertEquals("COMPLETED", string("SELECT status FROM games WHERE id='$completed'"))
    }

    @Test fun `future cancel retains identities and history while cancelling mutable future`() {
        val fixture = fixture(start = DATE.minusWeeks(1)); val past = game(fixture, DATE.minusWeeks(1)); val future = game(fixture, DATE); val completed = game(fixture, DATE.plusWeeks(1), "COMPLETED")
        val successor = successor(fixture, "Treino semanal")
        assertEquals(SeriesBoundaryResult.Applied, apply(repository(), fixture, successor, SeriesBoundaryAction.CANCEL))
        assertEquals("PUBLISHED", string("SELECT status FROM games WHERE id='$past'"))
        assertEquals("CANCELLED", string("SELECT status FROM games WHERE id='$future'"))
        assertEquals("COMPLETED", string("SELECT status FROM games WHERE id='$completed'"))
        assertEquals(fixture.lineage, uuid("SELECT series_id FROM games WHERE id='$future'"))
    }

    @Test fun `retry is replay and creates no duplicate revision or occurrence`() {
        val fixture = fixture(); game(fixture, DATE); val successor = successor(fixture, "Treino novo"); val repo = repository()
        assertEquals(SeriesBoundaryResult.Applied, apply(repo, fixture, successor))
        val count = int("SELECT count(*) FROM games")
        assertEquals(SeriesBoundaryResult.Replay, apply(repo, fixture, successor))
        assertEquals(2, int("SELECT count(*) FROM game_series WHERE lineage_id='${fixture.lineage}'"))
        assertEquals(count, int("SELECT count(*) FROM games"))
    }

    @Test fun `injected failure rolls back close successor slots and occurrences`() {
        val fixture = fixture(); game(fixture, DATE); val successor = successor(fixture, "Treino novo")
        val repo = repository(SeriesBoundaryFailureInjector { error("injected") })
        assertFailsWith<IllegalStateException> { apply(repo, fixture, successor) }
        assertEquals(1, int("SELECT count(*) FROM game_series WHERE lineage_id='${fixture.lineage}'"))
        assertEquals(1, int("SELECT version FROM game_series WHERE id='${fixture.revision}'"))
        assertEquals(1, int("SELECT count(*) FROM games"))
    }

    @Test fun `concurrent future edits serialize to one successor`() {
        val fixture = fixture(); game(fixture, DATE)
        val first = successor(fixture, "Primeiro"); val second = first.copy(revisionId = UUID.randomUUID())
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = listOf(first, second).map { rule -> pool.submit<SeriesBoundaryResult> { apply(repository(), fixture, rule) } }.map { it.get() }
            assertEquals(1, results.count { it == SeriesBoundaryResult.Applied })
            assertEquals(1, results.count { it == SeriesBoundaryResult.VersionConflict })
            assertEquals(2, int("SELECT count(*) FROM game_series WHERE lineage_id='${fixture.lineage}'"))
            assertEquals(int("SELECT count(*) FROM games"), int("SELECT count(DISTINCT (series_id,local_date,slot_key)) FROM games"))
        } finally { pool.shutdownNow() }
    }

    private fun apply(repository: JdbcSeriesBoundaryRepository, fixture: Fixture, successor: WeeklySeriesRule, action: SeriesBoundaryAction = SeriesBoundaryAction.EDIT) =
        ApplySeriesBoundary(repository, UUID::randomUUID, CLOCK).thisAndFuture(fixture.group, fixture.revision, 1, successor, 2, DATE, action)

    private fun repository(injector: SeriesBoundaryFailureInjector = SeriesBoundaryFailureInjector {}) = JdbcSeriesBoundaryRepository(dataSource, injector)

    private fun fixture(start: LocalDate = DATE): Fixture {
        val owner = UUID.randomUUID(); val group = UUID.randomUUID(); val venueId = UUID.randomUUID(); val lineage = UUID.randomUUID(); val revision = UUID.randomUUID(); val slotKey = UUID.randomUUID()
        execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$owner','owner-$owner',true,'Owner',now(),now())")
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())")
        execute("INSERT INTO group_venues (id,group_id,name,address,court,created_at,updated_at) VALUES ('$venueId','$group','Arena Central','Rua das Flores 100','Quadra 2',now(),now())")
        execute("INSERT INTO game_series (id,lineage_id,group_id,revision_number,zone_id,local_start_date,created_at,updated_at) VALUES ('$revision','$lineage','$group',1,'America/Sao_Paulo',DATE '$start',now(),now())")
        execute("INSERT INTO game_series_slots (series_revision_id,group_id,slot_key,title,weekday,local_time,duration_minutes,venue_id,venue_name,venue_address,venue_court,capacity,confirmation_lead_minutes,game_fee_cents,created_at) VALUES ('$revision','$group','$slotKey','Treino semanal',3,TIME '19:30',90,'$venueId','Arena Central','Rua das Flores 100','Quadra 2',24,180,2500,now())")
        return Fixture(group, venueId, lineage, revision, slotKey, start)
    }

    private fun successor(fixture: Fixture, title: String) = WeeklySeriesRule(fixture.group, fixture.lineage, UUID.randomUUID(), "America/Sao_Paulo", DATE, slots = listOf(WeeklySlotRule(fixture.slotKey, DayOfWeek.WEDNESDAY, LocalTime.of(20, 0), 100, GameVenueSnapshot(fixture.venue, "Arena Central", "Rua das Flores 100", "Quadra 2"), 20, 120, 3000, title)))

    private fun game(fixture: Fixture, localDate: LocalDate, status: String = "PUBLISHED"): UUID {
        val id = UUID.randomUUID(); val starts = localDate.atTime(19,30).toInstant(ZoneOffset.ofHours(-3)); val deadline = starts.minusSeconds(10800)
        execute("INSERT INTO games (id,group_id,series_id,series_revision_id,slot_key,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_id,venue_name,venue_address,venue_court,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('$id','${fixture.group}','${fixture.lineage}','${fixture.revision}','${fixture.slotKey}','Treino semanal',DATE '$localDate',TIME '19:30','America/Sao_Paulo',TIMESTAMPTZ '$starts',90,TIMESTAMPTZ '$deadline','${fixture.venue}','Arena Central','Rua das Flores 100','Quadra 2',24,2500,'$status',now(),now())")
        return id
    }

    private fun snapshot(date: LocalDate, title: String): GameSnapshot { val starts = date.atTime(20,0).toInstant(ZoneOffset.ofHours(-3)); return GameSnapshot(title, GameVenueSnapshot(null,"Arena Nova","Avenida Central 500",null), date, LocalTime.of(20,0), IanaTimeZone.from("America/Sao_Paulo"), starts, 100, 20, starts.minusSeconds(7200), 3000, "Alterado") }
    private fun flyway() = Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).cleanDisabled(false).load()
    private fun execute(sql: String) { dataSource.connection.use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun int(sql: String): Int = query(sql) { it.getInt(1) }
    private fun string(sql: String): String = query(sql) { it.getString(1) }
    private fun bool(sql: String): Boolean = query(sql) { it.getBoolean(1) }
    private fun uuid(sql: String): UUID = query(sql) { it.getObject(1, UUID::class.java) }
    private fun date(sql: String): LocalDate = query(sql) { it.getObject(1, LocalDate::class.java) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = dataSource.connection.use { connection: Connection -> connection.createStatement().use { statement -> statement.executeQuery(sql).use { result -> check(result.next()); read(result) } } }
    private data class Fixture(val group: UUID, val venue: UUID, val lineage: UUID, val revision: UUID, val slotKey: UUID, val start: LocalDate)
    private companion object { val DATE: LocalDate = LocalDate.of(2026,1,7); val CLOCK: Clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC) }
}
