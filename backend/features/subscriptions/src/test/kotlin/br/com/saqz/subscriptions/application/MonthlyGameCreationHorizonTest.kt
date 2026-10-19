package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.application.MonthlyGameCreationHorizon.Companion.monthlyMarkAfter
import br.com.saqz.subscriptions.application.MonthlyGameCreationHorizon.Companion.monthlyMarkBefore
import br.com.saqz.sharedkernel.subscription.GroupPlanOwnerLookup
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccess
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccessLookup
import br.com.saqz.sharedkernel.subscription.TrialStatus
import br.com.saqz.subscriptions.domain.AppStoreEnvironment
import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.AppStoreSubscription
import br.com.saqz.subscriptions.domain.GooglePlayProduct
import br.com.saqz.subscriptions.domain.GooglePlayState
import br.com.saqz.subscriptions.domain.GooglePlaySubscription
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MonthlyGameCreationHorizonTest {
    private val now = Instant.parse("2026-09-17T15:00:00Z")

    @Test
    fun `monthly plan goes to the period end and annual plan only to its next monthly mark`() {
        assertEquals(Instant.parse("2026-10-05T12:00:00Z"), monthlyMarkBefore(Instant.parse("2026-10-05T12:00:00Z"), now))
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), monthlyMarkBefore(Instant.parse("2027-03-10T12:00:00Z"), now))
    }

    @Test
    fun `overdue period extends nothing`() {
        val overdue = Instant.parse("2026-09-15T12:00:00Z")
        assertEquals(overdue, monthlyMarkBefore(overdue, now))
    }

    @Test
    fun `trial walks like a monthly plan from its start`() {
        assertEquals(Instant.parse("2026-10-10T09:00:00Z"), monthlyMarkAfter(Instant.parse("2026-09-10T09:00:00Z"), now))
        assertEquals(Instant.parse("2026-10-01T09:00:00Z"), monthlyMarkAfter(Instant.parse("2026-06-01T09:00:00Z"), now))
    }

    // DEB-075: o limite do jogo é 0h, no fuso do grupo, do dia do fim do plano ou do teste.

    private val group = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val sp = ZoneId.of("America/Sao_Paulo")
    private var access = OrganizerTrialAccess(TrialStatus.INELIGIBLE, null, null, now, false)
    private var asaas: Subscription? = null
    private val appStore = InMemoryAppStoreSubscriptions()
    private val googlePlay = StoreFakeGooglePlay()

    private fun horizon() = MonthlyGameCreationHorizon(
        groups = object : GroupPlanOwnerLookup {
            override fun ownerOf(groupId: UUID): UUID? = owner
            override fun ownerForMember(groupId: UUID, actorId: UUID): UUID? = owner
            override fun isOrganizer(groupId: UUID, actorId: UUID) = true
            override fun zoneOf(groupId: UUID): ZoneId = sp
        },
        trials = OrganizerTrialAccessLookup { access },
        subscriptions = object : SubscriptionRepository {
            override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) = null
            override fun findByOwnerUserId(ownerUserId: UUID) = asaas
            override fun findByOwnerUserIdForUpdate(ownerUserId: UUID) = asaas
            override fun findByPendingUpgradeChargeId(chargeId: String) = null
            override fun findByLastConfirmedPaymentId(paymentId: String) = null
            override fun lockOwner(ownerUserId: UUID) = Unit
            override fun insert(subscription: Subscription) = error("unused")
            override fun save(subscription: Subscription) = error("unused")
        },
        clock = Clock.fixed(now, ZoneOffset.UTC),
        appStore = appStore,
        googlePlay = googlePlay,
    )

    private fun trial(startedAt: String, endsAt: String, status: TrialStatus = TrialStatus.ACTIVE) {
        access = OrganizerTrialAccess(status, Instant.parse(startedAt), Instant.parse(endsAt), now, true)
    }

    private fun asaas(
        periodEnd: String,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        cycle: SubscriptionCycle = SubscriptionCycle.MONTHLY,
        pastDueSince: String? = null,
    ) {
        access = OrganizerTrialAccess(TrialStatus.SUBSCRIBED, null, null, now, true)
        asaas = Subscription(
            ownerUserId = owner, plan = Plan.TITULAR, cycle = cycle, asaasCustomerId = "c", asaasSubscriptionId = "s",
            billingType = null, currentPeriodEnd = Instant.parse(periodEnd), status = status,
            pastDueSince = pastDueSince?.let(Instant::parse), firstConfirmedAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
    }

    private fun startOfDay(day: String): Instant = java.time.LocalDate.parse(day).atStartOfDay(sp).toInstant()

    @Test
    fun `trial limit is zero hour of the end day in the group zone and the series walks month by month up to it`() {
        // Teste de 14 dias: começa 09/09 15h e acaba 23/09 15h UTC.
        trial("2026-09-09T15:00:00Z", "2026-09-23T15:00:00Z")
        assertEquals(startOfDay("2026-09-23"), horizon().startsBefore(group))
        assertEquals(startOfDay("2026-09-23"), horizon().until(group))
    }

    @Test
    fun `a long coupon trial keeps the series monthly but the single game limit is the end`() {
        trial("2026-09-10T09:00:00Z", "2026-11-09T09:00:00Z")
        assertEquals(startOfDay("2026-11-09"), horizon().startsBefore(group))
        assertEquals(Instant.parse("2026-10-10T09:00:00Z"), horizon().until(group))
    }

    @Test
    fun `expired trial has no limit at all`() {
        trial("2026-08-01T09:00:00Z", "2026-08-15T09:00:00Z", TrialStatus.EXPIRED)
        assertNull(horizon().startsBefore(group))
        assertNull(horizon().until(group))
    }

    @Test
    fun `monthly plan limit is the paid period end and an annual plan only walks monthly in the series`() {
        asaas("2026-10-05T12:00:00Z")
        assertEquals(startOfDay("2026-10-05"), horizon().startsBefore(group))
        assertEquals(startOfDay("2026-10-05"), horizon().until(group))

        asaas("2027-03-10T12:00:00Z", cycle = SubscriptionCycle.ANNUAL)
        assertEquals(startOfDay("2027-03-10"), horizon().startsBefore(group))
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), horizon().until(group))
    }

    @Test
    fun `grace does not extend the limit and nothing new fits while it lasts`() {
        asaas("2026-09-15T12:00:00Z", SubscriptionStatus.PAST_DUE, pastDueSince = "2026-09-15T12:00:00Z")
        assertEquals(startOfDay("2026-09-15"), horizon().startsBefore(group))
        assertEquals(startOfDay("2026-09-15"), horizon().until(group))
    }

    @Test
    fun `renewal not yet confirmed keeps the period end and only the legacy past due without a date falls back to the rolling month`() {
        asaas("2026-09-10T12:00:00Z")
        assertEquals(startOfDay("2026-09-10"), horizon().startsBefore(group))
        assertEquals(startOfDay("2026-09-10"), horizon().until(group))
        asaas("2026-09-10T12:00:00Z", SubscriptionStatus.PAST_DUE, pastDueSince = null)
        assertEquals(startOfDay("2026-10-17"), horizon().startsBefore(group))
    }

    @Test
    fun `store only subscriptions use their own end and an old canceled asaas row is ignored`() {
        access = OrganizerTrialAccess(TrialStatus.SUBSCRIBED, null, null, now, true)
        asaas = Subscription(
            ownerUserId = owner, plan = Plan.TITULAR, cycle = SubscriptionCycle.MONTHLY, asaasCustomerId = "c", asaasSubscriptionId = "s",
            billingType = null, currentPeriodEnd = Instant.parse("2026-06-01T00:00:00Z"), status = SubscriptionStatus.CANCELED,
            firstConfirmedAt = Instant.parse("2026-05-01T00:00:00Z"),
        )
        appStore.save(
            AppStoreSubscription(
                "otx", owner, AppStoreEnvironment.PRODUCTION, AppStoreProduct.TITULAR_MENSAL, "otx", now, now,
                expiresAt = Instant.parse("2026-10-20T12:00:00Z"), revokedAt = null,
            ),
        )
        assertEquals(startOfDay("2026-10-20"), horizon().startsBefore(group))

        // Mais de uma assinatura dando acesso: vale o fim maior.
        googlePlay.save(
            GooglePlaySubscription(
                "token", owner, GooglePlayProduct.TITULAR_MENSAL, GooglePlayState.ACTIVE, Instant.parse("2026-11-02T12:00:00Z"),
                true, null, "GPA.1", null, acknowledged = true, testPurchase = false,
            ),
        )
        assertEquals(startOfDay("2026-11-02"), horizon().startsBefore(group))
    }

    @Test
    fun `sandbox purchase from the Apple and test purchase from the Play use the rolling month instead of their short expiry`() {
        access = OrganizerTrialAccess(TrialStatus.SUBSCRIBED, null, null, now, true)
        appStore.save(
            AppStoreSubscription(
                "otx", owner, AppStoreEnvironment.SANDBOX, AppStoreProduct.TITULAR_MENSAL, "otx", now, now,
                expiresAt = now.plusSeconds(300), revokedAt = null,
            ),
        )
        assertEquals(startOfDay("2026-10-17"), horizon().startsBefore(group))
        assertEquals(startOfDay("2026-10-17"), horizon().until(group))

        appStore.save(
            AppStoreSubscription(
                "otx", owner, AppStoreEnvironment.XCODE, AppStoreProduct.TITULAR_MENSAL, "otx", now, now,
                expiresAt = now.plusSeconds(300), revokedAt = null,
            ),
        )
        assertEquals(startOfDay("2026-10-17"), horizon().startsBefore(group))
    }

    @Test
    fun `test purchase from the Play uses the rolling month and a real one keeps its own end`() {
        access = OrganizerTrialAccess(TrialStatus.SUBSCRIBED, null, null, now, true)
        fun play(test: Boolean) = GooglePlaySubscription(
            "token", owner, GooglePlayProduct.TITULAR_MENSAL, GooglePlayState.ACTIVE, now.plusSeconds(300),
            true, null, "GPA.1", null, acknowledged = true, testPurchase = test,
        )
        googlePlay.save(play(test = true))
        assertEquals(startOfDay("2026-10-17"), horizon().startsBefore(group))
        googlePlay.save(play(test = false))
        assertEquals(startOfDay("2026-09-17"), horizon().startsBefore(group))
    }

    @Test
    fun `Play grace period does not extend the limit and a real subscription next to it still counts`() {
        access = OrganizerTrialAccess(TrialStatus.SUBSCRIBED, null, null, now, true)
        googlePlay.save(
            GooglePlaySubscription(
                "token", owner, GooglePlayProduct.TITULAR_MENSAL, GooglePlayState.IN_GRACE_PERIOD, now.plusSeconds(5 * 86_400),
                true, null, "GPA.1", null, acknowledged = true, testPurchase = false,
            ),
        )
        assertEquals(startOfDay("2026-09-17"), horizon().startsBefore(group))
        assertEquals(startOfDay("2026-09-17"), horizon().until(group))

        appStore.save(
            AppStoreSubscription(
                "otx", owner, AppStoreEnvironment.PRODUCTION, AppStoreProduct.TITULAR_MENSAL, "otx", now, now,
                expiresAt = Instant.parse("2026-10-20T12:00:00Z"), revokedAt = null,
            ),
        )
        assertEquals(startOfDay("2026-10-20"), horizon().startsBefore(group))
    }

    @Test
    fun `subscribing in the middle of a long trial keeps the later trial end`() {
        trial("2026-09-10T09:00:00Z", "2026-11-09T09:00:00Z")
        asaas("2026-09-30T09:00:00Z")
        access = access.copy(status = TrialStatus.SUBSCRIBED, startedAt = Instant.parse("2026-09-10T09:00:00Z"), endsAt = Instant.parse("2026-11-09T09:00:00Z"))
        assertEquals(startOfDay("2026-11-09"), horizon().startsBefore(group))
        assertEquals(Instant.parse("2026-10-10T09:00:00Z"), horizon().until(group))
    }

    @Test
    fun `owner without trial or subscription keeps the rolling month as the series does`() {
        access = OrganizerTrialAccess(TrialStatus.INELIGIBLE, null, null, now, false)
        assertEquals(startOfDay("2026-10-17"), horizon().startsBefore(group))
        assertEquals(startOfDay("2026-10-17"), horizon().until(group))
    }

    @Test
    fun `the series horizon never passes the single game limit`() {
        trial("2026-09-10T09:00:00Z", "2026-09-24T09:00:00Z")
        assertEquals(true, horizon().until(group)!! <= horizon().startsBefore(group)!!)
    }
}

private class StoreFakeGooglePlay : GooglePlaySubscriptionRepository {
    val rows = linkedMapOf<String, GooglePlaySubscription>()
    override fun insertIfAbsent(subscription: GooglePlaySubscription) { rows.putIfAbsent(subscription.purchaseToken, subscription) }
    override fun findForUpdate(purchaseToken: String) = rows[purchaseToken]
    override fun save(subscription: GooglePlaySubscription) { rows[subscription.purchaseToken] = subscription }
    override fun findByOwner(ownerUserId: UUID) = rows.values.filter { it.ownerUserId == ownerUserId }
    override fun ownerExists(ownerUserId: UUID) = true
    override fun supersede(purchaseToken: String, at: Instant) = Unit
    override fun markAcknowledged(purchaseToken: String) = Unit
    override fun recordOrder(subscription: GooglePlaySubscription) = Unit
    override fun listOrdersForOwner(ownerUserId: UUID, limit: Int) = emptyList<GooglePlayOrderRecord>()
}
