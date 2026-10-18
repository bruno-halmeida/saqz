package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Types
import java.util.UUID
import kotlin.test.assertEquals

/**
 * DEB-065: jogo cancelado e mudança de dia, hora ou local chegam por push mesmo com "Avisos dos grupos" desligado;
 * os outros avisos, inclusive os do mesmo canal, seguem a chave.
 */
class EventosSempreNoPushIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val repository = JdbcGroupCommunicationRepository(source)

    private val owner = user("Dona")
    private val rafa = user("Rafa")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql(
            """INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'Vôlei de Quinta', 'America/Sao_Paulo', now(), now())""",
        ).param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        for (member in listOf(owner, rafa)) jdbc.sql(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES (:g, :u, 'ATHLETE', now(), now())",
        ).param("g", group).param("u", member).update()
    }

    @BeforeEach fun reset() {
        jdbc.sql("TRUNCATE group_messages CASCADE").update()
        jdbc.sql("DELETE FROM group_notification_preferences").update()
    }

    private fun avisosDesligados() {
        jdbc.sql("INSERT INTO group_notification_preferences (user_id, notices, push_notices) VALUES (:u, false, false)")
            .param("u", rafa).update()
    }

    @Test fun `cancelamento vai por push com a chave de avisos desligada`() {
        avisosDesligados()
        val game = game()
        attend(game, rafa)

        repository.publishGameCancelled(group, owner, game, "O jogo foi cancelado.")

        assertEquals(1, queuedPushes(rafa))
        assertEquals(listOf(true), pushEnabled(rafa))
    }

    @Test fun `mudanca de dia, hora ou local vai por push com a chave de avisos desligada`() {
        avisosDesligados()

        notify("AUTO_CONFIRMATION", "O jogo mudou.", eventKind = "GAME_SCHEDULE_CHANGED")

        assertEquals(1, queuedPushes(rafa))
        assertEquals(listOf(true), pushEnabled(rafa))
    }

    @Test fun `os outros avisos do mesmo canal seguem a chave`() {
        avisosDesligados()

        notify("AUTO_CONFIRMATION", "Você agora é administrador de Vôlei de Quinta.")

        assertEquals(0, queuedPushes(rafa))
        assertEquals(listOf(false), pushEnabled(rafa))
    }

    @Test fun `aviso do mural sem jogo segue a chave`() {
        avisosDesligados()

        notify("NOTICE", "Treino na sexta.")

        assertEquals(0, queuedPushes(rafa))
    }

    @Test fun `com a chave ligada tudo segue por push`() {
        notify("NOTICE", "Treino na sexta.")
        notify("AUTO_CONFIRMATION", "O jogo mudou.", eventKind = "GAME_SCHEDULE_CHANGED")

        assertEquals(2, queuedPushes(rafa))
    }

    private fun notify(channel: String, body: String, eventKind: String? = null) {
        val message = UUID.randomUUID()
        jdbc.sql(
            """INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body, event_kind)
            VALUES (:id, :g, :owner, 'Dona', CAST(:channel AS group_message_channel), :request, :body, :kind)""",
        ).param("id", message).param("g", group).param("owner", owner).param("channel", channel)
            .param("request", UUID.randomUUID()).param("body", body).param("kind", eventKind, Types.VARCHAR).update()
        jdbc.sql("INSERT INTO group_notifications (recipient_id, message_id) VALUES (:r, :m)")
            .param("r", rafa).param("m", message).update()
    }

    private fun pushEnabled(recipient: UUID) = jdbc.sql(
        "SELECT push_enabled FROM notification_delivery_context WHERE recipient_id = :r ORDER BY sequence",
    ).param("r", recipient).query(Boolean::class.javaObjectType).list()

    private fun queuedPushes(recipient: UUID) = jdbc.sql(
        "SELECT count(*) FROM notification_push_queue q JOIN group_notifications n ON n.sequence = q.notification_id WHERE n.recipient_id = :r",
    ).param("r", recipient).query(Int::class.java).single()

    private fun attend(game: UUID, member: UUID) {
        jdbc.sql(
            """INSERT INTO game_attendance (game_id, group_id, member_user_id, status, responded_at, updated_at, member_display_name)
            VALUES (:game, :g, :m, 'CONFIRMED', now(), now(), 'Rafa')""",
        ).param("game", game).param("g", group).param("m", member).update()
    }

    private fun game(): UUID = UUID.randomUUID().also { id ->
        jdbc.sql(
            """INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :g, 'Vôlei de Quinta', CURRENT_DATE + 2, '19:00', 'America/Sao_Paulo', now() + interval '48 hours', 90,
                now() + interval '24 hours', 'Arena', 'Rua 100', 12, 'PUBLISHED', now(), now())""",
        ).param("id", id).param("g", group).update()
    }

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql(
            """INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""",
        ).param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
