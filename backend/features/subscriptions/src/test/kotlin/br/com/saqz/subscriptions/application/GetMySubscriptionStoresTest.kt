package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.OwnedGroupCounter
import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.AppStoreSubscription
import br.com.saqz.subscriptions.domain.GooglePlayProduct
import br.com.saqz.subscriptions.domain.GooglePlayState
import br.com.saqz.subscriptions.domain.GooglePlaySubscription
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionProvider
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Como `/subscriptions/me` traduz o estado de cada loja para o que o app mostra. */
class GetMySubscriptionStoresTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val inTenDays = now.plus(Duration.ofDays(10))

    // --- Carencia de somente leitura ---

    @Test
    fun `past due turns read only only after seven full days`() {
        val since = Instant.parse("2026-10-01T00:00:00Z")
        val sevenDays = since.plus(Duration.ofDays(7))

        assertFalse(GetMySubscription.isReadOnly(SubscriptionStatus.PAST_DUE, since, null, sevenDays))
        assertTrue(GetMySubscription.isReadOnly(SubscriptionStatus.PAST_DUE, since, null, sevenDays.plusSeconds(1)))
        assertFalse(GetMySubscription.isReadOnly(SubscriptionStatus.PAST_DUE, null, null, sevenDays.plusSeconds(1)))
    }

    @Test
    fun `canceled turns read only only after thirty full days`() {
        val canceled = Instant.parse("2026-09-01T00:00:00Z")
        val thirtyDays = canceled.plus(Duration.ofDays(30))

        assertFalse(GetMySubscription.isReadOnly(SubscriptionStatus.CANCELED, null, canceled, thirtyDays))
        assertTrue(GetMySubscription.isReadOnly(SubscriptionStatus.CANCELED, null, canceled, thirtyDays.plusSeconds(1)))
        assertFalse(GetMySubscription.isReadOnly(SubscriptionStatus.CANCELED, null, null, thirtyDays.plusSeconds(1)))
        assertFalse(GetMySubscription.isReadOnly(SubscriptionStatus.ACTIVE, null, null, thirtyDays.plusSeconds(1)))
    }

    @Test
    fun `grace windows are 7 and 30 days`() {
        assertEquals(Duration.ofDays(7), GetMySubscription.STORE_PAST_DUE_GRACE)
        assertEquals(Duration.ofDays(30), GetMySubscription.CANCELED_GRACE)
    }

    // --- Google Play ---

    @Test
    fun `Google Play states map to the status the app shows`() {
        val expected = mapOf(
            GooglePlayState.ACTIVE to SubscriptionStatus.ACTIVE,
            GooglePlayState.IN_GRACE_PERIOD to SubscriptionStatus.PAST_DUE,
            GooglePlayState.ON_HOLD to SubscriptionStatus.PAST_DUE,
            GooglePlayState.PAUSED to SubscriptionStatus.PAST_DUE,
            GooglePlayState.CANCELED to SubscriptionStatus.CANCELED,
            GooglePlayState.EXPIRED to SubscriptionStatus.CANCELED,
            GooglePlayState.PENDING to SubscriptionStatus.CANCELED,
            GooglePlayState.PENDING_PURCHASE_CANCELED to SubscriptionStatus.CANCELED,
        )
        for ((state, status) in expected) {
            assertEquals(status, googlePlayView(googlePlay(state)).status, state.name)
        }
    }

    @Test
    fun `an active Google Play subscription shows the period, renewal and no cancellation`() {
        val view = googlePlayView(googlePlay(GooglePlayState.ACTIVE))

        assertTrue(view.entitled)
        assertEquals(SubscriptionProvider.GOOGLE_PLAY, view.provider)
        assertEquals(inTenDays, view.currentPeriodEnd)
        assertEquals(Plan.ORGANIZADOR, view.plan)
        assertEquals(SubscriptionCycle.MONTHLY, view.cycle)
        assertEquals(true, view.autoRenew)
        assertNull(view.paymentMethod)
        assertNull(view.pastDueSince)
        assertNull(view.canceledAt)
        assertNull(view.pendingPlan)
        assertNull(view.pendingPlanEffectiveAt)
        assertFalse(view.readOnly)
    }

    @Test
    fun `Google Play past due counts from the expiry and is read only after seven days`() {
        val expired = now.minus(Duration.ofDays(8))
        val view = googlePlayView(googlePlay(GooglePlayState.ON_HOLD, expiresAt = expired))

        assertEquals(expired, view.pastDueSince)
        assertNull(view.canceledAt)
        assertTrue(view.readOnly)
        assertFalse(googlePlayView(googlePlay(GooglePlayState.ON_HOLD, expiresAt = now.minus(Duration.ofDays(6)))).readOnly)
    }

    @Test
    fun `Google Play cancellation date is the cancel time, then the replacement time, then the expiry`() {
        val canceledAt = now.minusSeconds(3_600)
        val supersededAt = now.minusSeconds(7_200)

        assertEquals(
            canceledAt,
            googlePlayView(googlePlay(GooglePlayState.CANCELED, canceledAt = canceledAt, supersededAt = supersededAt)).canceledAt,
        )
        assertEquals(supersededAt, googlePlayView(googlePlay(GooglePlayState.ACTIVE, supersededAt = supersededAt)).canceledAt)
        assertEquals(inTenDays, googlePlayView(googlePlay(GooglePlayState.CANCELED)).canceledAt)
    }

    @Test
    fun `a canceled Google Play subscription keeps access until the paid period ends`() {
        val view = googlePlayView(googlePlay(GooglePlayState.CANCELED, canceledAt = now.minusSeconds(60)))

        assertTrue(view.entitled)
        assertEquals(SubscriptionStatus.CANCELED, view.status)
        assertFalse(view.readOnly)
    }

    @Test
    fun `a Google Play downgrade shows the pending plan at the expiry and the stricter group limit`() {
        val view = googlePlayView(googlePlay(GooglePlayState.ACTIVE, pendingPlan = Plan.TITULAR), groups = 2)

        assertEquals(Plan.TITULAR, view.pendingPlan)
        assertEquals(inTenDays, view.pendingPlanEffectiveAt)
        assertEquals(2, view.usage.groupsUsed)
        assertEquals(1, view.usage.groupsLimit)
    }

    // --- App Store ---

    @Test
    fun `an App Store subscription with renewal on is active and shows the product`() {
        val view = appStoreView(appStore(), groups = 1)

        assertEquals(SubscriptionStatus.ACTIVE, view.status)
        assertEquals(SubscriptionProvider.APP_STORE, view.provider)
        assertEquals(inTenDays, view.currentPeriodEnd)
        assertEquals(Plan.ORGANIZADOR, view.plan)
        assertEquals(SubscriptionCycle.MONTHLY, view.cycle)
        assertNull(view.paymentMethod)
        assertNull(view.pastDueSince)
        assertNull(view.canceledAt)
        assertNull(view.pendingPlan)
        assertNull(view.pendingPlanEffectiveAt)
        assertEquals(1, view.usage.groupsUsed)
        assertEquals(3, view.usage.groupsLimit)
        assertTrue(view.entitled)
        assertFalse(view.readOnly)
    }

    @Test
    fun `an App Store subscription with renewal off is canceled from the day it was turned off`() {
        val turnedOff = now.minusSeconds(3_600)
        val view = appStoreView(appStore().copy(autoRenew = false, autoRenewChangedAt = turnedOff))

        assertEquals(SubscriptionStatus.CANCELED, view.status)
        assertEquals(turnedOff, view.canceledAt)
        assertEquals(false, view.autoRenew)
        assertTrue(view.entitled)
    }

    @Test
    fun `an App Store cancellation without a recorded change falls back to the expiry`() {
        val view = appStoreView(appStore().copy(autoRenew = false, autoRenewChangedAt = null))

        assertEquals(inTenDays, view.canceledAt)
    }

    @Test
    fun `a refunded App Store subscription ends at the refund`() {
        val refund = now.minusSeconds(60)
        val view = appStoreView(appStore().copy(revokedAt = refund))

        assertEquals(SubscriptionStatus.CANCELED, view.status)
        assertEquals(refund, view.currentPeriodEnd)
        assertEquals(refund, view.canceledAt)
        assertFalse(view.entitled)
    }

    @Test
    fun `an expired App Store subscription in billing retry is past due since the expiry`() {
        val expired = now.minus(Duration.ofDays(2))
        val view = appStoreView(appStore(expiresAt = expired).copy(inBillingRetry = true))

        assertEquals(SubscriptionStatus.PAST_DUE, view.status)
        assertEquals(expired, view.pastDueSince)
        assertNull(view.canceledAt)
        assertFalse(view.readOnly)
        assertFalse(view.entitled)
    }

    @Test
    fun `an expired App Store subscription without retry is canceled at the expiry`() {
        val expired = now.minus(Duration.ofDays(31))
        val view = appStoreView(appStore(expiresAt = expired))

        assertEquals(SubscriptionStatus.CANCELED, view.status)
        assertEquals(expired, view.canceledAt)
        assertTrue(view.readOnly)
    }

    @Test
    fun `an App Store downgrade shows the pending plan at the expiry`() {
        val view = appStoreView(
            appStore().copy(autoRenew = true, autoRenewProductId = AppStoreProduct.TITULAR_MENSAL.productId),
            groups = 2,
        )

        assertEquals(Plan.TITULAR, view.pendingPlan)
        assertEquals(inTenDays, view.pendingPlanEffectiveAt)
        assertEquals(1, view.usage.groupsLimit)
    }

    // --- Escolha entre fontes ---

    @Test
    fun `with two entitling stores the one that lasts longer is shown`() {
        val view = viewOf(
            appStore = listOf(appStore(expiresAt = now.plus(Duration.ofDays(5)))),
            googlePlay = listOf(googlePlay(GooglePlayState.ACTIVE, expiresAt = now.plus(Duration.ofDays(20)))),
        )

        assertEquals(SubscriptionProvider.GOOGLE_PLAY, view.provider)
    }

    @Test
    fun `with no entitling source the one that expired last is shown`() {
        val view = viewOf(
            appStore = listOf(appStore(expiresAt = now.minus(Duration.ofDays(20)))),
            googlePlay = listOf(googlePlay(GooglePlayState.EXPIRED, expiresAt = now.minus(Duration.ofDays(3)))),
        )

        assertEquals(SubscriptionProvider.GOOGLE_PLAY, view.provider)
    }

    @Test
    fun `an owner with no source at all gets NotFound`() {
        val useCase = GetMySubscription(NoSubscriptions, OwnedGroupCounter { 0 }, clock)

        assertEquals(GetMySubscriptionResult.NotFound, useCase.execute(ownerId))
    }

    // --- Apoio ---

    private fun viewOf(
        appStore: List<AppStoreSubscription> = emptyList(),
        googlePlay: List<GooglePlaySubscription> = emptyList(),
        groups: Int = 0,
    ): MySubscriptionView {
        val appStoreRepo = InMemoryAppStoreSubscriptions().apply { appStore.forEach { save(it) } }
        val playRepo = object : GooglePlaySubscriptionRepository {
            override fun insertIfAbsent(subscription: GooglePlaySubscription) = Unit
            override fun findForUpdate(purchaseToken: String): GooglePlaySubscription? = null
            override fun save(subscription: GooglePlaySubscription) = Unit
            override fun findByOwner(ownerUserId: UUID) = googlePlay
            override fun ownerExists(ownerUserId: UUID) = true
            override fun supersede(purchaseToken: String, at: Instant) = Unit
            override fun markAcknowledged(purchaseToken: String) = Unit
            override fun recordOrder(subscription: GooglePlaySubscription) = Unit
            override fun listOrdersForOwner(ownerUserId: UUID, limit: Int) = emptyList<GooglePlayOrderRecord>()
        }
        val useCase = GetMySubscription(
            NoSubscriptions,
            OwnedGroupCounter { groups },
            clock,
            appStoreSubscriptions = appStoreRepo,
            googlePlaySubscriptions = playRepo,
        )
        return assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId)).subscription
    }

    private fun googlePlayView(subscription: GooglePlaySubscription, groups: Int = 0) =
        viewOf(googlePlay = listOf(subscription), groups = groups)

    private fun appStoreView(subscription: AppStoreSubscription, groups: Int = 0) =
        viewOf(appStore = listOf(subscription), groups = groups)

    private fun googlePlay(
        state: GooglePlayState,
        expiresAt: Instant = inTenDays,
        canceledAt: Instant? = null,
        supersededAt: Instant? = null,
        pendingPlan: Plan? = null,
    ) = GooglePlaySubscription(
        purchaseToken = "tok",
        ownerUserId = ownerId,
        product = GooglePlayProduct.ORGANIZADOR_MENSAL,
        state = state,
        expiresAt = expiresAt,
        autoRenew = true,
        canceledAt = canceledAt,
        latestOrderId = "GPA.1",
        linkedPurchaseToken = null,
        supersededAt = supersededAt,
        acknowledged = true,
        testPurchase = false,
        pendingPlan = pendingPlan,
    )

    private fun appStore(expiresAt: Instant = inTenDays) = AppStoreSubscription.startedBy(
        appStoreTransaction(ownerId, purchaseDate = expiresAt.minus(Duration.ofDays(30)), expiresDate = expiresAt),
        AppStoreProduct.ORGANIZADOR_MENSAL,
        ownerId,
    )

    private object NoSubscriptions : SubscriptionRepository {
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) = null
        override fun findByOwnerUserId(ownerUserId: UUID) = null
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID) = null
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun findByLastConfirmedPaymentId(paymentId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: br.com.saqz.subscriptions.domain.Subscription) = error("unused")
        override fun save(subscription: br.com.saqz.subscriptions.domain.Subscription) = error("unused")
    }
}
