package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateSubscriptionTest {
    private val fixedNow = Instant.parse("2026-07-30T12:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val requestId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val couponId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")

    private lateinit var subscriptions: FakeSubscriptionRepository
    private lateinit var coupons: FakeCouponRepository
    private lateinit var gateway: FakeAsaasGateway
    private lateinit var useCase: CreateSubscription

    @BeforeEach
    fun setUp() {
        subscriptions = FakeSubscriptionRepository()
        coupons = FakeCouponRepository()
        gateway = FakeAsaasGateway()
        useCase = CreateSubscription(
            subscriptions = subscriptions,
            coupons = coupons,
            asaasGateway = gateway,
            transaction = object : SubscriptionsTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = block()
            },
            clock = clock,
        )
    }

    @Test
    fun `creates subscription without coupon and returns pix payload`() {
        gateway.pixPayload = "00020126PIX-COPY-PASTE"

        val result = useCase.execute(baseCommand())

        val success = assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals(Plan.TITULAR, success.subscription.plan)
        assertEquals(SubscriptionStatus.PAST_DUE, success.subscription.status)
        assertEquals(fixedNow, success.subscription.pastDueSince)
        assertNull(success.subscription.firstConfirmedAt)
        assertEquals("sub_1", success.subscription.asaasSubscriptionId)
        assertEquals("00020126PIX-COPY-PASTE", success.pixCopyPaste)
        assertTrue(subscriptions.lockedOwners.contains(ownerId))
        assertEquals(1, subscriptions.inserted.size)
    }

    @Test
    fun `creates subscription with coupon applying discount and recording redemption`() {
        coupons.byCode["PROMO20"] = Coupon(
            id = couponId,
            code = "PROMO20",
            discountPercent = 20,
            durationCycles = 3,
        )

        val result = useCase.execute(baseCommand().copy(couponCode = "PROMO20"))

        val success = assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals(couponId, success.subscription.couponId)
        assertEquals(3, success.subscription.couponCyclesRemaining)
        assertEquals(3_192L, gateway.lastSubscriptionValueCents)
        assertEquals(1, coupons.redemptions.size)
    }

    @Test
    fun `refuses coupon already redeemed by the same user`() {
        coupons.byCode["PROMO20"] = Coupon(id = couponId, code = "PROMO20", discountPercent = 10)
        coupons.redemptions += CouponRedemption(couponId, ownerId, fixedNow.minusSeconds(3600))

        assertEquals(
            CreateSubscriptionResult.CouponAlreadyRedeemed,
            useCase.execute(baseCommand().copy(couponCode = "PROMO20")),
        )
        assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
    }

    @Test
    fun `refuses unknown coupon without calling asaas subscription create`() {
        assertEquals(
            CreateSubscriptionResult.CouponNotFound,
            useCase.execute(baseCommand().copy(couponCode = "MISSING")),
        )
    }

    @Test
    fun `refuses expired coupon`() {
        coupons.byCode["OLD"] = Coupon(
            id = couponId,
            code = "OLD",
            discountPercent = 10,
            validUntil = fixedNow.minusSeconds(1),
        )
        assertEquals(
            CreateSubscriptionResult.CouponExpired,
            useCase.execute(baseCommand().copy(couponCode = "OLD")),
        )
    }

    @Test
    fun `refuses when owner already has a confirmed subscription`() {
        subscriptions.insert(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_old",
                asaasSubscriptionId = "sub_old",
                billingType = AsaasBillingType.PIX,
                currentPeriodEnd = fixedNow,
                firstConfirmedAt = fixedNow,
            ),
        )

        assertEquals(CreateSubscriptionResult.AlreadySubscribed, useCase.execute(baseCommand()))
    }

    @Test
    fun `reactivates a canceled subscription with a new asaas subscription id`() {
        subscriptions.insert(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_old",
                asaasSubscriptionId = "sub_canceled",
                billingType = AsaasBillingType.PIX,
                currentPeriodEnd = fixedNow,
                status = SubscriptionStatus.CANCELED,
                canceledAt = fixedNow.minusSeconds(3600),
                firstConfirmedAt = fixedNow.minusSeconds(86_400),
            ),
        )
        gateway.pixPayload = "000201REACTIVATE"

        val result = useCase.execute(baseCommand().copy(plan = Plan.ORGANIZADOR))

        val success = assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals("sub_1", success.subscription.asaasSubscriptionId)
        assertEquals(Plan.ORGANIZADOR, success.subscription.plan)
        assertEquals(SubscriptionStatus.PAST_DUE, success.subscription.status)
        assertNull(success.subscription.canceledAt)
        assertNull(success.subscription.firstConfirmedAt)
        assertEquals("000201REACTIVATE", success.pixCopyPaste)
        assertEquals("sub_1", subscriptions.findByOwnerUserId(ownerId)!!.asaasSubscriptionId)
        assertNull(subscriptions.findByOwnerUserId(ownerId)!!.canceledAt)
    }

    @Test
    fun `reissues checkout when owner has unconfirmed subscription`() {
        subscriptions.insert(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_old",
                asaasSubscriptionId = "sub_old",
                billingType = AsaasBillingType.PIX,
                currentPeriodEnd = fixedNow,
                status = SubscriptionStatus.PAST_DUE,
                pastDueSince = fixedNow,
                firstConfirmedAt = null,
            ),
        )
        gateway.pixPayload = "000201REISSUE"

        val result = useCase.execute(baseCommand())

        val success = assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals("sub_old", success.subscription.asaasSubscriptionId)
        assertEquals("000201REISSUE", success.pixCopyPaste)
        assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
    }

    @Test
    fun `reissues checkout for unconfirmed subscription even when its own coupon was already redeemed`() {
        coupons.byCode["PROMO20"] = Coupon(id = couponId, code = "PROMO20", discountPercent = 20, durationCycles = 3)
        coupons.redemptions += CouponRedemption(couponId, ownerId, fixedNow.minusSeconds(3600))
        subscriptions.insert(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_old",
                asaasSubscriptionId = "sub_old",
                billingType = AsaasBillingType.PIX,
                currentPeriodEnd = fixedNow,
                status = SubscriptionStatus.PAST_DUE,
                pastDueSince = fixedNow,
                firstConfirmedAt = null,
                couponId = couponId,
                couponCyclesRemaining = 3,
            ),
        )
        gateway.pixPayload = "000201RETRY"

        val result = useCase.execute(baseCommand().copy(couponCode = "PROMO20"))

        val success = assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals("sub_old", success.subscription.asaasSubscriptionId)
        assertEquals("000201RETRY", success.pixCopyPaste)
        assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
    }

    @Test
    fun `refuses to reissue checkout when the retry asks for a different plan or cycle`() {
        subscriptions.insert(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_old",
                asaasSubscriptionId = "sub_old",
                billingType = AsaasBillingType.PIX,
                currentPeriodEnd = fixedNow,
                status = SubscriptionStatus.PAST_DUE,
                pastDueSince = fixedNow,
                firstConfirmedAt = null,
            ),
        )

        val result = useCase.execute(baseCommand().copy(plan = Plan.ILIMITADO, cycle = SubscriptionCycle.ANNUAL))

        assertEquals(CreateSubscriptionResult.PendingCheckoutMismatch, result)
        assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
        val stored = subscriptions.findByOwnerUserId(ownerId)!!
        assertEquals(Plan.TITULAR, stored.plan)
        assertEquals(SubscriptionCycle.MONTHLY, stored.cycle)
    }

    @Test
    fun `refuses to reissue checkout when the retry asks for a different billing type`() {
        subscriptions.insert(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_old",
                asaasSubscriptionId = "sub_old",
                billingType = AsaasBillingType.CREDIT_CARD,
                currentPeriodEnd = fixedNow,
                status = SubscriptionStatus.PAST_DUE,
                pastDueSince = fixedNow,
                firstConfirmedAt = null,
            ),
        )

        val result = useCase.execute(baseCommand().copy(billingType = AsaasBillingType.PIX))

        assertEquals(CreateSubscriptionResult.PendingCheckoutMismatch, result)
        assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
        assertEquals(AsaasBillingType.CREDIT_CARD, subscriptions.findByOwnerUserId(ownerId)!!.billingType)
    }

    @Test
    fun `reissues checkout for a legacy row with unknown billing type regardless of retry billing type`() {
        subscriptions.insert(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_old",
                asaasSubscriptionId = "sub_old",
                billingType = null,
                currentPeriodEnd = fixedNow,
                status = SubscriptionStatus.PAST_DUE,
                pastDueSince = fixedNow,
                firstConfirmedAt = null,
            ),
        )
        gateway.pixPayload = "000201LEGACY"

        val result = useCase.execute(baseCommand().copy(billingType = AsaasBillingType.CREDIT_CARD))

        val success = assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals("sub_old", success.subscription.asaasSubscriptionId)
        assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
    }

    @Test
    fun `rejects malformed cpf before calling asaas`() {
        assertEquals(
            CreateSubscriptionResult.InvalidCustomerDetails,
            useCase.execute(baseCommand().copy(cpfCnpj = "abc")),
        )
        assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
    }

    @Test
    fun `rejects email without at-sign before calling asaas`() {
        assertEquals(
            CreateSubscriptionResult.InvalidCustomerDetails,
            useCase.execute(baseCommand().copy(email = "not-an-email")),
        )
    }

    @Test
    fun `create still succeeds when the checkout enrichment lookup itself fails`() {
        gateway.latestPaymentIdThrows = true

        val result = useCase.execute(baseCommand())

        val success = assertIs<CreateSubscriptionResult.Success>(result)
        assertNull(success.pixCopyPaste)
        assertNull(success.invoiceUrl)
    }

    @Test
    fun `keeps the pix payload when only the invoice lookup fails`() {
        gateway.pixPayload = "00020126PIX-OK"
        gateway.invoiceUrlThrows = true

        val result = useCase.execute(baseCommand())

        val success = assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals("00020126PIX-OK", success.pixCopyPaste)
        assertNull(success.invoiceUrl)
    }

    @Test
    fun `credit card path returns invoice url`() {
        gateway.invoiceUrl = "https://asaas.test/i/abc"

        val result = useCase.execute(baseCommand().copy(billingType = AsaasBillingType.CREDIT_CARD))

        val success = assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals("https://asaas.test/i/abc", success.invoiceUrl)
    }

    private fun baseCommand() = CreateSubscriptionCommand(
        ownerUserId = ownerId,
        requestId = requestId,
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        billingType = AsaasBillingType.PIX,
        name = "Bruno",
        email = "bruno@example.com",
        cpfCnpj = "52998224725",
    )

    private class FakeSubscriptionRepository : SubscriptionRepository {
        val inserted = mutableListOf<Subscription>()
        val lockedOwners = mutableListOf<UUID>()
        private val byOwner = linkedMapOf<UUID, Subscription>()

        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) =
            byOwner.values.firstOrNull { it.asaasSubscriptionId == asaasSubscriptionId }

        override fun findByOwnerUserId(ownerUserId: UUID) = byOwner[ownerUserId]
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID) = byOwner[ownerUserId]
        override fun findByPendingUpgradeChargeId(chargeId: String) =
            byOwner.values.firstOrNull { it.pendingUpgradeChargeId == chargeId }

        override fun findByLastConfirmedPaymentId(paymentId: String) =
            byOwner.values.firstOrNull { it.lastConfirmedPaymentId == paymentId }

        override fun lockOwner(ownerUserId: UUID) {
            lockedOwners += ownerUserId
        }

        override fun insert(subscription: Subscription) {
            inserted += subscription
            byOwner[subscription.ownerUserId] = subscription
        }

        override fun save(subscription: Subscription) {
            byOwner[subscription.ownerUserId] = subscription
        }
    }

    private class FakeCouponRepository : CouponRepository {
        val byCode = linkedMapOf<String, Coupon>()
        val redemptions = mutableListOf<CouponRedemption>()

        override fun findByCode(code: String) = byCode[code]
        override fun findById(couponId: UUID) = byCode.values.firstOrNull { it.id == couponId }
        override fun hasRedemption(couponId: UUID, userId: UUID) =
            redemptions.any { it.couponId == couponId && it.userId == userId }

        override fun saveRedemption(redemption: CouponRedemption) {
            redemptions += redemption
        }
    }

    private class FakeAsaasGateway : AsaasGateway {
        var pixPayload: String? = "00020126DEFAULT-PIX"
        var invoiceUrl: String? = null
        var invoiceUrlThrows: Boolean = false
        var latestPaymentIdThrows: Boolean = false
        var lastSubscriptionValueCents: Long? = null
        val subscriptionIdempotencyKeys = mutableListOf<String>()

        override fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String) = "cus_1"

        override fun createSubscription(
            asaasCustomerId: String,
            plan: Plan,
            cycle: SubscriptionCycle,
            valueCents: Long,
            billingType: AsaasBillingType,
            idempotencyKey: String,
        ): String {
            lastSubscriptionValueCents = valueCents
            subscriptionIdempotencyKeys += idempotencyKey
            return "sub_1"
        }

        override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) = error("unused")
        override fun cancelSubscription(asaasSubscriptionId: String) = error("unused")
        override fun createOneOffCharge(
            asaasCustomerId: String,
            valueCents: Long,
            description: String,
            idempotencyKey: String,
        ) = error("unused")

        override fun regeneratePixPayload(asaasChargeId: String) = pixPayload ?: error("no pix")
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String): String? {
            if (latestPaymentIdThrows) throw RuntimeException("payment lookup failed")
            return if (asaasSubscriptionId == "sub_old" || asaasSubscriptionId == "sub_1") "pay_1" else null
        }

        override fun findPaymentInvoiceUrl(asaasPaymentId: String): String? {
            if (invoiceUrlThrows) throw RuntimeException("invoice lookup failed")
            return invoiceUrl
        }
    }
}
