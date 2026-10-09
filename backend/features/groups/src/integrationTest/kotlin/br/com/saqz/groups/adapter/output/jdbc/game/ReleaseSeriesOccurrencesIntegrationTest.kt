package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.adapter.output.jdbc.group.settings.JdbcGroupScheduleRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.game.ChangeGameLifecycle
import br.com.saqz.groups.application.game.GameListResult
import br.com.saqz.groups.application.game.GameSideEffectPort
import br.com.saqz.groups.application.game.ListGames
import br.com.saqz.groups.application.game.series.ApplySeriesBoundary
import br.com.saqz.groups.application.game.series.ReleaseSeriesOccurrences
import br.com.saqz.groups.application.game.series.SeriesOccurrenceRelease
import br.com.saqz.groups.application.game.series.SyncScheduleSeries
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReleaseSeriesOccurrencesIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private val group = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private var until: Instant? = Instant.parse("2026-10-17T15:00:00Z")
    private val horizon = GameCreationHorizon { until }

    @BeforeEach fun resetDatabase() {
        until = Instant.parse("2026-10-17T15:00:00Z")
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
        val venue = UUID.randomUUID()
        execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$owner','owner-$owner',true,'Owner',now(),now())")
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Vôlei de quinta','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())")
        execute("INSERT INTO group_venues (id,group_id,name,address,court,created_at,updated_at) VALUES ('$venue','$group','Arena Central','Rua das Flores 100',null,now(),now())")
        execute("UPDATE access_groups SET default_venue_id='$venue' WHERE id='$group'")
        execute("INSERT INTO group_regular_slots (id,group_id,weekday,start_time,duration_minutes,position,version,created_at,updated_at) VALUES ('${UUID.randomUUID()}','$group',4,TIME '20:00',120,0,1,now(),now())")
        execute("INSERT INTO group_regular_slots (id,group_id,weekday,start_time,duration_minutes,position,version,created_at,updated_at) VALUES ('${UUID.randomUUID()}','$group',6,TIME '10:00',120,1,1,now(),now())")
    }

    @Test fun `the series is born with the next game of each slot released and the rest stays hidden`() {
        // Quinta 17/09 12:00 em São Paulo: quinta 20h é hoje e sábado 10h é daqui a 2 dias.
        sync(CLOCK)

        assertEquals(listOf("2026-09-17 20:00", "2026-09-19 10:00"), published())
        assertEquals(2, int("SELECT version FROM games WHERE group_id='$group' AND local_date='2026-09-17'"))
        // O gestor só vê o que foi liberado; os outros rascunhos da série não existem para o app.
        assertEquals(published(), listedTo(owner))
        assertEquals(0, int("SELECT count(*) FROM games WHERE group_id='$group' AND status NOT IN ('DRAFT','PUBLISHED')"))
    }

    @Test fun `the routine releases each slot five days ahead and never twice`() {
        sync(CLOCK)

        // Domingo 20/09: quinta 24/09 entra na janela de 5 dias; sábado 26/09 ainda não.
        val sunday = Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneOffset.UTC)
        release(sunday).releaseAll(); release(sunday).releaseAll()
        assertEquals(listOf("2026-09-17 20:00", "2026-09-19 10:00", "2026-09-24 20:00"), published())

        // Terça 22/09: sábado 26/09 entra na janela.
        release(Clock.fixed(Instant.parse("2026-09-22T15:00:00Z"), ZoneOffset.UTC)).releaseAll()
        assertEquals(listOf("2026-09-17 20:00", "2026-09-19 10:00", "2026-09-24 20:00", "2026-09-26 10:00"), published())
    }

    @Test fun `a one-off draft and a group without a plan are not released`() {
        // Rascunho avulso do gestor no meio da janela: continua dele.
        val oneOff = UUID.randomUUID()
        execute(
            "INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,status,created_at,updated_at) " +
                "VALUES ('$oneOff','$group','Jogo extra',DATE '2026-09-18',TIME '19:00','America/Sao_Paulo','2026-09-18T22:00:00Z',120,TIMESTAMPTZ '2026-09-18T16:00:00Z','Arena Central','Rua das Flores 100',12,'DRAFT',now(),now())",
        )
        sync(CLOCK)
        assertEquals("DRAFT", string("SELECT status::text FROM games WHERE id='$oneOff'"))
        assertEquals(listOf("2026-09-17 20:00", "2026-09-18 19:00", "2026-09-19 10:00"), listedTo(owner))

        // Trial expirado sem assinatura: nada é liberado até o plano voltar.
        until = null
        release(Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneOffset.UTC)).releaseAll()
        assertEquals(listOf("2026-09-17 20:00", "2026-09-19 10:00"), published())
        until = Instant.parse("2026-10-17T15:00:00Z")
        release(Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneOffset.UTC)).releaseAll()
        assertEquals(listOf("2026-09-17 20:00", "2026-09-19 10:00", "2026-09-24 20:00"), published())
    }

    @Test fun `release after a group sync is limited to that group while the routine reaches all`() {
        val other = otherGroup()
        sync(CLOCK, other, release = SeriesOccurrenceRelease.None)
        sync(CLOCK)

        assertEquals(listOf("2026-09-17 20:00", "2026-09-19 10:00"), published())
        assertEquals(0, int("SELECT count(*) FROM games WHERE group_id='$other' AND status='PUBLISHED'"))

        release(CLOCK).releaseAll()
        assertEquals(1, int("SELECT count(*) FROM games WHERE group_id='$other' AND status='PUBLISHED'"))
    }

    /** Outro grupo do mesmo dono, com quinta 20h na agenda. */
    private fun otherGroup(): UUID {
        val other = UUID.randomUUID(); val venue = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$other','$owner','${UUID.randomUUID()}','Outro grupo','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())")
        execute("INSERT INTO group_venues (id,group_id,name,address,court,created_at,updated_at) VALUES ('$venue','$other','Ginásio','Avenida Norte 200',null,now(),now())")
        execute("UPDATE access_groups SET default_venue_id='$venue' WHERE id='$other'")
        execute("INSERT INTO group_regular_slots (id,group_id,weekday,start_time,duration_minutes,position,version,created_at,updated_at) VALUES ('${UUID.randomUUID()}','$other',4,TIME '20:00',120,0,1,now(),now())")
        return other
    }

    private fun release(clock: Clock) = ReleaseSeriesOccurrences(
        JdbcSeriesReleaseRepository(dataSource),
        ChangeGameLifecycle(JdbcTransactionRunner(dataSource), JdbcGameOccurrenceRepository(dataSource), GameSideEffectPort { _, _, _ -> }),
        horizon,
        Duration.ofDays(5),
        clock,
    ) { what, failure -> throw AssertionError("release failed for $what", failure) }

    private fun sync(clock: Clock, groupId: UUID = group, release: SeriesOccurrenceRelease = release(clock)) = SyncScheduleSeries(
        JdbcGroupScheduleRepository(dataSource), JdbcWeeklySeriesRepository(dataSource),
        ApplySeriesBoundary(JdbcSeriesBoundaryRepository(dataSource), UUID::randomUUID, clock, horizon = horizon),
        UUID::randomUUID, clock, horizon = horizon, release = release,
    ).sync(groupId)

    private fun listedTo(actor: UUID): List<String> {
        val repository = JdbcGameOccurrenceRepository(dataSource)
        return assertIs<GameListResult.Success>(ListGames(repository) { emptyMap() }.execute(actor, group))
            .games.map { "${it.game.snapshot.localDate} ${it.game.snapshot.localTime}" }
    }
    private fun published(): List<String> = dataSource.connection.use { connection: Connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT local_date::text || ' ' || to_char(local_time, 'HH24:MI') FROM games WHERE group_id='$group' AND status='${GameStatus.PUBLISHED}' ORDER BY starts_at").use { result ->
                buildList { while (result.next()) add(result.getString(1)) }
            }
        }
    }
    private fun execute(sql: String) = dataSource.connection.use { connection: Connection -> connection.createStatement().use { it.execute(sql) } }
    private fun int(sql: String): Int = query(sql) { it.getInt(1) }
    private fun string(sql: String): String = query(sql) { it.getString(1) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = dataSource.connection.use { connection: Connection -> connection.createStatement().use { statement -> statement.executeQuery(sql).use { result -> check(result.next()); read(result) } } }
    private companion object { val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-17T15:00:00Z"), ZoneOffset.UTC) }
}
