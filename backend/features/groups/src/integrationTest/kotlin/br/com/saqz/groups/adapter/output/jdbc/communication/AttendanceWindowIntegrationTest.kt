package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.*
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import kotlin.test.*

/**
 * Janela de presença das 24 h (VUL-263): abre uma vez por jogo publicado antes do T-24h, alcança
 * quem não respondeu (dono incluído), cala o push comum do mesmo jogo enquanto dura e nunca vai
 * para o WhatsApp.
 */
class AttendanceWindowIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val service = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), JdbcGroupCommunicationRepository(source))
    private val push = JdbcNotificationPush(source, transaction)

    private val owner = user("Owner")
    private val answered = user("Answered")
    private val silent = user("Silent")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'Vôlei de Quinta', 'America/Sao_Paulo', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        for (member in listOf(owner, answered, silent)) jdbc.sql("""
            INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, 'ATHLETE', 'AVULSO', true, now(), now())
        """).param("g", group).param("u", member).update()
        push.register(owner, UUID.randomUUID(), "owner-device", "IOS")
        push.register(answered, UUID.randomUUID(), "answered-device", "IOS")
        push.register(silent, UUID.randomUUID(), "silent-device", "ANDROID")
        // Vínculo ativo de propósito: é ele que provaria um vazamento da janela para o grupo.
        jdbc.sql("""
            INSERT INTO group_whatsapp_bindings(group_id, whatsapp_jid, invite_code, group_name, instance_jid, enabled, created_by)
            VALUES (:g, '120363000000000000@g.us', 'invite-code', 'Vôlei de Quinta', '5511900000000@s.whatsapp.net', true, :owner)
        """).param("g", group).param("owner", owner).update()
    }

    /** O banco é compartilhado entre os métodos: cada um começa sem jogo, mensagem nem preferência. */
    @BeforeEach fun reset() {
        jdbc.sql("TRUNCATE games CASCADE").update()
        jdbc.sql("TRUNCATE group_messages CASCADE").update()
        jdbc.sql("TRUNCATE group_notification_preferences").update()
    }

    @Test fun `the window opens once and reaches the owner and whoever has not answered`() {
        val game = game()
        answer(game, answered, "CONFIRMED")

        assertEquals(1, service.openAttendanceWindows())
        assertEquals(0, service.openAttendanceWindows())

        assertEquals(setOf(owner, silent), recipients("ATTENDANCE_WINDOW").toSet())
        val sent = drain()
        assertEquals(setOf("owner-device", "silent-device"), sent.map { it.first }.toSet())
        val message = sent.first().second
        assertEquals("ATTENDANCE_WINDOW" to game, message.channel to message.gameId)
        assertEquals(windowEnd(game), message.windowEndsAt)
        assertTrue(message.body.startsWith("Jogo: "), message.body)
        assertTrue(message.body.endsWith("Local: Arena\n1/12 confirmados\n\nConfirme sua presença."), message.body)
        assertEquals(0L, count("notification_whatsapp_group_queue"))
        assertEquals(0L, count("notification_whatsapp_queue"))
    }

    @Test fun `a game published inside the last 24 hours gets no window`() {
        game(publishedInMinutes = -30)

        assertEquals(0, service.openAttendanceWindows())
    }

    @Test fun `a deadline before the 24 hour mark means no window`() {
        game(deadlineInMinutes = -120)

        assertEquals(0, service.openAttendanceWindows())
    }

    @Test fun `the window mutes the reminder push while it lasts and releases it afterwards`() {
        val game = game()
        answer(game, answered, "CONFIRMED")
        service.openAttendanceWindows()
        drain()

        service.remindAutomatically()
        assertEquals(emptyList(), drain().map { it.first })

        jdbc.sql("UPDATE games SET starts_at = now() + interval '21 hours', confirmation_deadline = now() + interval '20 hours' WHERE id = :id")
            .param("id", game).update()
        service.remindAutomatically()
        // O REMINDER é assinado pelo dono e pula quem respondeu: sobra o silent, agora liberado.
        assertEquals(listOf("silent-device"), drain().map { it.first })
    }

    @Test fun `a window push still queued when the window closes is dropped`() {
        val game = game()
        service.openAttendanceWindows()

        jdbc.sql("UPDATE games SET starts_at = now() + interval '21 hours', confirmation_deadline = now() + interval '20 hours' WHERE id = :id")
            .param("id", game).update()

        push.drain(NotificationPushSender { _, _ -> error("closed window must not be pushed") })
        assertEquals(0L, count("notification_push_queue WHERE completed_at IS NULL"))
    }

    @Test fun `opting out of reminders silences the window push but keeps it in the central`() {
        val game = game()
        answer(game, answered, "CONFIRMED")
        service.savePreferences(silent, NotificationPreferences(push = PushPreferences(reminders = false)))

        service.openAttendanceWindows()

        assertEquals(listOf("owner-device"), drain().map { it.first })
        assertTrue(silent in recipients("ATTENDANCE_WINDOW"))
    }

    /** Padrão: começa daqui a 23 h (janela aberta há 1 h), prazo daqui a 20 h, publicado há 2 dias. */
    private fun game(startsInMinutes: Int = 23 * 60, deadlineInMinutes: Int = 20 * 60, publishedInMinutes: Int = -2 * 24 * 60): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, published_at, created_at, updated_at)
            VALUES (:id, :g, 'Vôlei de Quinta', current_date + 1, '19:00', 'America/Sao_Paulo',
                now() + make_interval(mins => :startsIn), 90, now() + make_interval(mins => :deadlineIn),
                'Arena', 'Rua 100', 12, 'PUBLISHED', now() + make_interval(mins => :publishedIn), now(), now())
        """).param("id", id).param("g", group).param("startsIn", startsInMinutes)
            .param("deadlineIn", deadlineInMinutes).param("publishedIn", publishedInMinutes).update()
        return id
    }

    private fun answer(game: UUID, member: UUID, status: String) = jdbc.sql("""
        INSERT INTO game_attendance(game_id, group_id, member_user_id, status, responded_at, updated_at, version, member_display_name)
        VALUES (:game, :g, :u, CAST(:status AS attendance_status), now(), now(), 1, 'Answered')
    """).param("game", game).param("g", group).param("u", member).param("status", status).update()

    private fun drain(): List<Pair<String, NotificationPush>> {
        val sent = mutableListOf<Pair<String, NotificationPush>>()
        push.drain(NotificationPushSender { token, message -> sent += token to message; PushDelivery.SENT })
        return sent
    }

    private fun recipients(channel: String): List<UUID> = jdbc.sql("""
        SELECT n.recipient_id FROM group_notifications n
        JOIN group_messages m ON m.id = n.message_id
        WHERE m.channel = CAST(:channel AS group_message_channel) ORDER BY n.recipient_id
    """).param("channel", channel).query { rs, _ -> rs.getObject("recipient_id", UUID::class.java) }.list()

    private fun windowEnd(game: UUID): Instant = jdbc.sql(
        "SELECT least(starts_at - interval '22 hours', confirmation_deadline) FROM games WHERE id = :id",
    ).param("id", game).query { rs, _ -> rs.getTimestamp(1).toInstant() }.single()

    private fun count(table: String) = jdbc.sql("SELECT count(*) FROM $table").query(Long::class.java).single()
    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
