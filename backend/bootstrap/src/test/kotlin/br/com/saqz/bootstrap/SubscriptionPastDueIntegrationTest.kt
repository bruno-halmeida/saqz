package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcTrialEndingNotice
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionPastDueRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionRepository
import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.application.SubscriptionPastDueReminders
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Aviso de atraso de pagamento ponta a ponta: o job, o banco migrado inteiro, a central e a fila de push. */
class SubscriptionPastDueIntegrationTest {
    private val source = TestPostgres.migrated("classpath:db/migration", owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val owner = user("Dona")
    private val athlete = user("Rafa")
    private val group = UUID.randomUUID()
    private val whatsApp = mutableListOf<String>()
    private val now = Instant.now()
    private val dueDay get() = Subscription.pastDueDay(now.minus(Duration.ofDays(3)))

    init {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'QA Vôlei', 'America/Sao_Paulo', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        membership(owner, "ADMIN")
        membership(athlete, "ATHLETE")
        JdbcSubscriptionRepository(source).insert(
            Subscription(owner, Plan.TITULAR, SubscriptionCycle.MONTHLY, "cus_1", "sub_1", AsaasBillingType.PIX, now.plus(Duration.ofDays(20)),
                SubscriptionStatus.PAST_DUE, pastDueSince = dueDay.atStartOfDay(Subscription.PAST_DUE_ZONE).toInstant(),
                firstConfirmedAt = now.minus(Duration.ofDays(60))),
        )
        jdbc.sql("""INSERT INTO subscription_events (id, asaas_event_id, type, payload, owner_user_id, processed_at, created_at)
            VALUES (:id, 'evt_1', 'PAYMENT_OVERDUE', :payload, :owner, now(), now())""")
            .param("id", UUID.randomUUID()).param("owner", owner)
            .param("payload", """{"payment":{"id":"pay_1","subscription":"sub_1","dueDate":"$dueDay","invoiceUrl":"https://www.asaas.com/i/abc"}}""").update()
    }

    private fun job(at: Instant = now) = SubscriptionPastDueReminders(
        JdbcSubscriptionPastDueRepository(source), JdbcTrialEndingNotice(source), { _, body -> whatsApp += body }, { null },
        Clock.fixed(at, ZoneOffset.UTC),
    )

    @Test fun `o aviso fica na central e na fila de push so do dono, com o push curto, e o WhatsApp recebe a fatura`() {
        assertEquals(1, job().run())

        val message = jdbc.sql("SELECT id, group_id, body, push_body FROM group_messages WHERE channel = 'TRIAL_ENDING'")
            .query { rs, _ -> listOf(rs.getObject("id", UUID::class.java), rs.getObject("group_id", UUID::class.java), rs.getString("body"), rs.getString("push_body")) }.single()
        assertEquals(group, message[1])
        assertEquals("Seu pagamento do Saqz continua pendente. Seus grupos serão congelados em breve se não houver pagamento. Pagar agora: https://www.asaas.com/i/abc", message[2])
        assertEquals("Seu pagamento do Saqz continua pendente. Toque para regularizar.", message[3])
        assertEquals(
            listOf(owner),
            jdbc.sql("SELECT recipient_id FROM group_notifications WHERE message_id = :m").param("m", message[0]).query(UUID::class.java).list(),
        )
        assertEquals(1, count("notification_push_queue"))
        assertTrue(pushEnabled())
        assertEquals(listOf(message[2]), whatsApp)
    }

    @Test fun `o aviso de atraso nao entra na fila de WhatsApp do grupo nem na do particular`() {
        job().run()

        assertEquals(0, count("notification_whatsapp_queue"))
        assertEquals(0, count("notification_whatsapp_group_queue"))
    }

    @Test fun `repetir a rodada no mesmo dia nao reenvia`() {
        assertEquals(1, job().run())
        assertEquals(0, job().run())
        assertEquals(1, count("group_messages WHERE channel = 'TRIAL_ENDING'"))
    }

    @Test fun `quem pagou nao recebe`() {
        jdbc.sql("UPDATE subscriptions SET status = 'ACTIVE', past_due_since = NULL WHERE owner_user_id = :o").param("o", owner).update()

        assertEquals(0, job().run())
        assertEquals(0, count("group_messages WHERE channel = 'TRIAL_ENDING'"))
    }

    @Test fun `quem cancelou nao recebe`() {
        jdbc.sql("UPDATE subscriptions SET canceled_at = now() WHERE owner_user_id = :o").param("o", owner).update()

        assertEquals(0, job().run())
    }

    @Test fun `depois do ultimo dia os grupos ja congelaram e nao sai nada`() {
        assertEquals(0, job(now.plus(Duration.ofDays(6))).run())
    }

    @Test fun `o acesso termina no mesmo instante que o job considera o ultimo dia`() {
        val lastDay = dueDay.plusDays(7)
        val freeze = Subscription.pastDueFreezeAt(dueDay.atStartOfDay(Subscription.PAST_DUE_ZONE).toInstant())

        assertEquals(lastDay.plusDays(1).atStartOfDay(Subscription.PAST_DUE_ZONE).toInstant(), freeze)
        assertFalse(freeze.isBefore(lastDay.atStartOfDay(Subscription.PAST_DUE_ZONE).toInstant()))
    }

    private fun pushEnabled() = jdbc.sql("SELECT push_enabled FROM notification_delivery_context WHERE recipient_id = :r AND channel = 'TRIAL_ENDING'")
        .param("r", owner).query(Boolean::class.javaObjectType).single()

    private fun count(table: String) = jdbc.sql("SELECT count(*) FROM $table").query(Int::class.java).single()

    private fun membership(user: UUID, role: String) = jdbc.sql("""
        INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
        VALUES (:g, :u, CAST(:role AS group_role), 'MENSALISTA', true, now(), now())""")
        .param("g", group).param("u", user).param("role", role).update()

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
