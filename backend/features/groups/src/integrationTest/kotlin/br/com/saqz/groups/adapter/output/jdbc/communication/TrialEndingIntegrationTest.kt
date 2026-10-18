package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.plan.JdbcTrialUsage
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.subscription.TrialUsage
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Aviso TRIAL_ENDING (só o dono, central e push) e o resumo de uso do teste. */
class TrialEndingIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val notice = JdbcTrialEndingNotice(source)
    private val usage = JdbcTrialUsage(source)

    private val owner = user("Dona")
    private val admin = user("Admin")
    private val athlete = user("Rafa")
    private val outsider = user("Outro Dono")
    private val group = group(owner, "QA Vôlei")

    init {
        membership(group, owner, "ADMIN")
        membership(group, admin, "ADMIN")
        membership(group, athlete, "ATHLETE")
    }

    @Test fun `aviso fica na central e no push so do dono`() {
        assertTrue(notice.publish(owner, "Faltam 3 dias do seu teste do Organizador."))

        val message = jdbc.sql("SELECT id, group_id, author_id, recipient_count FROM group_messages WHERE channel = 'TRIAL_ENDING'")
            .query { rs, _ -> listOf(rs.getObject("id", UUID::class.java), rs.getObject("group_id"), rs.getObject("author_id"), rs.getInt("recipient_count")) }.single()
        assertEquals(group, message[1])
        assertEquals(owner, message[2])
        assertEquals(1, message[3])
        assertEquals(
            listOf(owner),
            jdbc.sql("SELECT recipient_id FROM group_notifications WHERE message_id = :m AND in_app")
                .param("m", message[0]).query(UUID::class.java).list(),
        )
        assertTrue(pushEnabled(owner))
    }

    @Test fun `preferencia de avisos desligada vale para push e central`() {
        jdbc.sql("INSERT INTO group_notification_preferences (user_id, notices, push_notices) VALUES (:u, false, false)")
            .param("u", owner).update()

        notice.publish(owner, "Falta 1 dia do seu teste do Organizador.")

        assertFalse(pushEnabled(owner))
        assertEquals(0, jdbc.sql("SELECT count(*) FROM group_notifications WHERE in_app").query(Int::class.java).single())
    }

    @Test fun `dono sem grupo ativo nao tem onde guardar o aviso`() {
        assertFalse(notice.publish(outsider, "Hoje é o último dia do seu teste do Organizador."))
        assertEquals(0, jdbc.sql("SELECT count(*) FROM group_messages WHERE channel = 'TRIAL_ENDING'").query(Int::class.java).single())
    }

    @Test fun `grupo mais antigo e ativo hospeda o aviso`() {
        val newer = group(owner, "Segundo grupo")
        membership(newer, owner, "ADMIN")
        jdbc.sql("UPDATE access_groups SET created_at = now() - interval '2 days' WHERE id = :g").param("g", group).update()

        notice.publish(owner, "Faltam 3 dias do seu teste do Organizador.")

        assertEquals(group, jdbc.sql("SELECT group_id FROM group_messages WHERE channel = 'TRIAL_ENDING'").query(UUID::class.java).single())
    }

    @Test fun `uso conta jogos publicados ou encerrados, atletas confirmados e entradas no caixa do periodo`() {
        val since = Instant.now().minusSeconds(14 * 86400)
        val until = Instant.now().plusSeconds(60)
        val first = game(group, "PUBLISHED", Instant.now().minusSeconds(3600))
        val second = game(group, "COMPLETED", Instant.now().minusSeconds(7200))
        game(group, "DRAFT", null)
        game(group, "PUBLISHED", since.minusSeconds(86400)) // antes do teste
        game(group(outsider, "De outro"), "PUBLISHED", Instant.now().minusSeconds(3600))
        attendance(first, athlete, "CONFIRMED")
        attendance(second, athlete, "CONFIRMED") // o mesmo atleta conta uma vez
        attendance(first, admin, "CONFIRMED")
        attendance(second, owner, "DECLINED")
        charge(athlete, 3_000, "PAID", Instant.now().minusSeconds(100))
        charge(admin, 2_000, "PENDING", null)
        charge(admin, 9_000, "PAID", since.minusSeconds(86400)) // pago antes do teste
        expenseIn(1_500, "ACTIVE")
        expenseIn(700, "VOIDED")

        assertEquals(TrialUsage(games = 2, confirmedAthletes = 2, cashCents = 4_500), usage.usageOf(owner, since, until))
        assertEquals(TrialUsage(0, 0, 0), usage.usageOf(user("Sem nada"), since, until))
    }

    private fun pushEnabled(recipient: UUID) =
        jdbc.sql("SELECT push_enabled FROM notification_delivery_context WHERE recipient_id = :r AND channel = 'TRIAL_ENDING'")
            .param("r", recipient).query(Boolean::class.javaObjectType).single()

    private fun group(ownerId: UUID, name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, :name, 'America/Sao_Paulo', now(), now())""")
            .param("id", id).param("owner", ownerId).param("key", UUID.randomUUID()).param("name", name).update()
    }

    private fun membership(groupId: UUID, user: UUID, role: String) = jdbc.sql("""
        INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
        VALUES (:g, :u, CAST(:role AS group_role), 'MENSALISTA', true, now(), now())
    """).param("g", groupId).param("u", user).param("role", role).update()

    // Jogos do mesmo grupo não se sobrepõem (DEB-048): cada um começa 3 h depois do anterior.
    private var gamesCreated = 0

    private fun game(groupId: UUID, status: String, publishedAt: Instant?): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, published_at, created_at, updated_at)
            VALUES (:id, :g, 'QA Vôlei', current_date + 1, '20:00', 'America/Sao_Paulo',
                now() + interval '30 hours' + make_interval(hours => :shift), 120, now() + interval '20 hours',
                'Quadra QA', 'Av. Paulista, 1578', 12, CAST(:status AS game_status), :published, now(), now())
        """).param("id", id).param("g", groupId).param("status", status).param("shift", 3 * gamesCreated++)
            .param("published", publishedAt?.let { java.sql.Timestamp.from(it) }, java.sql.Types.TIMESTAMP).update()
    }

    private fun attendance(game: UUID, member: UUID, status: String) = jdbc.sql("""
        INSERT INTO game_attendance (game_id, group_id, member_user_id, status, responded_at, updated_at, member_display_name)
        SELECT id, group_id, :m, CAST(:status AS attendance_status), now(), now(), 'Atleta' FROM games WHERE id = :game
    """).param("game", game).param("m", member).param("status", status).update()

    private var months = 0

    private fun charge(member: UUID, cents: Long, status: String, paidAt: Instant?) {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date, status,
                created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name)
            VALUES (:id, :g, :m, CAST('MONTHLY' AS charge_kind), (date_trunc('month', current_date) - make_interval(months => :month))::date, :cents, current_date, CAST(:status AS charge_status), :o, :o, now(), now(), 'Atleta')
        """).param("id", id).param("g", group).param("m", member).param("month", months++).param("cents", cents).param("status", status).param("o", owner).update()
        if (paidAt != null) {
            jdbc.sql("""
                INSERT INTO group_charge_events (id, group_id, charge_id, actor_user_id, old_status, new_status, occurred_at)
                VALUES (:e, :g, :c, :o, CAST('PENDING' AS charge_status), CAST('PAID' AS charge_status), :at)
            """).param("e", UUID.randomUUID()).param("g", group).param("c", id).param("o", owner)
                .param("at", java.sql.Timestamp.from(paidAt)).update()
        }
    }

    private fun expenseIn(cents: Long, status: String) = jdbc.sql("""
        INSERT INTO group_expenses (id, group_id, description, amount_cents, expense_date, category, status, direction,
            created_by_user_id, changed_by_user_id, created_at, updated_at)
        VALUES (:id, :g, 'Patrocínio da quadra', :cents, current_date, CAST('RACHA' AS expense_category), CAST(:status AS expense_status), CAST('IN' AS expense_direction), :o, :o, now(), now())
    """).param("id", UUID.randomUUID()).param("g", group).param("cents", cents).param("status", status).param("o", owner).update()

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
