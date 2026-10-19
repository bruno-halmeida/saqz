package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.subscription.TrialEndingNotice
import br.com.saqz.sharedkernel.subscription.TrialUsage
import br.com.saqz.sharedkernel.subscription.TrialUsageLookup
import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.application.PlanCourtesies
import br.com.saqz.subscriptions.application.PlanReportsEntitlement
import br.com.saqz.subscriptions.application.SubscriptionLimitsAdapter
import br.com.saqz.subscriptions.application.SubscriptionPastDueReminders
import br.com.saqz.subscriptions.application.TrialEndingReminders
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.PlanCourtesy
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import br.com.saqz.subscriptions.testing.subscriptionsWithGroupsMigrationLocations
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cortesia de plano (DEB-097) contra o Postgres: plano efetivo, atraso, fim de teste, repositório e lote do painel. */
class JdbcPlanCourtesyIntegrationTest {
    private val source = TestPostgres.migrated(*subscriptionsWithGroupsMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val subscriptions = JdbcSubscriptionRepository(source)
    private val lookup = JdbcSubscriptionPlanLookup(source)
    private val repository = JdbcPlanCourtesyRepository(source)
    private val pastDue = JdbcSubscriptionPastDueRepository(source)
    private val now = Instant.now()
    private val admin = user("Admin")

    // --- Plano efetivo: o maior entre assinatura e cortesia ---

    @Test
    fun `assinatura menor com cortesia maior vale a cortesia e o contrario vale a assinatura`() {
        val owner = user()
        site(owner, Plan.TITULAR)
        courtesy(owner, Plan.ILIMITADO)
        assertEquals(Plan.ILIMITADO, lookup.findEntitlingPlan(owner)?.plan)
        assertNull(lookup.findEntitlingPlan(owner)?.pendingPlan)

        val other = user()
        site(other, Plan.ILIMITADO)
        courtesy(other, Plan.TITULAR)
        assertEquals(Plan.ILIMITADO, lookup.findEntitlingPlan(other)?.plan)
    }

    @Test
    fun `so cortesia ativa da plano, revogada ou vencida nao`() {
        val active = user()
        courtesy(active, Plan.ORGANIZADOR, endsAt = now.plus(Duration.ofDays(1)))
        val forever = user()
        courtesy(forever, Plan.TITULAR)
        val revoked = user()
        courtesy(revoked, Plan.ILIMITADO)
        repository.revoke(revoked, admin, "fim", now)
        val expired = user()
        courtesy(expired, Plan.ILIMITADO, endsAt = now.minusSeconds(1), createdAt = now.minus(Duration.ofDays(2)))

        assertEquals(Plan.ORGANIZADOR, lookup.findEntitlingPlan(active)?.plan)
        assertEquals(Plan.TITULAR, lookup.findEntitlingPlan(forever)?.plan)
        assertNull(lookup.findEntitlingPlan(revoked))
        assertNull(lookup.findEntitlingPlan(expired))
    }

    @Test
    fun `limites e relatorios seguem o plano da cortesia`() {
        val owner = user()
        assertEquals(0, SubscriptionLimitsAdapter(lookup).groupLimitFor(owner))
        assertFalse(PlanReportsEntitlement(lookup).isEnabledFor(owner))

        courtesy(owner, Plan.ORGANIZADOR)
        assertEquals(3, SubscriptionLimitsAdapter(lookup).groupLimitFor(owner))
        assertNull(SubscriptionLimitsAdapter(lookup).athleteLimitFor(owner))
        assertFalse(PlanReportsEntitlement(lookup).isEnabledFor(owner))

        useCases().grant(admin, owner, Plan.ILIMITADO, null, "Troca")
        assertNull(SubscriptionLimitsAdapter(lookup).groupLimitFor(owner))
        assertTrue(PlanReportsEntitlement(lookup).isEnabledFor(owner))
    }

    // --- Avisos ---

    @Test
    fun `aviso de atraso nao sai com cortesia que dura alem do acesso, e sai com a que acaba antes ou ja nao vale`() {
        val since = now.minus(Duration.ofDays(3))
        val forever = site(user(), Plan.TITULAR, SubscriptionStatus.PAST_DUE, since)
        courtesy(forever, Plan.TITULAR)
        val longer = site(user(), Plan.TITULAR, SubscriptionStatus.PAST_DUE, since)
        courtesy(longer, Plan.TITULAR, endsAt = now.plus(Duration.ofDays(30)))
        val shorter = site(user(), Plan.TITULAR, SubscriptionStatus.PAST_DUE, since)
        courtesy(shorter, Plan.TITULAR, endsAt = now.plus(Duration.ofHours(2)))
        val revoked = site(user(), Plan.TITULAR, SubscriptionStatus.PAST_DUE, since)
        courtesy(revoked, Plan.TITULAR)
        repository.revoke(revoked, admin, null, now)
        val expired = site(user(), Plan.TITULAR, SubscriptionStatus.PAST_DUE, since)
        courtesy(expired, Plan.TITULAR, endsAt = now.minusSeconds(1), createdAt = now.minus(Duration.ofDays(2)))

        assertEquals(setOf(shorter, revoked, expired), pastDue.pastDue(now).map { it.ownerUserId }.toSet())

        val published = mutableListOf<UUID>()
        val round = SubscriptionPastDueReminders(pastDue, { owner, _, _ -> published += owner; true }, { _, _ -> }, { null }, Clock.fixed(now, ZoneOffset.UTC))
        round.run()
        assertTrue(published.none { it in setOf(forever, longer) })
    }

    @Test
    fun `aviso de fim de teste nao sai para quem tem cortesia ativa`() {
        val withCourtesy = user()
        val without = user()
        listOf(withCourtesy, without).forEach { owner ->
            jdbc.sql("INSERT INTO organizer_trials (owner_user_id, started_at, ends_at) VALUES (:o, :s, :e)")
                .param("o", owner).param("s", Timestamp.from(now.minus(Duration.ofDays(12))))
                .param("e", Timestamp.from(now.plus(Duration.ofDays(2)))).update()
        }
        courtesy(withCourtesy, Plan.TITULAR)
        val notified = mutableListOf<UUID>()
        val reminders = TrialEndingReminders(
            JdbcTrialEndingRepository(source), lookup, TrialUsageLookup { _, _, _ -> TrialUsage(0, 0, 0) },
            TrialEndingNotice { owner, _, _ -> notified += owner; true }, { _, _ -> }, Clock.fixed(now, ZoneOffset.UTC),
        )

        reminders.run()

        assertEquals(listOf(without), notified)
    }

    // --- Repositório ---

    @Test
    fun `uma aberta por dono no banco, e dar outra revoga a anterior e guarda o historico`() {
        val owner = user()
        val useCases = useCases()
        val first = useCases.grant(admin, owner, Plan.TITULAR, null, "Primeira")
        val second = useCases.grant(admin, owner, Plan.ORGANIZADOR, now.plus(Duration.ofDays(10)), "Segunda")

        val (active, history) = useCases.view(owner)!!
        assertEquals(second.id, active?.id)
        assertEquals(listOf(second.id, first.id), history.map { it.id })
        assertEquals(admin, history.last().revokedBy)
        assertNull(history.first().revokedAt)
        assertEquals(1, openRows(owner))

        // Duas abertas direto no banco o índice parcial recusa.
        val duplicate = runCatching { repository.run { jdbc.sql(
            "INSERT INTO plan_courtesies (id, owner_user_id, plan, reason, granted_by) VALUES (:id, :o, 'TITULAR', 'x', :a)",
        ).param("id", UUID.randomUUID()).param("o", owner).param("a", admin).update() } }
        assertTrue(duplicate.isFailure)
    }

    @Test
    fun `revogar fecha a ativa, guarda quem e por que, e nao duplica`() {
        val owner = user()
        courtesy(owner, Plan.TITULAR)
        assertTrue(repository.revoke(owner, admin, "Fim do piloto", now))
        assertFalse(repository.revoke(owner, admin, null, now))
        val entry = repository.history(owner).single()
        assertEquals(admin, entry.revokedBy)
        assertEquals("Fim do piloto", entry.revokedReason)
        assertNull(repository.findActive(owner, now))
    }

    @Test
    fun `comprar ou trocar assinatura nao e barrado pela cortesia e ela nao cria linha em subscriptions`() {
        val owner = user()
        courtesy(owner, Plan.ILIMITADO)
        assertEquals(0, jdbc.sql("SELECT count(*) FROM subscriptions WHERE owner_user_id = :o").param("o", owner).query(Int::class.java).single())
        assertNull(subscriptions.findByOwnerUserId(owner))
        // O caminho de compra olha a linha de subscriptions (e as das lojas), nunca o lookup com cortesia.
        assertNull(lookup.findPendingCheckoutPlan(owner))
        site(owner, Plan.TITULAR)
        assertEquals(Plan.TITULAR, subscriptions.findByOwnerUserId(owner)?.plan)
    }

    // --- Painel: lista e lote ---

    @Test
    fun `lista ativas traz dono, e-mail, plano, fim e motivo so das ativas`() {
        val active = user("Ana", "ana@saqz.test")
        courtesy(active, Plan.ORGANIZADOR, endsAt = now.plus(Duration.ofDays(3)), reason = "Piloto")
        val revoked = user()
        courtesy(revoked, Plan.TITULAR)
        repository.revoke(revoked, admin, null, now)

        val list = repository.listActive(now)

        assertEquals(listOf(active), list.map { it.courtesy.ownerUserId })
        assertEquals("ana@saqz.test", list.single().email)
        assertEquals("Piloto", list.single().courtesy.reason)
    }

    @Test
    fun `lote, a lista de conferencia so tem donos de grupo vivo com conta ativa e mostra plano efetivo e cortesia`() {
        val plain = user("Zeca")
        group(plain, "Quadra do Zeca")
        group(plain, "Segundo")
        val subscribed = user("Ana")
        group(subscribed, "Racha da Ana")
        site(subscribed, Plan.ORGANIZADOR)
        val has = user("Bia")
        group(has, "Bia FC")
        courtesy(has, Plan.TITULAR)
        user("Sem grupo")
        val deletedGroup = user("Grupo apagado")
        group(deletedGroup, "Apagado", deleted = true)
        val suspended = user("Suspenso")
        group(suspended, "Suspenso FC")
        jdbc.sql("UPDATE access_users SET suspended_at = now() WHERE id = :id").param("id", suspended).update()

        val preview = useCases().preview(Plan.ILIMITADO, null, "Fase de testes").associateBy { it.ownerUserId }

        assertEquals(setOf(plain, subscribed, has), preview.keys)
        assertEquals(listOf("Quadra do Zeca", "Segundo"), preview.getValue(plain).groups)
        assertNull(preview.getValue(plain).effectivePlan)
        assertFalse(preview.getValue(plain).hasCourtesy)
        assertEquals(Plan.ORGANIZADOR, preview.getValue(subscribed).effectivePlan)
        assertEquals(Plan.TITULAR, preview.getValue(has).effectivePlan)
        assertTrue(preview.getValue(has).hasCourtesy)
        assertEquals(0, repository.listActive(now).count { it.courtesy.ownerUserId in setOf(plain, subscribed) })
    }

    @Test
    fun `lote grava so para os ids da lista elegivel e devolve o resultado por dono`() {
        val inList = user("Na lista")
        group(inList, "Grupo A")
        val notChosen = user("Fora da selecao")
        group(notChosen, "Grupo B")
        val noGroup = user("Sem grupo")

        val results = useCases().grantBatch(admin, listOf(inList, noGroup), Plan.ORGANIZADOR, null, "Fase de testes")

        assertEquals(listOf(inList to true, noGroup to false), results.map { it.ownerUserId to it.granted })
        assertEquals("NOT_ELIGIBLE", results.last().error)
        assertEquals(Plan.ORGANIZADOR, repository.findActive(inList, now)?.plan)
        assertNull(repository.findActive(notChosen, now))
        assertNull(repository.findActive(noGroup, now))
        assertEquals(admin, repository.findActive(inList, now)?.grantedBy)
    }

    // --- Apoio ---

    private fun useCases() = PlanCourtesies(repository, lookup, Clock.systemUTC())

    private fun user(name: String = "Owner", email: String? = null): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, :email, true, :name, now(), now())")
            .param("id", id).param("subject", id.toString()).param("email", email).param("name", name).update()
    }

    private fun group(owner: UUID, name: String, deleted: Boolean = false) {
        jdbc.sql(
            """
            INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at, deleted_at)
            VALUES (:id, :o, :key, :name, 'UTC', now() - interval '1 minute' * :age, now(), :deleted)
            """.trimIndent(),
        ).param("id", UUID.randomUUID()).param("o", owner).param("key", UUID.randomUUID()).param("name", name)
            .param("age", groupAge--).param("deleted", if (deleted) Timestamp.from(now) else null).update()
    }

    private var groupAge = 100

    private fun courtesy(owner: UUID, plan: Plan, endsAt: Instant? = null, createdAt: Instant = now.minusSeconds(60), reason: String = "teste") {
        repository.grant(PlanCourtesy(UUID.randomUUID(), owner, plan, endsAt, reason, admin, createdAt))
    }

    private fun site(owner: UUID, plan: Plan, status: SubscriptionStatus = SubscriptionStatus.ACTIVE, pastDueSince: Instant? = null): UUID {
        subscriptions.insert(
            Subscription(
                ownerUserId = owner, plan = plan, cycle = SubscriptionCycle.MONTHLY, asaasCustomerId = "cus_1",
                asaasSubscriptionId = "sub_$owner", billingType = AsaasBillingType.PIX, currentPeriodEnd = now.plus(Duration.ofDays(20)),
                status = status, pastDueSince = pastDueSince, firstConfirmedAt = now.minus(Duration.ofDays(60)),
            ),
        )
        return owner
    }

    private fun openRows(owner: UUID) = jdbc.sql("SELECT count(*) FROM plan_courtesies WHERE owner_user_id = :o AND revoked_at IS NULL")
        .param("o", owner).query(Int::class.java).single()
}
