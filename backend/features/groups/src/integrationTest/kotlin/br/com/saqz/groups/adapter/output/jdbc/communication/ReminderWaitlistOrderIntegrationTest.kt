package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.attendance.AttendanceChargeAdapter
import br.com.saqz.groups.adapter.output.jdbc.attendance.JdbcAttendanceCommandRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.RespondAttendance
import br.com.saqz.groups.application.communication.CommunicationResult
import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.groups.application.communication.MessageChannel
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** DEB-022/DEB-023: o "Avisar" manual sai com o corpo do automático e a espera aparece na ordem em que a promoção acontece. */
class ReminderWaitlistOrderIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val repository = JdbcGroupCommunicationRepository(source)
    private val service = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), repository)
    private val respond = RespondAttendance(
        transaction, JdbcAttendanceCommandRepository(source),
        AttendanceChargeAdapter(ChargeTransactions(transaction, JdbcChargeTransactionRepository(source)) { Instant.now() }),
        { Instant.now() },
    )

    private val owner = user("Dono")
    private val group = UUID.randomUUID()
    private var sequence = 0L

    init {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'Vôlei de Quinta', 'America/Sao_Paulo', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        membership(owner, "ADMIN", "AVULSO")
    }

    @BeforeEach fun reset() {
        jdbc.sql("TRUNCATE games CASCADE").update()
        jdbc.sql("TRUNCATE group_messages CASCADE").update()
        jdbc.sql("UPDATE access_groups SET mensalista_priority = true WHERE id = :g").param("g", group).update()
        sequence = 0
    }

    @Test fun `the manual reminder carries the deadline and the vacancies like the automatic one`() {
        val game = game(deadline = "now() + interval '2 hours'", capacity = 3)
        attend(game, member("Ana", "AVULSO"), "CONFIRMED")
        attend(game, member("Bia", "AVULSO"), "CONFIRMED")

        val manual = service.remind(owner, group, game, UUID.randomUUID()).success().body
        assertEquals(1, service.remindAutomatically())
        val automatic = bodies().filter { it != manual }.ifEmpty { bodies().drop(1) }.single()

        assertTrue(
            Regex("^Jogo: .+\nLocal: Arena\nConfirmações até (seg|ter|qua|qui|sex|sáb|dom) \\d{2}h\\d{2}\nVagas: 2/3\n\n✅ Confirmados:\nAna\nBia$")
                .matches(manual), manual,
        )
        assertEquals(automatic, manual)
    }

    @Test fun `the manual reminder says the list is complete when the game is full`() {
        val game = game(deadline = "now() + interval '2 hours'", capacity = 2)
        attend(game, member("Ana", "AVULSO"), "CONFIRMED")
        attend(game, member("Bia", "AVULSO"), "CONFIRMED")
        attend(game, member("Caio", "AVULSO"), "WAITLISTED")

        val manual = service.remind(owner, group, game, UUID.randomUUID()).success().body

        assertTrue("\nLista completa\n" in manual, manual)
        assertTrue(manual.endsWith("🕒 Lista de espera:\nCaio"), manual)
    }

    @Test fun `the waitlist follows the arrival order when there is no priority`() {
        jdbc.sql("UPDATE access_groups SET mensalista_priority = false WHERE id = :g").param("g", group).update()
        val game = game(deadline = "now() + interval '2 hours'", capacity = 2)
        val holder = member("Zeca", "AVULSO").also { attend(game, it, "CONFIRMED") }
        member("Yara", "AVULSO").also { attend(game, it, "CONFIRMED") }
        val names = listOf("Mara" to "MENSALISTA", "Ana" to "AVULSO", "Zilda" to "MENSALISTA", "Bruno" to "AVULSO")
        names.forEach { (name, type) -> attend(game, member(name, type), "WAITLISTED") }

        assertEquals(listOf("Mara", "Ana", "Zilda", "Bruno"), repository.reminderRoster(group, game).waitlisted)
        assertPromotionFollows(game, holder)
    }

    @Test fun `the waitlist puts monthly members first before the deadline, with guests behind them`() {
        val game = game(deadline = "now() + interval '2 hours'", capacity = 2)
        val holder = member("Zeca", "AVULSO").also { attend(game, it, "CONFIRMED") }
        val host = member("Yara", "AVULSO").also { attend(game, it, "CONFIRMED") }
        attend(game, host, "WAITLISTED", guestSeq = 1, name = "Convidado de Yara")
        attend(game, member("Ana", "AVULSO"), "WAITLISTED")
        attend(game, member("Zilda", "MENSALISTA"), "WAITLISTED")
        attend(game, member("Bruno", "MENSALISTA"), "WAITLISTED")

        assertEquals(
            listOf("Zilda", "Bruno", "Convidado de Yara", "Ana"),
            repository.reminderRoster(group, game).waitlisted,
        )
        assertPromotionFollows(game, holder)
    }

    @Test fun `the waitlist is arrival order only after the deadline`() {
        val game = game(deadline = "now() - interval '1 hour'", capacity = 2)
        member("Zeca", "AVULSO").also { attend(game, it, "CONFIRMED") }
        member("Yara", "AVULSO").also { attend(game, it, "CONFIRMED") }
        attend(game, member("Ana", "AVULSO"), "WAITLISTED")
        attend(game, member("Zilda", "MENSALISTA"), "WAITLISTED")
        attend(game, member("Bruno", "MENSALISTA"), "WAITLISTED")

        assertEquals(listOf("Ana", "Zilda", "Bruno"), repository.reminderRoster(group, game).waitlisted)
        assertEquals(
            listOf("Ana", "Zilda", "Bruno"),
            Regex("🕒 Lista de espera:\n(.*)", RegexOption.DOT_MATCHES_ALL)
                .find(automaticBody(game))!!.groupValues[1].lines(),
        )
    }

    /** Libera uma vaga por vez e confere que quem sobe é sempre o primeiro da lista nominal que o lembrete mostrava. */
    private fun assertPromotionFollows(game: UUID, holder: UUID) {
        val order = repository.reminderRoster(group, game).waitlisted
        val promoted = mutableListOf<String>()
        var leaving = holder
        repeat(order.size) {
            val before = repository.reminderRoster(group, game).waitlisted
            assertIs<br.com.saqz.groups.application.attendance.AttendanceCommandResult.Success>(
                respond.execute(leaving, group, game, intent = AttendanceIntent.DECLINE),
            )
            val confirmed = jdbc.sql(
                "SELECT member_display_name, member_user_id FROM game_attendance " +
                    "WHERE game_id = :game AND status = 'CONFIRMED' AND member_display_name <> 'Yara'",
            ).param("game", game).query { rs, _ -> rs.getString(1) to rs.getObject(2, UUID::class.java) }.single()
            assertEquals(before.first(), confirmed.first)
            promoted += confirmed.first
            leaving = confirmed.second
        }
        assertEquals(order, promoted)
    }

    private fun automaticBody(game: UUID): String {
        service.remindAutomatically()
        return jdbc.sql("SELECT body FROM group_messages WHERE channel = 'REMINDER' AND game_id = :game")
            .param("game", game).query(String::class.java).single()
    }

    private fun bodies() = jdbc.sql("SELECT body FROM group_messages WHERE channel = 'REMINDER'").query(String::class.java).list()

    private fun game(deadline: String, capacity: Int): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :g, 'Vôlei de Quinta', current_date + 1, '19:00', 'America/Sao_Paulo', now() + interval '5 hours', 90,
                $deadline, 'Arena', 'Rua 100', :capacity, 'PUBLISHED', now(), now())
        """).param("id", id).param("g", group).param("capacity", capacity).update()
        return id
    }

    /** Sequência crescente por chegada; só a espera tem sequência, como na aplicação. */
    private fun attend(game: UUID, member: UUID, status: String, guestSeq: Int = 0, name: String? = null) {
        val display = name ?: jdbc.sql("SELECT display_name FROM access_users WHERE id = :id").param("id", member)
            .query(String::class.java).single()
        jdbc.sql("""INSERT INTO game_attendance(game_id, group_id, member_user_id, status, waitlist_sequence, responded_at,
                updated_at, version, member_display_name, guest_seq)
            VALUES (:game, :g, :u, :status, :seq, now(), now(), 1, :name, :guest)""")
            .param("game", game).param("g", group).param("u", member).param("status", status, java.sql.Types.OTHER)
            .param("seq", if (status == "WAITLISTED") ++sequence else null, java.sql.Types.BIGINT)
            .param("name", display).param("guest", guestSeq).update()
        if (status == "WAITLISTED") jdbc.sql("UPDATE games SET waitlist_sequence_allocator = :seq WHERE id = :game")
            .param("seq", sequence).param("game", game).update()
    }

    private fun member(name: String, type: String): UUID = user(name).also { membership(it, "ATHLETE", type) }

    private fun membership(user: UUID, role: String, type: String) {
        jdbc.sql("""INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, :role, :type, true, now(), now())""")
            .param("g", group).param("u", user).param("role", role, java.sql.Types.OTHER).param("type", type, java.sql.Types.OTHER).update()
    }

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }

    private fun <T> CommunicationResult<T>.success(): T = assertIs<CommunicationResult.Success<T>>(this).value
}
