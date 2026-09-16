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
class GroupCommunicationIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var service: GroupCommunicationService
    private lateinit var repository: JdbcGroupCommunicationRepository
    private lateinit var owner: UUID
    private lateinit var member: UUID
    private lateinit var group: UUID

    @BeforeAll fun database() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations()).dataSource
        jdbc = JdbcClient.create(dataSource)
        repository = JdbcGroupCommunicationRepository(dataSource)
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
    @Test fun `only members read chat and only managers publish notices`() {
        val stranger = user("Stranger Person")
        assertEquals(CommunicationResult.Failure(CommunicationError.NOT_FOUND), service.messages(stranger, group, MessageChannel.CHAT, null))
        assertEquals(CommunicationResult.Failure(CommunicationError.FORBIDDEN), service.publish(member, group, MessageChannel.NOTICE, UUID.randomUUID(), "Aviso"))
        assertEquals(CommunicationResult.Failure(CommunicationError.NOT_FOUND), service.publish(member, UUID.randomUUID(), MessageChannel.CHAT, UUID.randomUUID(), "Oi"))
        val message = service.publish(member, group, MessageChannel.CHAT, UUID.randomUUID(), "  Vamos jogar?  ").success()
        assertEquals(member, message.authorId)
        assertEquals(group, message.groupId)
        assertEquals("Member Person", message.authorName)
        assertEquals("Vamos jogar?", message.body)
        assertEquals(listOf(message), service.messages(owner, group, MessageChannel.CHAT, null).success().items)
        assertTrue(service.messages(owner, group, MessageChannel.NOTICE, null).success().items.isEmpty())
    }
    @Test fun `message retry is exactly once with original payload and rejects key reuse`() {
        val request = UUID.randomUUID()
        val message = service.publish(owner, group, MessageChannel.NOTICE, request, "Treino amanhã").success()
        assertEquals(message, service.publish(owner, group, MessageChannel.NOTICE, request, "Treino amanhã").success())
        assertEquals(CommunicationResult.Failure(CommunicationError.CONFLICT), service.publish(owner, group, MessageChannel.NOTICE, request, "Outro aviso"))
        assertEquals(1, message.recipientCount)
        assertEquals(1, service.messages(member, group, MessageChannel.NOTICE, null).success().items.size)
        assertEquals(1, service.inbox(member, null).success().items.size)
        for (body in listOf("   ", "x".repeat(2001), "bad\u0000text")) {
            assertEquals(CommunicationResult.Failure(CommunicationError.INVALID), service.publish(owner, group, MessageChannel.CHAT, UUID.randomUUID(), body))
        }
    }
    @Test fun `concurrent retries all return the one committed message and notification`() {
        val request = UUID.randomUUID()
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val responses = (1..8).map {
                executor.submit<java.util.UUID> {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    service.publish(owner, group, MessageChannel.NOTICE, request, "Aviso simultâneo").success().id
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val ids = responses.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, ids.toSet().size)
            assertEquals(ids.first(), service.messages(member, group, MessageChannel.NOTICE, null).success().items.single().id)
            assertEquals(ids.first(), service.inbox(member, null).success().items.single().message.id)
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun `cursor pages are bounded ordered disjoint and cover all messages`() {
        repeat(53) { service.publish(owner, group, MessageChannel.CHAT, UUID.randomUUID(), "Mensagem $it").success() }
        val first = service.messages(member, group, MessageChannel.CHAT, null).success()
        val second = service.messages(member, group, MessageChannel.CHAT, first.nextCursor).success()
        assertEquals(50, first.items.size)
        assertEquals(3, second.items.size)
        assertEquals(null, second.nextCursor)
        assertEquals(53, (first.items + second.items).map { it.id }.toSet().size)
        assertEquals("Mensagem 52", first.items.first().body)
        assertEquals("Mensagem 0", second.items.last().body)
    }
    @Test fun `inbox and read state are private and departure hides group communications`() {
        val stranger = user("Stranger Person")
        val message = service.publish(owner, group, MessageChannel.NOTICE, UUID.randomUUID(), "Aviso privado").success()
        val notification = service.inbox(member, null).success().items.single()
        assertFalse(notification.read)
        assertTrue(service.inbox(stranger, null).success().items.isEmpty())
        service.markRead(stranger, notification.sequence)
        assertFalse(service.inbox(member, null).success().items.single().read)
        service.markRead(member, notification.sequence)
        service.markRead(member, notification.sequence)
        assertTrue(service.inbox(member, null).success().items.single().read)
        RemoveAthlete(JdbcTransactionRunner(dataSource), JdbcGroupReadRepository(dataSource), JdbcAthleteRepository(dataSource), GroupAccessPolicy()).leave(member, group)
        assertTrue(service.inbox(member, null).success().items.isEmpty())
        assertEquals(CommunicationResult.Failure(CommunicationError.NOT_FOUND), service.messages(member, group, MessageChannel.NOTICE, null))
        assertEquals(listOf(message), service.messages(owner, group, MessageChannel.NOTICE, null).success().items)
    }
    @Test fun `preferences persist and mute only future matching notifications`() {
        assertEquals(NotificationPreferences(), service.preferences(member))
        service.publish(owner, group, MessageChannel.NOTICE, UUID.randomUUID(), "Primeiro aviso").success()
        val preferences = NotificationPreferences(notices = false, messages = true, reminders = false)
        assertEquals(preferences, service.savePreferences(member, preferences))
        assertEquals(preferences, service.preferences(member))
        assertEquals(0, service.publish(owner, group, MessageChannel.NOTICE, UUID.randomUUID(), "Aviso silenciado").success().recipientCount)
        assertEquals(1, service.publish(owner, group, MessageChannel.CHAT, UUID.randomUUID(), "Mensagem liberada").success().recipientCount)
        assertEquals(2, service.inbox(member, null).success().items.size)
    }
    @Test fun `reminders list confirmed waitlist and out and retries preserve recipients`() {
        val confirmed = user("Confirmed Person").also { membership(it) }
        val declined = user("Declined Person").also { membership(it) }
        val waitlisted = user("Waitlisted Person").also { membership(it) }
        val inactive = user("Inactive Person").also { membership(it, active = false) }
        val game = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:game, :group, 'Treino', DATE '2026-09-20', TIME '19:30', 'UTC',
                ((now() AT TIME ZONE 'UTC')::date + 2) + time '12:00', 90, now() + interval '1 day',
                'Arena', 'Rua Central 100', 12, 'PUBLISHED', now(), now())
        """.trimIndent()).param("game", game).param("group", group).update()
        for ((row, sequence) in listOf(
            Triple(confirmed, "CONFIRMED", "Ana") to null,
            Triple(declined, "DECLINED", "Bia") to null,
            Triple(waitlisted, "WAITLISTED", "Caio") to 1L,
        )) {
            val (person, status, name) = row
            jdbc.sql("INSERT INTO game_attendance (game_id, group_id, member_user_id, status, waitlist_sequence, responded_at, updated_at, version, member_display_name) VALUES (:game, :group, :member, :status, :sequence, now(), now(), 1, :name)")
                .param("game", game).param("group", group).param("member", person)
                .param("status", status, java.sql.Types.OTHER).param("sequence", sequence, java.sql.Types.BIGINT)
                .param("name", name).update()
        }
        assertEquals(CommunicationResult.Failure(CommunicationError.FORBIDDEN), service.remind(member, group, game, UUID.randomUUID()))
        assertEquals(CommunicationResult.Failure(CommunicationError.INVALID), service.remind(owner, group, UUID.randomUUID(), UUID.randomUUID()))
        val request = UUID.randomUUID()
        val reminder = service.remind(owner, group, game, request).success()
        assertEquals(1, reminder.recipientCount)
        assertEquals(
            "Jogo: domingo, 20/09 às 19:30\nLocal: Arena\n\n✅ Confirmados:\nAna\n\n🕒 Lista de espera:\nCaio\n\n❌ Fora:\nBia",
            reminder.body,
        )
        assertFalse(reminder.body.contains("Inactive Person"))
        assertEquals(game, service.inbox(member, null).success().items.single().message.gameId)
        for (person in listOf(confirmed, declined, waitlisted, inactive, owner)) {
            assertTrue(service.inbox(person, null).success().items.isEmpty())
        }
        assertEquals(reminder, service.remind(owner, group, game, request).success())
        assertEquals(1, service.inbox(member, null).success().items.size)
        assertEquals(CommunicationResult.Failure(CommunicationError.INVALID), service.messages(member, group, MessageChannel.REMINDER, null))
        // GAME_OPEN é canal de sistema: nem aparece na thread, nem aceita publicação pela API.
        assertEquals(CommunicationResult.Failure(CommunicationError.INVALID), service.messages(member, group, MessageChannel.GAME_OPEN, null))
        assertEquals(
            CommunicationResult.Failure(CommunicationError.INVALID),
            service.publish(owner, group, MessageChannel.GAME_OPEN, UUID.randomUUID(), "Jogo liberado"),
        )
    }
    @Test fun `automatic reminders reach only open games and repeat on every run`() {
        val open = game("Treino aberto")
        game("Treino sem prazo", deadlineInHours = -1)
        game("Treino já iniciado", startsInHours = -1, deadlineInHours = -2)
        game("Treino concluído", status = "COMPLETED")
        assertEquals(1, service.remindAutomatically())
        val reminder = service.inbox(member, null).success().items.single().message
        assertEquals(open, reminder.gameId)
        assertEquals(MessageChannel.REMINDER, reminder.channel)
        assertEquals(owner, reminder.authorId)
        assertEquals("Jogo: domingo, 20/09 às 12:00\nLocal: Arena", reminder.body)
        assertEquals(1, service.remindAutomatically())
        assertEquals(2, service.inbox(member, null).success().items.size)
        assertTrue(service.inbox(owner, null).success().items.isEmpty())
    }
    private fun game(
        title: String,
        status: String = "PUBLISHED",
        startsInHours: Long = 48,
        deadlineInHours: Long = 24,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :group, :title, DATE '2026-09-20', TIME '12:00', 'UTC',
                now() + (:starts * interval '1 hour'), 90, now() + (:deadline * interval '1 hour'),
                'Arena', 'Rua Central 100', 12, '$status', now(), now())
        """.trimIndent()).param("id", id).param("group", group).param("title", title)
            .param("starts", startsInHours).param("deadline", deadlineInHours).update()
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
