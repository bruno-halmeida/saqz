package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.attendance.AttendanceChargeAdapter
import br.com.saqz.groups.adapter.output.jdbc.attendance.JdbcAttendanceCommandRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteRedemptionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.AttendanceCommandResult
import br.com.saqz.groups.application.attendance.RespondAttendance
import br.com.saqz.groups.application.communication.RemindStalledEntryRequests
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.redeem.RedeemInvite
import br.com.saqz.groups.application.invite.redeem.RedeemInviteResult
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Avisos FIRST_MEMBER e FIRST_CONFIRMATION: dono e admins, uma vez por grupo, na transação do evento. */
class OnboardingNoticesIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val notices = JdbcOnboardingNotices(source, JdbcGroupCommunicationRepository(source))
    private val code = InviteCode.from(Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 }))
    private val redeem = RedeemInvite(
        transaction, JdbcInviteRedemptionRepository(source), Unlimited, Clock.systemUTC(), firstMember = notices,
        entryRequest = notices,
    )
    private val respond = RespondAttendance(
        transaction,
        JdbcAttendanceCommandRepository(source),
        AttendanceChargeAdapter(ChargeTransactions(transaction, JdbcChargeTransactionRepository(source)) { Instant.now() }),
        Instant::now,
        firstConfirmation = notices,
    )

    private val reminders = RemindStalledEntryRequests(transaction, notices)

    private val owner = user("Dona")
    private val admin = user("Admin")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'QA Vôlei', 'America/Sao_Paulo', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        membership(owner, "ADMIN")
        jdbc.sql("INSERT INTO group_invites (group_id, token_digest, created_by_user_id, created_at, expires_at) VALUES (:g, :d, :o, now(), now() + interval '7 days')")
            .param("g", group).param("d", InviteTokenDigest.sha256(code).toByteArray()).param("o", owner).update()
    }

    @Test fun `primeiro atleta que entra avisa o dono, sem ele, e o segundo nao avisa`() {
        val rafa = user("Rafa Moreira")
        val bia = user("Bia")

        assertIs<RedeemInviteResult.Success>(redeem.execute(rafa, code.value))
        assertIs<RedeemInviteResult.Success>(redeem.execute(bia, code.value))

        val message = jdbc.sql("SELECT id, body, game_id, author_id FROM group_messages WHERE group_id = :g AND channel = 'FIRST_MEMBER'")
            .param("g", group).query { rs, _ -> listOf(rs.getObject("id", UUID::class.java), rs.getString("body"), rs.getObject("game_id"), rs.getObject("author_id")) }.single()
        assertEquals("Rafa Moreira entrou no QA Vôlei. A galera começou a chegar.", message[1])
        assertEquals(null, message[2])
        assertEquals(rafa, message[3])
        assertEquals(setOf(owner), recipients(message[0] as UUID))
        assertTrue(pushEnabled(owner, "FIRST_MEMBER"))
    }

    @Test fun `apelido do usuario vale mais que o nome`() {
        val rafa = user("Rafael Moreira")
        jdbc.sql("UPDATE access_users SET nickname = 'Rafa' WHERE id = :u").param("u", rafa).update()

        redeem.execute(rafa, code.value)

        assertEquals("Rafa entrou no QA Vôlei. A galera começou a chegar.", body("FIRST_MEMBER"))
    }

    @Test fun `grupo que ja teve outro membro, mesmo removido, nao avisa`() {
        val gone = user("Saiu")
        jdbc.sql("INSERT INTO group_membership_removals (group_id, user_id, removed_at) VALUES (:g, :u, now())")
            .param("g", group).param("u", gone).update()

        redeem.execute(user("Rafa"), code.value)

        assertEquals(0, count("FIRST_MEMBER"))
    }

    @Test fun `entrada que exige aprovacao nao avisa`() {
        jdbc.sql("UPDATE access_groups SET entry_requires_approval = true WHERE id = :g").param("g", group).update()

        assertIs<RedeemInviteResult.Pending>(redeem.execute(user("Rafa"), code.value))

        assertEquals(0, count("FIRST_MEMBER"))
    }

    @Test fun `preferencias de avisos do destinatario valem so para o push, a central mostra`() {
        jdbc.sql("INSERT INTO group_notification_preferences (user_id, notices, push_notices) VALUES (:u, false, false)")
            .param("u", owner).update()

        redeem.execute(user("Rafa"), code.value)

        val message = jdbc.sql("SELECT id FROM group_messages WHERE channel = 'FIRST_MEMBER'").query(UUID::class.java).single()
        assertEquals(setOf(owner), recipients(message))
        assertEquals(false, pushEnabled(owner, "FIRST_MEMBER"))
    }

    @Test fun `pedido de entrada avisa dono e admins, sem quem pediu, e o pedido repetido nao avisa de novo`() {
        membership(admin, "ADMIN")
        jdbc.sql("UPDATE access_groups SET entry_requires_approval = true WHERE id = :g").param("g", group).update()
        val rafa = user("Rafa Moreira")

        assertIs<RedeemInviteResult.Pending>(redeem.execute(rafa, code.value))
        assertIs<RedeemInviteResult.Pending>(redeem.execute(rafa, code.value))

        val message = jdbc.sql("SELECT id, body, game_id, author_id FROM group_messages WHERE group_id = :g AND channel = 'ENTRY_REQUEST'")
            .param("g", group).query { rs, _ -> listOf(rs.getObject("id", UUID::class.java), rs.getString("body"), rs.getObject("game_id"), rs.getObject("author_id")) }.single()
        assertEquals("Rafa Moreira pediu para entrar no QA Vôlei.", message[1])
        assertEquals(null, message[2])
        assertEquals(rafa, message[3])
        assertEquals(setOf(owner, admin), recipients(message[0] as UUID))
        assertTrue(pushEnabled(owner, "ENTRY_REQUEST"))
        assertTrue(pushEnabled(admin, "ENTRY_REQUEST"))
        assertEquals(0, count("FIRST_MEMBER"))
    }

    @Test fun `novo pedido depois de aprovado ou recusado avisa de novo`() {
        jdbc.sql("UPDATE access_groups SET entry_requires_approval = true WHERE id = :g").param("g", group).update()
        val rafa = user("Rafa")

        redeem.execute(rafa, code.value)
        jdbc.sql("DELETE FROM group_entry_requests WHERE user_id = :u").param("u", rafa).update()
        Thread.sleep(5)
        redeem.execute(rafa, code.value)

        assertEquals(2, count("ENTRY_REQUEST"))
    }

    @Test fun `pedido de entrada segue a preferencia de push do gestor e aparece na central`() {
        jdbc.sql("UPDATE access_groups SET entry_requires_approval = true WHERE id = :g").param("g", group).update()
        jdbc.sql("INSERT INTO group_notification_preferences (user_id, notices, push_notices) VALUES (:u, false, false)")
            .param("u", owner).update()

        redeem.execute(user("Rafa"), code.value)

        val message = jdbc.sql("SELECT id FROM group_messages WHERE channel = 'ENTRY_REQUEST'").query(UUID::class.java).single()
        assertEquals(setOf(owner), recipients(message))
        assertEquals(false, pushEnabled(owner, "ENTRY_REQUEST"))
    }

    @Test fun `duas entradas simultaneas geram um aviso so`() {
        val users = List(4) { user("Atleta $it") }
        val ready = CountDownLatch(users.size)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(users.size)
        try {
            users.map { u -> pool.submit<RedeemInviteResult> { ready.countDown(); start.await(); redeem.execute(u, code.value) } }
                .also { ready.await(); start.countDown() }
                .forEach { assertIs<RedeemInviteResult.Success>(it.get()) }
        } finally { pool.shutdownNow() }

        assertEquals(1, count("FIRST_MEMBER"))
    }

    @Test fun `falha ao gravar o aviso nao desfaz a entrada`() {
        jdbc.sql("ALTER TABLE group_messages ADD CONSTRAINT boom CHECK (channel <> 'FIRST_MEMBER')").update()
        val rafa = user("Rafa")

        assertIs<RedeemInviteResult.Success>(redeem.execute(rafa, code.value))

        assertEquals(1, jdbc.sql("SELECT count(*) FROM group_memberships WHERE group_id = :g AND user_id = :u")
            .param("g", group).param("u", rafa).query(Int::class.java).single())
        assertEquals(0, count("FIRST_MEMBER"))
    }

    @Test fun `primeira confirmacao do proprio atleta avisa dono e admins, e a segunda nao`() {
        membership(admin, "ADMIN")
        val rafa = member("Rafa Moreira"); val bia = member("Bia")
        val game = game()

        confirm(game, rafa)
        confirm(game, bia)

        val message = jdbc.sql("SELECT id, body, game_id FROM group_messages WHERE channel = 'FIRST_CONFIRMATION'")
            .query { rs, _ -> listOf(rs.getObject("id", UUID::class.java), rs.getString("body"), rs.getObject("game_id", UUID::class.java)) }.single()
        assertTrue((message[1] as String).startsWith("Rafa Moreira confirmou presença no jogo de "))
        assertTrue((message[1] as String).endsWith(" às 20:00. A lista começou a fechar sozinha."))
        assertEquals(game, message[2])
        assertEquals(setOf(owner, admin), recipients(message[0] as UUID))
        assertTrue(pushEnabled(owner, "FIRST_CONFIRMATION"))
    }

    @Test fun `confirmacao do dono, do organizador e do sistema nao contam`() {
        val rafa = member("Rafa"); val bia = member("Bia"); val carol = member("Carol")
        val game = game()

        respond.execute(owner, group, game, intent = AttendanceIntent.CONFIRM)
        respond.execute(owner, group, game, memberId = rafa, intent = AttendanceIntent.CONFIRM, source = AttendanceSource.ORGANIZER, reason = "Confirmado na quadra")
        respond.execute(bia, group, game, intent = AttendanceIntent.CONFIRM, source = AttendanceSource.SYSTEM)
        assertEquals(0, count("FIRST_CONFIRMATION"))

        confirm(game, carol)
        assertEquals(1, count("FIRST_CONFIRMATION"))
    }

    @Test fun `admin que confirma primeiro avisa so o dono`() {
        membership(admin, "ADMIN")
        val game = game()

        confirm(game, admin)

        val message = jdbc.sql("SELECT id FROM group_messages WHERE channel = 'FIRST_CONFIRMATION'").query(UUID::class.java).single()
        assertEquals(setOf(owner), recipients(message))
    }

    @Test fun `recusar e voltar a confirmar nao gera um segundo aviso`() {
        val rafa = member("Rafa"); val game = game()

        confirm(game, rafa)
        respond.execute(rafa, group, game, intent = AttendanceIntent.DECLINE)
        confirm(game, rafa)

        assertEquals(1, count("FIRST_CONFIRMATION"))
    }

    @Test fun `confirmacoes simultaneas geram um aviso so`() {
        val athletes = List(4) { member("Atleta $it") }; val game = game()
        val ready = CountDownLatch(athletes.size)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(athletes.size)
        try {
            athletes.map { a -> pool.submit<AttendanceCommandResult> { ready.countDown(); start.await(); respond.execute(a, group, game, intent = AttendanceIntent.CONFIRM) } }
                .also { ready.await(); start.countDown() }
                .forEach { assertIs<AttendanceCommandResult.Success>(it.get()) }
        } finally { pool.shutdownNow() }

        assertEquals(1, count("FIRST_CONFIRMATION"))
    }

    @Test fun `pedido parado ha 2 dias ganha um segundo aviso a dono e admins, uma vez so`() {
        membership(admin, "ADMIN")
        jdbc.sql("UPDATE access_groups SET entry_requires_approval = true WHERE id = :g").param("g", group).update()
        val rafa = user("Rafa Moreira")
        redeem.execute(rafa, code.value)
        ageRequest(rafa, "2 days 1 minute")

        assertEquals(1, reminders.run())
        assertEquals(0, reminders.run())

        val reminder = jdbc.sql("SELECT id, body, author_id FROM group_messages WHERE channel = 'ENTRY_REQUEST' AND body LIKE '%ainda espera%'")
            .query { rs, _ -> listOf(rs.getObject("id", UUID::class.java), rs.getString("body"), rs.getObject("author_id")) }.single()
        assertEquals("Rafa Moreira ainda espera sua resposta para entrar no QA Vôlei. Abra o app para aprovar ou recusar.", reminder[1])
        assertEquals(rafa, reminder[2])
        assertEquals(setOf(owner, admin), recipients(reminder[0] as UUID))
        val pushed = jdbc.sql("SELECT recipient_id FROM notification_delivery_context WHERE channel = 'ENTRY_REQUEST' AND push_enabled AND body LIKE '%ainda espera%'")
            .query(UUID::class.java).set()
        assertEquals(setOf(owner, admin), pushed)
        assertEquals(2, count("ENTRY_REQUEST"))
    }

    @Test fun `pedido com menos de 2 dias nao ganha segundo aviso`() {
        jdbc.sql("UPDATE access_groups SET entry_requires_approval = true WHERE id = :g").param("g", group).update()
        val rafa = user("Rafa")
        redeem.execute(rafa, code.value)
        ageRequest(rafa, "47 hours")

        assertEquals(0, reminders.run())

        assertEquals(1, count("ENTRY_REQUEST"))
    }

    @Test fun `pedido ja respondido nao ganha segundo aviso`() {
        jdbc.sql("UPDATE access_groups SET entry_requires_approval = true WHERE id = :g").param("g", group).update()
        val rafa = user("Rafa")
        redeem.execute(rafa, code.value)
        ageRequest(rafa, "3 days")
        jdbc.sql("DELETE FROM group_entry_requests WHERE user_id = :u").param("u", rafa).update()

        assertEquals(0, reminders.run())

        assertEquals(1, count("ENTRY_REQUEST"))
    }

    @Test fun `pedido novo depois de uma resposta tem o proprio segundo aviso`() {
        jdbc.sql("UPDATE access_groups SET entry_requires_approval = true WHERE id = :g").param("g", group).update()
        val rafa = user("Rafa")
        redeem.execute(rafa, code.value)
        ageRequest(rafa, "3 days")
        assertEquals(1, reminders.run())
        jdbc.sql("DELETE FROM group_entry_requests WHERE user_id = :u").param("u", rafa).update()
        redeem.execute(rafa, code.value)
        ageRequest(rafa, "2 days 1 minute")

        assertEquals(1, reminders.run())

        assertEquals(4, count("ENTRY_REQUEST"))
    }

    @Test fun `quem entrou direto depois do pedido nao ganha segundo aviso`() {
        jdbc.sql("UPDATE access_groups SET entry_requires_approval = true WHERE id = :g").param("g", group).update()
        val rafa = user("Rafa")
        redeem.execute(rafa, code.value)
        ageRequest(rafa, "3 days")
        // Aprovação desligada depois: a entrada é direta e a linha do pedido continua (causa fora do escopo do DEB-068).
        jdbc.sql("UPDATE access_groups SET entry_requires_approval = false WHERE id = :g").param("g", group).update()
        assertIs<RedeemInviteResult.Success>(redeem.execute(rafa, code.value))
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM group_entry_requests WHERE user_id = :u").param("u", rafa).query(Long::class.java).single())

        assertEquals(0, reminders.run())

        assertEquals(1, count("ENTRY_REQUEST"))
    }

    @Test fun `grupo excluido nao recebe segundo aviso`() {
        jdbc.sql("UPDATE access_groups SET entry_requires_approval = true WHERE id = :g").param("g", group).update()
        val rafa = user("Rafa")
        redeem.execute(rafa, code.value)
        ageRequest(rafa, "3 days")
        jdbc.sql("UPDATE access_groups SET deleted_at = now() WHERE id = :g").param("g", group).update()

        assertEquals(0, reminders.run())
    }

    private fun ageRequest(user: UUID, age: String) =
        jdbc.sql("UPDATE group_entry_requests SET requested_at = now() - CAST(:age AS interval) WHERE user_id = :u")
            .param("age", age).param("u", user).update()

    private fun confirm(game: UUID, member: UUID) =
        assertIs<AttendanceCommandResult.Success>(respond.execute(member, group, game, intent = AttendanceIntent.CONFIRM))

    private fun count(channel: String) = jdbc.sql("SELECT count(*) FROM group_messages WHERE channel = CAST(:c AS group_message_channel)")
        .param("c", channel).query(Int::class.java).single()

    private fun body(channel: String) = jdbc.sql("SELECT body FROM group_messages WHERE channel = CAST(:c AS group_message_channel)")
        .param("c", channel).query(String::class.java).single()

    private fun pushEnabled(recipient: UUID, channel: String) =
        jdbc.sql("SELECT push_enabled FROM notification_delivery_context WHERE recipient_id = :r AND channel = CAST(:c AS group_message_channel)")
            .param("r", recipient).param("c", channel).query(Boolean::class.javaObjectType).single()

    private fun recipients(message: UUID) = jdbc.sql("SELECT recipient_id FROM group_notifications WHERE message_id = :m AND in_app")
        .param("m", message).query(UUID::class.java).list().filterNotNull().toSet()

    private fun member(name: String) = user(name).also { membership(it, "ATHLETE") }

    private fun membership(user: UUID, role: String) = jdbc.sql("""
        INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
        VALUES (:g, :u, CAST(:role AS group_role), 'MENSALISTA', true, now(), now())
    """).param("g", group).param("u", user).param("role", role).update()

    private fun game(): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, published_at, created_at, updated_at)
            VALUES (:id, :g, 'QA Vôlei', current_date + 1, '20:00', 'America/Sao_Paulo',
                now() + interval '30 hours', 120, now() + interval '20 hours',
                'Quadra QA', 'Av. Paulista, 1578', 12, 'PUBLISHED', now() - interval '2 days', now(), now())
        """).param("id", id).param("g", group).update()
        return id
    }

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }

    private object Unlimited : SubscriptionLimits {
        override fun groupLimitFor(ownerId: UUID): Int? = null
        override fun athleteLimitFor(ownerId: UUID): Int? = null
    }
}
