package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.adapter.output.asaas.AsaasException
import br.com.saqz.subscriptions.adapter.output.asaas.CardDeclinedException
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
    private lateinit var creditCardTokens: RecordingCreditCardTokenStore
    private lateinit var useCase: CreateSubscription

    @BeforeEach
    fun setUp() {
        subscriptions = FakeSubscriptionRepository()
        coupons = FakeCouponRepository()
        gateway = FakeAsaasGateway()
        creditCardTokens = RecordingCreditCardTokenStore()
        useCase = CreateSubscription(
            subscriptions = subscriptions,
            coupons = coupons,
            asaasGateway = gateway,
            transaction = object : SubscriptionsTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = block()
            },
            clock = clock,
            creditCardTokens = creditCardTokens,
        )
    }

    private fun validCreditCard() = CreditCardDetails(
        holderName = "Bruno Almeida",
        number = "4111111111111111",
        expiryMonth = "12",
        expiryYear = "2030",
        ccv = "123",
    )

    private fun validCreditCardHolderInfo() = CreditCardHolderInfo(
        name = "Bruno Almeida",
        email = "bruno@example.com",
        cpfCnpj = "52998224725",
        postalCode = "01310930",
        addressNumber = "100",
        phone = "11999999999",
    )

    private fun cardCommand() = baseCommand().copy(
        billingType = AsaasBillingType.CREDIT_CARD,
        creditCard = validCreditCard(),
        creditCardHolderInfo = validCreditCardHolderInfo(),
        remoteIp = "203.0.113.5",
    )

    @Test
    fun `creates subscription with credit card and persists the returned token`() {
        gateway.creditCardResult = AsaasCreditCardInfo(token = "card_tok_1", lastFourDigits = "1111", brand = "VISA")

        val result = useCase.execute(cardCommand())

        assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals(validCreditCard(), gateway.lastCreditCard)
        assertEquals(validCreditCardHolderInfo(), gateway.lastCreditCardHolderInfo)
        assertEquals("203.0.113.5", gateway.lastRemoteIp)
        assertEquals(
            RecordingCreditCardTokenStore.Saved("sub_1", "card_tok_1", "1111", "VISA"),
            creditCardTokens.saved.single(),
        )
    }

    @Test
    fun `clears (does not leave stale) credit card columns when asaas does not return a token`() {
        gateway.creditCardResult = null

        useCase.execute(cardCommand())

        assertEquals(
            RecordingCreditCardTokenStore.Saved("sub_1", null, null, null),
            creditCardTokens.saved.single(),
        )
    }

    @Test
    fun `reactivating with pix clears a stale credit card token left by a prior card subscription`() {
        subscriptions.insert(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_old",
                asaasSubscriptionId = "sub_old_card",
                billingType = AsaasBillingType.CREDIT_CARD,
                currentPeriodEnd = fixedNow,
                status = SubscriptionStatus.CANCELED,
                canceledAt = fixedNow.minusSeconds(60),
            ),
        )
        // baseCommand() defaults to PIX — no card fields, nothing for gateway to return.

        val result = useCase.execute(baseCommand())

        assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals(
            RecordingCreditCardTokenStore.Saved("sub_1", null, null, null),
            creditCardTokens.saved.single(),
        )
    }

    @Test
    fun `rejects credit card billing missing the card block with a field error, not a 500`() {
        val result = useCase.execute(baseCommand().copy(billingType = AsaasBillingType.CREDIT_CARD))

        val invalid = assertIs<CreateSubscriptionResult.InvalidCreditCardDetails>(result)
        assertTrue(invalid.fieldErrors.containsKey("creditCard"))
        assertTrue(invalid.fieldErrors.containsKey("creditCardHolderInfo"))
        assertTrue(invalid.fieldErrors.containsKey("remoteIp"))
        assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
    }

    @Test
    fun `rejects malformed card expiry before calling asaas`() {
        val result = useCase.execute(cardCommand().copy(creditCard = validCreditCard().copy(expiryMonth = "13")))

        val invalid = assertIs<CreateSubscriptionResult.InvalidCreditCardDetails>(result)
        assertTrue(invalid.fieldErrors.containsKey("creditCard.expiryMonth"))
        assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
    }

    @Test
    fun `card declined by asaas maps to CardDeclined with the mapped reason`() {
        gateway.declineWith = CardDeclinedException(
            asaasCode = "invalid_creditCard",
            asaasDescription = "Transação não autorizada.",
            cause = AsaasException(statusCode = 400, message = "declined"),
        )

        val result = useCase.execute(cardCommand())

        val declined = assertIs<CreateSubscriptionResult.CardDeclined>(result)
        assertEquals("invalid_creditCard", declined.reason)
        assertEquals("Transação não autorizada.", declined.asaasDescription)
        assertNull(subscriptions.findByOwnerUserId(ownerId))
        assertTrue(creditCardTokens.saved.isEmpty())
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
    fun `paid charge confirms the subscription instead of offering payment again`() {
        // Webhook e push e pode ter se perdido: sem perguntar o status ao Asaas o usuario que
        // ja pagou era convidado a pagar de novo.
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
                pastDueSince = fixedNow.minusSeconds(3600),
                firstConfirmedAt = null,
            ),
        )
        gateway.paymentStatus = "RECEIVED"

        val result = useCase.execute(baseCommand())

        val success = assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals(SubscriptionStatus.ACTIVE, success.subscription.status)
        assertEquals(fixedNow, success.subscription.firstConfirmedAt)
        assertEquals("pay_1", success.subscription.lastConfirmedPaymentId)
        assertNull(success.subscription.pastDueSince)
        // Nada de checkout: e assim que o app sabe que nao deve oferecer pagamento de novo.
        assertNull(success.pixCopyPaste)
        assertNull(success.invoiceUrl)
        assertEquals(SubscriptionStatus.ACTIVE, subscriptions.findByOwnerUserId(ownerId)!!.status)
    }

    @Test
    fun `pending charge still returns the existing checkout`() {
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
        gateway.paymentStatus = "PENDING"
        gateway.pixPayload = "000201STILL-PENDING"

        val success = assertIs<CreateSubscriptionResult.Success>(useCase.execute(baseCommand()))

        assertEquals(SubscriptionStatus.PAST_DUE, success.subscription.status)
        assertEquals("000201STILL-PENDING", success.pixCopyPaste)
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

        val result = useCase.execute(cardCommand())

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

        val result = useCase.execute(cardCommand())

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
        /** Status devolvido pelo GET /payments/{id}. PENDING = cobranca em aberto. */
        var paymentStatus: String? = "PENDING"
        var findPaymentThrows: Boolean = false
        var lastSubscriptionValueCents: Long? = null
        var creditCardResult: AsaasCreditCardInfo? = null
        var declineWith: CardDeclinedException? = null
        var lastCreditCard: CreditCardDetails? = null
        var lastCreditCardHolderInfo: CreditCardHolderInfo? = null
        var lastRemoteIp: String? = null
        val subscriptionIdempotencyKeys = mutableListOf<String>()

        override fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String) = "cus_1"

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
        ): AsaasSubscriptionCreation {
            declineWith?.let { throw it }
            lastSubscriptionValueCents = valueCents
            subscriptionIdempotencyKeys += idempotencyKey
            lastCreditCard = creditCard
            lastCreditCardHolderInfo = creditCardHolderInfo
            lastRemoteIp = remoteIp
            return AsaasSubscriptionCreation("sub_1", creditCardResult)
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

        // invoiceUrl nulo aqui de proposito: mantem o fallback por findPaymentInvoiceUrl
        // exercitado pelos testes que ja existiam.
        override fun findPayment(asaasPaymentId: String): AsaasPaymentSnapshot? {
            if (findPaymentThrows) throw RuntimeException("payment lookup failed")
            return AsaasPaymentSnapshot(id = asaasPaymentId, status = paymentStatus, invoiceUrl = null)
        }
    }

    private class RecordingCreditCardTokenStore : CreditCardTokenStore {
        data class Saved(val asaasSubscriptionId: String, val token: String?, val lastFourDigits: String?, val brand: String?)

        val saved = mutableListOf<Saved>()

        override fun save(asaasSubscriptionId: String, token: String?, lastFourDigits: String?, brand: String?) {
            saved += Saved(asaasSubscriptionId, token, lastFourDigits, brand)
        }
    }
}
