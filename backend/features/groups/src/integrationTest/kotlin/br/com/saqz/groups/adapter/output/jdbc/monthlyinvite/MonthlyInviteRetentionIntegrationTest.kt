package br.com.saqz.groups.adapter.output.jdbc.monthlyinvite

import br.com.saqz.groups.adapter.output.jdbc.group.delete.JdbcGroupDeletionRepository
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteRedemptionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.delete.DeleteGroupResult
import br.com.saqz.groups.application.invite.redeem.RedeemInvite
import br.com.saqz.groups.application.invite.redeem.RedeemInviteResult
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteePhone
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteeStatus
import br.com.saqz.groups.application.monthlyinvite.NewMonthlyInvitee
import br.com.saqz.groups.application.monthlyinvite.PurgeMonthlyInvitees
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** CONV-010 / SAI-008: nome e telefone dos convidados têm prazo (LGPD). */
class MonthlyInviteRetentionIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val invitees = JdbcMonthlyInviteRepository(source)
    private val links = JdbcMonthlyInviteLinkRepository(source)
    private val now = Instant.parse("2026-12-01T12:00:00Z")
    private val purge = PurgeMonthlyInvitees(invitees, Clock.fixed(now, ZoneOffset.UTC))
    private val owner = user()

    @Test
    fun `link vencido ha 29 dias fica e ha 31 dias apaga`() {
        val recent = groupWithInvitee(expiresAt = now.minus(Duration.ofDays(29)))
        val old = groupWithInvitee(expiresAt = now.minus(Duration.ofDays(31)))

        assertEquals(1, transaction.inTransaction { purge.run() })

        assertEquals(1, inviteeCount(recent))
        assertEquals(0, inviteeCount(old))
    }

    @Test
    fun `link invalidado ha 29 dias fica e ha 31 dias apaga`() {
        val recent = groupWithInvitee(expiresAt = now.plus(Duration.ofDays(3)))
        val old = groupWithInvitee(expiresAt = now.plus(Duration.ofDays(3)))
        transaction.inTransaction {
            links.invalidate(recent, now.minus(Duration.ofDays(29)))
            links.invalidate(old, now.minus(Duration.ofDays(31)))
        }

        transaction.inTransaction { purge.run() }

        assertEquals(1, inviteeCount(recent))
        assertEquals(0, inviteeCount(old))
    }

    @Test
    fun `link renovado por um lote novo segura as linhas de quem nunca entrou`() {
        val group = groupWithInvitee(expiresAt = now.minus(Duration.ofDays(40)))
        transaction.inTransaction { links.replaceCode(group, "55EG9TB9", now.plus(Duration.ofDays(7)), owner, now) }

        transaction.inTransaction { purge.run() }

        assertEquals(1, inviteeCount(group))
    }

    @Test
    fun `excluir o grupo apaga os convidados na hora e o link passa a dizer grupo excluido`() {
        val group = groupWithInvitee(expiresAt = now.plus(Duration.ofDays(7)))
        val code = links.find(group)!!.shortCode!!

        assertSame(DeleteGroupResult.Success, transaction.inTransaction { JdbcGroupDeletionRepository(source).softDelete(owner, group) })

        assertEquals(0, inviteeCount(group))
        val redeem = RedeemInvite(
            transaction, JdbcInviteRedemptionRepository(source),
            object : SubscriptionLimits {
                override fun groupLimitFor(ownerId: UUID): Int? = null
                override fun athleteLimitFor(ownerId: UUID): Int? = null
            },
            Clock.systemUTC(),
        )
        assertSame(RedeemInviteResult.GroupDeleted, redeem.execute(user(), code))
    }

    private fun groupWithInvitee(expiresAt: Instant): UUID {
        val group = UUID.randomUUID()
        jdbc.sql(
            """INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'Vôlei', 'America/Sao_Paulo', now(), now())""",
        ).param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        val code = (2..9).map { "23456789ABCDEFGHJKMNPQRSTUVWXYZ".random() }.joinToString("")
        transaction.inTransaction {
            links.replaceCode(group, code, Instant.now().plus(Duration.ofDays(7)), owner, now)
            invitees.insert(
                group,
                listOf(NewMonthlyInvitee(UUID.randomUUID(), "Ana", MonthlyInviteePhone.parse("11988880001")!!)),
                MonthlyInviteeStatus.WAITING,
                owner,
                UUID.randomUUID(),
                now,
            )
        }
        jdbc.sql("UPDATE group_monthly_invite_links SET expires_at = :t WHERE group_id = :g")
            .param("t", java.sql.Timestamp.from(expiresAt)).param("g", group).update()
        return group
    }

    private fun inviteeCount(group: UUID) =
        jdbc.sql("SELECT count(*) FROM group_monthly_invitees WHERE group_id = :g").param("g", group).query(Int::class.java).single()

    private fun user(): UUID = UUID.randomUUID().also { id ->
        jdbc.sql(
            """INSERT INTO access_users(id, firebase_subject, email_verified, display_name, created_at, updated_at)
            VALUES (:id, :subject, true, 'Dona', now(), now())""",
        ).param("id", id).param("subject", id.toString()).update()
    }
}
