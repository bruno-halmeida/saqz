package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.assertEquals

/**
 * DEB-074: grupo congelado (dono sem plano ativo, o critério das escritas) não recebe o lembrete automático nem o
 * "jogo liberado", e por isso nada do grupo chega ao WhatsApp. Grupo com plano segue igual.
 */
class FrozenGroupRemindersIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val repository = JdbcGroupCommunicationRepository(source)
    private val frozenOwner = user("Frozen")
    private val paidOwner = user("Paid")
    private val frozen = UUID.randomUUID()
    private val paid = UUID.randomUUID()
    private val service = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), repository) { it != frozen }

    init {
        group(frozen, frozenOwner, "Congelado")
        group(paid, paidOwner, "Com plano")
    }

    @BeforeEach fun reset() {
        jdbc.sql("TRUNCATE games CASCADE").update()
        jdbc.sql("TRUNCATE group_messages CASCADE").update()
    }

    @Test fun `the automatic reminder skips the frozen group and keeps the paid one`() {
        game(frozen)
        game(paid)

        assertEquals(1, service.remindAutomatically())

        assertEquals(listOf(paid), groupsWith("REMINDER"))
        assertEquals(0L, whatsappQueueFor(frozen))
        assertEquals(1L, whatsappQueueFor(paid))
    }

    @Test fun `the 14h open game notice skips the frozen group and keeps the paid one`() {
        game(frozen)
        game(paid)

        assertEquals(1, service.announceOpenGames())

        assertEquals(listOf(paid), groupsWith("GAME_OPEN"))
    }

    @Test fun `the open game notice at publication skips the frozen group and keeps the paid one`() {
        val frozenGame = game(frozen)
        val paidGame = game(paid)

        assertEquals(false, service.announceOpenGame(frozen, frozenGame))
        assertEquals(true, service.announceOpenGame(paid, paidGame))

        assertEquals(listOf(paid), groupsWith("GAME_OPEN"))
    }

    @Test fun `with an unrestricted access both groups still get the notices`() {
        game(frozen)
        game(paid)
        val open = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), repository, writeAccess = GroupWriteAccess.Unrestricted)

        assertEquals(2, open.remindAutomatically())
    }

    private fun groupsWith(channel: String): List<UUID> = jdbc.sql(
        "SELECT group_id FROM group_messages WHERE channel = CAST(:c AS group_message_channel) ORDER BY group_id",
    ).param("c", channel).query { rs, _ -> rs.getObject(1, UUID::class.java) }.list()

    private fun whatsappQueueFor(group: UUID): Long = jdbc.sql(
        "SELECT count(*) FROM notification_whatsapp_group_queue q JOIN group_messages m ON m.id = q.message_id WHERE m.group_id = :g",
    ).param("g", group).query(Long::class.java).single()

    private fun group(id: UUID, owner: UUID, name: String) {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, :name, 'America/Sao_Paulo', now(), now())""")
            .param("id", id).param("owner", owner).param("key", UUID.randomUUID()).param("name", name).update()
        jdbc.sql("""INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, 'ADMIN', 'AVULSO', true, now(), now())""").param("g", id).param("u", owner).update()
        val athlete = user("Athlete $name")
        jdbc.sql("""INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, 'ATHLETE', 'AVULSO', true, now(), now())""").param("g", id).param("u", athlete).update()
        jdbc.sql("""INSERT INTO group_whatsapp_bindings(group_id, whatsapp_jid, invite_code, group_name, instance_jid, enabled, created_by)
            VALUES (:g, :jid, :code, :name, '5511900000000@s.whatsapp.net', true, :owner)""")
            .param("g", id).param("jid", "${id}@g.us").param("code", id.toString()).param("name", name).param("owner", owner).update()
    }

    private fun game(group: UUID): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, published_at, created_at, updated_at)
            VALUES (:id, :g, 'Vôlei', current_date + 2, '19:00', 'America/Sao_Paulo',
                now() + interval '2 days', 90, now() + interval '1 day',
                'Arena', 'Rua 100', 12, 'PUBLISHED', now() - interval '1 day', now(), now())
        """).param("id", id).param("g", group).update()
        return id
    }

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
