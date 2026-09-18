package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.*
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.*

/**
 * O aviso diário de jogo liberado: push/central para quem ainda não respondeu, nunca WhatsApp,
 * e derrubado na entrega se a resposta chegou ou o prazo fechou depois do enfileiramento.
 */
class GameOpenNotificationIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val repository = JdbcGroupCommunicationRepository(source)
    private val service = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), repository)
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
        push.register(answered, UUID.randomUUID(), "answered-device", "IOS")
        push.register(silent, UUID.randomUUID(), "silent-device", "ANDROID")
        // Vínculo ativo de propósito: é ele que provaria um vazamento do canal para o grupo.
        jdbc.sql("""
            INSERT INTO group_whatsapp_bindings(group_id, whatsapp_jid, invite_code, group_name, instance_jid, enabled, created_by)
            VALUES (:g, '120363000000000000@g.us', 'invite-code', 'Vôlei de Quinta', '5511900000000@s.whatsapp.net', true, :owner)
        """).param("g", group).param("owner", owner).update()
    }

    /** O banco é compartilhado entre os métodos: cada um começa sem jogo nem mensagem. */
    @BeforeEach fun reset() {
        jdbc.sql("TRUNCATE games CASCADE").update()
        jdbc.sql("TRUNCATE group_messages CASCADE").update()
    }

    /** O mensalista entra no jogo já confirmado pelo auto-confirm: é este aviso, e só ele, que conta que o jogo abriu. */
    @Test fun `the first announcement reaches the whole group, answered members and owner included`() {
        val game = publishedGame()
        answer(game, answered, "CONFIRMED")

        assertEquals(1, service.announceOpenGames())

        assertEquals(setOf(owner, answered, silent), recipients().toSet())
        assertTrue(body().startsWith("Jogo: "), body())
        assertTrue(body().endsWith("Local: Arena\n\nO jogo está liberado. Confirme sua presença."), body())
        val sent = mutableListOf<Pair<String, NotificationPush>>()
        push.drain(NotificationPushSender { token, message -> sent += token to message; PushDelivery.SENT })
        assertEquals(setOf("answered-device", "silent-device"), sent.map { it.first }.toSet())
        assertEquals("O jogo está liberado. Abra o app para confirmar sua presença.", sent.first().second.body)
        assertEquals(0L, count("notification_whatsapp_queue"))
        assertEquals(0L, count("notification_whatsapp_group_queue"))
    }

    /** Do segundo aviso em diante o canal volta a ser o toque em quem ficou de responder. */
    @Test fun `later announcements only reach the athletes who have not answered`() {
        val game = publishedGame()
        answer(game, answered, "CONFIRMED")
        service.announceOpenGames()
        push.drain(NotificationPushSender { _, _ -> PushDelivery.SENT })

        assertEquals(1, service.announceOpenGames())

        // O dono não respondeu: como qualquer atleta, segue na lista.
        assertEquals(setOf(owner, silent), recipientsOf(latestAnnouncement()).toSet())
        val sent = mutableListOf<String>()
        push.drain(NotificationPushSender { token, _ -> sent += token; PushDelivery.SENT })
        assertEquals(listOf("silent-device"), sent)
    }

    @Test fun `answering after a later announcement drops the queued push`() {
        val game = publishedGame()
        // O primeiro aviso alcança todo mundo; é o segundo que se importa com a resposta.
        service.announceOpenGames()
        push.drain(NotificationPushSender { _, _ -> PushDelivery.SENT })
        service.announceOpenGames()
        assertEquals(3L, count("notification_push_queue WHERE completed_at IS NULL"))

        answer(game, silent, "DECLINED")

        val sent = mutableListOf<String>()
        push.drain(NotificationPushSender { token, _ -> sent += token; PushDelivery.SENT })
        assertEquals(listOf("answered-device"), sent)
        assertEquals(0L, count("notification_push_queue WHERE completed_at IS NULL"))
    }

    @Test fun `deadline closing after the announcement drops the queued push`() {
        val game = publishedGame()
        service.announceOpenGames()

        jdbc.sql("UPDATE games SET confirmation_deadline = now() - interval '1 minute' WHERE id = :id")
            .param("id", game).update()

        push.drain(NotificationPushSender { _, _ -> error("closed game must not be pushed") })
        assertEquals(0L, count("notification_push_queue WHERE completed_at IS NULL"))
    }

    @Test fun `opting out of reminders also silences the daily announcement`() {
        publishedGame()
        service.savePreferences(silent, NotificationPreferences(push = PushPreferences(reminders = false)))

        assertEquals(1, service.announceOpenGames())

        val sent = mutableListOf<String>()
        push.drain(NotificationPushSender { token, _ -> sent += token; PushDelivery.SENT })
        assertEquals(listOf("answered-device"), sent)
        // A central continua registrando o aviso; o que a preferência desliga é o push.
        assertTrue(silent in recipients())
    }

    @Test fun `a second run the same day creates a new message instead of replaying the first`() {
        publishedGame()

        assertEquals(1, service.announceOpenGames())
        assertEquals(1, service.announceOpenGames())

        assertEquals(2L, count("group_messages WHERE channel = 'GAME_OPEN'"))
    }

    private fun publishedGame(): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :g, 'Vôlei de Quinta', current_date + 1, '19:00', 'America/Sao_Paulo', now() + interval '1 day', 90,
                now() + interval '22 hours', 'Arena', 'Rua 100', 12, 'PUBLISHED', now(), now())
        """).param("id", id).param("g", group).update()
        return id
    }

    private fun answer(game: UUID, member: UUID, status: String) = jdbc.sql("""
        INSERT INTO game_attendance(game_id, group_id, member_user_id, status, responded_at, updated_at, version, member_display_name)
        VALUES (:game, :g, :u, CAST(:status AS attendance_status), now(), now(), 1, 'Answered')
    """).param("game", game).param("g", group).param("u", member).param("status", status).update()

    private fun recipients(): List<UUID> = jdbc.sql("""
        SELECT n.recipient_id FROM group_notifications n
        JOIN group_messages m ON m.id = n.message_id
        WHERE m.channel = 'GAME_OPEN' ORDER BY n.recipient_id
    """).query { rs, _ -> rs.getObject("recipient_id", UUID::class.java) }.list()

    private fun recipientsOf(message: UUID): List<UUID> = jdbc.sql(
        "SELECT recipient_id FROM group_notifications WHERE message_id = :id",
    ).param("id", message).query { rs, _ -> rs.getObject("recipient_id", UUID::class.java) }.list()

    private fun latestAnnouncement(): UUID = jdbc.sql(
        "SELECT id FROM group_messages WHERE channel = 'GAME_OPEN' ORDER BY sequence DESC LIMIT 1",
    ).query(UUID::class.java).single()

    private fun body(): String = jdbc.sql("SELECT body FROM group_messages WHERE channel = 'GAME_OPEN'")
        .query(String::class.java).single()

    private fun count(table: String) = jdbc.sql("SELECT count(*) FROM $table").query(Long::class.java).single()
    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
