package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.athlete.RemoveAthlete
import br.com.saqz.groups.application.communication.CommunicationError
import br.com.saqz.groups.application.communication.CommunicationResult
import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.groups.application.communication.MessageChannel
import br.com.saqz.groups.application.communication.NotificationPreferences
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChargeReminderIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var service: GroupCommunicationService
    private lateinit var repository: JdbcGroupCommunicationRepository
    private lateinit var reminders: br.com.saqz.groups.application.communication.ChargeReminderService
    private lateinit var owner: UUID
    private lateinit var member: UUID
    private lateinit var group: UUID

    @BeforeAll fun database() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations()).dataSource
        jdbc = JdbcClient.create(dataSource)
        repository = JdbcGroupCommunicationRepository(dataSource)
        reminders = br.com.saqz.groups.application.communication.ChargeReminderService(
            JdbcTransactionRunner(dataSource), JdbcGroupReadRepository(dataSource), repository, JdbcChargeReminderStore(dataSource))
        service = GroupCommunicationService(JdbcTransactionRunner(dataSource), JdbcGroupReadRepository(dataSource), repository)
    }
    @BeforeEach fun setup() {
        jdbc.sql("TRUNCATE access_users CASCADE").update()
        owner = user("Owner Person")
        member = user("Member Person")
        group = UUID.randomUUID()
        jdbc.sql("INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) VALUES (:id, :owner, :key, 'Training Group', 'UTC', now(), now())")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        membership(owner, "ADMIN")
        membership(member)
    }
    @Test fun `selected charges create private inbox entries and durable push jobs without changing payments`() {
        val other = user("Other Member"); membership(other)
        val first = charge(member); val second = charge(other)
        val receipt = reminders.send(owner, group, UUID.randomUUID(), listOf(first, second)).success()
        assertEquals(2, receipt.notificationCount)
        val inbox = service.inbox(member, null).success().items
        assertEquals(1, inbox.size)
        assertTrue(inbox.single().message.body.contains("70,00"))
        assertEquals(MessageChannel.CHARGE, inbox.single().message.channel)
        assertEquals(1, service.inbox(other, null).success().items.size)
        assertEquals(0, service.inbox(owner, null).success().items.size)
        assertEquals(2L, count("notification_push_queue"))
        assertEquals(2L, jdbc.sql("SELECT count(*) FROM group_charges WHERE status = 'PENDING'").query(Long::class.java).single())
        assertEquals(CommunicationResult.Failure(CommunicationError.INVALID), service.messages(member, group, MessageChannel.CHARGE, null))
        assertEquals(CommunicationResult.Failure(CommunicationError.INVALID), service.publish(owner, group, MessageChannel.CHARGE, UUID.randomUUID(), "private"))
    }
    @Test fun `replay returns original receipt and conflicting selection does not notify again`() {
        val first = charge(member); val request = UUID.randomUUID()
        val receipt = reminders.send(owner, group, request, listOf(first)).success()
        assertEquals(receipt, reminders.send(owner, group, request, listOf(first)).success())
        assertEquals(1L, count("group_notifications"))
        assertEquals(1L, count("notification_push_queue"))
        assertEquals(CommunicationResult.Failure(CommunicationError.CONFLICT), reminders.send(owner, group, request, listOf(UUID.randomUUID())))
    }
    @Test fun `athletes strangers invalid and nonpending selections cannot notify`() {
        val first = charge(member)
        assertEquals(CommunicationResult.Failure(CommunicationError.FORBIDDEN), reminders.send(member, group, UUID.randomUUID(), listOf(first)))
        assertEquals(CommunicationResult.Failure(CommunicationError.NOT_FOUND), reminders.send(user("Stranger"), group, UUID.randomUUID(), listOf(first)))
        assertEquals(CommunicationResult.Failure(CommunicationError.INVALID), reminders.send(owner, group, UUID.randomUUID(), emptyList()))
        assertEquals(CommunicationResult.Failure(CommunicationError.INVALID), reminders.send(owner, group, UUID.randomUUID(), listOf(first, first)))
        assertEquals(CommunicationResult.Failure(CommunicationError.CONFLICT), reminders.send(owner, group, UUID.randomUUID(), listOf(first, UUID.randomUUID())))
        jdbc.sql("UPDATE group_charges SET status = 'PAID' WHERE id = :id").param("id", first).update()
        assertEquals(CommunicationResult.Failure(CommunicationError.CONFLICT), reminders.send(owner, group, UUID.randomUUID(), listOf(first)))
        assertEquals(0L, count("group_notifications"))
    }
    @Test fun `selection limit accepts 200 and rejects 201 without partial delivery`() {
        val ids = (1..201).map { index ->
            val recipient = user("Member $index")
            membership(recipient)
            charge(recipient)
        }
        assertEquals(CommunicationResult.Failure(CommunicationError.INVALID),
            reminders.send(owner, group, UUID.randomUUID(), ids))
        assertEquals(0L, count("group_notifications"))
        assertEquals(200, reminders.send(owner, group, UUID.randomUUID(), ids.take(200)).success().notificationCount)
        assertEquals(200L, count("group_notifications"))
    }
    @Test fun `charge from another group rejects the whole selection`() {
        val original = group
        val first = charge(member)
        group = UUID.randomUUID()
        jdbc.sql("INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) VALUES (:id, :owner, :key, 'Other', 'UTC', now(), now())")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        membership(owner, "ADMIN"); membership(member)
        val foreign = charge(member)
        assertEquals(CommunicationResult.Failure(CommunicationError.CONFLICT),
            reminders.send(owner, original, UUID.randomUUID(), listOf(first, foreign)))
        assertEquals(0L, count("group_notifications"))
    }
    @Test fun `inactive recipient is rejected without partial notifications`() {
        val first = charge(member)
        jdbc.sql("UPDATE group_memberships SET active = false WHERE user_id = :id").param("id", member).update()
        assertEquals(CommunicationResult.Failure(CommunicationError.CONFLICT), reminders.send(owner, group, UUID.randomUUID(), listOf(first)))
        assertEquals(0L, count("group_notifications"))
    }
    @Test fun `push delivers selected recipient only and retries without resending successful devices`() {
        val charge = charge(member)
        reminders.send(owner, group, UUID.randomUUID(), listOf(charge)).success()
        val push = JdbcNotificationPush(dataSource, JdbcTransactionRunner(dataSource))
        push.register(member, UUID.randomUUID(), "member-token", "ANDROID")
        push.register(member, UUID.randomUUID(), "retry-token", "IOS")
        push.register(owner, UUID.randomUUID(), "owner-token", "ANDROID")
        val delivered = mutableListOf<String>()
        val sender = br.com.saqz.groups.application.communication.NotificationPushSender { token, message ->
            assertEquals(group, message.groupId)
            assertEquals("Saqz", message.title)
            assertFalse(message.body.contains("70,00"))
            delivered += token
            if (token == "retry-token") br.com.saqz.groups.application.communication.PushDelivery.RETRY
            else br.com.saqz.groups.application.communication.PushDelivery.SENT
        }
        push.drain(sender)
        assertEquals(setOf("member-token", "retry-token"), delivered.toSet())
        assertEquals(1L, count("notification_push_deliveries"))
        jdbc.sql("UPDATE notification_push_queue SET next_attempt_at = now()").update()
        delivered.clear()
        push.drain(br.com.saqz.groups.application.communication.NotificationPushSender { token, _ ->
            delivered += token
            br.com.saqz.groups.application.communication.PushDelivery.INVALID_TOKEN
        })
        assertEquals(listOf("retry-token"), delivered)
        assertEquals(2L, count("notification_devices"))
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM notification_push_queue WHERE completed_at IS NOT NULL").query(Long::class.java).single())
    }
    @Test fun `muting push preserves inbox and reassigning a device stops previous recipient delivery`() {
        val push = JdbcNotificationPush(dataSource, JdbcTransactionRunner(dataSource))
        val installation = UUID.randomUUID()
        push.register(member, installation, "token", "ANDROID")
        push.unregister(owner, installation)
        assertEquals(1L, count("notification_devices"))
        push.register(owner, installation, "token", "ANDROID")
        assertEquals(1L, count("notification_devices"))
        reminders.send(owner, group, UUID.randomUUID(), listOf(charge(member))).success()
        push.drain(br.com.saqz.groups.application.communication.NotificationPushSender { _, _ -> error("wrong recipient") })
        assertEquals(1, service.inbox(member, null).success().items.size)
        push.register(member, installation, "token", "ANDROID")
        service.savePreferences(member, NotificationPreferences(reminders = false))
        reminders.send(owner, group, UUID.randomUUID(), listOf(jdbc.sql("SELECT id FROM group_charges").query(UUID::class.java).single())).success()
        push.drain(br.com.saqz.groups.application.communication.NotificationPushSender { _, _ -> error("muted") })
        assertEquals(2, service.inbox(member, null).success().items.size)
        push.unregister(member, installation)
        assertEquals(0L, count("notification_devices"))
    }
    private fun count(table: String) = jdbc.sql("SELECT count(*) FROM $table").query(Long::class.java).single()
    private fun charge(recipient: UUID): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date,
                created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name)
            VALUES (:id, :g, :member, 'MONTHLY', '2026-09-01', 7000, '2026-09-10', :owner, :owner, now(), now(), 'Member Person')
        """).param("id", id).param("g", group).param("member", recipient).param("owner", owner).update()
        return id
    }
    private fun user(name: String): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, :name, now(), now())")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
        return id
    }
    private fun membership(user: UUID, role: String = "ATHLETE", active: Boolean = true) {
        jdbc.sql("INSERT INTO group_memberships (group_id, user_id, role, membership_type, active, created_at, updated_at) VALUES (:group, :user, :role, 'AVULSO', :active, now(), now())")
            .param("group", group).param("user", user).param("role", role, java.sql.Types.OTHER).param("active", active).update()
    }
    private fun <T> CommunicationResult<T>.success(): T = assertIs<CommunicationResult.Success<T>>(this).value
}
