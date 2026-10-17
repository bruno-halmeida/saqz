package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.group.delete.JdbcGroupDeletionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.GROUP_DELETED_BODY
import br.com.saqz.groups.application.communication.NotificationPush
import br.com.saqz.groups.application.communication.NotificationPushSender
import br.com.saqz.groups.application.communication.PushDelivery
import br.com.saqz.groups.application.delete.DeleteGroup
import br.com.saqz.groups.application.delete.DeleteGroupResult
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * DEB-028 (SAI-008): excluir o grupo avisa todos os membros por push, mesmo com o grupo já invisível (`deleted_at`).
 * Canal AUTO_CONFIRMATION (os apps das lojas já o conhecem, o push leva o corpo), sem jogo vinculado.
 */
class GroupDeletedNoticeIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val push = JdbcNotificationPush(source, transaction)
    private val deleteGroup = DeleteGroup(transaction, JdbcGroupDeletionRepository(source))

    private val owner = user("Dona")
    private val admin = user("Admin")
    private val athlete = user("Atleta")
    private val inactive = user("Inativo")
    private val outsider = user("De fora")
    private val group = createGroup(owner, "Vôlei de quinta")
    private val otherGroup = createGroup(owner, "Outro grupo")

    init {
        member(group, admin, "ADMIN"); member(group, athlete, "ATHLETE"); member(group, inactive, "ATHLETE", active = false)
        member(otherGroup, athlete, "ATHLETE")
        for ((u, device) in listOf(admin to "admin-device", athlete to "athlete-device", inactive to "inactive-device", outsider to "outsider-device")) {
            push.register(u, UUID.randomUUID(), device, "ANDROID")
        }
    }

    @Test fun `todos os membros recebem um push neutro com o texto no corpo e o grupo ja invisivel`() {
        assertSame(DeleteGroupResult.Success, deleteGroup.execute(owner, group))
        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE id = '$group' AND deleted_at IS NOT NULL"))

        val sent = drain()

        assertEquals(setOf("admin-device", "athlete-device", "inactive-device"), sent.map { it.first }.toSet())
        assertEquals(3, sent.size)
        assertTrue(sent.all { (_, push) ->
            push.body == GROUP_DELETED_BODY && push.channel == "AUTO_CONFIRMATION" && push.groupId == group && push.gameId == null
        })
        assertEquals("Um dos seus grupos foi excluído pelo organizador.", GROUP_DELETED_BODY)
    }

    @Test fun `dono, quem nao e membro e outros grupos nao recebem`() {
        deleteGroup.execute(owner, group)

        val sent = drain()

        assertTrue(sent.none { it.first == "outsider-device" })
        assertEquals(0, count("SELECT count(*) FROM group_notifications WHERE recipient_id = '$owner'"))
        assertEquals(0, count("SELECT count(*) FROM group_messages WHERE group_id = '$otherGroup'"))
        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE id = '$otherGroup' AND deleted_at IS NULL"))
    }

    @Test fun `exclusao reprocessada nao repete aviso nem push`() {
        deleteGroup.execute(owner, group)
        assertSame(DeleteGroupResult.GroupNotFound, deleteGroup.execute(owner, group))
        assertEquals(1, count("SELECT count(*) FROM group_messages WHERE group_id = '$group' AND channel = 'AUTO_CONFIRMATION'"))
        assertEquals(3, count("SELECT count(*) FROM group_notifications n JOIN group_messages m ON m.id = n.message_id WHERE m.group_id = '$group'"))

        assertEquals(3, drain().size)
        assertEquals(0, drain().size)
        assertEquals(3, count("SELECT count(*) FROM notification_push_deliveries"))
    }

    @Test fun `quem desligou os avisos no push nao recebe e o aviso nao aparece na central`() {
        jdbc.sql("INSERT INTO group_notification_preferences (user_id, push_notices) VALUES (:u, false)").param("u", athlete).update()

        deleteGroup.execute(owner, group)

        assertEquals(setOf("admin-device", "inactive-device"), drain().map { it.first }.toSet())
        // A central filtra grupo excluído: o aviso só existe como push.
        assertEquals(0, count("""SELECT count(*) FROM group_notifications n JOIN group_messages m ON m.id = n.message_id
            JOIN access_groups g ON g.id = m.group_id AND g.deleted_at IS NULL WHERE n.recipient_id = '$admin'"""))
    }

    @Test fun `recusa de quem nao e o dono nao cria aviso`() {
        assertSame(DeleteGroupResult.AccessForbidden, deleteGroup.execute(admin, group))
        assertEquals(0, count("SELECT count(*) FROM group_messages WHERE group_id = '$group'"))
        assertEquals(0, drain().size)
    }

    private fun drain(): List<Pair<String, NotificationPush>> {
        val sent = mutableListOf<Pair<String, NotificationPush>>()
        push.drain(NotificationPushSender { token, message -> sent += token to message; PushDelivery.SENT })
        return sent
    }

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }

    private fun createGroup(owner: UUID, name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, :name, 'UTC', now(), now())""")
            .param("id", id).param("owner", owner).param("key", UUID.randomUUID()).param("name", name).update()
    }

    private fun member(group: UUID, user: UUID, role: String, active: Boolean = true) {
        jdbc.sql("""INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, CAST(:role AS group_role), 'AVULSO', :active, now(), now())""")
            .param("g", group).param("u", user).param("role", role).param("active", active).update()
    }

    private fun count(sql: String) = jdbc.sql(sql).query(Int::class.java).single()
}
