package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.OwnedGroupCounter
import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.AppStoreRenewalInfo
import br.com.saqz.subscriptions.domain.AppStoreSubscription
import br.com.saqz.subscriptions.domain.GooglePlayProduct
import br.com.saqz.subscriptions.domain.GooglePlayState
import br.com.saqz.subscriptions.domain.GooglePlaySubscription
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionProvider
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetMySubscriptionTest {
    private val now = Instant.parse("2026-07-30T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    @Test
    fun `reports groups used against plan limit via moreRestrictive`() {
        val subscriptions = MemorySubscriptions(
            baseSubscription().copy(
                plan = Plan.ORGANIZADOR,
                pendingPlan = Plan.TITULAR,
            ),
        )
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(2), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))

        assertEquals(2, found.subscription.usage.groupsUsed)
        assertEquals(1, found.subscription.usage.groupsLimit)
        assertEquals(Plan.ORGANIZADOR, found.subscription.plan)
        assertEquals(Plan.TITULAR, found.subscription.pendingPlan)
        assertFalse(found.subscription.readOnly)
    }

    @Test
    fun `unlimited plan reports null groups limit`() {
        val subscriptions = MemorySubscriptions(baseSubscription().copy(plan = Plan.ILIMITADO))
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(5), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))
        assertEquals(5, found.subscription.usage.groupsUsed)
        assertNull(found.subscription.usage.groupsLimit)
    }

    @Test
    fun `past due within 7 day grace is not read only`() {
        val subscriptions = MemorySubscriptions(
            baseSubscription().copy(
                status = SubscriptionStatus.PAST_DUE,
                pastDueSince = Instant.parse("2026-07-23T12:00:00Z"),
            ),
        )
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(1), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))
        assertFalse(found.subscription.readOnly)
    }

    @Test
    fun `past due for more than 7 days becomes read only at read time`() {
        val subscriptions = MemorySubscriptions(
            baseSubscription().copy(
                status = SubscriptionStatus.PAST_DUE,
                pastDueSince = Instant.parse("2026-07-23T11:59:59Z"),
            ),
        )
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(1), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))
        assertTrue(found.subscription.readOnly)
    }

    @Test
    fun `canceled within 30 day grace is not read only`() {
        val subscriptions = MemorySubscriptions(
            baseSubscription().copy(
                status = SubscriptionStatus.CANCELED,
                canceledAt = Instant.parse("2026-06-30T12:00:00Z"),
                pastDueSince = null,
            ),
        )
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(1), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))
        assertFalse(found.subscription.readOnly)
    }

    @Test
    fun `canceled for more than 30 days becomes read only at read time`() {
        val subscriptions = MemorySubscriptions(
            baseSubscription().copy(
                status = SubscriptionStatus.CANCELED,
                canceledAt = Instant.parse("2026-06-30T11:59:59Z"),
                pastDueSince = null,
            ),
        )
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(1), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))
        assertTrue(found.subscription.readOnly)
    }

    @Test
    fun `active subscription is never read only`() {
        val subscriptions = MemorySubscriptions(
            baseSubscription().copy(
                status = SubscriptionStatus.ACTIVE,
                pastDueSince = null,
                canceledAt = null,
            ),
        )
        val useCase = GetMySubscription(subscriptions, FixedOwnedGroups(0), clock)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))
        assertFalse(found.subscription.readOnly)
        assertEquals(SubscriptionStatus.ACTIVE, found.subscription.status)
        assertEquals(Instant.parse("2026-08-30T00:00:00Z"), found.subscription.currentPeriodEnd)
    }

    @Test
    fun `missing subscription is not found`() {
        val useCase = GetMySubscription(MemorySubscriptions(null), FixedOwnedGroups(0), clock)
        assertEquals(GetMySubscriptionResult.NotFound, useCase.execute(ownerId))
    }

    @Test
    fun `isReadOnly is computed from now not from event time`() {
        val pastDueSince = Instant.parse("2026-07-20T00:00:00Z")
        val subscription = baseSubscription().copy(
            status = SubscriptionStatus.PAST_DUE,
            pastDueSince = pastDueSince,
        )
        assertFalse(GetMySubscription.isReadOnly(subscription, Instant.parse("2026-07-27T00:00:00Z")))
        assertTrue(GetMySubscription.isReadOnly(subscription, Instant.parse("2026-07-27T00:00:01Z")))
    }

    /**
     * `entitled` segue `Subscription.isEntitlingAt` — a regra do POST de criação — e não
     * `readOnly`: PAST_DUE nunca confirmada fica dentro da carência de leitura mas não
     * cria grupo (achado do Codex no PR #132).
     */
    @Test
    fun `entitled mirrors the creation predicate not the read-only grace`() {
        fun viewFor(subscription: Subscription): MySubscriptionView {
            val useCase = GetMySubscription(MemorySubscriptions(subscription), FixedOwnedGroups(0), clock)
            return assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId)).subscription
        }

        assertTrue(viewFor(baseSubscription()).entitled)

        val pastDueNeverConfirmed = baseSubscription().copy(
            status = SubscriptionStatus.PAST_DUE,
            pastDueSince = Instant.parse("2026-07-28T12:00:00Z"),
            firstConfirmedAt = null,
        )
        assertFalse(viewFor(pastDueNeverConfirmed).readOnly)
        assertFalse(viewFor(pastDueNeverConfirmed).entitled)
        assertTrue(viewFor(pastDueNeverConfirmed.copy(firstConfirmedAt = now.minusSeconds(60))).entitled)

        val canceledPeriodExpired = baseSubscription().copy(
            status = SubscriptionStatus.CANCELED,
            canceledAt = Instant.parse("2026-07-28T12:00:00Z"),
            currentPeriodEnd = Instant.parse("2026-07-29T00:00:00Z"),
            firstConfirmedAt = now.minusSeconds(60),
        )
        assertFalse(viewFor(canceledPeriodExpired).readOnly)
        assertFalse(viewFor(canceledPeriodExpired).entitled)
        assertTrue(
            viewFor(canceledPeriodExpired.copy(currentPeriodEnd = Instant.parse("2026-08-30T00:00:00Z"))).entitled,
        )
    }

    @Test
    fun `unconfirmed past due becomes entitled when Asaas already received the pix`() {
        val pending = baseSubscription().copy(
            status = SubscriptionStatus.PAST_DUE,
            pastDueSince = now.minusSeconds(60),
            firstConfirmedAt = null,
        )
        val repo = MutableSubscriptions(pending)
        val recover = RecoverUnconfirmedPayment(
            repo,
            PaidAsaas(),
            object : SubscriptionsTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = block()
            },
            clock,
        )
        val useCase = GetMySubscription(repo, FixedOwnedGroups(0), clock, recover)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))

        assertTrue(found.subscription.entitled)
        assertEquals(SubscriptionStatus.ACTIVE, found.subscription.status)
        assertEquals(SubscriptionStatus.ACTIVE, repo.findByOwnerUserId(ownerId)!!.status)
    }

    @Test
    fun `paid upgrade charge becomes the current plan when reading me`() {
        val pendingUpgrade = baseSubscription().copy(
            status = SubscriptionStatus.ACTIVE,
            firstConfirmedAt = now.minusSeconds(3_600),
            pastDueSince = null,
            plan = Plan.TITULAR,
            pendingUpgradePlan = Plan.ORGANIZADOR,
            pendingUpgradeChargeId = "pay_upgrade_1",
        )
        val repo = MutableSubscriptions(pendingUpgrade)
        val recover = RecoverUnconfirmedPayment(
            repo,
            PaidAsaas(),
            object : SubscriptionsTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = block()
            },
            clock,
        )
        val useCase = GetMySubscription(repo, FixedOwnedGroups(0), clock, recover)

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))

        assertEquals(Plan.ORGANIZADOR, found.subscription.plan)
        assertNull(found.subscription.pendingPlan)
        assertEquals(Plan.ORGANIZADOR, repo.findByOwnerUserId(ownerId)!!.plan)
        assertNull(repo.findByOwnerUserId(ownerId)!!.pendingUpgradeChargeId)
    }

    @Test
    fun `an App Store subscriber sees the App Store plan with auto renew`() {
        val view = appStoreView(appStore())

        assertEquals(SubscriptionProvider.APP_STORE, view.provider)
        assertEquals(SubscriptionStatus.ACTIVE, view.status)
        assertTrue(view.entitled)
        assertEquals(Plan.ORGANIZADOR, view.plan)
        assertEquals(3, view.usage.groupsLimit)
        assertNull(view.autoRenew)
    }

    @Test
    fun `App Store with auto renew off is canceled but keeps access until expiry`() {
        val off = now.minusSeconds(3_600)
        val view = appStoreView(appStore().applying(renewal(autoRenew = false, signedAt = off)))

        assertEquals(SubscriptionStatus.CANCELED, view.status)
        assertEquals(off, view.canceledAt)
        assertTrue(view.entitled)
        assertEquals(false, view.autoRenew)
    }

    @Test
    fun `App Store in billing retry is past due and loses access without grace`() {
        val expired = appStore(expiresAt = now.minusSeconds(60))
        val view = appStoreView(expired.applying(renewal(inBillingRetry = true)))

        assertEquals(SubscriptionStatus.PAST_DUE, view.status)
        assertEquals(now.minusSeconds(60), view.pastDueSince)
        assertFalse(view.entitled)
    }

    @Test
    fun `a refunded App Store subscription is canceled at the refund`() {
        val refundedAt = now.minusSeconds(600)
        val view = appStoreView(appStore().copy(revokedAt = refundedAt))

        assertEquals(SubscriptionStatus.CANCELED, view.status)
        assertEquals(refundedAt, view.currentPeriodEnd)
        assertFalse(view.entitled)
    }

    @Test
    fun `a downgrade scheduled in the App Store is the pending plan at renewal`() {
        val subscription = appStore().applying(renewal(autoRenewProductId = AppStoreProduct.TITULAR_MENSAL.productId))
        val view = appStoreView(subscription)

        assertEquals(Plan.TITULAR, view.pendingPlan)
        assertEquals(subscription.expiresAt, view.pendingPlanEffectiveAt)
        assertEquals(1, view.usage.groupsLimit)
    }

    @Test
    fun `an entitling App Store subscription wins over an expired web one`() {
        val web = baseSubscription().copy(
            status = SubscriptionStatus.CANCELED,
            canceledAt = now.minusSeconds(86_400),
            currentPeriodEnd = now.minusSeconds(3_600),
        )
        val useCase = GetMySubscription(
            MemorySubscriptions(web),
            FixedOwnedGroups(0),
            clock,
            appStoreSubscriptions = appStoreRepository(appStore()),
        )

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))

        assertEquals(SubscriptionProvider.APP_STORE, found.subscription.provider)
    }

    @Test
    fun `an active web subscription wins over an expired App Store one`() {
        val useCase = GetMySubscription(
            MemorySubscriptions(baseSubscription()),
            FixedOwnedGroups(0),
            clock,
            appStoreSubscriptions = appStoreRepository(appStore(expiresAt = now.minusSeconds(60))),
        )

        val found = assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId))

        assertEquals(SubscriptionProvider.ASAAS, found.subscription.provider)
        assertNull(found.subscription.autoRenew)
    }

    @Test
    fun `a Google Play subscriber in grace keeps access as past due`() {
        val view = googlePlayView(googlePlay(GooglePlayState.IN_GRACE_PERIOD))

        assertEquals(SubscriptionProvider.GOOGLE_PLAY, view.provider)
        assertEquals(SubscriptionStatus.PAST_DUE, view.status)
        assertTrue(view.entitled)
        assertEquals(Plan.ORGANIZADOR, view.plan)
    }

    @Test
    fun `Google Play on hold or replaced by an upgrade gives no access`() {
        assertFalse(googlePlayView(googlePlay(GooglePlayState.ON_HOLD)).entitled)
        val replaced = googlePlayView(googlePlay(GooglePlayState.ACTIVE).copy(supersededAt = now.minusSeconds(60)))
        assertEquals(SubscriptionStatus.CANCELED, replaced.status)
        assertFalse(replaced.entitled)
    }

    private fun googlePlayView(subscription: GooglePlaySubscription): MySubscriptionView {
        val repository = object : GooglePlaySubscriptionRepository {
            override fun insertIfAbsent(subscription: GooglePlaySubscription) = Unit
            override fun findForUpdate(purchaseToken: String): GooglePlaySubscription? = null
            override fun save(subscription: GooglePlaySubscription) = Unit
            override fun findByOwner(ownerUserId: UUID) = listOf(subscription)
            override fun ownerExists(ownerUserId: UUID) = true
            override fun supersede(purchaseToken: String, at: Instant) = Unit
            override fun markAcknowledged(purchaseToken: String) = Unit
            override fun recordOrder(subscription: GooglePlaySubscription) = Unit
            override fun listOrdersForOwner(ownerUserId: UUID, limit: Int) = emptyList<GooglePlayOrderRecord>()
        }
        val useCase = GetMySubscription(
            MemorySubscriptions(null),
            FixedOwnedGroups(0),
            clock,
            googlePlaySubscriptions = repository,
        )
        return assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId)).subscription
    }

    private fun googlePlay(state: GooglePlayState) = GooglePlaySubscription(
        purchaseToken = "tok",
        ownerUserId = ownerId,
        product = GooglePlayProduct.ORGANIZADOR_MENSAL,
        state = state,
        expiresAt = now.plusSeconds(10L * 24 * 3600),
        autoRenew = true,
        canceledAt = null,
        latestOrderId = "GPA.1",
        linkedPurchaseToken = null,
        acknowledged = true,
        testPurchase = false,
    )

    private fun appStoreView(subscription: AppStoreSubscription): MySubscriptionView {
        val useCase = GetMySubscription(
            MemorySubscriptions(null),
            FixedOwnedGroups(0),
            clock,
            appStoreSubscriptions = appStoreRepository(subscription),
        )
        return assertIs<GetMySubscriptionResult.Found>(useCase.execute(ownerId)).subscription
    }

    private fun appStoreRepository(subscription: AppStoreSubscription) =
        InMemoryAppStoreSubscriptions().apply { save(subscription) }

    private fun appStore(expiresAt: Instant = now.plusSeconds(15L * 24 * 3600)) = AppStoreSubscription.startedBy(
        appStoreTransaction(ownerId, purchaseDate = expiresAt.minusSeconds(30L * 24 * 3600), expiresDate = expiresAt),
        AppStoreProduct.ORGANIZADOR_MENSAL,
        ownerId,
    )

    private fun renewal(
        autoRenew: Boolean = true,
        autoRenewProductId: String = AppStoreProduct.ORGANIZADOR_MENSAL.productId,
        inBillingRetry: Boolean = false,
        signedAt: Instant = now.minusSeconds(60),
    ) = AppStoreRenewalInfo(
        originalTransactionId = "2000000001",
        autoRenew = autoRenew,
        autoRenewProductId = autoRenewProductId,
        inBillingRetry = inBillingRetry,
        gracePeriodExpiresAt = null,
        signedAt = signedAt,
    )

    private fun baseSubscription() = Subscription(
        ownerUserId = ownerId,
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_1",
        asaasSubscriptionId = "sub_1",
        billingType = AsaasBillingType.PIX,
        currentPeriodEnd = Instant.parse("2026-08-30T00:00:00Z"),
        status = SubscriptionStatus.ACTIVE,
    )

    private class MutableSubscriptions(initial: Subscription) : SubscriptionRepository {
        private var current: Subscription = initial
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String): Subscription? = null
        override fun findByOwnerUserId(ownerUserId: UUID): Subscription? =
            current.takeIf { it.ownerUserId == ownerUserId }
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID): Subscription? =
            findByOwnerUserId(ownerUserId)
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun findByLastConfirmedPaymentId(paymentId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = error("unused")
        override fun save(subscription: Subscription) {
            current = subscription
        }
    }

    private class PaidAsaas : AsaasGateway {
        override fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String) = error("unused")
        override fun createSubscription(
            asaasCustomerId: String,
            plan: Plan,
            cycle: SubscriptionCycle,
            valueCents: Long,
            billingType: AsaasBillingType,
            idempotencyKey: String,
            creditCard: CreditCardDetails?,
            creditCardHolderInfo: CreditCardHolderInfo?,
            remoteIp: String?,
        ) = error("unused")
        override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) = Unit
        override fun cancelSubscription(asaasSubscriptionId: String) = error("unused")
        override fun createOneOffCharge(
            asaasCustomerId: String,
            valueCents: Long,
            description: String,
            idempotencyKey: String,
        ) = error("unused")
        override fun regeneratePixPayload(asaasChargeId: String) = error("unused")
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = "pay_1"
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = null
        override fun findPayment(asaasPaymentId: String) =
            AsaasPaymentSnapshot(id = asaasPaymentId, status = "RECEIVED", invoiceUrl = null)
    }

    private class MemorySubscriptions(private val subscription: Subscription?) : SubscriptionRepository {
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String): Subscription? = null
        override fun findByOwnerUserId(ownerUserId: UUID): Subscription? =
            subscription?.takeIf { it.ownerUserId == ownerUserId }
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID): Subscription? =
            findByOwnerUserId(ownerUserId)
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun findByLastConfirmedPaymentId(paymentId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = error("unused")
        override fun save(subscription: Subscription) = error("unused")
    }

    private class FixedOwnedGroups(private val count: Int) : OwnedGroupCounter {
        override fun countOwnedGroups(ownerUserId: UUID): Int = count
    }
}
