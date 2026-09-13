package br.com.saqz.receivables.domain

import kotlin.test.*

class ReceiptMoneyBoundaryTest {
    @Test fun verifierUnknownOrderCannotExportReceipt() {
        val quote = MemberPaymentQuote("schedule", "terms", ReceiptMethod.PIX, 1000, 61, 1061, 1000, 20, 41)
        val order = MemberPaymentOrder("order", "account", "charge", "group", "payer", "2026-09-20", "UNKNOWN_REMOTE",
            listOf(quote), "a".repeat(64))
        val paid = MemberPaymentInstrument("instrument", "account", "order", quote, "CONFIRMED", "payment", null,
            null, null, null, true, false, false, false, null)
        assertNull(MemberPaymentDetail(order, listOf(paid)).receiptInstrument(), "unknown order must fail closed")
    }
    @Test fun verifierOverflowCannotMakeNegativeRecurrenceMoneyValid() {
        val value = PaymentRecurrence("recurrence", "account", "group", "payer", ReceiptMethod.PIX,
            Long.MAX_VALUE, 1, Long.MIN_VALUE, "2026-10-10", "ACTIVE", "sub", null, null)
        assertFalse(value.validFor("account", "group", "payer"), "overflow total is negative")
    }
    @Test fun verifierOverflowCannotMakeNegativeReviewMoneyValid() {
        val value = RecurrenceReview("account", "group", "payer", ReceiptMethod.PIX, Long.MAX_VALUE, 1, Long.MIN_VALUE,
            1, 0, Long.MAX_VALUE, "schedule", "terms", "2026-10-10", "MONTHLY", "a".repeat(64))
        assertFalse(value.validFor("account", "group", "payer", ReceiptMethod.PIX), "overflow total is negative")
    }
}
