package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.adapter.output.asaas.AsaasException
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
    private var usage = br.com.saqz.sharedkernel.subscription.OwnerPlanUsage(0, 0)
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
            usageLookup = { usage },
        )
    }

    @Test
    fun `trial usage above titular limits cannot create payment or subscription`() {
        for (counts in listOf(2 to 20, 1 to 26)) {
            usage = br.com.saqz.sharedkernel.subscription.OwnerPlanUsage(counts.first, counts.second)
            assertEquals(CreateSubscriptionResult.PlanDoesNotFitUsage, useCase.execute(baseCommand().copy(plan = Plan.TITULAR)))
            assertNull(subscriptions.findByOwnerUserId(ownerId))
            assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
        }
    }

    @Test
    fun `organizador accommodates three trial groups and more than twenty five athletes`() {
        usage = br.com.saqz.sharedkernel.subscription.OwnerPlanUsage(3, 80)
        assertIs<CreateSubscriptionResult.Success>(useCase.execute(baseCommand().copy(plan = Plan.ORGANIZADOR)))
    }

    @Test
    fun `titular remains available when trial usage fits`() {
        usage = br.com.saqz.sharedkernel.subscription.OwnerPlanUsage(1, 25)
        assertIs<CreateSubscriptionResult.Success>(useCase.execute(baseCommand().copy(plan = Plan.TITULAR)))
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
    fun `annual checkout sends nine monthly payments as the annual total`() {
        val result = useCase.execute(baseCommand().copy(plan = Plan.ILIMITADO, cycle = SubscriptionCycle.ANNUAL))

        assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals(80_910, gateway.lastSubscriptionValueCents)
        assertEquals(SubscriptionCycle.ANNUAL, subscriptions.findByOwnerUserId(ownerId)?.cycle)
    }

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
    fun `payment method refused by asaas maps to PaymentMethodUnavailable and stores nothing`() {
        gateway.unavailableWith = PaymentMethodUnavailableException(
            billingType = AsaasBillingType.PIX,
            asaasCode = "invalid_billingType",
            cause = AsaasException(statusCode = 400, message = "not allowed"),
        )

        val result = useCase.execute(baseCommand())

        val unavailable = assertIs<CreateSubscriptionResult.PaymentMethodUnavailable>(result)
        assertEquals(AsaasBillingType.PIX, unavailable.billingType)
        assertNull(subscriptions.findByOwnerUserId(ownerId))
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
        gateway.pixQrImage = "QR-PNG-BASE64"

        val result = useCase.execute(baseCommand())

        val success = assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals(Plan.TITULAR, success.subscription.plan)
        assertEquals(SubscriptionStatus.PAST_DUE, success.subscription.status)
        assertEquals(fixedNow, success.subscription.pastDueSince)
        assertNull(success.subscription.firstConfirmedAt)
        assertEquals("sub_1", success.subscription.asaasSubscriptionId)
        assertEquals("00020126PIX-COPY-PASTE", success.pixCopyPaste)
        assertEquals("QR-PNG-BASE64", success.pixQrCodeBase64)
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
    fun `recreates asaas subscription when pending payments were deleted`() {
        subscriptions.insert(unconfirmed(plan = Plan.ORGANIZADOR))
        gateway.missingPaymentsFor += "sub_old"

        val success = assertIs<CreateSubscriptionResult.Success>(
            useCase.execute(baseCommand().copy(plan = Plan.ORGANIZADOR)),
        )

        assertEquals("sub_1", success.subscription.asaasSubscriptionId)
        assertEquals(listOf("sub_old"), gateway.canceledSubscriptions)
        assertEquals(5_990L, gateway.lastSubscriptionValueCents)
        assertEquals("00020126DEFAULT-PIX", success.pixCopyPaste)
        assertEquals(
            "subscription-replace:$ownerId:sub_old",
            gateway.subscriptionIdempotencyKeys.single(),
        )
    }

    @Test
    fun `recreates asaas subscription at discounted value when coupon arrives after pending checkout`() {
        coupons.byCode["PROMO20"] = Coupon(id = couponId, code = "PROMO20", discountPercent = 20)
        subscriptions.insert(unconfirmed(plan = Plan.ORGANIZADOR))

        val success = assertIs<CreateSubscriptionResult.Success>(
            useCase.execute(baseCommand().copy(plan = Plan.ORGANIZADOR, couponCode = "PROMO20")),
        )

        assertEquals("sub_1", success.subscription.asaasSubscriptionId)
        assertEquals(couponId, success.subscription.couponId)
        assertEquals(4_792L, gateway.lastSubscriptionValueCents)
        assertEquals(listOf("sub_old"), gateway.canceledSubscriptions)
        assertEquals(1, coupons.redemptions.size)
    }

    @Test
    fun `keeps existing coupon discount when recreating after payments were deleted`() {
        coupons.byCode["PROMO20"] = Coupon(id = couponId, code = "PROMO20", discountPercent = 20)
        coupons.redemptions += CouponRedemption(couponId, ownerId, fixedNow.minusSeconds(3600))
        subscriptions.insert(unconfirmed(plan = Plan.ORGANIZADOR, couponId = couponId))
        gateway.missingPaymentsFor += "sub_old"

        val success = assertIs<CreateSubscriptionResult.Success>(
            useCase.execute(baseCommand().copy(plan = Plan.ORGANIZADOR)),
        )

        assertEquals("sub_1", success.subscription.asaasSubscriptionId)
        assertEquals(couponId, success.subscription.couponId)
        assertEquals(4_792L, gateway.lastSubscriptionValueCents)
        assertEquals(1, coupons.redemptions.size)
    }

    @Test
    fun `does not recreate asaas subscription when payment lookup fails`() {
        subscriptions.insert(unconfirmed(plan = Plan.ORGANIZADOR))
        gateway.latestPaymentIdThrows = true

        val success = assertIs<CreateSubscriptionResult.Success>(
            useCase.execute(baseCommand().copy(plan = Plan.ORGANIZADOR)),
        )

        assertEquals("sub_old", success.subscription.asaasSubscriptionId)
        assertTrue(gateway.canceledSubscriptions.isEmpty())
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

    // --- Reemissao do checkout nao confirmado (replaceUnconfirmed) ---

    @Test
    fun `replacing an unconfirmed checkout stores the new subscription and its card token for the owner`() {
        subscriptions.insert(unconfirmed(plan = Plan.ORGANIZADOR))
        gateway.missingPaymentsFor += "sub_old"
        gateway.creditCardResult = AsaasCreditCardInfo(token = "card_tok_2", lastFourDigits = "4242", brand = "MASTERCARD")

        assertIs<CreateSubscriptionResult.Success>(useCase.execute(baseCommand().copy(plan = Plan.ORGANIZADOR)))

        val stored = subscriptions.findByOwnerUserId(ownerId)
        assertEquals("sub_1", stored?.asaasSubscriptionId)
        assertEquals("cus_old", stored?.asaasCustomerId)
        assertEquals(
            listOf(RecordingCreditCardTokenStore.Saved("sub_1", "card_tok_2", "4242", "MASTERCARD")),
            creditCardTokens.saved,
        )
    }

    @Test
    fun `replacing an unconfirmed checkout locks the owner row again inside its own transaction`() {
        subscriptions.insert(unconfirmed(plan = Plan.ORGANIZADOR))
        gateway.missingPaymentsFor += "sub_old"

        useCase.execute(baseCommand().copy(plan = Plan.ORGANIZADOR))

        // Uma trava na transacao de criacao e outra na troca: duas requisicoes do mesmo dono nao se cruzam.
        assertEquals(listOf(ownerId, ownerId), subscriptions.lockedOwners)
    }

    @Test
    fun `replacing an unconfirmed checkout creates a customer only when the old one has no customer id`() {
        subscriptions.insert(unconfirmed(plan = Plan.ORGANIZADOR).copy(asaasCustomerId = ""))
        gateway.missingPaymentsFor += "sub_old"

        assertIs<CreateSubscriptionResult.Success>(useCase.execute(baseCommand().copy(plan = Plan.ORGANIZADOR)))

        assertEquals("cus_1", subscriptions.findByOwnerUserId(ownerId)?.asaasCustomerId)
    }

    @Test
    fun `replacing an unconfirmed checkout keeps going when canceling the old charge fails at asaas`() {
        subscriptions.insert(unconfirmed(plan = Plan.ORGANIZADOR))
        gateway.missingPaymentsFor += "sub_old"
        gateway.cancelThrows = true

        val result = useCase.execute(baseCommand().copy(plan = Plan.ORGANIZADOR))

        assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals("sub_1", subscriptions.findByOwnerUserId(ownerId)?.asaasSubscriptionId)
    }

    @Test
    fun `replacing an unconfirmed checkout with a card declined by asaas reports the decline and keeps the old row`() {
        subscriptions.insert(unconfirmed(plan = Plan.ORGANIZADOR).copy(billingType = AsaasBillingType.CREDIT_CARD))
        gateway.missingPaymentsFor += "sub_old"
        gateway.declineWith = CardDeclinedException(
            asaasCode = "invalid_creditCard",
            asaasDescription = "Cartao recusado",
            cause = AsaasException(statusCode = 400, message = "recusado"),
        )

        val result = useCase.execute(cardCommand().copy(plan = Plan.ORGANIZADOR))

        val declined = assertIs<CreateSubscriptionResult.CardDeclined>(result)
        assertEquals("invalid_creditCard", declined.reason)
        assertEquals("sub_old", subscriptions.findByOwnerUserId(ownerId)?.asaasSubscriptionId)
    }

    @Test
    fun `replacing an unconfirmed checkout with a method asaas refuses reports it unavailable`() {
        subscriptions.insert(unconfirmed(plan = Plan.ORGANIZADOR))
        gateway.missingPaymentsFor += "sub_old"
        gateway.unavailableWith = PaymentMethodUnavailableException(
            billingType = AsaasBillingType.PIX,
            asaasCode = "invalid_billingType",
            cause = AsaasException(statusCode = 400, message = "not allowed"),
        )

        val result = useCase.execute(baseCommand().copy(plan = Plan.ORGANIZADOR))

        assertEquals(AsaasBillingType.PIX, assertIs<CreateSubscriptionResult.PaymentMethodUnavailable>(result).billingType)
        assertEquals("sub_old", subscriptions.findByOwnerUserId(ownerId)?.asaasSubscriptionId)
    }

    @Test
    fun `replacing an unconfirmed checkout whose coupon the owner already redeemed does not record it twice`() {
        coupons.byCode["PROMO20"] = Coupon(id = couponId, code = "PROMO20", discountPercent = 20)
        coupons.redemptions += CouponRedemption(couponId, ownerId, fixedNow.minusSeconds(3600))
        subscriptions.insert(unconfirmed(plan = Plan.ORGANIZADOR, couponId = couponId))
        gateway.missingPaymentsFor += "sub_old"

        useCase.execute(baseCommand().copy(plan = Plan.ORGANIZADOR))

        assertEquals(1, coupons.redemptions.size)
    }

    @Test
    fun `a brand new subscription whose first charge is not visible yet is not replaced`() {
        gateway.missingPaymentsFor += "sub_1"

        val result = useCase.execute(baseCommand())

        val success = assertIs<CreateSubscriptionResult.Success>(result)
        assertEquals("sub_1", success.subscription.asaasSubscriptionId)
        assertTrue(gateway.canceledSubscriptions.isEmpty())
        assertEquals(1, gateway.subscriptionIdempotencyKeys.size)
    }

    @Test
    fun `reactivating with a coupon records the redemption for the owner`() {
        coupons.byCode["PROMO20"] = Coupon(id = couponId, code = "PROMO20", discountPercent = 20, durationCycles = 2)
        subscriptions.insert(
            unconfirmed().copy(
                status = SubscriptionStatus.CANCELED,
                canceledAt = fixedNow.minusSeconds(86_400),
                firstConfirmedAt = fixedNow.minusSeconds(864_000),
            ),
        )

        val success = assertIs<CreateSubscriptionResult.Success>(
            useCase.execute(baseCommand().copy(couponCode = "PROMO20")),
        )

        assertEquals(couponId, success.subscription.couponId)
        assertEquals(2, success.subscription.couponCyclesRemaining)
        assertEquals(listOf(CouponRedemption(couponId, ownerId, fixedNow)), coupons.redemptions)
        assertEquals(3_192L, gateway.lastSubscriptionValueCents)
    }

    // --- Dados do cliente: fronteiras do e-mail ---

    private fun emailAccepted(email: String): Boolean =
        useCase.execute(baseCommand().copy(email = email)) !is CreateSubscriptionResult.InvalidCustomerDetails

    @Test
    fun `email accepts the shortest valid shape and rejects an at-sign at either edge`() {
        assertTrue(emailAccepted("a@b"))
        assertTrue(emailAccepted("a@bc"))
        assertTrue(!emailAccepted("@ab"))
        assertTrue(!emailAccepted("ab@"))
        assertTrue(!emailAccepted("ab"))
    }

    @Test
    fun `email accepts 254 characters and rejects 255`() {
        assertTrue(emailAccepted("a".repeat(250) + "@b.c"))
        assertTrue(!emailAccepted("a".repeat(251) + "@b.c"))
    }

    @Test
    fun `email and cpf are trimmed and cpf keeps only digits`() {
        val result = useCase.execute(baseCommand().copy(email = "  bruno@example.com  ", cpfCnpj = "529.982.247-25"))

        assertIs<CreateSubscriptionResult.Success>(result)
    }

    @Test
    fun `customer name that is only blanks is rejected`() {
        assertEquals(
            CreateSubscriptionResult.InvalidCustomerDetails,
            useCase.execute(baseCommand().copy(name = "   ")),
        )
        assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
    }

    @Test
    fun `cpf with 11 digits and cnpj with 14 are accepted while 10, 12 and 15 are rejected`() {
        for (ok in listOf("12345678901", "12345678000195")) {
            assertIs<CreateSubscriptionResult.Success>(useCase.execute(baseCommand().copy(cpfCnpj = ok)))
            subscriptions.clear()
        }
        for (bad in listOf("1234567890", "123456789012", "123456789012345")) {
            assertEquals(
                CreateSubscriptionResult.InvalidCustomerDetails,
                useCase.execute(baseCommand().copy(cpfCnpj = bad)),
            )
        }
    }

    // --- Cartao: formato validado na borda, campo a campo ---

    private fun cardErrors(command: CreateSubscriptionCommand): Map<String, List<String>> =
        (useCase.execute(command) as? CreateSubscriptionResult.InvalidCreditCardDetails)?.fieldErrors.orEmpty()

    @Test
    fun `a valid card passes the format validation`() {
        assertTrue(cardErrors(cardCommand()).isEmpty())
    }

    @Test
    fun `card number accepts 13 to 19 digits ignoring separators and rejects 12 and 20`() {
        for (digits in listOf("4".repeat(13), "4".repeat(19), "4111 1111 1111 1111")) {
            assertTrue(cardErrors(cardCommand().copy(creditCard = validCreditCard().copy(number = digits))).isEmpty())
            subscriptions.clear()
        }
        for (digits in listOf("4".repeat(12), "4".repeat(20))) {
            assertEquals(
                mapOf("creditCard.number" to listOf("must have 13 to 19 digits")),
                cardErrors(cardCommand().copy(creditCard = validCreditCard().copy(number = digits))),
            )
        }
    }

    @Test
    fun `card holder name blank is reported on its field`() {
        assertEquals(
            mapOf("creditCard.holderName" to listOf("is required")),
            cardErrors(cardCommand().copy(creditCard = validCreditCard().copy(holderName = " "))),
        )
    }

    @Test
    fun `card expiry year must have exactly 4 digits`() {
        for (year in listOf("30", "203", "20300", "20a0")) {
            assertEquals(
                mapOf("creditCard.expiryYear" to listOf("must have 4 digits")),
                cardErrors(cardCommand().copy(creditCard = validCreditCard().copy(expiryYear = year))),
            )
        }
    }

    @Test
    fun `card expiry month accepts 01 to 12 only`() {
        for (month in listOf("01", "09", "10", "12")) {
            assertTrue(cardErrors(cardCommand().copy(creditCard = validCreditCard().copy(expiryMonth = month))).isEmpty())
            subscriptions.clear()
        }
        for (month in listOf("00", "13", "1", "123")) {
            assertEquals(
                mapOf("creditCard.expiryMonth" to listOf("must be 2 digits between 01 and 12")),
                cardErrors(cardCommand().copy(creditCard = validCreditCard().copy(expiryMonth = month))),
            )
        }
    }

    @Test
    fun `card ccv accepts 3 or 4 digits and rejects 2 and 5`() {
        for (ccv in listOf("123", "1234")) {
            assertTrue(cardErrors(cardCommand().copy(creditCard = validCreditCard().copy(ccv = ccv))).isEmpty())
            subscriptions.clear()
        }
        for (ccv in listOf("12", "12345", "12a")) {
            assertEquals(
                mapOf("creditCard.ccv" to listOf("must have 3 or 4 digits")),
                cardErrors(cardCommand().copy(creditCard = validCreditCard().copy(ccv = ccv))),
            )
        }
    }

    @Test
    fun `holder info is validated field by field`() {
        val broken = validCreditCardHolderInfo().copy(
            name = "",
            email = "sem-arroba",
            cpfCnpj = "123",
            postalCode = "0131093",
            addressNumber = " ",
            phone = "119999999",
        )

        assertEquals(
            mapOf(
                "creditCardHolderInfo.name" to listOf("is required"),
                "creditCardHolderInfo.email" to listOf("must be a valid email"),
                "creditCardHolderInfo.cpfCnpj" to listOf("must have 11 (CPF) or 14 (CNPJ) digits"),
                "creditCardHolderInfo.postalCode" to listOf("must have 8 digits"),
                "creditCardHolderInfo.addressNumber" to listOf("is required"),
                "creditCardHolderInfo.phone" to listOf("must be a valid phone number"),
            ),
            cardErrors(cardCommand().copy(creditCardHolderInfo = broken)),
        )
        assertTrue(gateway.subscriptionIdempotencyKeys.isEmpty())
    }

    @Test
    fun `holder postal code needs exactly 8 digits and phone at least 10`() {
        val postal9 = validCreditCardHolderInfo().copy(postalCode = "013109300")
        assertEquals(
            mapOf("creditCardHolderInfo.postalCode" to listOf("must have 8 digits")),
            cardErrors(cardCommand().copy(creditCardHolderInfo = postal9)),
        )
        val formatted = validCreditCardHolderInfo().copy(postalCode = "01310-930", phone = "(11) 99999-9999")
        assertTrue(cardErrors(cardCommand().copy(creditCardHolderInfo = formatted)).isEmpty())
        subscriptions.clear()
        val tenDigits = validCreditCardHolderInfo().copy(phone = "1133334444")
        assertTrue(cardErrors(cardCommand().copy(creditCardHolderInfo = tenDigits)).isEmpty())
    }

    @Test
    fun `remote ip is required for card billing`() {
        for (ip in listOf(null, "", "  ")) {
            assertEquals(
                mapOf("remoteIp" to listOf("is required for CREDIT_CARD billing")),
                cardErrors(cardCommand().copy(remoteIp = ip)),
            )
        }
    }

    @Test
    fun `missing card blocks report exactly the three required fields`() {
        assertEquals(
            mapOf(
                "creditCard" to listOf("is required for CREDIT_CARD billing"),
                "creditCardHolderInfo" to listOf("is required for CREDIT_CARD billing"),
                "remoteIp" to listOf("is required for CREDIT_CARD billing"),
            ),
            cardErrors(baseCommand().copy(billingType = AsaasBillingType.CREDIT_CARD)),
        )
    }

    @Test
    fun `pix checkout does not validate card fields`() {
        val result = useCase.execute(baseCommand().copy(creditCard = null, creditCardHolderInfo = null, remoteIp = null))

        assertIs<CreateSubscriptionResult.Success>(result)
    }

    private fun unconfirmed(plan: Plan = Plan.TITULAR, couponId: UUID? = null) = Subscription(
        ownerUserId = ownerId,
        plan = plan,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_old",
        asaasSubscriptionId = "sub_old",
        billingType = AsaasBillingType.PIX,
        currentPeriodEnd = fixedNow,
        status = SubscriptionStatus.PAST_DUE,
        pastDueSince = fixedNow,
        firstConfirmedAt = null,
        couponId = couponId,
    )

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

        fun clear() {
            byOwner.clear()
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
        var pixQrImage: String? = null
        var invoiceUrl: String? = null
        var invoiceUrlThrows: Boolean = false
        var latestPaymentIdThrows: Boolean = false
        /** Status devolvido pelo GET /payments/{id}. PENDING = cobranca em aberto. */
        var paymentStatus: String? = "PENDING"
        var findPaymentThrows: Boolean = false
        var lastSubscriptionValueCents: Long? = null
        var creditCardResult: AsaasCreditCardInfo? = null
        var declineWith: CardDeclinedException? = null
        var unavailableWith: PaymentMethodUnavailableException? = null
        var lastCreditCard: CreditCardDetails? = null
        var lastCreditCardHolderInfo: CreditCardHolderInfo? = null
        var lastRemoteIp: String? = null
        val subscriptionIdempotencyKeys = mutableListOf<String>()
        val canceledSubscriptions = mutableListOf<String>()
        var cancelThrows: Boolean = false
        val missingPaymentsFor = mutableSetOf<String>()

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
            unavailableWith?.let { throw it }
            lastSubscriptionValueCents = valueCents
            subscriptionIdempotencyKeys += idempotencyKey
            lastCreditCard = creditCard
            lastCreditCardHolderInfo = creditCardHolderInfo
            lastRemoteIp = remoteIp
            return AsaasSubscriptionCreation("sub_1", creditCardResult)
        }

        override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) = error("unused")
        override fun cancelSubscription(asaasSubscriptionId: String) {
            if (cancelThrows) throw RuntimeException("cancel failed")
            canceledSubscriptions += asaasSubscriptionId
        }
        override fun createOneOffCharge(
            asaasCustomerId: String,
            valueCents: Long,
            description: String,
            idempotencyKey: String,
        ) = error("unused")

        override fun regeneratePixPayload(asaasChargeId: String) =
            PixCode(pixPayload ?: error("no pix"), pixQrImage)
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String): String? {
            if (latestPaymentIdThrows) throw RuntimeException("payment lookup failed")
            if (asaasSubscriptionId in missingPaymentsFor) return null
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
