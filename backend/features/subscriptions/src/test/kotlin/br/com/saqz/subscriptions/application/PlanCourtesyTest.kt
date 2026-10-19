package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.GroupPlanOwnerLookup
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccess
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccessLookup
import br.com.saqz.sharedkernel.subscription.TrialStatus
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.PlanCourtesy
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cortesia de plano (DEB-097) fora do banco: regra de "ativa", horizonte de jogo, `/me` e casos de uso do painel. */
class PlanCourtesyTest {
    private val now = Instant.parse("2026-10-19T15:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val owner = UUID.randomUUID()
    private val admin = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val sp = ZoneId.of("America/Sao_Paulo")
    private val courtesies = FakeCourtesies()
    private var asaas: Subscription? = null
    private val subscriptions = object : SubscriptionRepository {
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) = null
        override fun findByOwnerUserId(ownerUserId: UUID) = asaas
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID) = asaas
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun findByLastConfirmedPaymentId(paymentId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = error("unused")
        override fun save(subscription: Subscription) = error("unused")
    }

    private fun courtesy(plan: Plan, endsAt: Instant? = null, revokedAt: Instant? = null) =
        PlanCourtesy(UUID.randomUUID(), owner, plan, endsAt, "teste", admin, now.minusSeconds(60), revokedAt, revokedAt?.let { admin })

    private fun site(plan: Plan, periodEnd: Instant) {
        asaas = Subscription(
            ownerUserId = owner, plan = plan, cycle = SubscriptionCycle.MONTHLY, asaasCustomerId = "c", asaasSubscriptionId = "s",
            billingType = null, currentPeriodEnd = periodEnd, status = SubscriptionStatus.ACTIVE, firstConfirmedAt = now.minus(Duration.ofDays(40)),
        )
    }

    private fun horizon(trial: OrganizerTrialAccess = OrganizerTrialAccess(TrialStatus.SUBSCRIBED, null, null, now, true)) =
        MonthlyGameCreationHorizon(
            groups = object : GroupPlanOwnerLookup {
                override fun ownerOf(groupId: UUID): UUID? = owner
                override fun ownerForMember(groupId: UUID, actorId: UUID): UUID? = owner
                override fun isOrganizer(groupId: UUID, actorId: UUID) = true
                override fun zoneOf(groupId: UUID): ZoneId = sp
            },
            trials = OrganizerTrialAccessLookup { trial },
            subscriptions = subscriptions,
            clock = clock,
            courtesies = courtesies,
        )

    private fun startOfDay(instant: Instant): Instant = instant.atZone(sp).toLocalDate().atStartOfDay(sp).toInstant()

    // --- Quando vale ---

    @Test
    fun `cortesia vale sem fim e com fim no futuro, nao depois de revogada nem de vencida`() {
        assertTrue(courtesy(Plan.TITULAR).isActiveAt(now))
        assertTrue(courtesy(Plan.TITULAR, endsAt = now.plusSeconds(1)).isActiveAt(now))
        assertFalse(courtesy(Plan.TITULAR, endsAt = now).isActiveAt(now))
        assertFalse(courtesy(Plan.TITULAR, endsAt = now.minusSeconds(1)).isActiveAt(now))
        assertFalse(courtesy(Plan.TITULAR, revokedAt = now.minusSeconds(5)).isActiveAt(now))
    }

    // --- Horizonte de jogo (DEB-075) ---

    @Test
    fun `cortesia sem fim e dono sem assinatura nao tem limite de data`() {
        courtesies.rows += courtesy(Plan.ORGANIZADOR)
        assertEquals(Instant.MAX, horizon().startsBefore(group))
        assertEquals(Instant.MAX, horizon().until(group))
    }

    @Test
    fun `cortesia com fim limita no dia do fim e a serie vai ate ele sem andar mes a mes`() {
        val end = Instant.parse("2027-03-10T12:00:00Z")
        courtesies.rows += courtesy(Plan.ORGANIZADOR, endsAt = end)
        assertEquals(startOfDay(end), horizon().startsBefore(group))
        assertEquals(startOfDay(end), horizon().until(group))
    }

    @Test
    fun `vale o maior fim entre assinatura e cortesia`() {
        site(Plan.TITULAR, periodEnd = Instant.parse("2026-11-05T12:00:00Z"))
        courtesies.rows += courtesy(Plan.ORGANIZADOR, endsAt = Instant.parse("2026-12-20T12:00:00Z"))
        assertEquals(startOfDay(Instant.parse("2026-12-20T12:00:00Z")), horizon().startsBefore(group))

        courtesies.rows.clear()
        courtesies.rows += courtesy(Plan.ORGANIZADOR, endsAt = Instant.parse("2026-10-25T12:00:00Z"))
        assertEquals(startOfDay(Instant.parse("2026-11-05T12:00:00Z")), horizon().startsBefore(group))
    }

    @Test
    fun `cortesia sem fim vence uma assinatura com fim e cortesia revogada ou vencida nao conta`() {
        site(Plan.TITULAR, periodEnd = Instant.parse("2026-11-05T12:00:00Z"))
        courtesies.rows += courtesy(Plan.TITULAR)
        assertEquals(Instant.MAX, horizon().startsBefore(group))

        courtesies.rows.clear()
        courtesies.rows += courtesy(Plan.TITULAR, revokedAt = now.minusSeconds(5))
        courtesies.rows += courtesy(Plan.TITULAR, endsAt = now.minusSeconds(5))
        assertEquals(startOfDay(Instant.parse("2026-11-05T12:00:00Z")), horizon().startsBefore(group))
    }

    // --- /subscriptions/me ---

    private fun getMine() = GetMySubscription(subscriptions, { 1 }, clock, courtesies = courtesies)

    @Test
    fun `quem so tem cortesia segue sem assinatura no me, e a rota nova traz a cortesia`() {
        courtesies.rows += courtesy(Plan.ILIMITADO, endsAt = now.plus(Duration.ofDays(30)))
        assertIs<GetMySubscriptionResult.NotFound>(getMine().execute(owner))
        assertEquals(CourtesyView(Plan.ILIMITADO, now.plus(Duration.ofDays(30))), getMine().courtesy(owner))
        assertNull(getMine().courtesy(UUID.randomUUID()))
    }

    @Test
    fun `com assinatura o me so ganha o campo courtesy e o resto fica igual`() {
        site(Plan.TITULAR, periodEnd = now.plus(Duration.ofDays(10)))
        val without = (getMine().execute(owner) as GetMySubscriptionResult.Found).subscription
        assertNull(without.courtesy)

        courtesies.rows += courtesy(Plan.ORGANIZADOR)
        val with = (getMine().execute(owner) as GetMySubscriptionResult.Found).subscription
        assertEquals(CourtesyView(Plan.ORGANIZADOR, null), with.courtesy)
        assertEquals(without, with.copy(courtesy = null))
    }

    // --- Casos de uso do painel ---

    private val useCases = PlanCourtesies(courtesies, { null }, clock)

    @Test
    fun `dar uma cortesia nova revoga a anterior e o historico guarda as duas`() {
        courtesies.users += owner
        useCases.grant(admin, owner, Plan.TITULAR, null, " Parceiro ")
        val second = useCases.grant(admin, owner, Plan.ILIMITADO, now.plus(Duration.ofDays(5)), "Piloto")

        val (active, history) = useCases.view(owner)!!
        assertEquals(second, active)
        assertEquals(2, history.size)
        assertEquals(1, history.count { it.revokedAt != null })
        assertEquals("Parceiro", history.first { it.plan == Plan.TITULAR }.reason)
    }

    @Test
    fun `motivo em branco, fim no passado e usuario inexistente sao recusados`() {
        courtesies.users += owner
        assertEquals("reason", assertFailsWith<InvalidCourtesyException> { useCases.grant(admin, owner, Plan.TITULAR, null, "  ") }.field)
        assertEquals("endsAt", assertFailsWith<InvalidCourtesyException> { useCases.grant(admin, owner, Plan.TITULAR, now, "x") }.field)
        assertFailsWith<CourtesyUserNotFoundException> { useCases.grant(admin, UUID.randomUUID(), Plan.TITULAR, null, "x") }
        assertTrue(courtesies.rows.isEmpty())
    }

    @Test
    fun `revogar so responde true quando havia cortesia ativa`() {
        courtesies.users += owner
        assertFalse(useCases.revoke(admin, owner, null))
        useCases.grant(admin, owner, Plan.TITULAR, null, "x")
        assertTrue(useCases.revoke(admin, owner, "acabou o teste"))
        assertNull(useCases.active(owner))
        assertFalse(useCases.revoke(admin, owner, null))
        assertFailsWith<CourtesyUserNotFoundException> { useCases.revoke(admin, UUID.randomUUID(), null) }
    }

    @Test
    fun `lote em dry-run nao grava, e ao gravar so entram os ids da lista elegivel`() {
        val eligible = UUID.randomUUID()
        val outsider = UUID.randomUUID()
        courtesies.users += listOf(owner, eligible, outsider)
        courtesies.eligible += listOf(CourtesyCandidate(eligible, "Ana", "ana@saqz.test", listOf("Quadra"), null, false))

        val preview = useCases.preview(Plan.ORGANIZADOR, null, "Fase de testes")
        assertEquals(listOf(eligible), preview.map { it.ownerUserId })
        assertTrue(courtesies.rows.isEmpty())

        val results = useCases.grantBatch(admin, listOf(eligible, outsider, eligible), Plan.ORGANIZADOR, null, "Fase de testes")
        assertEquals(listOf(eligible to true, outsider to false), results.map { it.ownerUserId to it.granted })
        assertEquals("NOT_ELIGIBLE", results.last().error)
        assertEquals(listOf(eligible), courtesies.rows.map { it.ownerUserId })
        assertFailsWith<InvalidCourtesyException> { useCases.grantBatch(admin, listOf(eligible), Plan.ORGANIZADOR, null, "") }
    }

    private class FakeCourtesies : PlanCourtesyRepository {
        val rows = mutableListOf<PlanCourtesy>()
        val users = mutableSetOf<UUID>()
        val eligible = mutableListOf<CourtesyCandidate>()

        override fun findActive(ownerUserId: UUID, now: Instant) =
            rows.lastOrNull { it.ownerUserId == ownerUserId && it.isActiveAt(now) }

        override fun history(ownerUserId: UUID) = rows.filter { it.ownerUserId == ownerUserId }.reversed()
        override fun userExists(userId: UUID) = userId in users

        override fun grant(courtesy: PlanCourtesy): PlanCourtesy {
            rows.replaceAll { if (it.ownerUserId == courtesy.ownerUserId && it.revokedAt == null) it.copy(revokedAt = courtesy.createdAt, revokedBy = courtesy.grantedBy) else it }
            rows += courtesy
            return courtesy
        }

        override fun revoke(ownerUserId: UUID, by: UUID, reason: String?, now: Instant): Boolean {
            val open = rows.indexOfFirst { it.ownerUserId == ownerUserId && it.isActiveAt(now) }
            if (open < 0) return false
            rows[open] = rows[open].copy(revokedAt = now, revokedBy = by, revokedReason = reason)
            return true
        }

        override fun listActive(now: Instant) = emptyList<ActiveCourtesy>()
        override fun eligibleOwners(now: Instant) = eligible.toList()
    }
}
