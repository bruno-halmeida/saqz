package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteStatsRepository
import br.com.saqz.groups.adapter.output.jdbc.attendance.JdbcAttendanceCommandRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeManagementRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.finance.charge.ChargeManagement
import br.com.saqz.groups.application.finance.charge.ChargeListResult
import br.com.saqz.groups.application.finance.charge.ChargeStatusResult
import br.com.saqz.groups.application.game.NoShowResult
import br.com.saqz.groups.application.game.PastGames
import br.com.saqz.groups.application.game.PastGamesResult
import br.com.saqz.groups.domain.finance.charge.ChargeStatus
import br.com.saqz.groups.domain.finance.charge.ChargeStatusCommand
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DEB-128 (JOGO-011, jogos anteriores) e DEB-129 (PRES-016, faltou). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JogosAnterioresIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var pastGames: PastGames
    // Microssegundos, como o Postgres guarda: com nanos (Linux do CI) o fim lido do banco fica antes e o limite de 7 dias falha.
    private var now: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private lateinit var owner: UUID
    private lateinit var admin: UUID
    private lateinit var athlete: UUID
    private lateinit var group: UUID

    @BeforeAll
    fun startDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations()).dataSource
        pastGames = PastGames(JdbcTransactionRunner(dataSource), JdbcPastGamesRepository(dataSource)) { now }
    }

    @BeforeEach
    fun seed() {
        execute("TRUNCATE group_charge_events, group_charges, game_attendance, games, group_memberships, access_groups, access_users CASCADE")
        now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        owner = user("Dono")
        admin = user("Admin")
        athlete = user("Atleta")
        group = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES ('$group', '$owner', '${UUID.randomUUID()}', 'Vôlei', 'America/Sao_Paulo', now(), now())",
        )
        membership(admin, "ADMIN")
        membership(athlete, "ATHLETE")
    }

    @Test
    fun `gestor ve encerrados e cancelados publicados, atleta recebe forbidden e estranho nao ve o grupo`() {
        val completed = game(hoursAgo = 30, status = "COMPLETED")
        val cancelledPublished = game(hoursAgo = 20, status = "CANCELLED")
        game(hoursAgo = 10, status = "CANCELLED", published = false)
        game(hoursAgo = 5, status = "PUBLISHED")
        game(hoursAgo = -48, status = "CANCELLED")

        val page = assertIs<PastGamesResult.Success>(pastGames.list(owner, group, null))
        assertEquals(listOf(cancelledPublished, completed), page.games.map { it.id })
        assertNull(page.nextCursor)
        assertIs<PastGamesResult.Success>(pastGames.list(admin, group, null))
        assertEquals(PastGamesResult.Forbidden, pastGames.list(athlete, group, null))
        assertEquals(PastGamesResult.Hidden, pastGames.list(user("Estranho"), group, null))
    }

    @Test
    fun `45 anteriores vem em 20, 20 e 5 sem repetir e do mais recente para tras`() {
        // Pares com o mesmo início desempatam pelo id.
        val ids = (0 until 45).map { game(hoursAgo = 2L + it / 2, status = "COMPLETED") }
        val seen = mutableListOf<UUID>()
        var cursor: br.com.saqz.groups.application.game.PastGameCursor? = null
        val sizes = mutableListOf<Int>()
        do {
            val page = assertIs<PastGamesResult.Success>(pastGames.list(owner, group, cursor))
            sizes += page.games.size
            seen += page.games.map { it.id }
            cursor = page.nextCursor
        } while (cursor != null)
        assertEquals(listOf(20, 20, 5), sizes)
        assertEquals(45, seen.toSet().size)
        assertEquals(ids.toSet(), seen.toSet())
        val starts = seen.map { id -> query("SELECT (extract(epoch FROM starts_at) * 1000)::bigint FROM games WHERE id='$id'")!!.toLong() }
        assertEquals(starts.sortedDescending(), starts)
    }

    @Test
    fun `linha traz quem foi sem a falta e o acerto pendente`() {
        val played = game(hoursAgo = 3, status = "COMPLETED")
        val other = user("Outro")
        membership(other, "ATHLETE")
        attendance(played, athlete, "CONFIRMED")
        attendance(played, other, "CONFIRMED")
        attendance(played, admin, "DECLINED")
        charge(played, athlete, 2500, "PENDING")
        charge(played, other, 2500, "PENDING")
        assertEquals(NoShowResult.SUCCESS, pastGames.setNoShow(owner, group, played, other, 0, true))

        val row = assertIs<PastGamesResult.Success>(pastGames.list(owner, group, null)).games.single()
        assertEquals(1, row.confirmedCount)
        assertEquals(2, row.pendingChargeCount)
        assertEquals(5000, row.pendingChargeCents)
        assertEquals("Arena", row.venueName)
    }

    @Test
    fun `hasPastGames so para gestor e so com anterior`() {
        assertFalse(pastGames.hasPastGames(group, br.com.saqz.groups.domain.GroupRole.OWNER))
        game(hoursAgo = 3, status = "COMPLETED")
        assertTrue(pastGames.hasPastGames(group, br.com.saqz.groups.domain.GroupRole.ADMIN))
        assertFalse(pastGames.hasPastGames(group, br.com.saqz.groups.domain.GroupRole.ATHLETE))
    }

    @Test
    fun `marcar falta e idempotente, desfaz e nao muda o status`() {
        val played = game(hoursAgo = 3, status = "COMPLETED")
        attendance(played, athlete, "CONFIRMED")

        assertEquals(NoShowResult.SUCCESS, pastGames.setNoShow(admin, group, played, athlete, 0, true))
        val firstMark = query("SELECT no_show_marked_at FROM game_attendance WHERE game_id='$played'")
        now = now.plusSeconds(60)
        assertEquals(NoShowResult.SUCCESS, pastGames.setNoShow(owner, group, played, athlete, 0, true))
        assertEquals(firstMark, query("SELECT no_show_marked_at FROM game_attendance WHERE game_id='$played'"))
        assertEquals("$admin", query("SELECT no_show_marked_by::text FROM game_attendance WHERE game_id='$played'"))
        assertEquals("CONFIRMED", query("SELECT status::text FROM game_attendance WHERE game_id='$played'"))

        assertEquals(NoShowResult.SUCCESS, pastGames.setNoShow(owner, group, played, athlete, 0, false))
        assertEquals(NoShowResult.SUCCESS, pastGames.setNoShow(owner, group, played, athlete, 0, false))
        assertNull(query("SELECT no_show_marked_at FROM game_attendance WHERE game_id='$played'"))
    }

    @Test
    fun `falta so em jogo encerrado, so de confirmado, so do gestor e ate 7 dias do fim`() {
        val startedAt = now.minusSeconds(3 * 3600)
        val played = game(hoursAgo = 3, status = "COMPLETED")
        val upcoming = game(hoursAgo = -24, status = "PUBLISHED")
        attendance(played, athlete, "CONFIRMED")
        attendance(played, admin, "DECLINED")
        attendance(upcoming, athlete, "CONFIRMED")

        assertEquals(NoShowResult.FORBIDDEN, pastGames.setNoShow(athlete, group, played, athlete, 0, true))
        assertEquals(NoShowResult.NOT_CONFIRMED, pastGames.setNoShow(owner, group, played, admin, 0, true))
        assertEquals(NoShowResult.NOT_COMPLETED, pastGames.setNoShow(owner, group, upcoming, athlete, 0, true))
        assertEquals(NoShowResult.HIDDEN, pastGames.setNoShow(owner, group, UUID.randomUUID(), athlete, 0, true))

        // O jogo terminou 1h30 depois do início; o prazo vai até 7 dias depois do fim.
        val end = startedAt.plusSeconds(90 * 60)
        now = end.plusSeconds(7 * 24 * 3600)
        assertEquals(NoShowResult.SUCCESS, pastGames.setNoShow(owner, group, played, athlete, 0, true))
        now = end.plusSeconds(7 * 24 * 3600 + 1)
        assertEquals(NoShowResult.DEADLINE_PASSED, pastGames.setNoShow(owner, group, played, athlete, 0, false))
        assertNotNull(query("SELECT no_show_marked_at FROM game_attendance WHERE game_id='$played'"))
    }

    @Test
    fun `convidado e marcado pelo guest_seq sem tocar no anfitriao`() {
        val played = game(hoursAgo = 3, status = "COMPLETED")
        attendance(played, athlete, "CONFIRMED")
        attendance(played, athlete, "CONFIRMED", guestSeq = 1)

        assertEquals(NoShowResult.SUCCESS, pastGames.setNoShow(owner, group, played, athlete, 1, true))
        assertNull(query("SELECT no_show_marked_at FROM game_attendance WHERE game_id='$played' AND guest_seq=0"))
        assertNotNull(query("SELECT no_show_marked_at FROM game_attendance WHERE game_id='$played' AND guest_seq=1"))
    }

    @Test
    fun `falta sai de jogos realizados, entra a parte nas faltas e so o gestor ve no elenco`() {
        val played = game(hoursAgo = 30, status = "COMPLETED")
        val missed = game(hoursAgo = 3, status = "COMPLETED")
        attendance(played, athlete, "CONFIRMED")
        attendance(missed, athlete, "CONFIRMED")
        assertEquals(NoShowResult.SUCCESS, pastGames.setNoShow(owner, group, missed, athlete, 0, true))

        assertEquals("1", query("SELECT count(*)::text FROM member_played_games WHERE user_id='$athlete'"))
        val stats = assertNotNull(JdbcAthleteStatsRepository(dataSource).find(group, athlete))
        assertEquals(1, stats.games)
        assertEquals(0, stats.absences)
        assertEquals(1, stats.noShows)

        val rosters = JdbcAttendanceCommandRepository(dataSource)
        assertTrue(rosters.roster(owner, group, missed)!!.confirmed.single().noShow)
        assertTrue(rosters.roster(admin, group, missed)!!.confirmed.single().noShow)
        assertFalse(rosters.roster(athlete, group, missed)!!.confirmed.single().noShow)
    }

    @Test
    fun `diaria pendente de quem faltou vem com noShow e o gestor dispensa`() {
        val missed = game(hoursAgo = 3, status = "COMPLETED")
        attendance(missed, athlete, "CONFIRMED")
        val chargeId = charge(missed, athlete, 2500, "PENDING")
        val repository = JdbcChargeManagementRepository(dataSource)
        assertFalse(repository.find(group, chargeId)!!.charge.noShow)

        assertEquals(NoShowResult.SUCCESS, pastGames.setNoShow(owner, group, missed, athlete, 0, true))
        val management = ChargeManagement(JdbcTransactionRunner(dataSource), repository, { now }) { UUID.randomUUID() }
        val listed = assertIs<ChargeListResult.Success>(management.listOrganizer(owner, group)).charges.single()
        assertTrue(listed.charge.noShow)
        assertFalse(listed.charge.withdrew)

        val waived = management.status(owner, group, chargeId, listed.charge.version, ChargeStatusCommand(ChargeStatus.WAIVED))
        assertEquals(ChargeStatus.WAIVED, assertIs<ChargeStatusResult.Success>(waived).value.charge.status)
        assertFalse(repository.find(group, chargeId)!!.charge.noShow)
    }

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', 'sub-$id', true, '$name', now(), now())",
        )
    }

    private fun membership(user: UUID, role: String) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES ('$group', '$user', '$role', now(), now())",
    )

    private fun game(hoursAgo: Long, status: String, published: Boolean = true): UUID = UUID.randomUUID().also { id ->
        val startsAt = now.minusSeconds(hoursAgo * 3600)
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, " +
                "confirmation_deadline, venue_name, venue_address, capacity, status, published_at, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Treino', DATE '2026-08-12', TIME '19:30', 'America/Sao_Paulo', TIMESTAMPTZ '$startsAt', 90, " +
                "TIMESTAMPTZ '${startsAt.minusSeconds(3600)}', 'Arena', 'Rua Central 100', 12, '$status', " +
                "${if (published) "TIMESTAMPTZ '${startsAt.minusSeconds(86_400)}'" else "NULL"}, now(), now())",
        )
    }

    private fun attendance(game: UUID, member: UUID, status: String, guestSeq: Int = 0) = execute(
        "INSERT INTO game_attendance (game_id, group_id, member_user_id, guest_seq, status, responded_at, updated_at, version, member_display_name) " +
            "VALUES ('$game', '$group', '$member', $guestSeq, '$status', now(), now(), 1, 'Pessoa')",
    )

    private fun charge(game: UUID, member: UUID, cents: Long, status: String): UUID = UUID.randomUUID().also { id ->
        execute(
            "INSERT INTO group_charges (id,group_id,member_user_id,kind,game_id,amount_cents,due_date,status," +
                "created_by_user_id,changed_by_user_id,created_at,updated_at,member_display_name,guest_seq) " +
                "VALUES ('$id','$group','$member','GAME','$game',$cents,CURRENT_DATE,'$status','$owner','$owner',now(),now(),'Pessoa',0)",
        )
    }

    private fun query(sql: String): String? = dataSource.connection.use { connection ->
        connection.createStatement().use { statement -> statement.executeQuery(sql).use { rs -> if (rs.next()) rs.getString(1) else null } }
    }

    private fun execute(sql: String) {
        dataSource.connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
