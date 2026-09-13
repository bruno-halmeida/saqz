package br.com.saqz.receivables.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurringPaymentsTest {
    private val quote = MemberPaymentQuote("schedule", "terms", ReceiptMethod.PIX, 10_000, 490, 10_490, 10_000, 300, 190)
    private val order = MemberPaymentOrder("order", "account", "charge", "group", "payer", "2026-09-20", "ISSUED",
        listOf(quote), "a".repeat(64))
    private val instrument = MemberPaymentInstrument("instrument", "account", "order", quote, "EXPIRED", "payment", null,
        "old-pix", null, null, false, false, false, false, "2026-09-12T23:59:59Z")

    @Test fun reviewRequiresExactActorContextMonthlyCycleFingerprintAndMoneyIdentity() {
        val review = review()
        assertTrue(review.validFor("account", "group", "payer", ReceiptMethod.PIX))
        assertFalse(review.copy(memberUserId = "other").validFor("account", "group", "payer", ReceiptMethod.PIX))
        assertFalse(review.copy(totalCents = 10_491).validFor("account", "group", "payer", ReceiptMethod.PIX))
        assertFalse(review.copy(expectedNetCents = 9_999).validFor("account", "group", "payer", ReceiptMethod.PIX))
        assertFalse(review.copy(cycle = "YEARLY").validFor("account", "group", "payer", ReceiptMethod.PIX))
        assertFalse(review.copy(fingerprint = "bad").validFor("account", "group", "payer", ReceiptMethod.PIX))
    }

    @Test fun recurrenceRejectsForeignContextUnknownStateInvalidMoneyAndUnsafeCheckout() {
        val recurrence = recurrence()
        assertTrue(recurrence.validFor("account", "group", "payer"))
        assertFalse(recurrence.copy(groupId = "other").validFor("account", "group", "payer"))
        assertFalse(recurrence.copy(status = "PAID").validFor("account", "group", "payer"))
        assertFalse(recurrence.copy(totalCents = 1).validFor("account", "group", "payer"))
        assertFalse(recurrence.copy(method = ReceiptMethod.CARD, hostedCheckoutUrl = "https://asaas.com.evil.test/x")
            .validFor("account", "group", "payer"))
        assertTrue(recurrence.copy(method = ReceiptMethod.CARD, hostedCheckoutUrl = "https://sandbox.asaas.com/c/x")
            .validFor("account", "group", "payer"))
    }

    @Test fun expiredExistingPixCanRenewEvenAfterUnrelatedEligibilityCutButNeverChangesSnapshot() {
        assertTrue(order.canRenew(instrument, expired = true))
        assertFalse(order.canRenew(instrument, expired = false))
        assertFalse(order.copy(status = "REFUNDED").canRenew(instrument, expired = true))
        assertFalse(order.canRenew(instrument.copy(paymentId = null), expired = true))
        val renewal = PixRenewal("order", "instrument", "payment", ReceiptMethod.PIX, "ACTIVE", 10_000, 490, 10_490,
            "2026-09-20", "new-pix", "new-image", "2026-09-20T23:59:59Z")
        assertTrue(renewal.validFor(order, instrument))
        assertFalse(renewal.copy(instrumentId = "new-instrument").validFor(order, instrument))
        assertFalse(renewal.copy(baseCents = 9_999).validFor(order, instrument))
        assertFalse(renewal.copy(providerPaymentId = "new-payment").validFor(order, instrument))
    }

    @Test fun receiptRequiresObservedFinalPaymentAndNeverUsesHistoricalMilestonesAlone() {
        val confirmed = instrument.copy(status = "CONFIRMED")
        assertTrue(MemberPaymentDetail(order, listOf(confirmed)).receiptInstrument() === confirmed)
        assertNull(MemberPaymentDetail(order, listOf(instrument.copy(status = "ACTIVE", confirmed = true, available = true)))
            .receiptInstrument())
        assertNull(MemberPaymentDetail(order.copy(status = "REFUNDED"), listOf(confirmed.copy(status = "AVAILABLE")))
            .receiptInstrument())
        assertNull(MemberPaymentDetail(order, listOf(confirmed.copy(paymentId = null))).receiptInstrument())
    }

    private fun review() = RecurrenceReview("account", "group", "payer", ReceiptMethod.PIX, 10_000, 490, 10_490,
        300, 190, 10_000, "schedule", "terms", "2026-10-10", "MONTHLY", "a".repeat(64))
    private fun recurrence() = PaymentRecurrence("recurrence", "account", "group", "payer", ReceiptMethod.PIX, 10_000, 490,
        10_490, "2026-10-10", "ACTIVE", "subscription", null, null)
}
