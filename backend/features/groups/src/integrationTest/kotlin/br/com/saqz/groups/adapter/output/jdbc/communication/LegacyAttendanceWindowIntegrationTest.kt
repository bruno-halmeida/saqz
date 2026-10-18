package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.*
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DEB-067: a janela de presença das 24 h saiu. Nada mais abre uma, mas a mensagem `ATTENDANCE_WINDOW` que já
 * existe no banco (e o valor no enum, que os apps das lojas conhecem) continua legível.
 */
class LegacyAttendanceWindowIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val repository = JdbcGroupCommunicationRepository(source)
    private val service = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), repository)
    private val push = JdbcNotificationPush(source, transaction)

    private val owner = user("Owner")
    private val athlete = user("Athlete")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'Vôlei de Quinta', 'America/Sao_Paulo', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        for (member in listOf(owner, athlete)) jdbc.sql("""
            INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, 'ATHLETE', 'AVULSO', true, now(), now())
        """).param("g", group).param("u", member).update()
        push.register(athlete, UUID.randomUUID(), "athlete-device", "IOS", liveActivityStartToken = "athlete-start")
    }

    @Test fun `a game inside the 24 hour mark opens no window whatever the jobs do`() {
        // Publicado há 2 dias, começa em 23 h, prazo em 20 h: era exatamente o caso que abria a janela.
        game()

        service.remindAutomatically()

        assertEquals(0, windowMessages())
    }

    @Test fun `the old window message is still read in the central`() {
        val game = game()
        val old = transaction.inTransaction {
            repository.publish(
                group, owner, MessageChannel.ATTENDANCE_WINDOW, UUID.randomUUID(),
                "Jogo: quinta, 15/10 às 19:00\nLocal: Arena\n1/12 confirmados\n\nConfirme sua presença.", game,
            )
        }

        val inbox = service.inbox(athlete, null)

        val page = (inbox as CommunicationResult.Success).value
        val item = page.items.single { it.message.id == old.id }
        assertEquals(MessageChannel.ATTENDANCE_WINDOW, item.message.channel)
        assertTrue("1/12 confirmados" in item.message.body)
    }

    @Test fun `the old window push, if still queued, goes out as a regular push without the live activity`() {
        val game = game()
        transaction.inTransaction {
            repository.publish(group, owner, MessageChannel.ATTENDANCE_WINDOW, UUID.randomUUID(), "Confirme sua presença.", game)
        }

        val sent = mutableListOf<NotificationPush>()
        push.drain(NotificationPushSender { _, message -> sent += message; PushDelivery.SENT })

        val message = sent.single()
        assertEquals("ATTENDANCE_WINDOW", message.channel)
        assertEquals("Confirme sua presença.", message.body)
    }

    @Test fun `the device still stores the live activity start token the store apps send`() {
        val stored = jdbc.sql("SELECT live_activity_start_token FROM notification_devices WHERE token = 'athlete-device'")
            .query { rs, _ -> rs.getString(1) }.list().single()

        assertEquals("athlete-start", stored)
    }

    private fun windowMessages() = jdbc.sql("SELECT count(*) FROM group_messages WHERE channel = 'ATTENDANCE_WINDOW'")
        .query(Int::class.java).single()

    private fun game(): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, published_at, created_at, updated_at)
            VALUES (:id, :g, 'Vôlei de Quinta', current_date + 1, '19:00', 'America/Sao_Paulo',
                now() + interval '23 hours', 90, now() + interval '20 hours',
                'Arena', 'Rua 100', 12, 'PUBLISHED', now() - interval '2 days', now(), now())
        """).param("id", id).param("g", group).update()
        return id
    }

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
