package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.MessageChannel
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.assertEquals

/**
 * DEB-064: a central mostra tudo; "Avisos dos grupos", "Mensagens" e "Lembretes de presença" desligados só tiram
 * push e WhatsApp, que seguem as chaves.
 */
class CentralSempreMostraTudoIntegrationTest {
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

    @Test fun `com todas as chaves de central desligadas a central mostra avisos, mensagens, lembretes e promocao`() {
        jdbc.sql(
            """INSERT INTO group_notification_preferences (user_id, notices, messages, reminders, push_notices, push_messages, push_reminders)
            VALUES (:u, false, false, false, false, false, false)""",
        ).param("u", rafa).update()
        val game = game()
        for ((channel, body) in listOf(
            "NOTICE" to "Treino na sexta.", "CHAT" to "Bom dia!", "REMINDER" to "Confirme sua presença.",
            "GAME_OPEN" to "O jogo está liberado.", "AUTO_CONFIRMATION" to "Você agora é administrador de Vôlei de Quinta.",
        )) notify(channel, body, game.takeIf { channel in setOf("REMINDER", "GAME_OPEN") })

        val inbox = repository.inbox(rafa, null).map { it.message.body }

        assertEquals(
            setOf("Treino na sexta.", "Bom dia!", "Confirme sua presença.", "O jogo está liberado.", "Você agora é administrador de Vôlei de Quinta."),
            inbox.toSet(),
        )
        assertEquals(5, inbox.size)
    }

    @Test fun `as chaves de central desligadas nao mudam o push, que segue as chaves de push`() {
        jdbc.sql("INSERT INTO group_notification_preferences (user_id, notices, push_notices) VALUES (:u, false, false)")
            .param("u", rafa).update()
        notify("NOTICE", "Treino na sexta.")
        assertEquals(0, queuedPushes())
        assertEquals(1, repository.inbox(rafa, null).size)

        jdbc.sql("UPDATE group_notification_preferences SET push_notices = true").update()
        notify("NOTICE", "Treino no sábado.")
        assertEquals(1, queuedPushes())
        assertEquals(2, repository.inbox(rafa, null).size)
    }

    @Test fun `o WhatsApp particular segue a chave de cobranca e a central mostra a cobranca`() {
        jdbc.sql("INSERT INTO group_notification_preferences (user_id, reminders, whatsapp_charges) VALUES (:u, false, false)")
            .param("u", rafa).update()
        val charge = charge()
        notify("CHARGE", "Cobrança em aberto.", chargeId = charge)
        assertEquals(0, queuedWhatsApp())
        assertEquals(1, repository.inbox(rafa, null).size)

        jdbc.sql("UPDATE group_notification_preferences SET whatsapp_charges = true").update()
        notify("CHARGE", "Outra cobrança.", chargeId = charge)
        assertEquals(1, queuedWhatsApp())
    }

    @Test fun `o aviso de jogo cancelado tambem aparece com a chave de avisos desligada`() {
        jdbc.sql("INSERT INTO group_notification_preferences (user_id, notices) VALUES (:u, false)").param("u", rafa).update()
        val game = game()
        jdbc.sql(
            """INSERT INTO game_attendance (game_id, group_id, member_user_id, status, responded_at, updated_at, member_display_name)
            VALUES (:game, :g, :m, 'CONFIRMED', now(), now(), 'Rafa')""",
        ).param("game", game).param("g", group).param("m", rafa).update()

        repository.publishGameCancelled(group, owner, game, "O jogo foi cancelado.")

        assertEquals(listOf("O jogo foi cancelado."), repository.inbox(rafa, null).map { it.message.body })
        assertEquals(MessageChannel.NOTICE, repository.inbox(rafa, null).single().message.channel)
    }

    private fun notify(channel: String, body: String, game: UUID? = null, chargeId: UUID? = null) {
        val message = UUID.randomUUID()
        jdbc.sql(
            """INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body, game_id, charge_id)
            VALUES (:id, :g, :owner, 'Dona', CAST(:channel AS group_message_channel), :request, :body, :game, :charge)""",
        ).param("id", message).param("g", group).param("owner", owner).param("channel", channel)
            .param("request", UUID.randomUUID()).param("body", body)
            .param("game", game, java.sql.Types.OTHER).param("charge", chargeId, java.sql.Types.OTHER).update()
        jdbc.sql("INSERT INTO group_notifications (recipient_id, message_id) VALUES (:r, :m)")
            .param("r", rafa).param("m", message).update()
    }

    private fun queuedPushes() = jdbc.sql("SELECT count(*) FROM notification_push_queue").query(Int::class.java).single()

    private fun queuedWhatsApp() = jdbc.sql("SELECT count(*) FROM notification_whatsapp_queue").query(Int::class.java).single()

    private fun game(): UUID = UUID.randomUUID().also { id ->
        jdbc.sql(
            """INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :g, 'Vôlei de Quinta', CURRENT_DATE + 2, '19:00', 'America/Sao_Paulo', now() + interval '48 hours', 90,
                now() + interval '24 hours', 'Arena', 'Rua 100', 12, 'PUBLISHED', now(), now())""",
        ).param("id", id).param("g", group).update()
    }

    private var chargeSequence = 0

    private fun charge(): UUID = UUID.randomUUID().also { id ->
        chargeSequence += 1
        jdbc.sql(
            """INSERT INTO group_charges(id, group_id, member_user_id, kind, billing_month, amount_cents, due_date,
                created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name)
            VALUES (:id, :g, :u, 'MONTHLY', CAST(:month AS date), 7000, '2026-09-10', :a, :a, now(), now(), 'Rafa')""",
        ).param("id", id).param("g", group).param("u", rafa).param("a", owner)
            .param("month", "2026-%02d-01".format(chargeSequence)).update()
    }

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql(
            """INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, verified_phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', '+5511999999999', now(), now())""",
        ).param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
