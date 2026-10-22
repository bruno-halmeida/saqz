package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.game.AttendanceHistory
import br.com.saqz.groups.application.game.AttendanceHistoryCursor
import br.com.saqz.groups.application.game.AttendanceHistoryKind
import br.com.saqz.groups.application.game.AttendanceHistoryResult
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DEB-135 (JOGO-012, histórico de respostas do jogo). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoricoRespostasIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var history: AttendanceHistory
    private val base: Instant = Instant.parse("2026-10-21T22:00:00Z")

    private lateinit var owner: UUID
    private lateinit var admin: UUID
    private lateinit var ana: UUID
    private lateinit var carla: UUID
    private lateinit var felipe: UUID
    private lateinit var group: UUID
    private lateinit var game: UUID

    @BeforeAll
    fun startDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations()).dataSource
        history = AttendanceHistory(JdbcAttendanceHistoryRepository(dataSource))
    }

    @BeforeEach
    fun seed() {
        execute("TRUNCATE attendance_events, group_charge_events, group_charges, game_attendance, games, group_memberships, access_groups, access_users CASCADE")
        owner = user("Bruno")
        admin = user("Admin")
        ana = user("Ana Souza")
        carla = user("Carla Dias")
        felipe = user("Felipe Nunes")
        group = newGroup(owner)
        listOf(admin to "ADMIN", ana to "ATHLETE", carla to "ATHLETE", felipe to "ATHLETE").forEach { (u, r) -> membership(group, u, r) }
        game = game(group)
    }

    @Test
    fun `gestor le do mais recente para o mais antigo com tipos, motivo, convidado e quem liberou a vaga`() {
        attendance(ana, "CONFIRMED"); attendance(ana, "CONFIRMED", guestSeq = 1, name = "Duda")
        attendance(carla, "DECLINED"); attendance(felipe, "CONFIRMED")
        event(carla, carla, "SELF", null, "CONFIRMED", at = 1)
        event(ana, ana, "SELF", null, "CONFIRMED", at = 2)
        event(ana, ana, "SELF", null, "CONFIRMED", at = 3, guestSeq = 1)
        event(felipe, felipe, "SELF", null, "WAITLISTED", at = 4)
        // Carla desiste e Felipe sobe na mesma transação (mesmo instante).
        event(felipe, carla, "SYSTEM", "WAITLISTED", "CONFIRMED", at = 5)
        event(carla, carla, "SELF", "CONFIRMED", "DECLINED", at = 5)
        event(ana, owner, "ORGANIZER", "CONFIRMED", "DECLINED", at = 6, reason = "pediu para sair pelo WhatsApp")

        val page = assertIs<AttendanceHistoryResult.Success>(history.list(owner, group, game, null))
        assertEquals(
            listOf(
                AttendanceHistoryKind.ORGANIZER_REMOVED, AttendanceHistoryKind.PROMOTED, AttendanceHistoryKind.GAVE_UP,
                AttendanceHistoryKind.WAITLISTED, AttendanceHistoryKind.GUEST_JOINED, AttendanceHistoryKind.CONFIRMED,
                AttendanceHistoryKind.CONFIRMED,
            ),
            page.entries.map { it.kind },
        )
        val removed = page.entries[0].row
        assertEquals("Ana Souza", removed.memberName)
        assertEquals("Bruno", removed.actorName)
        assertEquals("pediu para sair pelo WhatsApp", removed.reason)
        val promoted = page.entries[1].row
        assertEquals("Felipe Nunes", promoted.memberName)
        assertEquals("Carla Dias", promoted.freedByName)
        val guest = page.entries[4].row
        assertEquals("Ana Souza", guest.memberName)
        assertEquals("Duda", guest.guestName)
        assertEquals(1, guest.guestSeq)
        assertTrue(page.entries.drop(1).all { it.row.reason == null })
        assertNull(page.nextCursor)
        assertIs<AttendanceHistoryResult.Success>(history.list(admin, group, game, null))
    }

    @Test
    fun `subida sem uma saida unica no mesmo instante fica sem nome de quem liberou`() {
        attendance(felipe, "CONFIRMED"); attendance(carla, "DECLINED"); attendance(ana, "DECLINED")
        // Ajuste de vagas: ninguém saiu.
        event(felipe, owner, "SYSTEM", "WAITLISTED", "CONFIRMED", at = 1)
        // Duas saídas no mesmo instante: não dá para saber de quem era a vaga.
        event(carla, carla, "SELF", "CONFIRMED", "DECLINED", at = 2)
        event(ana, ana, "SELF", "CONFIRMED", "DECLINED", at = 2)
        event(admin, carla, "SYSTEM", "WAITLISTED", "CONFIRMED", at = 2)
        val page = assertIs<AttendanceHistoryResult.Success>(history.list(owner, group, game, null))
        assertTrue(page.entries.all { it.row.freedByName == null })
    }

    @Test
    fun `atleta recebe forbidden, estranho e jogo de outro grupo ficam ocultos`() {
        assertEquals(AttendanceHistoryResult.Forbidden, history.list(ana, group, game, null))
        assertEquals(AttendanceHistoryResult.Hidden, history.list(user("Estranho"), group, game, null))
        val other = newGroup(owner)
        assertEquals(AttendanceHistoryResult.Hidden, history.list(owner, other, game, null))
        assertEquals(AttendanceHistoryResult.Hidden, history.list(owner, group, UUID.randomUUID(), null))
    }

    @Test
    fun `jogo sem respostas vem vazio`() {
        val page = assertIs<AttendanceHistoryResult.Success>(history.list(owner, group, game, null))
        assertTrue(page.entries.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test
    fun `120 eventos vem em 50, 50 e 20 sem repetir, com empates de instante`() {
        attendance(ana, "CONFIRMED"); attendance(felipe, "CONFIRMED")
        repeat(60) { i ->
            val confirmed = i % 2 == 0
            event(ana, ana, "SELF", if (i == 0) null else if (confirmed) "DECLINED" else "CONFIRMED", if (confirmed) "CONFIRMED" else "DECLINED", at = i / 3L)
            event(felipe, felipe, "SELF", if (i == 0) null else if (confirmed) "DECLINED" else "CONFIRMED", if (confirmed) "CONFIRMED" else "DECLINED", at = i / 3L)
        }
        val seen = mutableListOf<UUID>()
        val sizes = mutableListOf<Int>()
        var cursor: AttendanceHistoryCursor? = null
        do {
            val page = assertIs<AttendanceHistoryResult.Success>(history.list(owner, group, game, cursor))
            sizes += page.entries.size
            seen += page.entries.map { it.row.id }
            cursor = page.nextCursor
        } while (cursor != null)
        assertEquals(listOf(50, 50, 20), sizes)
        assertEquals(120, seen.toSet().size)
    }

    private fun event(
        member: UUID, actor: UUID, source: String, old: String?, new: String, at: Long,
        reason: String? = null, guestSeq: Int = 0,
    ) = execute(
        "INSERT INTO attendance_events (id,game_id,group_id,member_user_id,actor_user_id,source,old_status,new_status,reason,occurred_at,guest_seq) " +
            "VALUES ('${UUID.randomUUID()}','$game','$group','$member','$actor','$source',${old?.let { "'$it'" } ?: "NULL"},'$new'," +
            "${reason?.let { "'$it'" } ?: "NULL"},TIMESTAMPTZ '${base.plus(at, ChronoUnit.MINUTES)}',$guestSeq)",
    )

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', 'sub-$id', true, '$name', now(), now())",
        )
    }

    private fun newGroup(owner: UUID): UUID = UUID.randomUUID().also { id ->
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES ('$id', '$owner', '${UUID.randomUUID()}', 'Vôlei', 'America/Sao_Paulo', now(), now())",
        )
    }

    private fun membership(group: UUID, user: UUID, role: String) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES ('$group', '$user', '$role', now(), now())",
    )

    private fun game(group: UUID): UUID = UUID.randomUUID().also { id ->
        val startsAt = base.plusSeconds(86_400)
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, " +
                "confirmation_deadline, venue_name, venue_address, capacity, status, published_at, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Treino', DATE '2026-10-22', TIME '20:00', 'America/Sao_Paulo', TIMESTAMPTZ '$startsAt', 90, " +
                "TIMESTAMPTZ '${startsAt.minusSeconds(3600)}', 'Arena', 'Rua Central 100', 12, 'PUBLISHED', TIMESTAMPTZ '$base', now(), now())",
        )
    }

    private fun attendance(member: UUID, status: String, guestSeq: Int = 0, name: String = "Pessoa") = execute(
        "INSERT INTO game_attendance (game_id, group_id, member_user_id, guest_seq, status, responded_at, updated_at, version, member_display_name) " +
            "VALUES ('$game', '$group', '$member', $guestSeq, '$status', now(), now(), 1, '$name')",
    )

    private fun execute(sql: String) {
        dataSource.connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
