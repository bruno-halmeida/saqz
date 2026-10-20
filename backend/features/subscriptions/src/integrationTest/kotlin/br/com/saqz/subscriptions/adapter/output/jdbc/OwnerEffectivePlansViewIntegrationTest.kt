package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** DEB-099: a view `owner_effective_plans` precisa dar o mesmo plano que `findEntitlingPlan` (e o teste de OrganizerTrial). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnerEffectivePlansViewIntegrationTest {
    private lateinit var jdbc: JdbcClient
    private lateinit var lookup: JdbcSubscriptionPlanLookup

    private data class Effective(val plan: String, val source: String, val endsAt: Instant?)

    @BeforeEach
    fun resetDatabase() {
        val dataSource = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
        jdbc = JdbcClient.create(dataSource)
        lookup = JdbcSubscriptionPlanLookup(dataSource)
    }

    @Test
    fun `site ativo vale como SITE`() {
        val dono = user()
        site(dono, "ORGANIZADOR", "ACTIVE")

        assertEquals(Effective("ORGANIZADOR", "SITE", null), effective(dono))
    }

    @Test
    fun `site cancelado ainda no periodo pago aparece`() {
        val dono = user()
        site(dono, "TITULAR", "CANCELED", periodEnd = "now() + interval '5 days'")

        assertEquals(Effective("TITULAR", "SITE", null), effective(dono))
    }

    @Test
    fun `site cancelado depois do periodo nao aparece`() {
        val dono = user()
        site(dono, "TITULAR", "CANCELED", periodEnd = "now() - interval '1 day'")

        assertNull(effective(dono))
    }

    @Test
    fun `site em atraso dentro da carencia aparece e congelado vira sem plano`() {
        val naCarencia = user()
        val congelado = user()
        site(naCarencia, "ORGANIZADOR", "PAST_DUE", pastDueSince = "now() - interval '2 days'")
        site(congelado, "ORGANIZADOR", "PAST_DUE", pastDueSince = "now() - interval '30 days'")

        assertEquals(Effective("ORGANIZADOR", "SITE", null), effective(naCarencia))
        assertNull(effective(congelado))
    }

    @Test
    fun `App Store e Google Play`() {
        val apple = user()
        val google = user()
        jdbc.sql(
            """
            INSERT INTO app_store_subscriptions (original_transaction_id, owner_user_id, environment, product_id, plan, cycle,
                latest_transaction_id, latest_purchase_date, latest_signed_at, expires_at)
            VALUES (:tx, :owner, 'Production', 'p', 'ORGANIZADOR', 'MONTHLY', :tx, now(), now(), now() + interval '10 days')
            """.trimIndent(),
        ).param("tx", "tx-$apple").param("owner", apple).update()
        jdbc.sql(
            """
            INSERT INTO google_play_subscriptions (purchase_token, owner_user_id, product_id, base_plan_id, plan, cycle, state, expires_at)
            VALUES (:token, :owner, 'p', 'b', 'ILIMITADO', 'MONTHLY', 'ACTIVE', now() + interval '10 days')
            """.trimIndent(),
        ).param("token", "tok-$google").param("owner", google).update()

        assertEquals(Effective("ORGANIZADOR", "APP_STORE", null), effective(apple))
        assertEquals(Effective("ILIMITADO", "GOOGLE_PLAY", null), effective(google))
    }

    @Test
    fun `cortesia com fim, sem fim, revogada e vencida`() {
        val comFim = user()
        val semFim = user()
        val revogada = user()
        val vencida = user()
        val fim = Instant.now().plusSeconds(86_400 * 20).let { Instant.ofEpochSecond(it.epochSecond) }
        courtesy(comFim, "ORGANIZADOR", "'$fim'")
        courtesy(semFim, "ILIMITADO", "NULL")
        courtesy(revogada, "ILIMITADO", "NULL", revoked = true)
        courtesy(vencida, "ILIMITADO", "now() - interval '1 day'")

        assertEquals(Effective("ORGANIZADOR", "COURTESY", fim), effective(comFim))
        assertEquals(Effective("ILIMITADO", "COURTESY", null), effective(semFim))
        assertNull(effective(revogada))
        assertNull(effective(vencida))
    }

    @Test
    fun `teste ativo vale ORGANIZADOR e vencido nao vale`() {
        val ativo = user()
        val vencido = user()
        trial(ativo, "now() - interval '1 day'", "now() + interval '13 days'")
        trial(vencido, "now() - interval '20 days'", "now() - interval '6 days'")

        val visto = effective(ativo)!!
        assertEquals("ORGANIZADOR", visto.plan)
        assertEquals("TRIAL", visto.source)
        assertEquals(true, visto.endsAt!!.isAfter(Instant.now()))
        assertNull(effective(vencido))
    }

    @Test
    fun `teste ativo cede a qualquer fonte de acesso`() {
        val dono = user()
        trial(dono, "now() - interval '1 day'", "now() + interval '13 days'")
        courtesy(dono, "TITULAR", "NULL")

        assertEquals(Effective("TITULAR", "COURTESY", null), effective(dono))
    }

    @Test
    fun `maior plano vence e empate fica com a ordem das origens`() {
        val maior = user()
        val empate = user()
        site(maior, "TITULAR", "ACTIVE")
        courtesy(maior, "ILIMITADO", "NULL")
        site(empate, "ORGANIZADOR", "ACTIVE")
        courtesy(empate, "ORGANIZADOR", "NULL")

        assertEquals(Effective("ILIMITADO", "COURTESY", null), effective(maior))
        assertEquals(Effective("ORGANIZADOR", "SITE", null), effective(empate))
    }

    @Test
    fun `dono sem nada nao tem linha e uma linha por dono`() {
        val nada = user()
        val varios = user()
        site(varios, "TITULAR", "ACTIVE")
        courtesy(varios, "ORGANIZADOR", "NULL")
        trial(varios, "now() - interval '1 day'", "now() + interval '13 days'")

        assertNull(effective(nada))
        assertEquals(1, jdbc.sql("SELECT count(*) FROM owner_effective_plans WHERE owner_user_id = :o").param("o", varios).query(Long::class.java).single())
    }

    @Test
    fun `a view da o mesmo plano que findEntitlingPlan`() {
        val donos = listOf(
            user().also { site(it, "ORGANIZADOR", "ACTIVE") },
            user().also { site(it, "TITULAR", "CANCELED", periodEnd = "now() + interval '3 days'") },
            user().also { site(it, "TITULAR", "CANCELED", periodEnd = "now() - interval '3 days'") },
            user().also { site(it, "ORGANIZADOR", "PAST_DUE", pastDueSince = "now() - interval '30 days'") },
            user().also { site(it, "ORGANIZADOR", "PAST_DUE", pastDueSince = "now() - interval '1 day'") },
            user().also { courtesy(it, "ILIMITADO", "NULL") },
            user().also { courtesy(it, "ILIMITADO", "now() - interval '1 day'") },
            user().also { courtesy(it, "ILIMITADO", "NULL", revoked = true) },
            user().also { site(it, "TITULAR", "ACTIVE"); courtesy(it, "ILIMITADO", "NULL") },
            user().also { site(it, "ORGANIZADOR", "ACTIVE"); courtesy(it, "ORGANIZADOR", "NULL") },
            user(),
        )

        donos.forEach { dono ->
            assertEquals(lookup.findEntitlingPlan(dono)?.plan?.name, effective(dono)?.plan, "dono $dono")
        }
    }

    private fun effective(dono: UUID): Effective? =
        jdbc.sql("SELECT plan::text, source, ends_at FROM owner_effective_plans WHERE owner_user_id = :o")
            .param("o", dono)
            .query { rs, _ ->
                Effective(rs.getString(1), rs.getString(2), rs.getTimestamp(3)?.toInstant())
            }
            .optional().orElse(null)

    private fun user(): UUID {
        val id = UUID.randomUUID()
        jdbc.sql(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES (:id, :subject, true, 'Owner', now(), now())",
        ).param("id", id).param("subject", "subject-$id").update()
        return id
    }

    private fun site(dono: UUID, plan: String, status: String, periodEnd: String = "now() + interval '30 days'", pastDueSince: String = "NULL") {
        jdbc.sql(
            "INSERT INTO subscriptions (owner_user_id, plan, cycle, status, asaas_customer_id, asaas_subscription_id, " +
                "current_period_end, first_confirmed_at, past_due_since, created_at, updated_at) " +
                "VALUES (:owner, '$plan', 'MONTHLY', '$status', 'cus-$dono', 'sub-$dono', $periodEnd, now() - interval '60 days', " +
                "$pastDueSince, now(), now())",
        ).param("owner", dono).update()
    }

    private fun courtesy(dono: UUID, plan: String, endsAt: String, revoked: Boolean = false) {
        val admin = user()
        jdbc.sql(
            "INSERT INTO plan_courtesies (id, owner_user_id, plan, ends_at, reason, granted_by, revoked_at, revoked_by) " +
                "VALUES (:id, :owner, '$plan', $endsAt, 'teste', :admin, ${if (revoked) "now()" else "NULL"}, ${if (revoked) ":admin" else "NULL"})",
        ).param("id", UUID.randomUUID()).param("owner", dono).param("admin", admin).update()
    }

    private fun trial(dono: UUID, startedAt: String, endsAt: String) {
        jdbc.sql("INSERT INTO organizer_trials (owner_user_id, started_at, ends_at) VALUES (:owner, $startedAt, $endsAt)")
            .param("owner", dono).update()
    }
}
