package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.link.PublicAttendanceLinkFactory
import br.com.saqz.groups.application.communication.CommunicationResult
import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.groups.application.communication.MessageChannel
import br.com.saqz.groups.application.communication.NotificationWhatsAppGroupSender
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.whatsapp.WhatsAppGroupDirectory
import br.com.saqz.groups.application.whatsapp.WhatsAppGroupInfo
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.net.URI
import java.sql.Types
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Modo da lista no grupo do WhatsApp (V187): o lembrete dos horários segue para central e push em qualquer modo, mas só
 * vai ao grupo do WhatsApp no SCHEDULED; o ON_CHANGE só vai ao grupo. "Avisar" e aviso vão nos dois.
 */
class AttendanceUpdatesModeIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val repository = JdbcGroupCommunicationRepository(source)
    private val service = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), repository)
    private val queue = JdbcNotificationWhatsAppGroup(source, transaction, PublicAttendanceLinkFactory(URI("https://links.saqz.app")))
    private val owner = user("Dono")
    private val group = UUID.randomUUID()
    private var sequence = 0L

    init {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'Vôlei de Quinta', 'America/Sao_Paulo', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        membership(owner, "ADMIN")
        jdbc.sql("""INSERT INTO group_whatsapp_bindings
                (group_id, whatsapp_jid, invite_code, group_name, instance_jid, enabled, created_by)
            VALUES (:g, '120363000000000000@g.us', 'invite', 'Vôlei', '5511999999999@s.whatsapp.net', true, :owner)""")
            .param("g", group).param("owner", owner).update()
    }

    @BeforeEach fun reset() {
        jdbc.sql("TRUNCATE games CASCADE").update()
        jdbc.sql("TRUNCATE group_messages CASCADE").update()
        jdbc.sql("DELETE FROM group_position_slots").update()
        jdbc.sql("UPDATE access_groups SET attendance_updates_mode = 'SCHEDULED', game_system = NULL, promotion_mode = 'FIFO', mensalista_priority = true")
            .update()
        sequence = 0
    }

    @Test fun `the default mode keeps the scheduled reminder in the WhatsApp group and never runs the change pass`() {
        val game = game()
        member("Ana").also { attend(game, it, "CONFIRMED") }
        assertEquals(1, service.remindAutomatically())
        assertEquals(0, service.remindOnChanges())
        assertEquals(listOf("SCHEDULED" to true), reminders())
    }

    @Test fun `ON_CHANGE keeps push and inbox on the schedule and sends only the change messages to the WhatsApp group`() {
        mode("ON_CHANGE")
        val game = game()
        val ana = member("Ana")
        attend(game, ana, "CONFIRMED")
        val silent = member("Bia")

        assertEquals(1, service.remindAutomatically())
        assertEquals(1, service.remindOnChanges())
        assertEquals(0, service.remindOnChanges())
        assertEquals(listOf("SCHEDULED" to false, "ON_CHANGE" to true), reminders())
        assertEquals(listOf(silent), recipients("SCHEDULED"))
        assertEquals(emptyList(), recipients("ON_CHANGE"))
        assertEquals(1, jdbc.sql("SELECT day_count FROM group_attendance_digest WHERE game_id = :g").param("g", game).query(Int::class.java).single())
    }

    @Test fun `the manual reminder and the notice reach the WhatsApp group in both modes`() {
        val game = game()
        for (current in listOf("SCHEDULED", "ON_CHANGE")) {
            mode(current)
            service.remind(owner, group, game, UUID.randomUUID()).success()
            service.publish(owner, group, MessageChannel.NOTICE, UUID.randomUUID(), "Aviso $current").success()
        }
        assertEquals(4L, jdbc.sql("SELECT count(*) FROM notification_whatsapp_group_queue").query(Long::class.java).single())
        assertEquals(listOf<String?>(null, null), reminders().map { it.first })
    }

    @Test fun `a queued message whose origin no longer matches the mode is cancelled on delivery`() {
        mode("ON_CHANGE")
        game().also { attend(it, member("Ana"), "CONFIRMED") }
        service.remindOnChanges()
        mode("SCHEDULED")
        val sent = mutableListOf<String>()
        queue.drain(NotificationWhatsAppGroupSender { _, _, text, _ -> sent += text; WhatsAppDelivery.Accepted }, MemberDirectory)
        assertEquals(emptyList(), sent)
        assertEquals("CANCELLED", jdbc.sql("SELECT status FROM notification_whatsapp_group_queue").query(String::class.java).single())
    }

    @Test fun `a six zero game prints the confirmed numbered up to the capacity and the FIFO waitlist numbered`() {
        jdbc.sql("UPDATE access_groups SET game_system = 'SIX_ZERO', mensalista_priority = false").update()
        val game = game(capacity = 3)
        attend(game, member("Ana"), "CONFIRMED")
        attend(game, member("Caio"), "WAITLISTED")
        attend(game, member("Duda"), "DECLINED")
        val body = service.remind(owner, group, game, UUID.randomUUID()).success().body
        assertTrue(body.endsWith("Vagas: 1/3\n\n✅ Confirmados:\n1. Ana\n2.\n3.\n\n🕒 Lista de espera:\n1. Caio\n\n❌ Fora:\nDuda"), body)
        println("CORPO 6-0:\n$body")
    }

    @Test fun `a five one game prints each list by position and the waitlist without numbers under monthly priority`() {
        jdbc.sql("UPDATE access_groups SET game_system = 'FIVE_ONE'").update()
        jdbc.sql("""INSERT INTO group_position_slots(group_id, position, slots) VALUES
            (:g, 'LEVANTADOR', 1), (:g, 'PONTA', 2), (:g, 'CENTRAL', 2), (:g, 'OPOSTO', 1), (:g, 'LIBERO', 0)""")
            .param("g", group).update()
        val game = game()
        attend(game, member("Ana"), "CONFIRMED", "PONTA")
        attend(game, member("Bia"), "CONFIRMED", "LEVANTADOR")
        attend(game, member("Caio"), "WAITLISTED", "LEVANTADOR")
        attend(game, member("Duda"), "DECLINED", "CENTRAL")
        attend(game, member("Edu"), "DECLINED")
        val body = service.remind(owner, group, game, UUID.randomUUID()).success().body
        assertTrue(
            body.endsWith(
                "Vagas: 2/6\n\n✅ Confirmados:\nLevantador:\n1. Bia\n\nPonta:\n1. Ana\n2.\n\nCentral:\n1.\n2.\n\nOposto:\n1.\n\n" +
                    "🕒 Lista de espera:\nLevantador:\nCaio\n\n❌ Fora:\nCentral:\nDuda\n\nSem posição:\nEdu",
            ),
            body,
        )
        println("CORPO 5-1:\n$body")
    }

    @Test fun `a legacy game keeps the body without numbers`() {
        val game = game(capacity = 3)
        attend(game, member("Ana"), "CONFIRMED")
        attend(game, member("Caio"), "WAITLISTED")
        val body = service.remind(owner, group, game, UUID.randomUUID()).success().body
        assertTrue(body.endsWith("Vagas: 1/3\n\n✅ Confirmados:\nAna\n\n🕒 Lista de espera:\nCaio"), body)
        assertNull(repository.reminderRoster(group, game).layout)
        println("CORPO legado:\n$body")
    }

    private fun mode(value: String) = jdbc.sql("UPDATE access_groups SET attendance_updates_mode = :m").param("m", value).update()

    /** (origem, foi para a fila do grupo do WhatsApp) de cada lembrete, em ordem. */
    private fun reminders() = jdbc.sql("""
        SELECT m.reminder_origin, EXISTS (SELECT 1 FROM notification_whatsapp_group_queue q WHERE q.message_id = m.id) AS queued
        FROM group_messages m WHERE m.channel = 'REMINDER' ORDER BY m.sequence
    """).query { rs, _ -> rs.getString(1) to rs.getBoolean(2) }.list()

    private fun recipients(origin: String) = jdbc.sql("""
        SELECT n.recipient_id FROM group_notifications n JOIN group_messages m ON m.id = n.message_id
        WHERE m.reminder_origin = :o
    """).param("o", origin).query(UUID::class.java).list()

    private fun game(capacity: Int = 6): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :g, 'Vôlei de Quinta', current_date + 1, '19:00', 'America/Sao_Paulo', now() + interval '5 hours', 90,
                now() + interval '2 hours', 'Arena', 'Rua 100', :capacity, 'PUBLISHED', now(), now())
        """).param("id", id).param("g", group).param("capacity", capacity).update()
        return id
    }

    private fun attend(game: UUID, member: UUID, status: String, position: String? = null) {
        jdbc.sql("""INSERT INTO game_attendance(game_id, group_id, member_user_id, status, waitlist_sequence, responded_at,
                updated_at, version, member_display_name, guest_seq, position)
            VALUES (:game, :g, :u, :status, :seq, now(), now(), 1,
                (SELECT display_name FROM access_users WHERE id = :u), 0, CAST(:position AS athlete_position))""")
            .param("game", game).param("g", group).param("u", member).param("status", status, Types.OTHER)
            .param("seq", if (status == "WAITLISTED") ++sequence else null, Types.BIGINT)
            .param("position", position, Types.VARCHAR).update()
    }

    private fun member(name: String): UUID = user(name).also { membership(it, "ATHLETE") }

    private fun membership(user: UUID, role: String) {
        jdbc.sql("""INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, :role, 'AVULSO', true, now(), now())""")
            .param("g", group).param("u", user).param("role", role, Types.OTHER).update()
    }

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }

    private fun <T> CommunicationResult<T>.success(): T = assertIs<CommunicationResult.Success<T>>(this).value

    private object MemberDirectory : WhatsAppGroupDirectory {
        override fun groupInfo(jid: String) = WhatsAppGroupInfo(jid, "Vôlei", emptyList())
        override fun isMember(jid: String) = true
        override fun instanceStatus() = "open"
        override fun inviteInfo(inviteCode: String) = WhatsAppGroupInfo("120363000000000000@g.us", "Vôlei", emptyList())
        override fun join(inviteCode: String) = Unit
    }
}
