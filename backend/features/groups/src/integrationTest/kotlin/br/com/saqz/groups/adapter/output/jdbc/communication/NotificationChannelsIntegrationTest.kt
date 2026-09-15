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
    private val reminders = ChargeReminderService(transaction, JdbcGroupReadRepository(source), repository, JdbcChargeReminderStore(source))
    private val owner = user("Owner")
    private val member = user("Member")
    private val group = UUID.randomUUID()
    private var chargeSequence = 0
    private var gameSequence = 0
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

    @Test fun `notice never uses WhatsApp DM even with opt in and never reaches the author`() {
        publish(MessageChannel.NOTICE)
        assertEquals(0L, count("notification_whatsapp_queue"))
        assertEquals(1, inbox().size)
        service.savePreferences(member, NotificationPreferences(whatsapp = WhatsAppPreferences(true, true, true)))
        publish(MessageChannel.CHAT)
        publish(MessageChannel.NOTICE, "Treino amanhã")
        drain { error("NOTICE and CHAT must not enqueue a WhatsApp DM") }
        assertEquals(0L, count("notification_whatsapp_queue"))
        assertEquals(3, inbox().size)
        val pushes = mutableListOf<NotificationPush>()
        push.drain(NotificationPushSender { _, message -> pushes += message; PushDelivery.SENT })
        assertEquals(2, pushes.count { it.body == "Você recebeu um aviso do grupo. Abra o app para conferir." })
        assertEquals(1, pushes.count { it.body == "Você recebeu uma mensagem no grupo. Abra o app para conferir." })
        assertTrue(service.inbox(owner, null).success().items.isEmpty())
    }

    @Test fun `charge WhatsApp delivery is independent from push and replay creates one job`() {
        val preferences = NotificationPreferences(notices = false, push = PushPreferences(charges = false),
            whatsapp = WhatsAppPreferences(charges = true))
        assertEquals(preferences, service.savePreferences(member, preferences))
        assertEquals(preferences, service.preferences(member))
        val request = UUID.randomUUID()
        val charge = charge()
        remindCharges(charge, request = request)
        remindCharges(charge, request = request)
        assertEquals(1L, count("notification_whatsapp_queue"))
        assertEquals(0L, count("notification_push_queue"))
        val sent = mutableListOf<WhatsAppNotification>()
        drain { sent += it; WhatsAppDelivery.Accepted }
        drain { error("accepted delivery was repeated") }
        assertEquals(1, sent.size)
        assertEquals("+5511999999999", sent.single().phone)
        assertTrue(sent.single().body.startsWith("Saqz · Futebol\nVocê tem uma cobrança de"))
        assertTrue(sent.single().body.contains("em aberto. Abra o grupo para conferir sua cobrança."))
    }

    @Test fun `charge WhatsApp failure cannot lose inbox or block push and respects retry after`() {
        service.savePreferences(member, NotificationPreferences(whatsapp = WhatsAppPreferences(charges = true)))
        remindCharges(charge())
        drain { WhatsAppDelivery.Retry(7200) }
        assertEquals("PENDING", status())
        assertTrue(jdbc.sql("SELECT next_attempt_at >= now() + interval '7190 seconds' FROM notification_whatsapp_queue")
            .query(Boolean::class.java).single())
        assertEquals(1, inbox().size)
        val sent = mutableListOf<Pair<String, NotificationPush>>()
        push.drain(NotificationPushSender { token, message -> sent += token to message; PushDelivery.SENT })
        assertEquals("member-device", sent.single().first)
        assertEquals(group, sent.single().second.groupId)
        assertEquals("Você recebeu um lembrete de cobrança. Abra o app para conferir.", sent.single().second.body)
        jdbc.sql("UPDATE notification_whatsapp_queue SET next_attempt_at = now()").update()
        drain { WhatsAppDelivery.Accepted }
        assertEquals("ACCEPTED", status())
        assertEquals(2, jdbc.sql("SELECT attempts FROM notification_whatsapp_queue").query(Int::class.java).single())
    }

    @Test fun `pending charge delivery is cancelled after opt out departure or phone change`() {
        for (change in listOf("mute", "departure", "phone")) {
            jdbc.sql("TRUNCATE group_messages CASCADE").update()
            jdbc.sql("UPDATE group_memberships SET active = true").update()
            jdbc.sql("UPDATE access_users SET phone = '+5511999999999' WHERE id = :u").param("u", member).update()
            service.savePreferences(member, NotificationPreferences(whatsapp = WhatsAppPreferences(charges = true)))
            remindCharges(charge())
            when (change) {
                "mute" -> service.savePreferences(member, NotificationPreferences())
                "departure" -> jdbc.sql("UPDATE group_memberships SET active = false WHERE user_id = :u").param("u", member).update()
                "phone" -> jdbc.sql("UPDATE access_users SET phone = '+5511988888888' WHERE id = :u").param("u", member).update()
            }
            drain { error("ineligible delivery: $change") }
            assertEquals("CANCELLED", status(), change)
        }
    }

    @Test fun `missing phone and permanent or exhausted charge failures do not loop`() {
        service.savePreferences(member, NotificationPreferences(whatsapp = WhatsAppPreferences(charges = true)))
        jdbc.sql("UPDATE access_users SET phone = null WHERE id = :u").param("u", member).update()
        remindCharges(charge())
        assertEquals(0L, count("notification_whatsapp_queue"))
        jdbc.sql("UPDATE access_users SET phone = '+5511999999999' WHERE id = :u").param("u", member).update()
        remindCharges(charge())
        drain { WhatsAppDelivery.Failed }
        assertEquals("FAILED", status())
        jdbc.sql("UPDATE notification_whatsapp_queue SET status = 'PENDING', attempts = 9, completed_at = null").update()
        drain { WhatsAppDelivery.Retry() }
        assertEquals("FAILED", status())
        assertEquals(10, jdbc.sql("SELECT attempts FROM notification_whatsapp_queue").query(Int::class.java).single())
        drain { error("terminal delivery retried") }
    }

    @Test fun `presence reminder never enqueues a WhatsApp DM and is dropped once the deadline closes`() {
        service.savePreferences(member, NotificationPreferences(whatsapp = WhatsAppPreferences(reminders = true)))
        val game = publishedGame()
        service.remind(owner, group, game, UUID.randomUUID()).success()
        assertEquals(0L, count("notification_whatsapp_queue"))
        drain { error("REMINDER must not enqueue a WhatsApp DM") }
        jdbc.sql("UPDATE games SET confirmation_deadline = now() - interval '1 minute' WHERE id = :id").param("id", game).update()
        push.drain(NotificationPushSender { _, _ -> error("expired reminder") })
        assertEquals(1, inbox().size)
    }

    @Test fun `charge WhatsApp is private and cancelled charges cancel pending delivery`() {
        service.savePreferences(member, NotificationPreferences(whatsapp = WhatsAppPreferences(charges = true)))
        val charge = charge()
        remindCharges(charge)
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
        val game = publishedGame()
        val request = UUID.randomUUID()
        service.remind(owner, group, game, request).success()
        service.remind(owner, group, game, request).success()
        assertEquals(1L, count("notification_attendance_links"))
        assertEquals(0L, count("notification_whatsapp_queue"))
        val code = jdbc.sql("SELECT code FROM notification_attendance_links").query(String::class.java).single()
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
    private fun remindCharges(vararg charges: UUID, request: UUID = UUID.randomUUID()): ChargeReminderReceipt =
        reminders.send(owner, group, request, charges.toList()).success()
    private fun charge(amountCents: Long = 7000): UUID {
        chargeSequence += 1
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO group_charges(id, group_id, member_user_id, kind, billing_month, amount_cents, due_date,
                created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name)
            VALUES (:id, :g, :u, 'MONTHLY', CAST(:month AS date), :amount, '2026-09-10', :a, :a, now(), now(), 'Member')
        """).param("id", id).param("g", group).param("u", member).param("a", owner)
            .param("month", "2026-%02d-01".format(chargeSequence)).param("amount", amountCents).update()
        return id
    }
    private fun publishedGame(): UUID {
        gameSequence += 1
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :g, 'Treino', current_date + :offset, '12:00', 'UTC', now() + (:offset * interval '1 day'), 90,
                now() + interval '1 day', 'Arena', 'Rua 100', 12, 'PUBLISHED', now(), now())
        """).param("id", id).param("g", group).param("offset", gameSequence + 1).update()
        return id
    }
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