package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteRedemptionRepository
import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcMembershipRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.entryrequest.ApproveEntryRequest
import br.com.saqz.groups.application.entryrequest.ApproveEntryRequestResult
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.redeem.RedeemInvite
import br.com.saqz.groups.application.invite.redeem.RedeemInviteResult
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** CONV-007 / DEB-036: quem teve o pedido aprovado recebe aviso direto (central e push), uma vez por pedido. */
class EntryApprovalNoticeIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val redemptions = JdbcInviteRedemptionRepository(source)
    private val code = InviteCode.from(Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 3 }))
    private val redeem = RedeemInvite(transaction, redemptions, Unlimited, Clock.systemUTC())
    private val approve = ApproveEntryRequest(
        transaction, JdbcGroupReadRepository(source), redemptions, JdbcMembershipRepository(source), Unlimited,
        GroupAccessPolicy(), Clock.systemUTC(), JdbcEntryApprovalNotice(source),
    )

    private val owner = user("Dona")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql(
            """INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, entry_requires_approval, created_at, updated_at)
            VALUES (:id, :owner, :key, 'QA Vôlei', 'America/Sao_Paulo', true, now(), now())""",
        ).param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        jdbc.sql(
            "INSERT INTO group_invites (group_id, token_digest, created_by_user_id, created_at, expires_at) " +
                "VALUES (:g, :d, :o, now(), now() + interval '7 days')",
        ).param("g", group).param("d", InviteTokenDigest.sha256(code).toByteArray()).param("o", owner).update()
    }

    @Test fun `aprovar avisa so a pessoa aprovada, no canal que os apps conhecem, com texto curto`() {
        val rafa = user("Rafa")
        assertIs<RedeemInviteResult.Pending>(redeem.execute(rafa, code.value))

        assertIs<ApproveEntryRequestResult.Success>(approve.execute(owner, group, rafa))

        assertEquals("Você entrou no QA Vôlei.", body())
        assertEquals(setOf(rafa), recipients())
        assertEquals(owner, jdbc.sql("SELECT author_id FROM group_messages WHERE channel = 'AUTO_CONFIRMATION'")
            .query(UUID::class.java).single())
        assertTrue(pushEnabled(rafa))
        assertEquals(1, queuedPushes(rafa))
    }

    @Test fun `aprovar de novo o mesmo pedido nao repete o aviso`() {
        val rafa = user("Rafa")
        redeem.execute(rafa, code.value)

        approve.execute(owner, group, rafa)
        approve.execute(owner, group, rafa)

        assertEquals(1, count())
    }

    @Test fun `o mesmo pedido anotado duas vezes na mesma transacao grava um aviso so`() {
        val rafa = user("Rafa")
        redeem.execute(rafa, code.value)
        approve.execute(owner, group, rafa)
        val requestedAt = java.time.Instant.parse("2026-10-17T12:00:00Z")
        val notice = JdbcEntryApprovalNotice(source)

        transaction.inTransaction { notice.approved(group, rafa, requestedAt); notice.approved(group, rafa, requestedAt) }

        assertEquals(2, count())
    }

    @Test fun `novo pedido depois de sair de novo avisa outra vez`() {
        val rafa = user("Rafa")
        redeem.execute(rafa, code.value)
        approve.execute(owner, group, rafa)
        jdbc.sql("DELETE FROM group_memberships WHERE group_id = :g AND user_id = :u").param("g", group).param("u", rafa).update()
        Thread.sleep(5)
        redeem.execute(rafa, code.value)

        approve.execute(owner, group, rafa)

        assertEquals(2, count())
    }

    @Test fun `preferencia de avisos da pessoa vale para push e lista`() {
        val rafa = user("Rafa")
        jdbc.sql("INSERT INTO group_notification_preferences (user_id, notices, push_notices) VALUES (:u, false, false)")
            .param("u", rafa).update()
        redeem.execute(rafa, code.value)

        approve.execute(owner, group, rafa)

        assertEquals(1, count())
        assertEquals(emptySet(), recipients())
        assertEquals(false, pushEnabled(rafa))
    }

    @Test fun `falha ao gravar o aviso nao desfaz a aprovacao`() {
        jdbc.sql("ALTER TABLE group_messages ADD CONSTRAINT boom CHECK (channel <> 'AUTO_CONFIRMATION')").update()
        val rafa = user("Rafa")
        redeem.execute(rafa, code.value)

        assertIs<ApproveEntryRequestResult.Success>(approve.execute(owner, group, rafa))

        assertEquals(1, jdbc.sql("SELECT count(*) FROM group_memberships WHERE group_id = :g AND user_id = :u")
            .param("g", group).param("u", rafa).query(Int::class.java).single())
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

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql(
            """INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""",
        ).param("id", id).param("subject", id.toString()).param("name", name).update()
    }

    private object Unlimited : SubscriptionLimits {
        override fun groupLimitFor(ownerId: UUID): Int? = null
        override fun athleteLimitFor(ownerId: UUID): Int? = null
    }
}
