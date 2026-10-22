package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.attendance.AttendanceChargeAdapter
import br.com.saqz.groups.adapter.output.jdbc.attendance.JdbcAttendanceCommandRepository
import br.com.saqz.groups.adapter.output.jdbc.attendance.share.JdbcAttendanceLinkRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.application.attendance.RespondAttendance
import br.com.saqz.groups.application.attendance.share.ResolveAttendanceLink
import br.com.saqz.groups.application.attendance.share.ResolveAttendanceLinkResult
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import java.time.Clock
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.*
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.*

/** O lembrete de 3 h segue até o início do jogo, inclusive com o prazo vencido; o aviso de jogo liberado não. */
class ReminderAfterDeadlineIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val repository = JdbcGroupCommunicationRepository(source)
    private val service = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), repository)
    private val push = JdbcNotificationPush(source, transaction)

    private val owner = user("Owner")
    private val silent = user("Silent")
    private val admin = user("Admin")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'Vôlei de Quinta', 'America/Sao_Paulo', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        for (member in listOf(owner, silent)) jdbc.sql("""
            INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, 'ATHLETE', 'AVULSO', true, now(), now())
        """).param("g", group).param("u", member).update()
        jdbc.sql("""
            INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, 'ADMIN', 'AVULSO', true, now(), now())
        """).param("g", group).param("u", admin).update()
        push.register(silent, UUID.randomUUID(), "silent-device", "ANDROID")
        jdbc.sql("""
            INSERT INTO group_whatsapp_bindings(group_id, whatsapp_jid, invite_code, group_name, instance_jid, enabled, created_by)
            VALUES (:g, '120363000000000000@g.us', 'invite-code', 'Vôlei de Quinta', '5511900000000@s.whatsapp.net', true, :owner)
        """).param("g", group).param("owner", owner).update()
    }

    @BeforeEach fun reset() {
        jdbc.sql("TRUNCATE games CASCADE").update()
        jdbc.sql("TRUNCATE group_messages CASCADE").update()
    }

    @Test fun `candidates include a game past its deadline that has not started, open game candidates do not`() {
        val game = game(deadline = "now() - interval '1 hour'", starts = "now() + interval '5 hours'")

        val candidate = repository.reminderCandidates().single()
        assertEquals(game, candidate.gameId)
        assertNotNull(candidate.game.confirmationDeadline)
        assertEquals(12, candidate.game.capacity)
        assertTrue(repository.openGameCandidates().isEmpty())
    }

    @Test fun `candidates skip started games and keep only the next game of the group`() {
        game(deadline = "now() - interval '3 hours'", starts = "now() - interval '1 hour'")
        val next = game(deadline = "now() - interval '1 hour'", starts = "now() + interval '5 hours'")
        game(deadline = "now() + interval '30 hours'", starts = "now() + interval '31 hours'")

        assertEquals(listOf(next), repository.reminderCandidates().map { it.gameId })
    }

    @Test fun `the reminder after the deadline has no deadline line, reaches the group queue and pushes the updated list text`() {
        game(deadline = "now() - interval '1 hour'", starts = "now() + interval '5 hours'")

        assertEquals(1, service.remindAutomatically())

        val body = jdbc.sql("SELECT body FROM group_messages WHERE channel = 'REMINDER'").query(String::class.java).single()
        assertTrue("\nLocal: Arena\nVagas: 0/12" in body, body)
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM notification_whatsapp_group_queue WHERE status = 'PENDING'")
            .query(Long::class.java).single())
        val sent = mutableListOf<NotificationPush>()
        push.drain(NotificationPushSender { _, message -> sent += message; PushDelivery.SENT })
        assertEquals(listOf("Lista do jogo atualizada. Abra o app para conferir."), sent.map { it.body })
    }

    @Test fun `the reminder before the deadline keeps asking for confirmation`() {
        game(deadline = "now() + interval '2 hours'", starts = "now() + interval '5 hours'")

        service.remindAutomatically()

        val body = jdbc.sql("SELECT body FROM group_messages WHERE channel = 'REMINDER'").query(String::class.java).single()
        assertTrue(Regex("\nPrioridade dos mensalistas até (seg|ter|qua|qui|sex|sáb|dom) \\d{2}h\\d{2}\nVagas: 0/12").containsMatchIn(body), body)
        val sent = mutableListOf<NotificationPush>()
        push.drain(NotificationPushSender { _, message -> sent += message; PushDelivery.SENT })
        assertEquals(listOf(expectedPushBody()), sent.map { it.body })
    }

    @Test fun `after the deadline the link resolves and Vou on a full game waits while Nao vou declines`() {
        val game = game(deadline = "now() - interval '1 hour'", starts = "now() + interval '5 hours'")
        jdbc.sql("UPDATE games SET capacity = 2 WHERE id = :id").param("id", game).update()
        for (name in listOf("Ana Souza", "Bia Lima")) {
            val confirmed = user(name)
            jdbc.sql("""INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
                VALUES (:g, :u, 'ATHLETE', 'AVULSO', true, now(), now())""").param("g", group).param("u", confirmed).update()
            jdbc.sql("""INSERT INTO game_attendance(game_id, group_id, member_user_id, status, responded_at, updated_at, member_display_name)
                VALUES (:game, :g, :u, 'CONFIRMED', now(), now(), :name)""")
                .param("game", game).param("g", group).param("u", confirmed).param("name", name).update()
        }
        service.remindAutomatically()
        val code = jdbc.sql("SELECT code FROM notification_attendance_links").query(String::class.java).single()
        val resolve = ResolveAttendanceLink(transaction, JdbcAttendanceLinkRepository(source), Clock.systemUTC())
        val resolved = assertIs<ResolveAttendanceLinkResult.Success>(resolve.execute(silent, code))
        assertEquals(game, resolved.gameId)
        val respond = RespondAttendance(
            transaction, JdbcAttendanceCommandRepository(source),
            AttendanceChargeAdapter(ChargeTransactions(transaction, JdbcChargeTransactionRepository(source)) { java.time.Instant.now() }),
            { java.time.Instant.now() },
        )

        respond.execute(silent, resolved.groupId, resolved.gameId, intent = AttendanceIntent.CONFIRM)
        assertEquals("WAITLISTED", attendanceOf(game, silent))
        respond.execute(silent, resolved.groupId, resolved.gameId, intent = AttendanceIntent.DECLINE)
        assertEquals("DECLINED", attendanceOf(game, silent))
    }

    @Test fun `manual notice before the deadline asks for confirmation`() {
        val game = game(deadline = "now() + interval '2 hours'", starts = "now() + interval '5 hours'")

        val sent = assertIs<CommunicationResult.Success<GroupMessage>>(service.remind(admin, group, game, UUID.randomUUID())).value

        assertTrue(Regex("\nPrioridade dos mensalistas até (seg|ter|qua|qui|sex|sáb|dom) \\d{2}h\\d{2}\n").containsMatchIn(sent.body), sent.body)
    }

    @Test fun `manual notice after the deadline and before the start has no deadline line`() {
        val game = game(deadline = "now() - interval '1 hour'", starts = "now() + interval '5 hours'")

        val sent = assertIs<CommunicationResult.Success<GroupMessage>>(service.remind(admin, group, game, UUID.randomUUID())).value

        assertTrue("\nLocal: Arena\nVagas: " in sent.body, sent.body)
        assertEquals(game, sent.gameId)
    }

    @Test fun `manual notice after the start is refused`() {
        val game = game(deadline = "now() - interval '3 hours'", starts = "now() - interval '1 hour'")

        assertEquals(CommunicationResult.Failure(CommunicationError.INVALID), service.remind(admin, group, game, UUID.randomUUID()))
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM group_messages WHERE channel = 'REMINDER'").query(Long::class.java).single())
    }

    @Test fun `manual notice for a draft game is refused`() {
        val game = game(deadline = "now() + interval '2 hours'", starts = "now() + interval '5 hours'")
        jdbc.sql("UPDATE games SET status = 'DRAFT' WHERE id = :id").param("id", game).update()

        assertEquals(CommunicationResult.Failure(CommunicationError.INVALID), service.remind(admin, group, game, UUID.randomUUID()))
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM group_messages WHERE channel = 'REMINDER'").query(Long::class.java).single())
    }

    private fun attendanceOf(game: UUID, member: UUID): String = jdbc.sql(
        "SELECT status::text FROM game_attendance WHERE game_id = :game AND member_user_id = :u AND guest_seq = 0",
    ).param("game", game).param("u", member).query(String::class.java).single()

    private fun game(deadline: String, starts: String): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :g, 'Vôlei de Quinta', current_date + 1, '19:00', 'America/Sao_Paulo', $starts, 90,
                $deadline, 'Arena', 'Rua 100', 12, 'PUBLISHED', now(), now())
        """).param("id", id).param("g", group).update()
        return id
    }

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }
    /** "O jogo de quarta 14/10 está fechando a lista…": o dia vem do jogo gravado, no formato dos outros avisos. */
    private fun expectedPushBody(): String {
        val date = jdbc.sql("SELECT local_date FROM games").query { rs, _ -> rs.getObject(1, java.time.LocalDate::class.java) }.list().single()
        return reminderPushBody(date)
    }
}
