package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGroupCommunicationRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.SettleWaitlistAtDeadline
import br.com.saqz.groups.application.communication.MessageChannel
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/** Prazo vencido com fila e vaga: ordem de chegada promove; escolha manual avisa dono e admins uma vez. */
class WaitlistDeadlineIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val communication = JdbcGroupCommunicationRepository(source)
    private val service = SettleWaitlistAtDeadline(
        transaction,
        JdbcDeadlineWaitlistRepository(source),
        JdbcAttendanceCommandRepository(source),
        AttendanceChargeAdapter(ChargeTransactions(transaction, JdbcChargeTransactionRepository(source)) { Instant.now() }),
        { candidate, body ->
            communication.publish(candidate.groupId, candidate.ownerId, MessageChannel.WAITLIST_DEADLINE, UUID.randomUUID(), body, candidate.gameId)
        },
        Instant::now,
    )

    private val owner = user("Owner")
    private val admin = user("Admin")
    private val confirmed = user("Confirmed")
    private val first = user("First")
    private val second = user("Second")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'QA Vôlei', 'America/Sao_Paulo', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        membership(owner, "ADMIN"); membership(admin, "ADMIN")
        for (athlete in listOf(confirmed, first, second)) membership(athlete, "ATHLETE")
    }

    @Test fun `ordem de chegada promove a fila nas vagas que sobraram e não repete`() {
        val game = game(deadlineInMinutes = -5, capacity = 2)
        answer(game, confirmed, "CONFIRMED", null)
        answer(game, first, "WAITLISTED", 1)
        answer(game, second, "WAITLISTED", 2)

        assertEquals(1, service.run())

        assertEquals("CONFIRMED", status(game, first))
        assertEquals("WAITLISTED", status(game, second))
        assertEquals(listOf("SYSTEM"), jdbc.sql("SELECT source::text FROM attendance_events WHERE game_id = :g").param("g", game).query(String::class.java).list())
        assertEquals(0, service.run())
    }

    @Test fun `escolha manual avisa só dono e admins, uma vez por jogo`() {
        jdbc.sql("UPDATE access_groups SET promotion_mode = 'MANUAL' WHERE id = :g").param("g", group).update()
        val game = game(deadlineInMinutes = -5, capacity = 2)
        answer(game, first, "WAITLISTED", 1)

        assertEquals(1, service.run())
        assertEquals(0, service.run())

        assertEquals("WAITLISTED", status(game, first))
        val messages = jdbc.sql("SELECT id FROM group_messages WHERE game_id = :g AND channel = 'WAITLIST_DEADLINE'")
            .param("g", game).query(UUID::class.java).list().filterNotNull()
        assertEquals(1, messages.size)
        assertEquals(setOf(owner, admin), recipients(messages.single()))
        assertEquals(true, jdbc.sql("SELECT push_enabled FROM notification_delivery_context WHERE recipient_id = :r AND game_id = :g")
            .param("r", admin).param("g", game).query(Boolean::class.javaObjectType).single())
    }

    @Test fun `dono e admin que já confirmaram presença continuam recebendo o aviso de prazo vencido`() {
        jdbc.sql("UPDATE access_groups SET promotion_mode = 'MANUAL' WHERE id = :g").param("g", group).update()
        val game = game(deadlineInMinutes = -5, capacity = 3)
        answer(game, owner, "CONFIRMED", null)
        answer(game, admin, "CONFIRMED", null)
        answer(game, first, "WAITLISTED", 1)

        assertEquals(1, service.run())

        val message = jdbc.sql("SELECT id FROM group_messages WHERE game_id = :g AND channel = 'WAITLIST_DEADLINE'")
            .param("g", game).query(UUID::class.java).single()
        assertEquals(setOf(owner, admin), recipients(message))
        for (recipient in listOf(owner, admin)) {
            assertEquals(true, jdbc.sql("SELECT push_enabled FROM notification_delivery_context WHERE recipient_id = :r AND game_id = :g")
                .param("r", recipient).param("g", game).query(Boolean::class.javaObjectType).single())
        }
    }

    @Test fun `prazo aberto ou jogo lotado não é candidato`() {
        val open = game(deadlineInMinutes = 30, capacity = 2)
        answer(open, first, "WAITLISTED", 1)
        val full = game(deadlineInMinutes = -5, capacity = 2)
        answer(full, confirmed, "CONFIRMED", null)
        answer(full, admin, "CONFIRMED", null)
        answer(full, second, "WAITLISTED", 1)

        assertEquals(0, service.run())
        assertEquals("WAITLISTED", status(open, first))
        assertEquals("WAITLISTED", status(full, second))
    }

    private fun membership(user: UUID, role: String) = jdbc.sql("""
        INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
        VALUES (:g, :u, CAST(:role AS group_role), 'AVULSO', true, now(), now())
    """).param("g", group).param("u", user).param("role", role).update()

    private fun game(deadlineInMinutes: Int, capacity: Int): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, published_at, created_at, updated_at)
            VALUES (:id, :g, 'QA Vôlei', current_date + 1, '20:00', 'America/Sao_Paulo',
                now() + interval '6 hours', 120, now() + make_interval(mins => :deadlineIn),
                'Quadra QA', 'Av. Paulista, 1578', :capacity, 'PUBLISHED', now() - interval '2 days', now(), now())
        """).param("id", id).param("g", group).param("deadlineIn", deadlineInMinutes).param("capacity", capacity).update()
        return id
    }

    private fun answer(game: UUID, member: UUID, status: String, sequence: Long?) = jdbc.sql("""
        INSERT INTO game_attendance(game_id, group_id, member_user_id, status, waitlist_sequence, responded_at, updated_at, version, member_display_name)
        VALUES (:game, :g, :u, CAST(:status AS attendance_status), :sequence, now(), now(), 1, 'Pessoa')
    """).param("game", game).param("g", group).param("u", member).param("status", status).param("sequence", sequence).update()

    private fun status(game: UUID, member: UUID) = jdbc.sql("SELECT status::text FROM game_attendance WHERE game_id = :g AND member_user_id = :u")
        .param("g", game).param("u", member).query(String::class.java).single()

    private fun recipients(message: UUID) = jdbc.sql("SELECT recipient_id FROM group_notifications WHERE message_id = :m")
        .param("m", message).query(UUID::class.java).list().filterNotNull().toSet()

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
