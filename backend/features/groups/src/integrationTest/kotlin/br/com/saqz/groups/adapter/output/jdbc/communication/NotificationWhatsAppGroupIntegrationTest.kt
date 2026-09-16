package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.jdbc.whatsapp.JdbcGroupWhatsAppBindingRepository
import br.com.saqz.groups.adapter.output.link.PublicAttendanceLinkFactory
import br.com.saqz.groups.application.communication.*
import br.com.saqz.groups.application.whatsapp.DirectoryError
import br.com.saqz.groups.application.whatsapp.LinkGroupWhatsApp
import br.com.saqz.groups.application.whatsapp.WhatsAppGroupDirectory
import br.com.saqz.groups.application.whatsapp.WhatsAppGroupInfo
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.net.URI
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.*

class NotificationWhatsAppGroupIntegrationTest {
    private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var transaction: JdbcTransactionRunner
    private lateinit var service: GroupCommunicationService
    private lateinit var queue: JdbcNotificationWhatsAppGroup
    private lateinit var link: LinkGroupWhatsApp
    private lateinit var owner: UUID
    private lateinit var member: UUID
    private lateinit var group: UUID
    private val directory = FakeDirectory()

    @BeforeEach fun reset() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
        jdbc = JdbcClient.create(dataSource)
        transaction = JdbcTransactionRunner(dataSource)
        service = GroupCommunicationService(transaction, JdbcGroupReadRepository(dataSource), JdbcGroupCommunicationRepository(dataSource))
        queue = JdbcNotificationWhatsAppGroup(dataSource, transaction, PublicAttendanceLinkFactory(URI("https://links.saqz.app")))
        link = LinkGroupWhatsApp(transaction, JdbcGroupReadRepository(dataSource), JdbcGroupWhatsAppBindingRepository(dataSource), directory)
        directory.reset()
        owner = user("Owner", "+$OWNER_PHONE")
        member = user("Member")
        group = UUID.randomUUID()
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'Futebol', 'UTC', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        for (athlete in listOf(owner, member)) jdbc.sql("""
            INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, 'ADMIN', 'AVULSO', true, now(), now())
        """).param("g", group).param("u", athlete).update()
    }

    @Test fun `trigger enqueues one job per notice and replays the same request without duplicating`() {
        binding()
        val request = UUID.randomUUID()
        publish(MessageChannel.NOTICE, "Aviso", request)
        publish(MessageChannel.NOTICE, "Aviso", request)
        assertEquals(1L, count("notification_whatsapp_group_queue"))
        publish(MessageChannel.NOTICE, "Segundo aviso")
        assertEquals(2L, count("notification_whatsapp_group_queue"))
        assertEquals(2, countStatus("PENDING"))
    }

    @Test fun `chat and charge never enqueue a group job`() {
        binding()
        publish(MessageChannel.CHAT, "Mensagem da conversa")
        chargeMessage()
        assertEquals(0L, count("notification_whatsapp_group_queue"))
    }

    @Test fun `a group without an active binding never enqueues`() {
        publish(MessageChannel.NOTICE, "Sem vínculo")
        assertEquals(0L, count("notification_whatsapp_group_queue"))
        binding(enabled = false)
        publish(MessageChannel.NOTICE, "Desabilitado")
        assertEquals(0L, count("notification_whatsapp_group_queue"))
        jdbc.sql("UPDATE group_whatsapp_bindings SET enabled = true, broken_at = now()").update()
        publish(MessageChannel.NOTICE, "Quebrado")
        assertEquals(0L, count("notification_whatsapp_group_queue"))
    }

    @Test fun `worker accepts a notice with the group prefix and revalidates membership`() {
        binding()
        publish(MessageChannel.NOTICE, "Treino amanhã")
        val sent = mutableListOf<Triple<String, UUID, String>>()
        drain { jid, id, text, _ -> sent += Triple(jid, id, text); WhatsAppDelivery.Accepted }
        assertEquals(1, sent.size)
        assertEquals(GROUP_JID, sent.single().first)
        assertEquals("Saqz · Vôlei do CERET\nTreino amanhã", sent.single().third)
        assertEquals("ACCEPTED", status())
        assertEquals(1, attempts())
        assertTrue(directory.infoChecks >= 1)
    }

    @Test fun `worker sends a reminder with the attendance link and no private data`() {
        binding()
        val game = publishedGame()
        service.remind(owner, group, game, UUID.randomUUID()).success()
        val code = jdbc.sql("SELECT code FROM notification_attendance_links").query(String::class.java).single()
        val sent = mutableListOf<Pair<String, List<WhatsAppGroupButton>>>()
        drain { _, _, text, buttons -> sent += text to buttons; WhatsAppDelivery.Accepted }
        val (text, buttons) = sent.single()
        assertEquals("Vôlei do CERET\nJogo: domingo, 20/09 às 12:00\nLocal: Arena", text)
        // Os links vão como botões: a URL crua nunca entra no texto.
        assertFalse(text.contains("https://"), text)
        assertFalse(text.contains("Confirmar minha presença"), text)
        assertEquals(
            listOf(
                "https://links.saqz.app/attendance/$code",
                "https://links.saqz.app/attendance/$code?saqz_intent=decline",
            ),
            buttons.map { it.url },
        )
        assertTrue(buttons.all { it.label.isNotBlank() })
        assertEquals("ACCEPTED", status())
        assertFalse(text.contains(INSTANCE_JID))
        assertFalse(text.contains("@s.whatsapp.net"))
        assertFalse(text.contains("R$"))
    }

    @Test fun `not in group breaks the binding and cancels every pending job of the group`() {
        binding()
        publish(MessageChannel.NOTICE)
        publish(MessageChannel.NOTICE)
        directory.groupError = DirectoryError.NotInGroup
        drain { _, _, _, _ -> error("a broken group must never be sent") }
        assertEquals(2, countStatus("CANCELLED"))
        assertTrue(isBroken())
        assertEquals(0, countStatus("PENDING"))
    }

    @Test fun `instance missing from participants breaks the binding`() {
        binding()
        publish(MessageChannel.NOTICE)
        directory.member = false
        drain { _, _, _, _ -> error("removed instance must never be sent") }
        assertEquals("CANCELLED", status())
        assertTrue(isBroken())
    }

    @Test fun `a transient directory failure retries instead of breaking the binding`() {
        binding()
        publish(MessageChannel.NOTICE)
        directory.groupError = DirectoryError.Unavailable("upstream down")
        drain { _, _, _, _ -> error("unavailable provider must not send") }
        assertEquals("PENDING", status())
        assertEquals(1, attempts())
        assertFalse(isBroken())
    }

    @Test fun `a binding disabled before delivery cancels the job`() {
        binding()
        publish(MessageChannel.NOTICE)
        jdbc.sql("UPDATE group_whatsapp_bindings SET enabled = false").update()
        drain { _, _, _, _ -> error("disabled binding must not send") }
        assertEquals("CANCELLED", status())
    }

    @Test fun `a binding removed before delivery cancels the job`() {
        binding()
        publish(MessageChannel.NOTICE)
        jdbc.sql("DELETE FROM group_whatsapp_bindings").update()
        drain { _, _, _, _ -> error("unlinked group must not send") }
        assertEquals("CANCELLED", status())
    }

    @Test fun `retry respects the backoff and exhausts at ten attempts`() {
        binding()
        publish(MessageChannel.NOTICE)
        drain { _, _, _, _ -> WhatsAppDelivery.Retry(7200) }
        assertEquals("PENDING", status())
        assertTrue(jdbc.sql("SELECT next_attempt_at >= now() + interval '7190 seconds' FROM notification_whatsapp_group_queue")
            .query(Boolean::class.java).single())
        assertEquals(1, attempts())
        jdbc.sql("UPDATE notification_whatsapp_group_queue SET next_attempt_at = now()").update()
        drain { _, _, _, _ -> WhatsAppDelivery.Retry() }
        assertEquals(2, attempts())
        jdbc.sql("UPDATE notification_whatsapp_group_queue SET attempts = 9, next_attempt_at = now()").update()
        drain { _, _, _, _ -> WhatsAppDelivery.Retry() }
        assertEquals("FAILED", status())
        assertEquals(10, attempts())
        drain { _, _, _, _ -> error("terminal delivery retried") }
    }

    @Test fun `a permanent send failure becomes FAILED`() {
        binding()
        publish(MessageChannel.NOTICE)
        drain { _, _, _, _ -> WhatsAppDelivery.Failed }
        assertEquals("FAILED", status())
        drain { _, _, _, _ -> error("failed delivery retried") }
    }

    @Test fun `an expired reminder is cancelled without losing the in-app notification`() {
        binding()
        val game = publishedGame()
        service.remind(owner, group, game, UUID.randomUUID()).success()
        jdbc.sql("UPDATE games SET confirmation_deadline = now() - interval '1 minute' WHERE id = :id")
            .param("id", game).update()
        drain { _, _, _, _ -> error("expired reminder must not send") }
        assertEquals("CANCELLED", status())
        assertEquals(1, service.inbox(member, null).success().items.size)
    }

    @Test fun `relinking with a different jid cancels the pending jobs of the previous binding`() {
        binding()
        publish(MessageChannel.NOTICE)
        assertEquals(1, countStatus("PENDING"))
        directory.invite = WhatsAppGroupInfo(NEW_JID, "Novo Grupo", listOf(OWNER_PHONE))

        link.execute(owner, group, "NewInvite01")

        assertEquals("CANCELLED", status())
        assertEquals(0, attempts())
        assertEquals(0, countStatus("PENDING"))
        assertEquals(NEW_JID, bindingJid())
    }

    @Test fun `relinking the same jid preserves the pending jobs`() {
        binding()
        publish(MessageChannel.NOTICE)
        assertEquals(1, countStatus("PENDING"))
        directory.invite = WhatsAppGroupInfo(GROUP_JID, "Vôlei do CERET", listOf(OWNER_PHONE))

        link.execute(owner, group, "SameInvite01")

        assertEquals("PENDING", status())
        assertEquals(GROUP_JID, bindingJid())
    }

    @Test fun `relinking keeps terminal jobs of the previous binding intact`() {
        binding()
        publish(MessageChannel.NOTICE, "Primeiro")
        drain { _, _, _, _ -> WhatsAppDelivery.Accepted }
        publish(MessageChannel.NOTICE, "Segundo")
        assertEquals(1, countStatus("ACCEPTED"))
        assertEquals(1, countStatus("PENDING"))
        directory.invite = WhatsAppGroupInfo(NEW_JID, "Novo Grupo", listOf(OWNER_PHONE))

        link.execute(owner, group, "NewInvite02")

        assertEquals(1, countStatus("ACCEPTED"))
        assertEquals(0, countStatus("PENDING"))
        assertEquals(NEW_JID, bindingJid())
    }

    private fun binding(jid: String = GROUP_JID, name: String = "Vôlei do CERET", enabled: Boolean = true, broken: Boolean = false) {
        jdbc.sql("""
            INSERT INTO group_whatsapp_bindings
                (group_id, whatsapp_jid, invite_code, group_name, instance_jid, enabled, broken_at, created_by)
            VALUES (:g, :jid, 'invite-code', :name, :instance, :enabled,
                CASE WHEN :broken THEN now() ELSE NULL END, :owner)
        """).param("g", group).param("jid", jid).param("name", name).param("instance", INSTANCE_JID)
            .param("enabled", enabled).param("broken", broken).param("owner", owner).update()
    }

    private fun publish(channel: MessageChannel, body: String = "Aviso", request: UUID = UUID.randomUUID()) =
        service.publish(owner, group, channel, request, body).success()

    private fun drain(send: (String, UUID, String, List<WhatsAppGroupButton>) -> WhatsAppDelivery) =
        queue.drain(NotificationWhatsAppGroupSender(send), directory)

    private fun chargeMessage() {
        jdbc.sql("""
            INSERT INTO group_messages (id, group_id, author_id, author_name, channel, body, request_id)
            VALUES (:id, :g, :owner, 'Owner', 'CHARGE', 'Você tem uma cobrança em aberto.', :request)
        """).param("id", UUID.randomUUID()).param("g", group).param("owner", owner)
            .param("request", UUID.randomUUID()).update()
    }

    private fun publishedGame(): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :g, 'Treino', DATE '2026-09-20', TIME '12:00', 'UTC', now() + interval '1 day', 90,
                now() + interval '1 day', 'Arena', 'Rua 100', 12, 'PUBLISHED', now(), now())
        """).param("id", id).param("g", group).update()
        return id
    }

    private fun status() = jdbc.sql("SELECT status FROM notification_whatsapp_group_queue").query(String::class.java).single()
    private fun bindingJid() = jdbc.sql("SELECT whatsapp_jid FROM group_whatsapp_bindings").query(String::class.java).single()
    private fun attempts() = jdbc.sql("SELECT attempts FROM notification_whatsapp_group_queue").query(Int::class.java).single()
    private fun count(table: String) = jdbc.sql("SELECT count(*) FROM $table").query(Long::class.java).single()
    private fun countStatus(value: String) = jdbc.sql("SELECT count(*) FROM notification_whatsapp_group_queue WHERE status = :value")
        .param("value", value).query(Int::class.java).single()
    private fun isBroken() = jdbc.sql("SELECT broken_at IS NOT NULL FROM group_whatsapp_bindings").query(Boolean::class.java).single()

    private fun user(name: String, phone: String? = null): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, :phone, now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).param("phone", phone).update()
    }

    private fun <T> CommunicationResult<T>.success(): T = assertIs<CommunicationResult.Success<T>>(this).value

    private class FakeDirectory : WhatsAppGroupDirectory {
        var groupError: DirectoryError? = null
        var member = true
        var infoChecks = 0
        var invite = WhatsAppGroupInfo(GROUP_JID, "Vôlei do CERET", emptyList())

        override fun groupInfo(jid: String): WhatsAppGroupInfo {
            infoChecks += 1
            groupError?.let { throw it }
            return WhatsAppGroupInfo(jid, "Vôlei do CERET", emptyList())
        }

        override fun isMember(jid: String): Boolean = member
        override fun instanceStatus(): String = INSTANCE_JID
        override fun inviteInfo(inviteCode: String): WhatsAppGroupInfo = invite
        override fun join(inviteCode: String) = Unit

        fun reset() {
            groupError = null
            member = true
            infoChecks = 0
            invite = WhatsAppGroupInfo(GROUP_JID, "Vôlei do CERET", emptyList())
        }
    }

    private companion object {
        const val GROUP_JID = "120363000000000000@g.us"
        const val NEW_JID = "120363111111111111@g.us"
        const val INSTANCE_JID = "5511999990000"
        const val OWNER_PHONE = "5511988887777"
    }
}
