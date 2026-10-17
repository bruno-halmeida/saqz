package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGroupCommunicationRepository
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcGameOccurrenceRepository
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcOccurrenceMaterializationRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.GameOpenOnPublish
import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.game.ChangeGameLifecycle
import br.com.saqz.groups.application.game.GameCommandResult
import br.com.saqz.groups.application.game.GameSideEffects
import br.com.saqz.groups.application.game.recurrence.GameIdFactory
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeries
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeriesResult
import br.com.saqz.groups.domain.game.GameMutation
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule
import br.com.saqz.groups.domain.game.recurrence.WeeklySlotRule
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
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

/**
 * DEB-040: a confirmação automática do mensalista foi removida. Grupo com a função ligada e mensalista com a
 * preferência ligada (estado que ainda existe no banco) não ganham presença sozinhos em nenhum caminho: publicar,
 * abrir a janela, materializar a série e o lembrete.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SemConfirmacaoAutomaticaIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun reset() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    }

    @Test
    fun `publicar o jogo nao confirma ninguem sozinho`() {
        val fixture = publishFixture()
        val communication = GroupCommunicationService(
            JdbcTransactionRunner(dataSource), JdbcGroupReadRepository(dataSource), JdbcGroupCommunicationRepository(dataSource),
        )
        val lifecycle = ChangeGameLifecycle(
            JdbcTransactionRunner(dataSource),
            JdbcGameOccurrenceRepository(dataSource),
            GameSideEffects(listOf(GameOpenOnPublish(communication, true))),
            Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(
            GameStatus.PUBLISHED,
            assertIs<GameCommandResult.Success>(
                lifecycle.execute(fixture.owner, fixture.group, fixture.game, 1, GameMutation.PUBLISH),
            ).game.status,
        )

        assertNoAutomaticResponse(fixture.game)
    }

    @Test
    fun `lembrete com jogo aberto nao confirma ninguem e nao cria aviso de confirmacao automatica`() {
        val fixture = publishFixture()
        execute(
            "UPDATE games SET status='PUBLISHED', starts_at=now() + interval '2 days', " +
                "confirmation_deadline=now() + interval '1 day', local_date=current_date + 2 WHERE id='${fixture.game}'",
        )
        val communication = GroupCommunicationService(
            JdbcTransactionRunner(dataSource), JdbcGroupReadRepository(dataSource), JdbcGroupCommunicationRepository(dataSource),
        )

        assertEquals(1, communication.remindAutomatically())

        assertNoAutomaticResponse(fixture.game)
        assertEquals(1, count("SELECT count(*) FROM group_messages WHERE group_id='${fixture.group}' AND channel='REMINDER'"))
    }

    @Test
    fun `materializar a serie nao confirma ninguem`() {
        val fixture = seriesFixture()
        val materializer = MaterializeWeeklySeries(
            DirectTransactionRunner,
            JdbcOccurrenceMaterializationRepository(dataSource),
            GameIdFactory(UUID::randomUUID),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
        )
        val rule = WeeklySeriesRule(
            fixture.group, fixture.series, fixture.revision, "America/Sao_Paulo", DATE,
            slots = listOf(
                WeeklySlotRule(
                    fixture.slot, DayOfWeek.WEDNESDAY, LocalTime.of(19, 30), 90,
                    GameVenueSnapshot(fixture.venue, "Arena Central", "Rua das Flores 100", "Quadra 2"),
                    4, 180, null, "Treino semanal",
                ),
            ),
        )

        assertIs<MaterializeWeeklySeriesResult.Success>(materializer.execute(rule, DATE))

        assertEquals(true, count("SELECT count(*) FROM games WHERE series_id='${fixture.series}'") > 0)
        assertEquals(0, count("SELECT count(*) FROM game_attendance WHERE group_id='${fixture.group}'"))
        assertEquals(0, count("SELECT count(*) FROM attendance_events WHERE group_id='${fixture.group}'"))
        assertEquals(0, count("SELECT count(*) FROM group_messages WHERE group_id='${fixture.group}' AND channel='AUTO_CONFIRMATION'"))
    }

    @Test
    fun `ligar a funcao no grupo ou virar mensalista nao cria aviso`() {
        val fixture = publishFixture()
        execute("UPDATE access_groups SET auto_confirm_enabled=false WHERE id='${fixture.group}'")
        execute("UPDATE access_groups SET auto_confirm_enabled=true WHERE id='${fixture.group}'")
        execute("UPDATE group_memberships SET membership_type='AVULSO' WHERE user_id='${fixture.late}'")
        execute("UPDATE group_memberships SET membership_type='MENSALISTA' WHERE user_id='${fixture.late}'")

        assertEquals(0, count("SELECT count(*) FROM group_messages WHERE group_id='${fixture.group}' AND channel='AUTO_CONFIRMATION'"))
        assertEquals(0, count("SELECT count(*) FROM group_notifications"))
    }

    private fun assertNoAutomaticResponse(game: UUID) {
        assertEquals(0, count("SELECT count(*) FROM game_attendance WHERE game_id='$game'"))
        assertEquals(0, count("SELECT count(*) FROM attendance_events WHERE game_id='$game'"))
        assertEquals(0, count("SELECT count(*) FROM group_messages WHERE channel='AUTO_CONFIRMATION'"))
    }

    private fun publishFixture(): PublishFixture {
        val owner = user("owner")
        val group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,auto_confirm_enabled,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',true,now(),now())")
        member(group, "early", "2026-08-01T10:00:00Z")
        member(group, "middle", "2026-08-02T10:00:00Z")
        val late = member(group, "late", "2026-08-03T10:00:00Z")
        val game = UUID.randomUUID()
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,status,created_at,updated_at) VALUES ('$game','$group','Treino',DATE '2026-08-12',TIME '19:30','America/Sao_Paulo',TIMESTAMPTZ '2026-08-12 22:30Z',90,TIMESTAMPTZ '2026-08-11 22:30Z','Arena','Rua Central 100',4,'DRAFT',now(),now())")
        return PublishFixture(owner, group, game, late)
    }

    private fun seriesFixture(): SeriesFixture {
        val owner = user("series-owner")
        val group = UUID.randomUUID()
        val venue = UUID.randomUUID()
        val series = UUID.randomUUID()
        val revision = UUID.randomUUID()
        val slot = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,auto_confirm_enabled,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',true,now(),now())")
        member(group, "series-early", "2026-08-01T10:00:00Z")
        member(group, "series-middle", "2026-08-02T10:00:00Z")
        execute("INSERT INTO group_venues (id,group_id,name,address,court,created_at,updated_at) VALUES ('$venue','$group','Arena Central','Rua das Flores 100','Quadra 2',now(),now())")
        execute("INSERT INTO game_series (id,lineage_id,group_id,revision_number,zone_id,local_start_date,created_at,updated_at) VALUES ('$revision','$series','$group',1,'America/Sao_Paulo',DATE '$DATE',now(),now())")
        execute("INSERT INTO game_series_slots (series_revision_id,group_id,slot_key,title,weekday,local_time,duration_minutes,venue_id,venue_name,venue_address,venue_court,capacity,confirmation_lead_minutes,created_at) VALUES ('$revision','$group','$slot','Treino semanal',3,TIME '19:30',90,'$venue','Arena Central','Rua das Flores 100','Quadra 2',4,180,now())")
        return SeriesFixture(group, venue, series, revision, slot)
    }

    /** Mensalista com a preferência de confirmação automática ligada, como ficou no banco de produção. */
    private fun member(group: UUID, subject: String, joinedAt: String): UUID {
        val id = user(subject)
        execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type,auto_confirm_enabled) VALUES ('$group','$id','ATHLETE',TIMESTAMPTZ '$joinedAt',TIMESTAMPTZ '$joinedAt','MENSALISTA',true)")
        return id
    }

    private fun user(subject: String): UUID = UUID.randomUUID().also { id ->
        execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-$id',true,'$subject',now(),now())")
    }

    private fun execute(sql: String) { connection().use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun count(sql: String): Int = connection().use { connection ->
        connection.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getInt(1) } }
    }
    private fun connection(): Connection = dataSource.connection

    private object DirectTransactionRunner : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private data class PublishFixture(val owner: UUID, val group: UUID, val game: UUID, val late: UUID)
    private data class SeriesFixture(val group: UUID, val venue: UUID, val series: UUID, val revision: UUID, val slot: UUID)
    private companion object { val DATE = LocalDate.of(2026, 8, 5); val FIXED_NOW = Instant.parse("2026-08-03T12:00:00Z") }
}
