package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteManagementRepository
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteRedemptionRepository
import br.com.saqz.groups.adapter.output.jdbc.monthlyinvite.JdbcMonthlyInviteLinkRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.manage.RotateInviteCommand
import br.com.saqz.groups.application.invite.redeem.RedeemInvite
import br.com.saqz.groups.application.invite.redeem.RedeemInviteResult
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** NOT-004 / DEB-106: aviso a dono e admins quando alguém entra ou vira mensalista pelo link de mensalistas. */
class MonthlyJoinNoticeIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val communication = JdbcGroupCommunicationRepository(source)
    private val unlimited = object : SubscriptionLimits {
        override fun groupLimitFor(ownerId: UUID): Int? = null
        override fun athleteLimitFor(ownerId: UUID): Int? = null
    }
    private val onboarding = JdbcOnboardingNotices(source, communication)
    private val redeem = RedeemInvite(
        transaction, JdbcInviteRedemptionRepository(source), unlimited, Clock.systemUTC(),
        firstMember = onboarding, entryRequest = onboarding, monthlyJoin = JdbcMonthlyJoinNotices(source, communication),
    )
    private val monthlyCode = InviteCode.from("44DF8SA8")
    private val publicCode = InviteCode.from("55EG9TB9")

    // Dono com telefone só cadastrado (não confirmado); admin sem telefone; atleta com telefone confirmado.
    private val owner = user("Dona Lu", phone = "+5511911110000", verified = false)
    private val admin = user("Rafa", phone = null, verified = false)
    private val athlete = user("Cris", phone = "+5511922220000", verified = true)
    private val group = UUID.randomUUID()

    init {
        jdbc.sql(
            """INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at,
                   monthly_fee_cents, monthly_due_day)
            VALUES (:id, :owner, :key, 'Vôlei de Quinta', 'America/Sao_Paulo', now(), now(), 8000, 10)""",
        ).param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        membership(owner, "ADMIN", "AVULSO")
        transaction.inTransaction {
            JdbcMonthlyInviteLinkRepository(source).replaceCode(group, monthlyCode.value, Instant.now().plus(Duration.ofDays(7)), owner, Instant.now())
            JdbcInviteManagementRepository(source).rotate(RotateInviteCommand(group, publicCode, owner, null))
        }
    }

    @Test
    fun `primeiro atleta entra pelo link e recebe so o aviso do link, nao o de primeiro atleta`() {
        val ana = user("Ana", phone = "+5511933330000", verified = false)

        assertIs<RedeemInviteResult.Success>(redeem.execute(ana, monthlyCode.value))

        assertEquals(listOf("Ana entrou no Vôlei de Quinta como mensalista."), messages())
        assertEquals(0, count("SELECT count(*) FROM group_messages WHERE channel = 'FIRST_MEMBER'"))
    }

    @Test
    fun `dono e admins recebem na central e no push, quem entrou nao, e o whatsapp vai ao telefone cadastrado`() {
        membership(admin, "ADMIN", "AVULSO")
        membership(athlete, "ATHLETE", "AVULSO")
        val ana = user("Ana", phone = "+5511933330000", verified = true)

        redeem.execute(ana, monthlyCode.value)

        assertEquals(setOf(owner, admin), recipients())
        assertEquals(setOf(owner, admin), pushRecipients())
        // Dono com número não confirmado recebe; admin sem telefone fica só com central e push.
        assertEquals(listOf("+5511911110000"), whatsAppPhones())
        assertEquals("MEMBER_JOINED_BY_LINK", jdbc.sql("SELECT event_kind FROM group_messages").query(String::class.java).single())
    }

    @Test
    fun `os tres textos e nada quando nada muda`() {
        membership(athlete, "ATHLETE", "AVULSO")
        redeem.execute(athlete, monthlyCode.value)
        redeem.execute(athlete, monthlyCode.value)
        jdbc.sql("UPDATE access_groups SET monthly_fee_cents = NULL, monthly_due_day = NULL WHERE id = :g").param("g", group).update()
        val bia = user("Bia", phone = null, verified = false)
        redeem.execute(bia, monthlyCode.value)
        val caio = user("Caio", phone = null, verified = false)
        membership(caio, "ATHLETE", "AVULSO")
        redeem.execute(caio, monthlyCode.value)

        assertEquals(
            listOf(
                "Cris virou mensalista no Vôlei de Quinta.",
                "Bia entrou no Vôlei de Quinta como avulso, porque o grupo está sem mensalidade padrão.",
            ),
            messages(),
        )
    }

    @Test
    fun `os tres avisos usam o nome da conta, mesmo para quem tem apelido no grupo`() {
        val cris = user("Cris Conta", phone = null, verified = false)
        membership(cris, "ATHLETE", "AVULSO")
        jdbc.sql("UPDATE group_memberships SET nickname = 'Crizinha' WHERE user_id = :u").param("u", cris).update()
        redeem.execute(cris, monthlyCode.value)
        redeem.execute(user("Bia Conta", phone = null, verified = false), monthlyCode.value)
        jdbc.sql("UPDATE access_groups SET monthly_fee_cents = NULL, monthly_due_day = NULL WHERE id = :g").param("g", group).update()
        redeem.execute(user("Caio Conta", phone = null, verified = false), monthlyCode.value)

        assertEquals(
            listOf(
                "Cris Conta virou mensalista no Vôlei de Quinta.",
                "Bia Conta entrou no Vôlei de Quinta como mensalista.",
                "Caio Conta entrou no Vôlei de Quinta como avulso, porque o grupo está sem mensalidade padrão.",
            ),
            messages(),
        )
    }

    @Test
    fun `admin avulso que vira mensalista avisa os outros organizadores e nao a si mesmo`() {
        membership(admin, "ADMIN", "AVULSO")

        redeem.execute(admin, monthlyCode.value)

        assertEquals(setOf(owner), recipients())
    }

    @Test
    fun `avisos dos grupos desligado deixa so a central, sem push nem whatsapp`() {
        jdbc.sql("INSERT INTO group_notification_preferences (user_id, notices, push_notices) VALUES (:u, false, false)")
            .param("u", owner).update()

        redeem.execute(user("Ana", phone = null, verified = false), monthlyCode.value)

        assertEquals(setOf(owner), recipients())
        assertEquals(1, count("SELECT count(*) FROM group_notifications WHERE in_app"))
        assertTrue(pushRecipients().isEmpty())
        assertTrue(whatsAppPhones().isEmpty())
    }

    @Test
    fun `pedido de entrada comum continua sem whatsapp e o aviso do link nao vai ao grupo do whatsapp`() {
        jdbc.sql("UPDATE access_groups SET entry_requires_approval = true WHERE id = :g").param("g", group).update()
        jdbc.sql(
            """INSERT INTO group_whatsapp_bindings (group_id, whatsapp_jid, invite_code, group_name, instance_jid, enabled, created_by)
            VALUES (:g, '1203@g.us', 'invite', 'Vôlei', '5511@s.whatsapp.net', true, :owner)""",
        ).param("g", group).param("owner", owner).update()

        assertIs<RedeemInviteResult.Pending>(redeem.execute(user("Davi", phone = null, verified = false), publicCode.value))
        assertEquals(1, count("SELECT count(*) FROM group_messages WHERE channel = 'ENTRY_REQUEST' AND event_kind IS NULL"))
        assertTrue(whatsAppPhones().isEmpty())

        redeem.execute(user("Ana", phone = null, verified = false), monthlyCode.value)
        assertEquals(listOf("+5511911110000"), whatsAppPhones())
        assertEquals(0, count("SELECT count(*) FROM notification_whatsapp_group_queue"))
    }

    private fun messages() =
        jdbc.sql("SELECT body FROM group_messages WHERE event_kind = 'MEMBER_JOINED_BY_LINK' ORDER BY sequence").query(String::class.java).list()

    private fun recipients() = jdbc.sql(
        "SELECT n.recipient_id FROM group_notifications n JOIN group_messages m ON m.id = n.message_id WHERE m.event_kind = 'MEMBER_JOINED_BY_LINK'",
    ).query { rs, _ -> rs.getObject(1, UUID::class.java) }.list().toSet()

    private fun pushRecipients() = jdbc.sql(
        """SELECT n.recipient_id FROM notification_push_queue q JOIN group_notifications n ON n.sequence = q.notification_id
           JOIN group_messages m ON m.id = n.message_id WHERE m.event_kind = 'MEMBER_JOINED_BY_LINK'""",
    ).query { rs, _ -> rs.getObject(1, UUID::class.java) }.list().toSet()

    private fun whatsAppPhones() = jdbc.sql("SELECT phone FROM notification_whatsapp_queue ORDER BY notification_id")
        .query(String::class.java).list()

    private fun count(sql: String) = jdbc.sql(sql).query(Int::class.java).single()

    private fun membership(userId: UUID, role: String, type: String) {
        jdbc.sql(
            """INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, CAST(:role AS group_role), CAST(:t AS athlete_membership_type), true, now(), now())""",
        ).param("g", group).param("u", userId).param("role", role).param("t", type).update()
    }

    private fun user(name: String, phone: String?, verified: Boolean): UUID = UUID.randomUUID().also { id ->
        jdbc.sql(
            """INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, verified_phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, :phone, :verified, now(), now())""",
        ).param("id", id).param("subject", id.toString()).param("name", name).param("phone", phone)
            .param("verified", phone.takeIf { verified }).update()
    }
}
