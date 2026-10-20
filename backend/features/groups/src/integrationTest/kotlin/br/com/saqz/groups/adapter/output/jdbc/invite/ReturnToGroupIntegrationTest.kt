package br.com.saqz.groups.adapter.output.jdbc.invite

import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcMembershipRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.entryrequest.ApproveEntryRequest
import br.com.saqz.groups.application.entryrequest.ApproveEntryRequestResult
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.redeem.RedeemInvite
import br.com.saqz.groups.application.invite.redeem.RedeemInviteResult
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** SAI-007 / DEB-027: quem saiu ou foi removido volta por pedido; saída antiga (sem a marca) entra direto. */
class ReturnToGroupIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val redemptions = JdbcInviteRedemptionRepository(source)
    private val code = InviteCode.from(Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 9 }))
    private val redeem = RedeemInvite(transaction, redemptions, Unlimited, Clock.systemUTC())
    private val approve = ApproveEntryRequest(
        transaction, JdbcGroupReadRepository(source), redemptions, JdbcMembershipRepository(source), Unlimited,
        GroupAccessPolicy(), Clock.systemUTC(),
    )

    private val owner = user("Dona")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql(
            """INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'QA Vôlei', 'America/Sao_Paulo', now(), now())""",
        ).param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        jdbc.sql(
            "INSERT INTO group_invites (group_id, token_digest, created_by_user_id, created_at, expires_at) " +
                "VALUES (:g, :d, :o, now(), now() + interval '7 days')",
        ).param("g", group).param("d", InviteTokenDigest.sha256(code).toByteArray()).param("o", owner).update()
    }

    @Test fun `saida nova vira pedido mesmo com convite que entra direto e nao cria vinculo`() {
        val rafa = member("Rafa")
        leave(rafa)

        assertIs<RedeemInviteResult.Pending>(redeem.execute(rafa, code.value))

        assertEquals(1, count("SELECT count(*) FROM group_entry_requests WHERE group_id = '$group' AND user_id = '$rafa'"))
        assertEquals(0, count("SELECT count(*) FROM group_memberships WHERE group_id = '$group' AND user_id = '$rafa'"))
        assertTrue(marked(rafa))
    }

    @Test fun `saida antiga sem a marca entra direto como antes`() {
        val rafa = user("Rafa")
        jdbc.sql("INSERT INTO group_membership_removals (group_id, user_id, removed_at) VALUES (:g, :u, now())")
            .param("g", group).param("u", rafa).update()
        assertFalse(marked(rafa))

        assertEquals(RedeemInviteResult.Success(group, GroupRole.ATHLETE), redeem.execute(rafa, code.value))
    }

    @Test fun `quem nunca saiu entra direto`() {
        assertEquals(RedeemInviteResult.Success(group, GroupRole.ATHLETE), redeem.execute(user("Nova"), code.value))
    }

    @Test fun `aprovar o pedido limpa a marca, mantem a linha da saida e deixa a pessoa entrar`() {
        val rafa = member("Rafa")
        leave(rafa)
        redeem.execute(rafa, code.value)

        assertIs<ApproveEntryRequestResult.Success>(approve.execute(owner, group, rafa))

        assertFalse(marked(rafa))
        assertEquals(1, count("SELECT count(*) FROM group_membership_removals WHERE group_id = '$group' AND user_id = '$rafa'"))
        assertEquals(0, count("SELECT count(*) FROM group_entry_requests WHERE group_id = '$group' AND user_id = '$rafa'"))
        assertEquals(RedeemInviteResult.Success(group, GroupRole.ATHLETE), redeem.execute(rafa, code.value))
    }

    @Test fun `sair de novo depois de aprovado marca outra vez`() {
        val rafa = member("Rafa")
        leave(rafa)
        redeem.execute(rafa, code.value)
        approve.execute(owner, group, rafa)

        leave(rafa)

        assertTrue(marked(rafa))
        assertIs<RedeemInviteResult.Pending>(redeem.execute(rafa, code.value))
    }

    @Test fun `quem voltou aceito como mensalista nasce mensalista com a mensalidade do grupo e mensalista_since hoje`() {
        setMonthlyFee(8000, 10)
        val rafa = member("Rafa")
        leave(rafa)
        redeem.execute(rafa, code.value)

        assertIs<ApproveEntryRequestResult.Success>(approve.execute(owner, group, rafa, AthleteMembershipType.MENSALISTA))

        val row = jdbc.sql(
            "SELECT membership_type::text AS t, monthly_fee_cents IS NULL AS own_fee_null, " +
                "mensalista_since = (now() AT TIME ZONE 'America/Sao_Paulo')::date AS today " +
                "FROM group_memberships WHERE group_id = :g AND user_id = :u",
        ).param("g", group).param("u", rafa).query { rs, _ -> Triple(rs.getString("t"), rs.getBoolean("own_fee_null"), rs.getBoolean("today")) }
            .single()
        assertEquals(Triple("MENSALISTA", true, true), row)
        assertFalse(marked(rafa))
        assertEquals(0, count("SELECT count(*) FROM group_entry_requests WHERE group_id = '$group' AND user_id = '$rafa'"))
    }

    @Test fun `aceitar como mensalista sem mensalidade padrao recusa e deixa o pedido e a marca`() {
        setMonthlyFee(null, null)
        val rafa = member("Rafa")
        leave(rafa)
        redeem.execute(rafa, code.value)

        assertIs<ApproveEntryRequestResult.MonthlyFeeRequired>(approve.execute(owner, group, rafa, AthleteMembershipType.MENSALISTA))

        assertEquals(1, count("SELECT count(*) FROM group_entry_requests WHERE group_id = '$group' AND user_id = '$rafa'"))
        assertEquals(0, count("SELECT count(*) FROM group_memberships WHERE group_id = '$group' AND user_id = '$rafa'"))
        assertTrue(marked(rafa))
    }

    @Test fun `aceitar sem tipo segue avulso mesmo com mensalidade padrao`() {
        setMonthlyFee(8000, 10)
        val rafa = member("Rafa")
        leave(rafa)
        redeem.execute(rafa, code.value)

        assertIs<ApproveEntryRequestResult.Success>(approve.execute(owner, group, rafa))

        assertEquals(
            "AVULSO",
            jdbc.sql("SELECT membership_type::text FROM group_memberships WHERE group_id = :g AND user_id = :u")
                .param("g", group).param("u", rafa).query(String::class.java).single(),
        )
    }

    private fun setMonthlyFee(cents: Int?, dueDay: Int?) {
        jdbc.sql("UPDATE access_groups SET monthly_fee_cents = :c, monthly_due_day = :d WHERE id = :g")
            .param("c", cents).param("d", dueDay).param("g", group).update()
    }

    private fun leave(userId: UUID) = transaction.inTransaction { JdbcAthleteRepository(source).remove(group, userId) }

    private fun marked(userId: UUID) =
        jdbc.sql("SELECT requires_approval FROM group_membership_removals WHERE group_id = :g AND user_id = :u")
            .param("g", group).param("u", userId).query(Boolean::class.javaObjectType).single()

    private fun count(sql: String) = jdbc.sql(sql).query(Int::class.java).single()

    private fun member(name: String) = user(name).also {
        jdbc.sql(
            "INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at) " +
                "VALUES (:g, :u, 'ATHLETE', 'AVULSO', true, now(), now())",
        ).param("g", group).param("u", it).update()
    }

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
