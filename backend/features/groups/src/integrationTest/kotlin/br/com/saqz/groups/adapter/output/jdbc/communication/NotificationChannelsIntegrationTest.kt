package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.*
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.*

class NotificationChannelsIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val repository = JdbcGroupCommunicationRepository(source)
    private val service = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), repository)
    private val whatsapp = JdbcNotificationWhatsApp(source, transaction,
        br.com.saqz.groups.adapter.output.link.BranchAttendanceLinkFactory(java.net.URI("https://saqz.test-app.link")))
    private val push = JdbcNotificationPush(source, transaction)
    private val owner = user("Owner")
    private val member = user("Member")
    private val group = UUID.randomUUID()
    init {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'Futebol', 'UTC', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        for (user in listOf(owner, member)) jdbc.sql("""
            INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, 'ADMIN', 'AVULSO', true, now(), now())
        """).param("g", group).param("u", user).update()
        push.register(member, UUID.randomUUID(), "member-device", "ANDROID")
    }

    @Test fun `WhatsApp requires opt in and never sends conversations or to the author`() {
        publish(MessageChannel.NOTICE)
        assertEquals(0L, count("notification_whatsapp_queue"))
        assertEquals(1, inbox().size)
        service.savePreferences(member, NotificationPreferences(whatsapp = WhatsAppPreferences(true, true, true)))
        publish(MessageChannel.CHAT)
        publish(MessageChannel.NOTICE, "Treino amanhã")
        val sent = mutableListOf<WhatsAppNotification>()
        drain { message -> sent += message; WhatsAppDelivery.Accepted }
        assertEquals(1, sent.size)
        assertEquals("+5511999999999", sent.single().phone)
        assertEquals("Saqz · Futebol\nTreino amanhã", sent.single().body)
        assertEquals(inbox().first().sequence, sent.single().notificationId)
        assertEquals("ACCEPTED", status())
        assertTrue(service.inbox(owner, null).success().items.isEmpty())
    }

    @Test fun `app push and WhatsApp selections are independent and replay creates one job`() {
        val preferences = NotificationPreferences(notices = false, push = PushPreferences(notices = false),
            whatsapp = WhatsAppPreferences(notices = true))
        assertEquals(preferences, service.savePreferences(member, preferences))
        assertEquals(preferences, service.preferences(member))
        val request = UUID.randomUUID()
        val message = publish(MessageChannel.NOTICE, request = request)
        assertEquals(message, publish(MessageChannel.NOTICE, request = request))
        assertTrue(inbox().isEmpty())
        assertEquals(0L, count("notification_push_queue"))
        assertEquals(1L, count("notification_whatsapp_queue"))
        val sent = mutableListOf<WhatsAppNotification>()
        drain { sent += it; WhatsAppDelivery.Accepted }
        drain { error("accepted delivery was repeated") }
        assertEquals(1, sent.size)
    }

    @Test fun `WhatsApp failure cannot lose inbox or block push and respects retry after`() {
        service.savePreferences(member, NotificationPreferences(whatsapp = WhatsAppPreferences(notices = true)))
        publish(MessageChannel.NOTICE)
        drain { WhatsAppDelivery.Retry(7200) }
        assertEquals("PENDING", status())
        assertTrue(jdbc.sql("SELECT next_attempt_at >= now() + interval '7190 seconds' FROM notification_whatsapp_queue")
            .query(Boolean::class.java).single())
        assertEquals(1, inbox().size)
        val sent = mutableListOf<Pair<String, NotificationPush>>()
        push.drain(NotificationPushSender { token, message -> sent += token to message; PushDelivery.SENT })
        assertEquals("member-device", sent.single().first)
        assertEquals(group, sent.single().second.groupId)
        assertEquals("Você recebeu um aviso do grupo. Abra o app para conferir.", sent.single().second.body)
        jdbc.sql("UPDATE notification_whatsapp_queue SET next_attempt_at = now()").update()
        drain { WhatsAppDelivery.Accepted }
        assertEquals("ACCEPTED", status())
        assertEquals(2, jdbc.sql("SELECT attempts FROM notification_whatsapp_queue").query(Int::class.java).single())
    }

    @Test fun `pending delivery is cancelled after opt out departure or phone change`() {
        for (change in listOf("mute", "departure", "phone")) {
            jdbc.sql("TRUNCATE group_messages CASCADE").update()
            jdbc.sql("UPDATE group_memberships SET active = true").update()
            jdbc.sql("UPDATE access_users SET phone = '+5511999999999' WHERE id = :u").param("u", member).update()
            service.savePreferences(member, NotificationPreferences(whatsapp = WhatsAppPreferences(notices = true)))
            publish(MessageChannel.NOTICE)
            when (change) {
                "mute" -> service.savePreferences(member, NotificationPreferences())
                "departure" -> jdbc.sql("UPDATE group_memberships SET active = false WHERE user_id = :u").param("u", member).update()
                "phone" -> jdbc.sql("UPDATE access_users SET phone = '+5511988888888' WHERE id = :u").param("u", member).update()
            }
            drain { error("ineligible delivery: $change") }
            assertEquals("CANCELLED", status(), change)
        }
    }

    @Test fun `missing phone and permanent or exhausted failures do not loop`() {
        service.savePreferences(member, NotificationPreferences(whatsapp = WhatsAppPreferences(notices = true)))
        jdbc.sql("UPDATE access_users SET phone = null WHERE id = :u").param("u", member).update()
        publish(MessageChannel.NOTICE)
        assertEquals(0L, count("notification_whatsapp_queue"))
        jdbc.sql("UPDATE access_users SET phone = '+5511999999999' WHERE id = :u").param("u", member).update()
        publish(MessageChannel.NOTICE)
        drain { WhatsAppDelivery.Failed }
        assertEquals("FAILED", status())
        jdbc.sql("UPDATE notification_whatsapp_queue SET status = 'PENDING', attempts = 9, completed_at = null").update()
        drain { WhatsAppDelivery.Retry() }
        assertEquals("FAILED", status())
        assertEquals(10, jdbc.sql("SELECT attempts FROM notification_whatsapp_queue").query(Int::class.java).single())
        drain { error("terminal delivery retried") }
    }

    @Test fun `presence reminder is delivered only while attendance remains open and unanswered`() {
        service.savePreferences(member, NotificationPreferences(whatsapp = WhatsAppPreferences(reminders = true)))
        val game = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :g, 'Treino', current_date + 2, '12:00', 'UTC', now() + interval '2 days', 90,
                now() + interval '1 day', 'Arena', 'Rua 100', 12, 'PUBLISHED', now(), now())
        """).param("id", game).param("g", group).update()
        service.remind(owner, group, game, UUID.randomUUID()).success()
        assertEquals(1L, count("notification_whatsapp_queue"))
        jdbc.sql("UPDATE games SET confirmation_deadline = now() - interval '1 minute' WHERE id = :id").param("id", game).update()
        drain { error("expired reminder") }
        push.drain(NotificationPushSender { _, _ -> error("expired push") })
        assertEquals("CANCELLED", status())
        assertEquals(1, inbox().size)
    }

    @Test fun `charge WhatsApp is private and cancelled charges cancel pending delivery`() {
        service.savePreferences(member, NotificationPreferences(whatsapp = WhatsAppPreferences(charges = true)))
        val charge = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO group_charges(id, group_id, member_user_id, kind, billing_month, amount_cents, due_date,
                created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name)
            VALUES (:id, :g, :u, 'MONTHLY', '2026-09-01', 7000, '2026-09-10', :a, :a, now(), now(), 'Member')
        """).param("id", charge).param("g", group).param("u", member).param("a", owner).update()
        val reminders = ChargeReminderService(transaction, JdbcGroupReadRepository(source), repository, JdbcChargeReminderStore(source))
        reminders.send(owner, group, UUID.randomUUID(), listOf(charge)).success()
        assertEquals(1L, count("notification_whatsapp_queue"))
        assertEquals(MessageChannel.CHARGE, inbox().single().message.channel)
        assertTrue(service.inbox(owner, null).success().items.isEmpty())
        jdbc.sql("UPDATE group_charges SET status = 'CANCELLED' WHERE id = :id").param("id", charge).update()
        drain { error("cancelled charge") }
        assertEquals("CANCELLED", status())
    }

    @Test fun `chat push delivers without WhatsApp and respects its own preference`() {
        publish(MessageChannel.CHAT, "Mensagem da conversa")
        val sent = mutableListOf<Pair<String, NotificationPush>>()
        push.drain(NotificationPushSender { token, message -> sent += token to message; PushDelivery.SENT })
        assertEquals("member-device", sent.single().first)
        assertEquals(group, sent.single().second.groupId)
        assertEquals("Você recebeu uma mensagem no grupo. Abra o app para conferir.", sent.single().second.body)
        assertEquals(inbox().single().sequence, sent.single().second.notificationId)
        assertEquals(0L, count("notification_whatsapp_queue"))
        service.savePreferences(member, NotificationPreferences(push = PushPreferences(messages = false)))
        publish(MessageChannel.CHAT)
        push.drain(NotificationPushSender { _, _ -> error("muted chat") })
        assertEquals(2, inbox().size)
    }

    @Test fun `presence sends app link and resolves only for an active member without changing attendance`() {
        service.savePreferences(member, NotificationPreferences(whatsapp = WhatsAppPreferences(reminders = true)))
        val game = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :g, 'Treino', current_date + 2, '12:00', 'UTC', now() + interval '2 days', 90,
                now() + interval '1 day', 'Arena', 'Rua 100', 12, 'PUBLISHED', now(), now())
        """).param("id", game).param("g", group).update()
        val request = UUID.randomUUID()
        service.remind(owner, group, game, request).success()
        service.remind(owner, group, game, request).success()
        assertEquals(1L, count("notification_attendance_links"))
        val code = jdbc.sql("SELECT code FROM notification_attendance_links").query(String::class.java).single()
        val sent = mutableListOf<WhatsAppNotification>()
        drain { sent += it; WhatsAppDelivery.Accepted }
        assertTrue(sent.single().body.contains("saqz_attendance=$code"))
        assertTrue(sent.single().body.contains("Confirmar minha presença no Saqz:"))
        val pushes = mutableListOf<NotificationPush>()
        push.drain(NotificationPushSender { token, message -> assertEquals("member-device", token); pushes += message; PushDelivery.SENT })
        assertEquals("Confirme sua presença no próximo jogo. Abra o app para conferir.", pushes.single().body)
        val resolver = br.com.saqz.groups.application.attendance.share.ResolveAttendanceLink(transaction,
            br.com.saqz.groups.adapter.output.jdbc.attendance.share.JdbcAttendanceLinkRepository(source), java.time.Clock.systemUTC())
        assertEquals(br.com.saqz.groups.application.attendance.share.ResolveAttendanceLinkResult.Success(group, game, registrationRequired = true),
            resolver.execute(member, code))
        assertEquals(0L, count("game_attendance"))
        assertEquals(br.com.saqz.groups.application.attendance.share.ResolveAttendanceLinkResult.InvalidOrExpired,
            resolver.execute(user("Stranger"), code))
        val redeemer = br.com.saqz.groups.application.invite.redeem.RedeemInvite(transaction,
            br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteRedemptionRepository(source),
            object : br.com.saqz.sharedkernel.subscription.SubscriptionLimits {
                override fun groupLimitFor(ownerId: UUID): Int? = null
                override fun athleteLimitFor(ownerId: UUID): Int? = null
            }, java.time.Clock.systemUTC())
        val newcomer = user("New athlete")
        assertIs<br.com.saqz.groups.application.invite.redeem.RedeemInviteResult.Success>(redeemer.execute(newcomer, code))
        assertEquals(br.com.saqz.groups.application.attendance.share.ResolveAttendanceLinkResult.Success(group, game, true),
            resolver.execute(newcomer, code))
        br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository(source).updateOwn(
            br.com.saqz.groups.application.athlete.UpdateOwnAthleteProfileCommand(group, newcomer, null, null, null, null, null, null))
        assertEquals(br.com.saqz.groups.application.attendance.share.ResolveAttendanceLinkResult.Success(group, game, false),
            resolver.execute(newcomer, code))
        assertEquals(0L, count("game_attendance"))
        jdbc.sql("UPDATE access_groups SET entry_requires_approval = true WHERE id = :id").param("id", group).update()
        val pending = user("Pending athlete")
        assertEquals(br.com.saqz.groups.application.invite.redeem.RedeemInviteResult.Pending(group), redeemer.execute(pending, code))
        assertEquals(1L, count("group_entry_requests"))
        assertEquals(br.com.saqz.groups.application.attendance.share.ResolveAttendanceLinkResult.InvalidOrExpired,
            resolver.execute(pending, code))
        jdbc.sql("UPDATE group_memberships SET active = false WHERE user_id = :u").param("u", member).update()
        assertEquals(br.com.saqz.groups.application.attendance.share.ResolveAttendanceLinkResult.InvalidOrExpired, resolver.execute(member, code))
    }

    private fun publish(channel: MessageChannel, body: String = "Aviso", request: UUID = UUID.randomUUID()) =
        service.publish(owner, group, channel, request, body).success()
    private fun drain(send: (WhatsAppNotification) -> WhatsAppDelivery) = whatsapp.drain(NotificationWhatsAppSender(send))
    private fun inbox() = service.inbox(member, null).success().items
    private fun status() = jdbc.sql("SELECT status FROM notification_whatsapp_queue").query(String::class.java).single()
    private fun count(table: String) = jdbc.sql("SELECT count(*) FROM $table").query(Long::class.java).single()
    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }
    private fun <T> CommunicationResult<T>.success(): T = assertIs<CommunicationResult.Success<T>>(this).value
}
