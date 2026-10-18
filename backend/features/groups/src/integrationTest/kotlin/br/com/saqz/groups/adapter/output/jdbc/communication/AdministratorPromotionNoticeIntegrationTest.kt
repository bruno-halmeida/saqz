package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcMembershipRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.membership.ChangeMemberRole
import br.com.saqz.groups.application.membership.ChangeMemberRoleResult
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.PersistedMembershipRole
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** MEM-001 / DEB-060: quem é promovido a administrador recebe aviso direto (central e push); rebaixar não avisa. */
class AdministratorPromotionNoticeIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val change = ChangeMemberRole(
        JdbcTransactionRunner(source), JdbcGroupReadRepository(source), JdbcMembershipRepository(source),
        GroupAccessPolicy(), promotionNotice = JdbcAdministratorPromotionNotice(source),
    )

    private val owner = user("Dona")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql(
            """INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'QA Vôlei', 'America/Sao_Paulo', now(), now())""",
        ).param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
    }

    @Test fun `promover avisa so a pessoa promovida, no canal que os apps conhecem, com texto curto`() {
        val rafa = member("Rafa")

        assertIs<ChangeMemberRoleResult.Success>(change.execute(owner, group, rafa, PersistedMembershipRole.ADMIN))

        assertEquals("Você agora é administrador de QA Vôlei.", body())
        assertEquals(setOf(rafa), recipients())
        assertEquals(owner, jdbc.sql("SELECT author_id FROM group_messages WHERE channel = 'AUTO_CONFIRMATION'")
            .query(UUID::class.java).single())
        assertTrue(pushEnabled(rafa))
        assertEquals(1, queuedPushes(rafa))
    }

    @Test fun `repetir a promocao nao manda dois avisos`() {
        val rafa = member("Rafa")

        change.execute(owner, group, rafa, PersistedMembershipRole.ADMIN)
        change.execute(owner, group, rafa, PersistedMembershipRole.ADMIN)

        assertEquals(1, count())
    }

    @Test fun `o mesmo minuto anotado duas vezes na mesma transacao grava um aviso so`() {
        val rafa = member("Rafa")
        val notice = JdbcAdministratorPromotionNotice(source)

        JdbcTransactionRunner(source).inTransaction { notice.promoted(group, rafa); notice.promoted(group, rafa) }

        assertEquals(1, count())
    }

    @Test fun `rebaixar nao avisa ninguem`() {
        val rafa = member("Rafa", "ADMIN")

        assertIs<ChangeMemberRoleResult.Success>(change.execute(owner, group, rafa, PersistedMembershipRole.ATHLETE))

        assertEquals(0, count())
        assertEquals(0, queuedPushes(rafa))
    }

    @Test fun `preferencia de avisos da pessoa vale so para o push, a promocao aparece na central`() {
        val rafa = member("Rafa")
        jdbc.sql("INSERT INTO group_notification_preferences (user_id, notices, push_notices) VALUES (:u, false, false)")
            .param("u", rafa).update()

        change.execute(owner, group, rafa, PersistedMembershipRole.ADMIN)

        assertEquals(1, count())
        assertEquals(setOf(rafa), recipients())
        assertEquals(false, pushEnabled(rafa))
    }

    @Test fun `falha ao gravar o aviso nao desfaz a promocao`() {
        jdbc.sql("ALTER TABLE group_messages ADD CONSTRAINT boom CHECK (channel <> 'AUTO_CONFIRMATION')").update()
        val rafa = member("Rafa")

        assertIs<ChangeMemberRoleResult.Success>(change.execute(owner, group, rafa, PersistedMembershipRole.ADMIN))

        assertEquals("ADMIN", jdbc.sql("SELECT role::text FROM group_memberships WHERE group_id = :g AND user_id = :u")
            .param("g", group).param("u", rafa).query(String::class.java).single())
        assertEquals(0, count())
    }

    private fun count() = jdbc.sql("SELECT count(*) FROM group_messages WHERE channel = 'AUTO_CONFIRMATION'")
        .query(Int::class.java).single()

    private fun body() = jdbc.sql("SELECT body FROM group_messages WHERE channel = 'AUTO_CONFIRMATION'")
        .query(String::class.java).single()

    private fun recipients() = jdbc.sql(
        "SELECT n.recipient_id FROM group_notifications n JOIN group_messages m ON m.id = n.message_id " +
            "WHERE m.channel = 'AUTO_CONFIRMATION' AND n.in_app",
    ).query(UUID::class.java).list().toSet()

    private fun pushEnabled(recipient: UUID) =
        jdbc.sql("SELECT push_enabled FROM notification_delivery_context WHERE recipient_id = :r AND channel = 'AUTO_CONFIRMATION'")
            .param("r", recipient).query(Boolean::class.javaObjectType).single()

    private fun queuedPushes(recipient: UUID) = jdbc.sql(
        "SELECT count(*) FROM notification_push_queue q JOIN group_notifications n ON n.sequence = q.notification_id " +
            "WHERE n.recipient_id = :r",
    ).param("r", recipient).query(Int::class.java).single()

    private fun member(name: String, role: String = "ATHLETE"): UUID = user(name).also { id ->
        jdbc.sql("INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES (:g, :u, CAST(:r AS group_role), now(), now())")
            .param("g", group).param("u", id).param("r", role).update()
    }

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql(
            """INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""",
        ).param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
