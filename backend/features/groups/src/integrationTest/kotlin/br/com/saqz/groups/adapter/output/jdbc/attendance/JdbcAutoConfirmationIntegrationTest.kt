package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.game.JdbcGameOccurrenceRepository
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcOccurrenceMaterializationRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.AttendanceChargePort
import br.com.saqz.groups.application.attendance.AutoConfirmAttendance
import br.com.saqz.groups.application.attendance.AutoConfirmationMaterializationPort
import br.com.saqz.groups.application.attendance.AutoConfirmationOptInUpdate
import br.com.saqz.groups.application.attendance.RespondAttendance
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.game.ChangeGameLifecycle
import br.com.saqz.groups.application.game.GameCommandResult
import br.com.saqz.groups.application.game.GameSideEffects
import br.com.saqz.groups.application.game.recurrence.GameIdFactory
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeries
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeriesResult
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.game.GameMutation
import br.com.saqz.groups.domain.game.GameStatus
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAutoConfirmationIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeAll
    fun start() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    @BeforeEach
    fun reset() {
        flyway().clean()
        flyway().migrate()
    }

    @AfterAll
    fun stop() = postgres.stop()

    @Test
    fun `publish confirms opted in mensalistas by entry date and waitlists excess`() {
        val fixture = publishFixture()
        val auto = autoConfirm()
        val lifecycle = ChangeGameLifecycle(
            JdbcTransactionRunner(dataSource),
            JdbcGameOccurrenceRepository(dataSource),
            GameSideEffects(listOf(auto)),
        )

        assertEquals(
            GameStatus.PUBLISHED,
            assertIs<GameCommandResult.Success>(
                lifecycle.execute(fixture.owner, fixture.group, fixture.game, 1, GameMutation.PUBLISH),
            ).game.status,
        )
        assertEquals("CONFIRMED", status(fixture.early, fixture.game))
        assertEquals("CONFIRMED", status(fixture.middle, fixture.game))
        assertEquals("WAITLISTED", status(fixture.late, fixture.game))
        assertEquals(null, status(fixture.optOut, fixture.game))
        assertEquals(null, status(fixture.avulso, fixture.game))
        assertEquals(2, count("SELECT count(*) FROM game_attendance WHERE game_id='${fixture.game}' AND status='CONFIRMED'"))
        assertEquals(1, count("SELECT count(*) FROM game_attendance WHERE game_id='${fixture.game}' AND status='WAITLISTED'"))
        assertEquals(3, count("SELECT count(*) FROM attendance_events WHERE game_id='${fixture.game}' AND source='SYSTEM'"))
        assertEquals(0, count("SELECT count(*) FROM group_charges"))

        val self = RespondAttendance(
            JdbcTransactionRunner(dataSource),
            JdbcAttendanceCommandRepository(dataSource),
            AttendanceChargePort { _, _ -> },
            { FIXED_NOW },
        )
        self.execute(fixture.early, fixture.group, fixture.game, fixture.early, AttendanceIntent.DECLINE)
        assertEquals("DECLINED", status(fixture.early, fixture.game))
        assertEquals(1, count("SELECT count(*) FROM attendance_events WHERE game_id='${fixture.game}' AND member_user_id='${fixture.early}' AND source='SELF'"))
    }

    @Test
    fun `materialization applies the same opt in policy to each occurrence`() {
        val fixture = seriesFixture()
        val auto = autoConfirm()
        val materializer = MaterializeWeeklySeries(
            DirectTransactionRunner,
            JdbcOccurrenceMaterializationRepository(dataSource),
            GameIdFactory(UUID::randomUUID),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
            AutoConfirmationMaterializationPort { occurrences -> auto.applyMaterialized(occurrences) },
        )

        val rule = WeeklySeriesRule(
            fixture.group,
            fixture.series,
            fixture.revision,
            "America/Sao_Paulo",
            DATE,
            slots = listOf(
                WeeklySlotRule(
                    fixture.slot,
                    DayOfWeek.WEDNESDAY,
                    LocalTime.of(19, 30),
                    90,
                    GameVenueSnapshot(fixture.venue, "Arena Central", "Rua das Flores 100", "Quadra 2"),
                    2,
                    180,
                    null,
                    "Treino semanal",
                ),
            ),
        )

        assertIs<MaterializeWeeklySeriesResult.Success>(materializer.execute(rule, DATE))
        assertIs<MaterializeWeeklySeriesResult.Success>(materializer.execute(rule, DATE))
        val game = string("SELECT id::text FROM games WHERE series_id='${fixture.series}' ORDER BY local_date LIMIT 1")
        assertEquals(2, count("SELECT count(*) FROM game_attendance WHERE game_id='$game' AND status='CONFIRMED'"))
        assertEquals(1, count("SELECT count(*) FROM game_attendance WHERE game_id='$game' AND status='WAITLISTED'"))
        assertEquals(3, count("SELECT count(*) FROM attendance_events WHERE game_id='$game' AND source='SYSTEM'"))
    }

    @Test
    fun `disabled group does not erase stored member opt in`() {
        val fixture = publishFixture()
        val repository = JdbcAutoConfirmationRepository(dataSource)
        assertEquals(AutoConfirmationOptInUpdate.Success(true), repository.updateOwnOptIn(fixture.group, fixture.early, true))
        execute("UPDATE access_groups SET auto_confirm_enabled=false WHERE id='${fixture.group}'")

        assertSame(
            AutoConfirmationOptInUpdate.FeatureDisabled,
            repository.updateOwnOptIn(fixture.group, fixture.early, false),
        )
        assertEquals(true, bool("SELECT auto_confirm_enabled FROM group_memberships WHERE user_id='${fixture.early}'"))
    }

    private fun autoConfirm() = AutoConfirmAttendance(
        JdbcTransactionRunner(dataSource),
        JdbcAutoConfirmationRepository(dataSource),
        { FIXED_NOW },
    )

    private fun publishFixture(): PublishFixture {
        val owner = user("owner")
        val group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,auto_confirm_enabled,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',true,now(),now())")
        val early = member(group, "early", "2026-08-01T10:00:00Z", "MENSALISTA", true)
        val middle = member(group, "middle", "2026-08-02T10:00:00Z", "MENSALISTA", true)
        val late = member(group, "late", "2026-08-03T10:00:00Z", "MENSALISTA", true)
        val optOut = member(group, "opt-out", "2026-08-04T10:00:00Z", "MENSALISTA", false)
        val avulso = member(group, "avulso", "2026-08-05T10:00:00Z", "AVULSO", true)
        val game = UUID.randomUUID()
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,status,created_at,updated_at) VALUES ('$game','$group','Treino',DATE '2026-08-12',TIME '19:30','America/Sao_Paulo',TIMESTAMPTZ '2026-08-12 22:30Z',90,TIMESTAMPTZ '2026-08-11 22:30Z','Arena','Rua Central 100',2,'DRAFT',now(),now())")
        return PublishFixture(owner, group, game, early, middle, late, optOut, avulso)
    }

    private fun seriesFixture(): SeriesFixture {
        val owner = user("series-owner")
        val group = UUID.randomUUID()
        val venue = UUID.randomUUID()
        val series = UUID.randomUUID()
        val revision = UUID.randomUUID()
        val slot = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,auto_confirm_enabled,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',true,now(),now())")
        member(group, "series-early", "2026-08-01T10:00:00Z", "MENSALISTA", true)
        member(group, "series-middle", "2026-08-02T10:00:00Z", "MENSALISTA", true)
        member(group, "series-late", "2026-08-03T10:00:00Z", "MENSALISTA", true)
        execute("INSERT INTO group_venues (id,group_id,name,address,court,created_at,updated_at) VALUES ('$venue','$group','Arena Central','Rua das Flores 100','Quadra 2',now(),now())")
        execute("INSERT INTO game_series (id,lineage_id,group_id,revision_number,zone_id,local_start_date,created_at,updated_at) VALUES ('$revision','$series','$group',1,'America/Sao_Paulo',DATE '$DATE',now(),now())")
        execute("INSERT INTO game_series_slots (series_revision_id,group_id,slot_key,title,weekday,local_time,duration_minutes,venue_id,venue_name,venue_address,venue_court,capacity,confirmation_lead_minutes,created_at) VALUES ('$revision','$group','$slot','Treino semanal',3,TIME '19:30',90,'$venue','Arena Central','Rua das Flores 100','Quadra 2',2,180,now())")
        return SeriesFixture(group, venue, series, revision, slot)
    }

    private fun member(group: UUID, subject: String, joinedAt: String, type: String, enabled: Boolean): UUID {
        val id = user(subject)
        execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type,auto_confirm_enabled) VALUES ('$group','$id','ATHLETE',TIMESTAMPTZ '$joinedAt',TIMESTAMPTZ '$joinedAt','$type',$enabled)")
        return id
    }

    private fun user(subject: String): UUID = UUID.randomUUID().also { id ->
        execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-$id',true,'User',now(),now())")
    }

    private fun flyway() = Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).cleanDisabled(false).load()
    private fun execute(sql: String) { connection().use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun count(sql: String): Int = requireNotNull(query(sql) { it.getInt(1) })
    private fun string(sql: String): String = requireNotNull(query(sql) { it.getString(1) })
    private fun status(member: UUID, game: UUID): String? = query("SELECT status FROM game_attendance WHERE member_user_id='$member' AND game_id='$game'", true) { it.getString(1) }
    private fun bool(sql: String): Boolean = requireNotNull(query(sql) { it.getBoolean(1) })
    private fun <T> query(sql: String, nullable: Boolean = false, read: (java.sql.ResultSet) -> T): T? = connection().use { connection ->
        connection.createStatement().use { statement -> statement.executeQuery(sql).use { result -> if (!result.next() && nullable) null else read(result) } }
    }
    private fun connection(): Connection = dataSource.connection

    private object DirectTransactionRunner : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private data class PublishFixture(val owner: UUID, val group: UUID, val game: UUID, val early: UUID, val middle: UUID, val late: UUID, val optOut: UUID, val avulso: UUID)
    private data class SeriesFixture(val group: UUID, val venue: UUID, val series: UUID, val revision: UUID, val slot: UUID)
    private companion object { val DATE = LocalDate.of(2026, 8, 5); val FIXED_NOW = Instant.parse("2026-08-03T12:00:00Z") }
}
