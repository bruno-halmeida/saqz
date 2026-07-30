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
        assertEquals("cus_1", success.subscription.asaasCustomerId)
        assertEquals("00020126PIX-COPY-PASTE", success.pixCopyPaste)
        assertNull(success.invoiceUrl)
        assertEquals(listOf("subscription-create:$ownerId:$requestId"), gateway.subscriptionIdempotencyKeys)
        assertEquals(3_990L, gateway.lastSubscriptionValueCents)
        assertEquals(1, subscriptions.inserted.size)
        assertTrue(coupons.redemptions.isEmpty())
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
        assertEquals(3_192L, gateway.lastSubscriptionValueCents) // 3990 * 0.8
        assertEquals(1, coupons.redemptions.size)
        assertEquals(couponId, coupons.redemptions.single().couponId)
        assertEquals(ownerId, coupons.redemptions.single().userId)
    }

    @Test
    fun `refuses coupon already redeemed by the same user`() {
        coupons.byCode["PROMO20"] = Coupon(id = couponId, code = "PROMO20", discountPercent = 10)
        coupons.redemptions += CouponRedemption(couponId, ownerId, fixedNow.minusSeconds(3600))

        val result = useCase.execute(baseCommand().copy(couponCode = "PROMO20"))

        assertEquals(CreateSubscriptionResult.CouponAlreadyRedeemed, result)
        assertTrue(subscriptions.inserted.isEmpty())
        assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
    }

    @Test
    fun `refuses unknown coupon without calling asaas subscription create`() {
        val result = useCase.execute(baseCommand().copy(couponCode = "MISSING"))

        assertEquals(CreateSubscriptionResult.CouponNotFound, result)
        assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
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
    fun `refuses when owner already has a subscription`() {
        subscriptions.insert(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_old",
                asaasSubscriptionId = "sub_old",
                currentPeriodEnd = fixedNow,
            ),
        )

        assertEquals(CreateSubscriptionResult.AlreadySubscribed, useCase.execute(baseCommand()))
    }

    @Test
    fun `credit card path returns invoice url`() {
        gateway.invoiceUrl = "https://asaas.test/i/abc"

        val result = useCase.execute(baseCommand().copy(billingType = AsaasBillingType.CREDIT_CARD))

        val success = assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals("https://asaas.test/i/abc", success.invoiceUrl)
        assertNull(success.pixCopyPaste)
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
        private val byOwner = linkedMapOf<UUID, Subscription>()

        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) =
            byOwner.values.firstOrNull { it.asaasSubscriptionId == asaasSubscriptionId }

        override fun findByOwnerUserId(ownerUserId: UUID) = byOwner[ownerUserId]

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
        override fun hasRedemption(couponId: UUID, userId: UUID) =
            redemptions.any { it.couponId == couponId && it.userId == userId }

        override fun saveRedemption(redemption: CouponRedemption) {
            redemptions += redemption
        }
    }

    private class FakeAsaasGateway : AsaasGateway {
        var pixPayload: String? = "00020126DEFAULT-PIX"
        var invoiceUrl: String? = null
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
        override fun createOneOffCharge(
            asaasCustomerId: String,
            valueCents: Long,
            description: String,
            idempotencyKey: String,
        ) = error("unused")

        override fun regeneratePixPayload(asaasChargeId: String) = pixPayload ?: error("no pix")
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = "pay_1"
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = invoiceUrl
    }
}
