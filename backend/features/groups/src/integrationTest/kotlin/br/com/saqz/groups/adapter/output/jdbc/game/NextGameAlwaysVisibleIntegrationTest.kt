package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGroupCommunicationRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.group.settings.JdbcGroupScheduleRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.GameOpenOnPublish
import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.groups.application.game.ChangeGameLifecycle
import br.com.saqz.groups.application.game.CreateGame
import br.com.saqz.groups.application.game.GameCommandResult
import br.com.saqz.groups.application.game.GameListResult
import br.com.saqz.groups.application.game.GameReadResult
import br.com.saqz.groups.application.game.GameSideEffects
import br.com.saqz.groups.application.game.GetGame
import br.com.saqz.groups.application.game.ListGames
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeries
import br.com.saqz.groups.application.game.series.ApplySeriesBoundary
import br.com.saqz.groups.application.game.series.ExtendGameSeries
import br.com.saqz.groups.application.game.series.ReleaseSeriesOccurrences
import br.com.saqz.groups.application.game.series.SyncScheduleSeries
import br.com.saqz.groups.domain.game.CreateGameInput
import br.com.saqz.groups.domain.game.GameMutation
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Guarda do DEB-163, o caso real de 02/11: o jogo do horário fixo ficava escondido até faltarem 4 dias, o organizador via "Sem
 * jogo marcado", tentava criar, recebia "Já existe um jogo nesse horário" e o "Ver jogo" dava em "Jogo não encontrado".
 *
 * Três garantias, com banco, rotina de hora em hora e avisos de verdade:
 * - o grupo sempre vê o próximo jogo, publicado, por mais distante que esteja;
 * - jogo da série não avisa ninguém antes de 4 dias da data, em nenhum canal;
 * - o organizador nunca é barrado por um jogo que não consegue abrir.
 *
 * Os avisos olham o `now()` do banco, então aqui o relógio é o real e as datas são relativas a hoje.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NextGameAlwaysVisibleIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var jdbc: JdbcClient
    private val group = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val athlete = UUID.randomUUID()
    private val clock = Clock.systemUTC()
    private val horizon = GameCreationHorizon { Instant.now().plus(Duration.ofDays(60)) }

    @BeforeEach fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
        jdbc = JdbcClient.create(dataSource)
        val venue = UUID.randomUUID()
        for (user in listOf(owner, athlete)) {
            execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$user','user-$user',true,'Pessoa',now(),now())")
        }
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,default_capacity,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','MBJR','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',18,now(),now())")
        execute("INSERT INTO group_venues (id,group_id,name,address,court,created_at,updated_at) VALUES ('$venue','$group','Arena Central','Rua das Flores 100',null,now(),now())")
        execute("UPDATE access_groups SET default_venue_id='$venue' WHERE id='$group'")
        for ((user, role) in listOf(owner to "ADMIN", athlete to "ATHLETE")) {
            execute("INSERT INTO group_memberships (group_id,user_id,role,membership_type,active,created_at,updated_at) VALUES ('$group','$user','$role','AVULSO',true,now(),now())")
        }
    }

    @Test fun `the group always sees the next game, week after week, however far it is`() {
        weeklyGame(daysAhead = 1)
        assertEquals(day(1), nextGameListedTo(owner))

        // Três semanas seguidas: o jogo começa, a rotina roda, e o seguinte já aparece a 7 dias, para o organizador e para o atleta.
        for (week in listOf(1L, 8L, 15L)) {
            gameStarts(day(week), hoursAgo = week)
            routine()
            assertEquals(day(week + 7), nextGameListedTo(owner))
            assertEquals(day(week + 7), nextGameListedTo(athlete))
        }
    }

    @Test fun `a series game outside the notice window reaches nobody on any channel`() {
        attendanceUpdates("ON_CHANGE")
        weeklyGame(daysAhead = 5)
        routine()
        assertEquals(day(5), nextGameListedTo(athlete))

        assertEquals(0, communication().announceOpenGames())
        assertEquals(0, communication().remindAutomatically())
        assertEquals(0, communication().remindOnChanges())

        assertEquals(0, messages())
        assertEquals(0, count("SELECT count(*) FROM notification_whatsapp_group_queue"))
    }

    @Test fun `the notices start on the fourth day before the date and not a day earlier`() {
        attendanceUpdates("ON_CHANGE")
        weeklyGame(daysAhead = 5)
        assertEquals(0, communication().announceOpenGames())

        // Passa um dia: faltam 4.
        execute("UPDATE games SET local_date=local_date-1, starts_at=starts_at-interval '1 day', confirmation_deadline=confirmation_deadline-interval '1 day' WHERE group_id='$group' AND status='PUBLISHED'")

        assertEquals(1, communication().announceOpenGames())
        assertEquals(1, communication().remindAutomatically())
        assertEquals(1, communication().remindOnChanges())
        assertEquals(1, messages("GAME_OPEN"))
        assertEquals(2, messages("REMINDER"))
    }

    @Test fun `a series game published inside the notice window notifies right away`() {
        weeklyGame(daysAhead = 3)

        assertEquals(day(3), nextGameListedTo(athlete))
        assertEquals(1, messages("GAME_OPEN"))
    }

    @Test fun `a one-off game keeps notifying from its publication however far it is`() {
        val game = created(startOf(day(10), "19:00"))

        publish(game)

        assertEquals(1, messages("GAME_OPEN"))
    }

    /** A rotina não pode se perder depois: o avulso no início exato vira o jogo da série, sem duplicar nem falhar. */
    @Test fun `creating a game over a hidden occurrence takes its place and the routine adopts it`() {
        weeklyGame(daysAhead = 1)
        assertEquals("DRAFT", statusOn(day(8)))

        val game = created(startOf(day(8), "20:00"))
        publish(game)
        routine()

        assertEquals(1, gamesOn(day(8)))
        assertEquals("PUBLISHED", statusOn(day(8)))
        assertNotNull(jdbc.sql("SELECT series_id FROM games WHERE id='$game'").query(UUID::class.java).single())
    }

    @Test fun `a game overlapping a hidden occurrence with another start also takes its place and it does not come back`() {
        weeklyGame(daysAhead = 1)

        val game = created(startOf(day(8), "20:30"))
        publish(game)
        routine()

        assertEquals(1, gamesOn(day(8)))
        assertEquals("20:30", jdbc.sql("SELECT to_char(local_time,'HH24:MI') FROM games WHERE group_id='$group' AND local_date='${day(8)}'").query(String::class.java).single())
    }

    /** Vale para qualquer semana: só o próximo jogo fica publicado, e os seguintes não podem virar parede invisível. */
    @Test fun `hidden occurrences further ahead do not block the organizer either`() {
        weeklyGame(daysAhead = 1)

        for (week in listOf(15L, 22L)) assertEquals(1, gamesOn(day(week)))
        for (week in listOf(15L, 22L)) assertIs<GameCommandResult.Success>(create(startOf(day(week), "20:00")))
    }

    @Test fun `a conflict always points to a game the organizer can open`() {
        weeklyGame(daysAhead = 1)

        val conflict = assertIs<GameCommandResult.ScheduleConflict>(create(startOf(day(1), "20:00")))

        assertIs<GameReadResult.Success>(GetGame(JdbcGameOccurrenceRepository(dataSource)) { emptyMap() }.execute(owner, group, conflict.gameId))
    }

    @Test fun `the edited game is never removed as a hidden occurrence of itself`() {
        weeklyGame(daysAhead = 1)
        val hidden = jdbc.sql("SELECT id FROM games WHERE group_id='$group' AND local_date='${day(8)}'").query(UUID::class.java).single()

        JdbcGameOccurrenceRepository(dataSource).yieldHiddenOccurrences(group, startOf(day(8), "20:00"), 120, excludingGameId = hidden)
        assertEquals(1, gamesOn(day(8)))

        JdbcGameOccurrenceRepository(dataSource).yieldHiddenOccurrences(group, startOf(day(8), "20:00"), 120, excludingGameId = null)
        assertEquals(0, gamesOn(day(8)))
    }

    /** Fim do plano é outra coisa: a recusa tem mensagem própria e a ocorrência escondida fica onde está. */
    @Test fun `a game after the plan end is refused and leaves the hidden occurrence alone`() {
        weeklyGame(daysAhead = 1)
        val planEnd = GameCreationHorizon { startOf(day(8), "00:00") }

        val result = CreateGame(JdbcTransactionRunner(dataSource), JdbcGameOccurrenceRepository(dataSource), clock, planEnd)
            .execute(owner, group, UUID.randomUUID(), input(startOf(day(8), "20:00")))

        assertIs<GameCommandResult.AfterPlanEnd>(result)
        assertEquals("DRAFT", statusOn(day(8)))
    }

    private fun today(): LocalDate = LocalDate.now(ZONE)
    private fun day(daysAhead: Long): LocalDate = today().plusDays(daysAhead)
    private fun startOf(date: LocalDate, time: String): Instant = date.atTime(LocalTime.parse(time)).atZone(ZONE).toInstant()

    /** Horário fixo no dia da semana de hoje + [daysAhead], às 20h, sincronizado como o app faz ao salvar. */
    private fun weeklyGame(daysAhead: Long) {
        execute("INSERT INTO group_regular_slots (id,group_id,weekday,start_time,duration_minutes,position,version,created_at,updated_at) VALUES ('${UUID.randomUUID()}','$group',${day(daysAhead).dayOfWeek.value},TIME '20:00',120,0,1,now(),now())")
        sync().sync(group)
    }

    private fun attendanceUpdates(mode: String) = execute("UPDATE access_groups SET attendance_updates_mode='$mode' WHERE id='$group'")

    /** [hoursAgo] diferente a cada jogo: dois jogos "já começados" na mesma hora bateriam na trava de sobreposição do banco. */
    private fun gameStarts(date: LocalDate, hoursAgo: Long) = execute(
        "UPDATE games SET starts_at=now()-interval '$hoursAgo hours', confirmation_deadline=now()-interval '$hoursAgo hours'-interval '1 hour' WHERE group_id='$group' AND local_date='$date'",
    )

    /** O que o app usa para o topo do grupo: o publicado mais perto que ainda não começou. Nulo é "Sem jogo marcado". */
    private fun nextGameListedTo(actor: UUID): LocalDate? =
        assertIs<GameListResult.Success>(ListGames(JdbcGameOccurrenceRepository(dataSource)) { emptyMap() }.execute(actor, group)).games
            .map { it.game }
            .filter { it.status == GameStatus.PUBLISHED && it.snapshot.startsAt.isAfter(Instant.now()) }
            .minByOrNull { it.snapshot.startsAt }
            ?.snapshot?.localDate

    private fun input(startsAt: Instant): CreateGameInput {
        val local = startsAt.atZone(ZONE)
        return CreateGameInput(
            localDate = local.toLocalDate(),
            localTime = local.toLocalTime(),
            zoneId = ZONE.id,
            startsAt = startsAt,
            durationMinutes = 120,
            confirmationDeadline = startsAt.minus(Duration.ofHours(6)),
        )
    }

    private fun create(startsAt: Instant) = CreateGame(JdbcTransactionRunner(dataSource), JdbcGameOccurrenceRepository(dataSource), clock)
        .execute(owner, group, UUID.randomUUID(), input(startsAt))

    private fun created(startsAt: Instant): UUID = assertIs<GameCommandResult.Success>(create(startsAt)).game.id

    private fun publish(game: UUID) {
        val version = jdbc.sql("SELECT version FROM games WHERE id='$game'").query(Long::class.java).single()
        assertIs<GameCommandResult.Success>(lifecycle().execute(owner, group, game, version, GameMutation.PUBLISH))
    }

    private fun communication() = GroupCommunicationService(
        JdbcTransactionRunner(dataSource), JdbcGroupReadRepository(dataSource), JdbcGroupCommunicationRepository(dataSource), quietHours = false,
    )

    /** O mesmo efeito da publicação em produção: o aviso sai na hora se a regra deixar. */
    private fun lifecycle() = ChangeGameLifecycle(
        JdbcTransactionRunner(dataSource), JdbcGameOccurrenceRepository(dataSource),
        GameSideEffects(listOf(GameOpenOnPublish(communication(), true))), clock,
    )

    private fun release() = ReleaseSeriesOccurrences(JdbcSeriesReleaseRepository(dataSource), lifecycle(), horizon, clock) { what, failure ->
        throw AssertionError("release failed for $what", failure)
    }

    private fun boundary() = ApplySeriesBoundary(JdbcSeriesBoundaryRepository(dataSource), UUID::randomUUID, clock, horizon = horizon)

    private fun sync() = SyncScheduleSeries(
        JdbcGroupScheduleRepository(dataSource), JdbcWeeklySeriesRepository(dataSource), boundary(), UUID::randomUUID, clock,
        horizon = horizon, release = release(),
    )

    /** A rotina de hora em hora inteira, do jeito que roda em produção. */
    private fun routine() {
        val outcome = ExtendGameSeries(
            JdbcWeeklySeriesRepository(dataSource), JdbcGroupScheduleRepository(dataSource),
            MaterializeWeeklySeries(JdbcTransactionRunner(dataSource), JdbcOccurrenceMaterializationRepository(dataSource), UUID::randomUUID, clock, horizon = horizon),
            boundary(), sync(), horizon, UUID::randomUUID, clock, release(),
        ) { what, failure -> throw AssertionError("routine failed at $what", failure) }.run()
        assertEquals(0, outcome.failed)
    }

    private fun messages(channel: String? = null): Int = count(
        "SELECT count(*) FROM group_messages WHERE group_id='$group'" + (channel?.let { " AND channel='$it'" } ?: ""),
    )
    private fun gamesOn(date: LocalDate): Int = count("SELECT count(*) FROM games WHERE group_id='$group' AND local_date='$date' AND status IN ('DRAFT','PUBLISHED')")
    private fun statusOn(date: LocalDate): String? = jdbc.sql("SELECT status::text FROM games WHERE group_id='$group' AND local_date='$date'").query(String::class.java).optional().orElse(null)
    private fun count(sql: String): Int = jdbc.sql(sql).query(Int::class.java).single()
    private fun execute(sql: String) { jdbc.sql(sql).update() }

    private companion object { val ZONE: ZoneId = ZoneId.of("America/Sao_Paulo") }
}
