package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.adapter.output.jdbc.group.settings.JdbcGroupScheduleRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeries
import br.com.saqz.groups.application.game.series.ApplySeriesBoundary
import br.com.saqz.groups.application.game.series.ExtendGameSeries
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import br.com.saqz.groups.application.game.series.SyncScheduleSeries
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncScheduleSeriesIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private val group = UUID.randomUUID()

    @BeforeEach fun resetDatabase() {
        until = Instant.parse("2026-10-17T15:00:00Z")
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
        val owner = UUID.randomUUID(); val venue = UUID.randomUUID()
        execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$owner','owner-$owner',true,'Owner',now(),now())")
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Vôlei de quinta','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())")
        execute("INSERT INTO group_venues (id,group_id,name,address,court,created_at,updated_at) VALUES ('$venue','$group','Arena Central','Rua das Flores 100',null,now(),now())")
        execute("UPDATE access_groups SET default_venue_id='$venue' WHERE id='$group'")
    }

    @Test fun `DEB-102 series games copy the group positions and take capacity from their sum`() {
        execute("INSERT INTO group_position_slots (group_id,position,slots) VALUES ('$group','PONTA',2),('$group','CENTRAL',2),('$group','LEVANTADOR',1)")
        slots(4 to "20:00")
        sync(); sync()
        assertEquals(activeGames(), int("SELECT count(*) FROM games WHERE group_id='$group' AND capacity = 5"))
        assertEquals(activeGames() * 3, int("SELECT count(*) FROM game_position_slots WHERE game_id IN (SELECT id FROM games WHERE group_id='$group')"))
    }

    @Test fun `DEB-102 series games in a group without positions keep capacity and have no slots`() {
        slots(4 to "20:00")
        sync(); sync()
        assertEquals(0, int("SELECT count(*) FROM game_position_slots"))
        assertEquals(0, int("SELECT count(*) FROM games WHERE group_id='$group' AND capacity = 5"))
    }

    @Test fun `schedule slots become a series and follow later schedule changes`() {
        // Quinta 2026-09-17, 12:00 em São Paulo: 9h já passou, 20h ainda não.
        slots(4 to "09:00", 4 to "20:00")
        sync(); sync()
        assertEquals(1, int("SELECT count(*) FROM game_series WHERE group_id='$group'"))
        assertEquals(9, activeGames()) // 1 mês: 5 quintas às 20h + 4 às 9h
        assertEquals("2026-09-17 20:00:00", string("SELECT (local_date + local_time)::text FROM games WHERE group_id='$group' ORDER BY starts_at LIMIT 1"))
        assertEquals(0, int("SELECT count(*) FROM games WHERE group_id='$group' AND venue_id IS NULL"))

        // Troca quinta 9h por sábado 10h: vale a partir de agora; quinta 20h mantém os mesmos jogos.
        val kept = string("SELECT id::text FROM games WHERE group_id='$group' AND local_date='2026-09-24' AND local_time='20:00'")
        slots(4 to "20:00", 6 to "10:00")
        sync(); sync()
        assertEquals(2, int("SELECT count(*) FROM game_series WHERE group_id='$group'"))
        assertEquals(0, int("SELECT count(*) FROM games WHERE group_id='$group' AND local_time='09:00' AND status<>'CANCELLED'"))
        assertEquals(5, int("SELECT count(*) FROM games WHERE group_id='$group' AND local_time='10:00' AND status='DRAFT'"))
        assertEquals(kept, string("SELECT id::text FROM games WHERE group_id='$group' AND local_date='2026-09-24' AND local_time='20:00' AND status='DRAFT'"))

        // Jogo que já começou não é tocado por nada disto.
        execute("UPDATE games SET starts_at='2026-09-17T14:00:00Z', confirmation_deadline='2026-09-17T08:00:00Z' WHERE group_id='$group' AND local_date='2026-09-17'")

        // Desliga a recorrência: cancela tudo que ainda não começou.
        slots()
        sync(); sync()
        assertEquals(1, activeGames())

        // Religa: geração nova da série, jogos novos (os cancelados não ressuscitam).
        slots(4 to "21:00")
        sync(); sync()
        assertEquals(2, int("SELECT count(DISTINCT lineage_id) FROM game_series WHERE group_id='$group'"))
        assertEquals(1 + 5, activeGames())

        // Trial expirado sem assinatura: cancela o futuro; ao voltar o plano, a rotina recria.
        until = null
        sync(); sync()
        assertEquals(1, activeGames())
        until = Instant.parse("2026-10-17T15:00:00Z")
        sync(); sync()
        assertEquals(1 + 5, activeGames())
    }

    @Test fun `routine extends open series month by month`() {
        slots(4 to "20:00")
        sync()
        assertEquals(5, activeGames())
        until = Instant.parse("2026-11-17T15:00:00Z")
        extend(); extend()
        assertEquals(9, activeGames())
    }

    @Test fun `published one-off game at a slot time is adopted as the first game of the series`() {
        // "Marcar jogo" com repetição: o jogo avulso já está publicado quando o horário regular chega.
        val oneOff = oneOffGame("2026-09-24", "20:00", "2026-09-24T23:00:00Z")
        val versionBefore = int("SELECT version FROM games WHERE id='$oneOff'")
        slots(4 to "20:00")
        sync(); sync()
        assertEquals(1, int("SELECT count(*) FROM game_series WHERE group_id='$group'"))
        assertEquals(4, activeGames()) // 5 quintas no mês, menos a que o avulso ocupa
        // Mesmo jogo, agora da série: linhagem, horário (slot) e título do horário; o resto fica.
        assertEquals(1, int("SELECT count(*) FROM games WHERE id='$oneOff' AND series_id='${lineage()}' AND slot_key IS NOT NULL AND series_revision_id IS NOT NULL"))
        assertEquals("Vôlei de quinta", string("SELECT title FROM games WHERE id='$oneOff'"))
        assertEquals("PUBLISHED", string("SELECT status::text FROM games WHERE id='$oneOff'"))
        assertEquals(versionBefore + 1, int("SELECT version FROM games WHERE id='$oneOff'"))
        assertEquals(12, int("SELECT capacity FROM games WHERE id='$oneOff'"))
        assertEquals(5, int("SELECT count(*) FROM games WHERE group_id='$group' AND series_id='${lineage()}'"))
        assertEquals(1, int("SELECT count(*) FROM games WHERE group_id='$group' AND local_date='2026-09-24'"))
        // Repetir a sincronização não adota de novo.
        assertEquals(versionBefore + 1, int("SELECT version FROM games WHERE id='$oneOff'"))
    }

    @Test fun `one-off draft at a slot time is not adopted and the series only skips it`() {
        val draft = oneOffGame("2026-09-24", "20:00", "2026-09-24T23:00:00Z", status = "DRAFT")
        slots(4 to "20:00")
        sync(); sync()
        assertEquals(0, int("SELECT count(*) FROM games WHERE id='$draft' AND series_id IS NOT NULL"))
        assertEquals("Jogo extra", string("SELECT title FROM games WHERE id='$draft'"))
        assertEquals(1, int("SELECT count(*) FROM games WHERE group_id='$group' AND local_date='2026-09-24'"))
        assertEquals(5, activeGames()) // o rascunho avulso é DRAFT e conta; a série tem 4
        assertEquals(4, int("SELECT count(*) FROM games WHERE group_id='$group' AND series_id='${lineage()}'"))
    }

    @Test fun `a slot added to an existing series adopts the published one-off at that time`() {
        slots(4 to "20:00")
        sync()
        val oneOff = oneOffGame("2026-09-26", "10:00", "2026-09-26T13:00:00Z")
        val draft = oneOffGame("2026-10-03", "10:00", "2026-10-03T13:00:00Z", status = "DRAFT")
        slots(4 to "20:00", 6 to "10:00")
        sync(); sync()
        // A edição abre uma revisão sucessora; o avulso passa para a série dela.
        assertEquals(2, int("SELECT count(*) FROM game_series WHERE group_id='$group'"))
        assertEquals(1, int("SELECT count(*) FROM games WHERE id='$oneOff' AND series_id='${lineage()}' AND slot_key IS NOT NULL AND status='PUBLISHED' AND title='Vôlei de quinta'"))
        assertEquals(1, int("SELECT count(*) FROM games WHERE group_id='$group' AND local_date='2026-09-26'"))
        // O rascunho avulso do sábado seguinte segue do gestor.
        assertEquals(0, int("SELECT count(*) FROM games WHERE id='$draft' AND series_id IS NOT NULL"))
        assertEquals(1, int("SELECT count(*) FROM games WHERE group_id='$group' AND local_date='2026-10-03'"))
    }

    @Test fun `the monthly routine adopts a published one-off in the new horizon`() {
        slots(4 to "20:00")
        sync()
        val oneOff = oneOffGame("2026-10-22", "20:00", "2026-10-22T23:00:00Z")
        until = Instant.parse("2026-11-17T15:00:00Z")
        val failures = mutableListOf<String>()
        extend { what, _ -> failures += what }
        assertEquals(emptyList(), failures)
        assertEquals(1, int("SELECT count(*) FROM games WHERE id='$oneOff' AND series_id='${lineage()}' AND status='PUBLISHED'"))
        assertEquals(1, int("SELECT count(*) FROM games WHERE group_id='$group' AND local_date='2026-10-22'"))
        assertEquals(9, int("SELECT count(*) FROM games WHERE group_id='$group' AND series_id='${lineage()}'"))
    }

    @Test fun `paused schedule or missing venue generates nothing`() {
        slots(4 to "20:00")
        execute("UPDATE access_groups SET schedule_paused=true WHERE id='$group'")
        sync()
        execute("UPDATE access_groups SET schedule_paused=false, default_venue_id=null WHERE id='$group'")
        sync()
        assertEquals(0, int("SELECT count(*) FROM games WHERE group_id='$group'"))
    }

    private var until: Instant? = Instant.parse("2026-10-17T15:00:00Z")
    private val horizon = GameCreationHorizon { until }
    private fun boundary() = ApplySeriesBoundary(JdbcSeriesBoundaryRepository(dataSource), UUID::randomUUID, CLOCK, horizon = horizon)
    private fun syncer() = SyncScheduleSeries(
        JdbcGroupScheduleRepository(dataSource), JdbcWeeklySeriesRepository(dataSource),
        boundary(), UUID::randomUUID, CLOCK, horizon = horizon,
    )
    private fun sync() = syncer().sync(group)
    private fun extend(onFailure: (String, Throwable) -> Unit = { _, _ -> }) = ExtendGameSeries(
        JdbcWeeklySeriesRepository(dataSource), JdbcGroupScheduleRepository(dataSource),
        MaterializeWeeklySeries(JdbcTransactionRunner(dataSource), JdbcOccurrenceMaterializationRepository(dataSource), UUID::randomUUID, CLOCK, horizon = horizon),
        boundary(), syncer(), horizon, UUID::randomUUID, CLOCK, onFailure = onFailure,
    ).run()

    /** Jogo avulso publicado, fora de qualquer série, como o editor de jogo cria. */
    private fun oneOffGame(date: String, time: String, startsAt: String, status: String = "PUBLISHED"): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,status,created_at,updated_at) " +
                "VALUES ('$id','$group','Jogo extra',DATE '$date',TIME '$time','America/Sao_Paulo','$startsAt',120,TIMESTAMPTZ '$startsAt' - interval '6 hours','Arena Central','Rua das Flores 100',12,'$status',now(),now())",
        )
        return id
    }

    private fun slots(vararg slots: Pair<Int, String>) {
        execute("DELETE FROM group_regular_slots WHERE group_id='$group'")
        slots.forEachIndexed { index, (weekday, time) ->
            execute("INSERT INTO group_regular_slots (id,group_id,weekday,start_time,duration_minutes,position,version,created_at,updated_at) VALUES ('${UUID.randomUUID()}','$group',$weekday,TIME '$time',120,$index,1,now(),now())")
        }
    }
    /** Linhagem da série do grupo (a mesma em todas as revisões da geração atual). */
    private fun lineage() = string("SELECT lineage_id::text FROM game_series WHERE group_id='$group' ORDER BY revision_number DESC LIMIT 1")
    private fun activeGames() = int("SELECT count(*) FROM games WHERE group_id='$group' AND status='DRAFT'")
    private fun execute(sql: String) = dataSource.connection.use { connection: Connection -> connection.createStatement().use { it.execute(sql) } }
    private fun int(sql: String): Int = query(sql) { it.getInt(1) }
    private fun string(sql: String): String = query(sql) { it.getString(1) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = dataSource.connection.use { connection: Connection -> connection.createStatement().use { statement -> statement.executeQuery(sql).use { result -> check(result.next()); read(result) } } }
    private companion object { val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-17T15:00:00Z"), ZoneOffset.UTC) }
}
