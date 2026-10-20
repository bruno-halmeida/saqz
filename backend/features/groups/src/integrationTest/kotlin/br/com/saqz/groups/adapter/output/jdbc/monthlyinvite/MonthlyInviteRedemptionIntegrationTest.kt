package br.com.saqz.groups.adapter.output.jdbc.monthlyinvite

import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteManagementRepository
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInvitePreviewRepository
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteRedemptionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteKind
import br.com.saqz.groups.application.invite.manage.RotateInviteCommand
import br.com.saqz.groups.application.invite.preview.AnonymousInvitePreviewRateLimiter
import br.com.saqz.groups.application.invite.preview.PreviewInvite
import br.com.saqz.groups.application.invite.preview.PreviewInviteResult
import br.com.saqz.groups.application.invite.redeem.MonthlyJoinNotice
import br.com.saqz.groups.application.invite.redeem.MonthlyJoinOutcome
import br.com.saqz.groups.application.invite.redeem.RedeemInvite
import br.com.saqz.groups.application.invite.redeem.RedeemInviteResult
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** CONV-011: prévia e resgate pelo link de mensalistas, no banco. */
class MonthlyInviteRedemptionIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val redemptions = JdbcInviteRedemptionRepository(source)
    private val links = JdbcMonthlyInviteLinkRepository(source)
    private val now = Instant.now()
    private val notices = mutableListOf<Pair<UUID, MonthlyJoinOutcome>>()
    private var athleteLimit: Int? = null
    private val limits = object : SubscriptionLimits {
        override fun groupLimitFor(ownerId: UUID): Int? = null
        override fun athleteLimitFor(ownerId: UUID): Int? = athleteLimit
    }
    private val redeem = RedeemInvite(
        transaction, redemptions, limits, Clock.fixed(now, ZoneOffset.UTC),
        monthlyJoin = MonthlyJoinNotice { _, user, outcome -> notices += user to outcome },
    )
    private val preview = PreviewInvite(
        transaction, JdbcInvitePreviewRepository(source), AnonymousInvitePreviewRateLimiter(), Clock.fixed(now, ZoneOffset.UTC),
    )

    private val owner = user("Dona", phone = null)
    private val group = UUID.randomUUID()
    private val monthlyCode = code(1)
    private val publicCode = code(2)

    init {
        jdbc.sql(
            """INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at,
                   monthly_fee_cents, monthly_due_day, entry_requires_approval)
            VALUES (:id, :owner, :key, 'Vôlei de Quinta', 'America/Sao_Paulo', now(), now(), 8000, 10, true)""",
        ).param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        transaction.inTransaction {
            links.replaceCode(group, monthlyCode.value, now.plus(Duration.ofDays(7)), owner, now)
            JdbcInviteManagementRepository(source).rotate(RotateInviteCommand(group, publicCode, owner, null))
        }
    }

    @Test
    fun `prevista do link diz MONTHLY, sem aprovacao e com o local escondido`() {
        val card = assertIs<PreviewInviteResult.Success>(preview.execute(null, "1.1.1.1", monthlyCode.value)).card

        assertEquals(InviteKind.MONTHLY, card.inviteKind)
        assertFalse(card.entryRequiresApproval)
        assertEquals(now.plus(Duration.ofDays(7)).toEpochMilli(), card.expiresAt!!.toEpochMilli())
        assertEquals("Dona", card.inviterName)

        val public = assertIs<PreviewInviteResult.Success>(preview.execute(null, "1.1.1.1", publicCode.value)).card
        assertEquals(InviteKind.PUBLIC, public.inviteKind)
        assertTrue(public.entryRequiresApproval)
    }

    @Test
    fun `prevista do link vencido mostra a data e do link invalidado ou trocado e invalida`() {
        expire()
        assertIs<PreviewInviteResult.Expired>(preview.execute(null, "1.1.1.1", monthlyCode.value))

        transaction.inTransaction { links.replaceCode(group, code(3).value, now.plus(Duration.ofDays(7)), owner, now) }
        assertSame(PreviewInviteResult.Invalid, preview.execute(null, "1.1.1.1", monthlyCode.value))

        transaction.inTransaction { links.invalidate(group, now) }
        assertSame(PreviewInviteResult.Invalid, preview.execute(null, "1.1.1.1", code(3).value))
    }

    @Test
    fun `pessoa nova entra como mensalista sem pedido, mesmo em grupo com aprovacao`() {
        val ana = user("Ana")

        val result = redeem.execute(ana, monthlyCode.value)

        assertEquals(RedeemInviteResult.Success(group, GroupRole.ATHLETE, AthleteMembershipType.MENSALISTA), result)
        assertEquals("MENSALISTA", membershipType(ana))
        assertNotNull(jdbc.sql("SELECT mensalista_since FROM group_memberships WHERE group_id = :g AND user_id = :u")
            .param("g", group).param("u", ana).query(java.sql.Date::class.java).single())
        assertEquals(0, count("SELECT count(*) FROM group_entry_requests WHERE group_id = '$group'"))
        assertEquals(listOf(ana to MonthlyJoinOutcome.JOINED_MENSALISTA), notices)
    }

    @Test
    fun `sem mensalidade padrao na hora entra como avulso`() {
        jdbc.sql("UPDATE access_groups SET monthly_fee_cents = NULL, monthly_due_day = NULL WHERE id = :g").param("g", group).update()
        val ana = user("Ana")

        val result = redeem.execute(ana, monthlyCode.value)

        assertEquals(RedeemInviteResult.Success(group, GroupRole.ATHLETE, AthleteMembershipType.AVULSO), result)
        assertEquals("AVULSO", membershipType(ana))
        assertEquals(listOf(ana to MonthlyJoinOutcome.JOINED_AVULSO_NO_DEFAULT_FEE), notices)
    }

    @Test
    fun `avulso vira mensalista e mensalista que reabre nao muda`() {
        val bia = member("Bia", "AVULSO")

        assertEquals(
            RedeemInviteResult.Success(group, GroupRole.ATHLETE, AthleteMembershipType.MENSALISTA),
            redeem.execute(bia, monthlyCode.value),
        )
        assertEquals("MENSALISTA", membershipType(bia))
        assertEquals(
            RedeemInviteResult.Success(group, GroupRole.ATHLETE, AthleteMembershipType.MENSALISTA),
            redeem.execute(bia, monthlyCode.value),
        )
        assertEquals(listOf(bia to MonthlyJoinOutcome.BECAME_MENSALISTA), notices)
    }

    @Test
    fun `quem saiu antes entra pelo link sem pedido`() {
        val rafa = member("Rafa", "AVULSO")
        transaction.inTransaction { JdbcAthleteRepository(source).remove(group, rafa) }

        val result = redeem.execute(rafa, monthlyCode.value)

        assertEquals(RedeemInviteResult.Success(group, GroupRole.ATHLETE, AthleteMembershipType.MENSALISTA), result)
        assertEquals(0, count("SELECT count(*) FROM group_entry_requests WHERE group_id = '$group'"))
    }

    @Test
    fun `grupo cheio bloqueia e nada e gravado`() {
        athleteLimit = 1
        member("Cris", "AVULSO")
        val ana = user("Ana")

        assertSame(RedeemInviteResult.AthleteLimitExceeded, redeem.execute(ana, monthlyCode.value))
        assertEquals(0, count("SELECT count(*) FROM group_memberships WHERE user_id = '$ana'"))
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `link vencido nao entra e conta tentativa`() {
        expire()
        val ana = user("Ana")

        assertSame(RedeemInviteResult.InvalidOrExpired, redeem.execute(ana, monthlyCode.value))
        assertEquals(1, count("SELECT invalid_count FROM invite_redemption_limits WHERE user_id = '$ana'"))
    }

    @Test
    fun `entrar com o telefone cadastrado apaga a linha de convidado, outro telefone nao`() {
        invitee("Ana", "+5511988887777")
        invitee("Outro", "+5511911112222")
        val ana = user("Ana", phone = "+5511988887777")

        redeem.execute(ana, monthlyCode.value)

        assertEquals(1, count("SELECT count(*) FROM group_monthly_invitees WHERE group_id = '$group'"))
        assertEquals(0, count("SELECT count(*) FROM group_monthly_invitees WHERE phone = '+5511988887777'"))
    }

    @Test
    fun `convite publico continua entrando como avulso com pedido`() {
        val ana = user("Ana")

        assertIs<RedeemInviteResult.Pending>(redeem.execute(ana, publicCode.value))
        assertTrue(notices.isEmpty())
    }

    private fun expire() {
        jdbc.sql("UPDATE group_monthly_invite_links SET expires_at = :t WHERE group_id = :g")
            .param("t", java.sql.Timestamp.from(now.minusSeconds(60))).param("g", group).update()
    }

    private fun invitee(name: String, phone: String) {
        jdbc.sql(
            """INSERT INTO group_monthly_invitees (id, group_id, name, phone, status, request_id, created_at)
            VALUES (:id, :g, :name, :phone, 'WAITING', :r, now())""",
        ).param("id", UUID.randomUUID()).param("g", group).param("name", name).param("phone", phone)
            .param("r", UUID.randomUUID()).update()
    }

    private fun membershipType(userId: UUID) =
        jdbc.sql("SELECT membership_type::text FROM group_memberships WHERE group_id = :g AND user_id = :u")
            .param("g", group).param("u", userId).query(String::class.java).single()

    private fun count(sql: String) = jdbc.sql(sql).query(Int::class.java).single()

    private fun member(name: String, type: String) = user(name).also {
        jdbc.sql(
            "INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at) " +
                "VALUES (:g, :u, 'ATHLETE', CAST(:t AS athlete_membership_type), true, now(), now())",
        ).param("g", group).param("u", it).param("t", type).update()
    }

    private fun user(name: String, phone: String? = "+5511999999999"): UUID = UUID.randomUUID().also { id ->
        jdbc.sql(
            """INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, :phone, now(), now())""",
        ).param("id", id).param("subject", id.toString()).param("name", name).param("phone", phone).update()
    }

    private fun code(seed: Int) = InviteCode.from(InviteCode.SHORT_ALPHABET.substring(seed, seed + 8))
}
