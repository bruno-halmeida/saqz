package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.AppStoreEnvironment
import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.AppStoreSubscription
import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.domain.GooglePlayProduct
import br.com.saqz.subscriptions.domain.GooglePlayState
import br.com.saqz.subscriptions.domain.GooglePlaySubscription
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReactivateSubscriptionTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val periodEnd = Instant.parse("2026-11-10T00:00:00Z")
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val couponId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")

    private lateinit var subscriptions: RecordingSubscriptions
    private lateinit var gateway: RecordingGateway
    private lateinit var coupons: Coupons
    private val appStore = InMemoryAppStoreSubscriptions()
    private val googlePlay = GooglePlayRows()
    private var cardToken: String? = "card_tok_1"
    private lateinit var useCase: ReactivateSubscription

    @BeforeEach
    fun setUp() {
        subscriptions = RecordingSubscriptions()
        gateway = RecordingGateway()
        coupons = Coupons()
        useCase = useCase()
        subscriptions.save(canceled())
    }

    private fun useCase(at: Instant = now) = ReactivateSubscription(
        subscriptions,
        gateway,
        object : SubscriptionsTransactionRunner {
            override fun <T> inTransaction(block: () -> T): T = block()
        },
        Clock.fixed(at, ZoneOffset.UTC),
        coupons,
        { cardToken },
        appStore,
        googlePlay,
    )

    @Test
    fun `owner without a subscription gets NotFound`() {
        val result = useCase.execute(UUID.randomUUID(), "203.0.113.5")

        assertEquals(ReactivateSubscriptionResult.NotFound, result)
        assertTrue(gateway.created.isEmpty())
    }

    @Test
    fun `a subscription that was never canceled has nothing to reactivate`() {
        subscriptions.save(canceled().copy(canceledAt = null, status = SubscriptionStatus.ACTIVE))

        assertEquals(ReactivateSubscriptionResult.NotCanceled, useCase.execute(ownerId, null))
        assertTrue(gateway.created.isEmpty())
    }

    @Test
    fun `after the paid period ended the way back is a new subscription`() {
        assertEquals(ReactivateSubscriptionResult.AccessEnded, useCase(at = periodEnd).execute(ownerId, null))
        assertTrue(gateway.created.isEmpty())
    }

    @Test
    fun `access is still granted one second before the period ends`() {
        assertIs<ReactivateSubscriptionResult.Success>(useCase(at = periodEnd.minusSeconds(1)).execute(ownerId, null))
    }

    @Test
    fun `a subscription that never had a payment confirmed cannot be reactivated`() {
        subscriptions.save(canceled().copy(firstConfirmedAt = null))

        assertEquals(ReactivateSubscriptionResult.AccessEnded, useCase.execute(ownerId, null))
    }

    @Test
    fun `a refunded subscription whose period was cut cannot be reactivated`() {
        subscriptions.save(canceled().copy(currentPeriodEnd = now))

        assertEquals(ReactivateSubscriptionResult.AccessEnded, useCase.execute(ownerId, null))
    }

    @Test
    fun `an active App Store subscription blocks reactivating the web one`() {
        appStore.rows["otx"] = appStoreSubscription(expiresAt = now.plusSeconds(60))

        assertEquals(ReactivateSubscriptionResult.AlreadySubscribed, useCase.execute(ownerId, null))
        assertTrue(gateway.created.isEmpty())
    }

    @Test
    fun `an expired App Store subscription does not block reactivating`() {
        appStore.rows["otx"] = appStoreSubscription(expiresAt = now.minusSeconds(60))

        assertIs<ReactivateSubscriptionResult.Success>(useCase.execute(ownerId, null))
    }

    @Test
    fun `an active Google Play subscription blocks reactivating the web one`() {
        googlePlay.rows["token"] = googlePlaySubscription(expiresAt = now.plusSeconds(60))

        assertEquals(ReactivateSubscriptionResult.AlreadySubscribed, useCase.execute(ownerId, null))
        assertTrue(gateway.created.isEmpty())
    }

    @Test
    fun `an expired Google Play subscription does not block reactivating`() {
        googlePlay.rows["token"] = googlePlaySubscription(expiresAt = now.minusSeconds(60))

        assertIs<ReactivateSubscriptionResult.Success>(useCase.execute(ownerId, null))
    }

    @Test
    fun `a card subscription without a stored token cannot be reactivated`() {
        subscriptions.save(canceled().copy(billingType = AsaasBillingType.CREDIT_CARD))
        cardToken = null

        assertEquals(ReactivateSubscriptionResult.CardUnavailable, useCase.execute(ownerId, "203.0.113.5"))
        assertTrue(gateway.created.isEmpty())
    }

    @Test
    fun `reactivating a card subscription charges the stored token on the day the period ends`() {
        subscriptions.save(canceled().copy(billingType = AsaasBillingType.CREDIT_CARD))

        val result = useCase.execute(ownerId, "203.0.113.5")

        assertIs<ReactivateSubscriptionResult.Success>(result)
        val call = gateway.created.single()
        assertEquals("card_tok_1", call.creditCardToken)
        assertEquals("203.0.113.5", call.remoteIp)
        assertEquals(AsaasBillingType.CREDIT_CARD, call.billingType)
        assertEquals(LocalDate.parse("2026-11-10"), call.nextDueDate)
    }

    @Test
    fun `reactivating a pix subscription sends no card token`() {
        val result = useCase.execute(ownerId, "203.0.113.5")

        assertIs<ReactivateSubscriptionResult.Success>(result)
        val call = gateway.created.single()
        assertNull(call.creditCardToken)
        assertEquals(AsaasBillingType.PIX, call.billingType)
    }

    @Test
    fun `a legacy row without billing type is reactivated as pix`() {
        subscriptions.save(canceled().copy(billingType = null))
        cardToken = "nao-deve-ser-usado"

        useCase.execute(ownerId, null)

        val call = gateway.created.single()
        assertEquals(AsaasBillingType.PIX, call.billingType)
        assertNull(call.creditCardToken)
    }

    @Test
    fun `reactivation creates the new asaas subscription and stores it as active`() {
        val result = useCase.execute(ownerId, null)

        val success = assertIs<ReactivateSubscriptionResult.Success>(result)
        assertEquals("sub_new", success.subscription.asaasSubscriptionId)
        assertEquals(SubscriptionStatus.ACTIVE, success.subscription.status)
        assertNull(success.subscription.canceledAt)
        val stored = subscriptions.findByOwnerUserId(ownerId)!!
        assertEquals("sub_new", stored.asaasSubscriptionId)
        assertEquals(SubscriptionStatus.ACTIVE, stored.status)
        assertNull(stored.canceledAt)
        assertEquals(periodEnd, stored.currentPeriodEnd)
        assertEquals(listOf(ownerId), subscriptions.lockedOwners)
    }

    @Test
    fun `the same cancellation always uses the same idempotency key`() {
        useCase.execute(ownerId, null)

        val call = gateway.created.single()
        assertEquals("subscription-reactivate:$ownerId:sub_old", call.idempotencyKey)
        assertEquals("cus_1", call.asaasCustomerId)
        assertEquals(SubscriptionCycle.MONTHLY, call.cycle)
    }

    @Test
    fun `the first charge is on the day the period ends when that is still today`() {
        val endsLaterToday = Instant.parse("2026-10-18T23:00:00Z")
        subscriptions.save(canceled().copy(currentPeriodEnd = endsLaterToday))

        useCase.execute(ownerId, null)

        assertEquals(LocalDate.parse("2026-10-18"), gateway.created.single().nextDueDate)
    }

    @Test
    fun `a scheduled downgrade is what gets billed after reactivating`() {
        subscriptions.save(canceled().copy(plan = Plan.ORGANIZADOR, pendingPlan = Plan.TITULAR))

        useCase.execute(ownerId, null)

        val call = gateway.created.single()
        assertEquals(Plan.TITULAR, call.plan)
        assertEquals(3_990L, call.valueCents)
    }

    @Test
    fun `without a scheduled change the current plan is billed at the full price`() {
        subscriptions.save(canceled().copy(plan = Plan.ORGANIZADOR))

        useCase.execute(ownerId, null)

        val call = gateway.created.single()
        assertEquals(Plan.ORGANIZADOR, call.plan)
        assertEquals(5_990L, call.valueCents)
    }

    @Test
    fun `an annual cycle is billed at the annual price`() {
        subscriptions.save(canceled().copy(plan = Plan.TITULAR, cycle = SubscriptionCycle.ANNUAL))

        useCase.execute(ownerId, null)

        assertEquals(35_910L, gateway.created.single().valueCents)
    }

    @Test
    fun `a coupon with cycles left keeps its discount in the new subscription`() {
        coupons.byId[couponId] = Coupon(couponId, "DEZ", discountPercent = 10)
        subscriptions.save(canceled().copy(plan = Plan.ORGANIZADOR, couponId = couponId, couponCyclesRemaining = 2))

        useCase.execute(ownerId, null)

        // 5990 - 10% = 5391
        assertEquals(5_391L, gateway.created.single().valueCents)
    }

    @Test
    fun `a permanent coupon keeps its discount in the new subscription`() {
        coupons.byId[couponId] = Coupon(couponId, "DEZ", discountPercent = 10)
        subscriptions.save(canceled().copy(plan = Plan.ORGANIZADOR, couponId = couponId, couponCyclesRemaining = null))

        useCase.execute(ownerId, null)

        assertEquals(5_391L, gateway.created.single().valueCents)
    }

    @Test
    fun `an exhausted coupon is billed at the full price`() {
        coupons.byId[couponId] = Coupon(couponId, "DEZ", discountPercent = 10)
        subscriptions.save(canceled().copy(plan = Plan.ORGANIZADOR, couponId = couponId, couponCyclesRemaining = 0))

        useCase.execute(ownerId, null)

        assertEquals(5_990L, gateway.created.single().valueCents)
    }

    @Test
    fun `a coupon that no longer exists is billed at the full price`() {
        subscriptions.save(canceled().copy(plan = Plan.ORGANIZADOR, couponId = couponId, couponCyclesRemaining = 2))

        useCase.execute(ownerId, null)

        assertEquals(5_990L, gateway.created.single().valueCents)
    }

    @Test
    fun `reactivating without a coupon repository bills the full price`() {
        val withoutCoupons = ReactivateSubscription(
            subscriptions,
            gateway,
            object : SubscriptionsTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = block()
            },
            Clock.fixed(now, ZoneOffset.UTC),
        )
        subscriptions.save(canceled().copy(plan = Plan.ORGANIZADOR, couponId = couponId, couponCyclesRemaining = 2))

        withoutCoupons.execute(ownerId, null)

        assertEquals(5_990L, gateway.created.single().valueCents)
    }

    @Test
    fun `without store repositories reactivation goes ahead`() {
        val plain = ReactivateSubscription(
            subscriptions,
            gateway,
            object : SubscriptionsTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = block()
            },
            Clock.fixed(now, ZoneOffset.UTC),
        )

        assertIs<ReactivateSubscriptionResult.Success>(plain.execute(ownerId, null))
    }

    private fun canceled() = Subscription(
        ownerUserId = ownerId,
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_1",
        asaasSubscriptionId = "sub_old",
        billingType = AsaasBillingType.PIX,
        currentPeriodEnd = periodEnd,
        status = SubscriptionStatus.CANCELED,
        canceledAt = now.minusSeconds(3_600),
        firstConfirmedAt = Instant.parse("2026-09-10T00:00:00Z"),
    )

    private fun appStoreSubscription(expiresAt: Instant) = AppStoreSubscription(
        originalTransactionId = "otx",
        ownerUserId = ownerId,
        environment = AppStoreEnvironment.SANDBOX,
        product = AppStoreProduct.TITULAR_MENSAL,
        latestTransactionId = "otx",
        latestPurchaseDate = now.minusSeconds(86_400),
        latestSignedAt = now.minusSeconds(86_400),
        expiresAt = expiresAt,
        revokedAt = null,
    )

    private fun googlePlaySubscription(expiresAt: Instant) = GooglePlaySubscription(
        purchaseToken = "token",
        ownerUserId = ownerId,
        product = GooglePlayProduct.TITULAR_MENSAL,
        state = GooglePlayState.ACTIVE,
        expiresAt = expiresAt,
        autoRenew = true,
        canceledAt = null,
        latestOrderId = "GPA.1",
        linkedPurchaseToken = null,
        acknowledged = true,
        testPurchase = false,
    )

    private class RecordingSubscriptions : SubscriptionRepository {
        val lockedOwners = mutableListOf<UUID>()
        private val byOwner = linkedMapOf<UUID, Subscription>()
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) =
            byOwner.values.firstOrNull { it.asaasSubscriptionId == asaasSubscriptionId }
        override fun findByOwnerUserId(ownerUserId: UUID) = byOwner[ownerUserId]
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID) = byOwner[ownerUserId]
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun findByLastConfirmedPaymentId(paymentId: String) = null
        override fun lockOwner(ownerUserId: UUID) {
            lockedOwners += ownerUserId
        }
        override fun insert(subscription: Subscription) = save(subscription)
        override fun save(subscription: Subscription) {
            byOwner[subscription.ownerUserId] = subscription
        }
    }

    private class Coupons : CouponRepository {
        val byId = linkedMapOf<UUID, Coupon>()
        override fun findByCode(code: String) = byId.values.firstOrNull { it.code == code }
        override fun findById(couponId: UUID) = byId[couponId]
        override fun hasRedemption(couponId: UUID, userId: UUID) = false
        override fun saveRedemption(redemption: CouponRedemption) = Unit
    }

    private class GooglePlayRows : GooglePlaySubscriptionRepository {
        val rows = linkedMapOf<String, GooglePlaySubscription>()
        override fun insertIfAbsent(subscription: GooglePlaySubscription) {
            rows.putIfAbsent(subscription.purchaseToken, subscription)
        }
        override fun findForUpdate(purchaseToken: String) = rows[purchaseToken]
        override fun save(subscription: GooglePlaySubscription) {
            rows[subscription.purchaseToken] = subscription
        }
        override fun findByOwner(ownerUserId: UUID) = rows.values.filter { it.ownerUserId == ownerUserId }
        override fun ownerExists(ownerUserId: UUID) = true
        override fun supersede(purchaseToken: String, at: Instant) = Unit
        override fun markAcknowledged(purchaseToken: String) = Unit
        override fun recordOrder(subscription: GooglePlaySubscription) = Unit
        override fun listOrdersForOwner(ownerUserId: UUID, limit: Int) = emptyList<GooglePlayOrderRecord>()
    }

    private class RecordingGateway : AsaasGateway {
        data class Created(
            val asaasCustomerId: String,
            val plan: Plan,
            val cycle: SubscriptionCycle,
            val valueCents: Long,
            val billingType: AsaasBillingType,
            val nextDueDate: LocalDate,
            val idempotencyKey: String,
            val creditCardToken: String?,
            val remoteIp: String?,
        )

        val created = mutableListOf<Created>()

        override fun createSubscriptionStartingAt(
            asaasCustomerId: String,
            plan: Plan,
            cycle: SubscriptionCycle,
            valueCents: Long,
            billingType: AsaasBillingType,
            nextDueDate: LocalDate,
            idempotencyKey: String,
            creditCardToken: String?,
            remoteIp: String?,
        ): String {
            created += Created(
                asaasCustomerId, plan, cycle, valueCents, billingType, nextDueDate, idempotencyKey, creditCardToken, remoteIp,
            )
            return "sub_new"
        }

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
        override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) = error("unused")
        override fun cancelSubscription(asaasSubscriptionId: String) = error("unused")
        override fun createOneOffCharge(
            asaasCustomerId: String,
            valueCents: Long,
            description: String,
            idempotencyKey: String,
        ) = error("unused")
        override fun regeneratePixPayload(asaasChargeId: String) = error("unused")
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = error("unused")
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = error("unused")
        override fun findPayment(asaasPaymentId: String) = error("unused")
    }
}
