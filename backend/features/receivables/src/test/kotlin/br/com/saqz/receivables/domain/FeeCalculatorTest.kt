package br.com.saqz.receivables.domain

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FeeCalculatorTest {
    // Synthetic test configurations only; not commercial defaults.
    @Test
    fun `commission uses base and processor uses gross rather than adding the percentages`() {
        val schedule = schedule("0.0299", 39, "0.025", 50)
        val quote = FeeCalculator.quote(10000, schedule)
        assertEquals(10000, quote.baseCents)
        assertEquals(300, quote.commissionCents)
        assertEquals(358, quote.providerFeeCents)
        assertEquals(658, quote.feesCents)
        assertEquals(10658, quote.totalCents)
        assertEquals(10000, quote.expectedNetCents)
        assertEquals(schedule.id, quote.feeScheduleId)
        assertEquals("terms-test", quote.termsVersion)
        assertEquals(PaymentMethod.CARD, quote.method)
        assertEquals(BigDecimal("3.00"), quote.fixedSplitValue())
    }

    @Test
    fun `rounds commission explicitly and does not charge an unnecessary extra cent`() {
        val quote = FeeCalculator.quote(1999, schedule("0.035", 49, "0.015", 30))
        assertEquals(60, quote.commissionCents)
        assertEquals(125, quote.providerFeeCents)
        assertEquals(2184, quote.totalCents)
        assertEquals(185, quote.feesCents)
        assertEquals(1999, quote.expectedNetCents)
        val tiny = FeeCalculator.quote(3, schedule("0.1", 0, "0", 0))
        assertEquals(3, tiny.totalCents)
        assertEquals(0, tiny.feesCents)
    }

    @Test
    fun `fixed costs and zero rates are supported without fabricated minimum fees`() {
        val quote = FeeCalculator.quote(1000, schedule("0", 99, "0", 29))
        assertEquals(1128, quote.totalCents)
        assertEquals(128, quote.feesCents)
        assertEquals(1000, quote.expectedNetCents)
        assertEquals(1000, FeeCalculator.quote(1000, schedule("0", 0, "0", 0)).totalCents)
    }

    @Test
    fun `half cent rounds up and impossible rates or overflow never produce a quote`() {
        val quote = FeeCalculator.quote(1, schedule("0.5", 0, "0", 0))
        assertEquals(2, quote.totalCents)
        assertEquals(1, quote.providerFeeCents)
        assertEquals(1, quote.expectedNetCents)
        assertFailsWith<IllegalArgumentException> { schedule("1", 0, "0", 0) }
        assertFailsWith<IllegalArgumentException> { schedule("-0.1", 0, "0", 0) }
        assertFailsWith<IllegalArgumentException> { schedule("0", -1, "0", 0) }
        assertFailsWith<IllegalArgumentException> { FeeCalculator.quote(0, schedule("0", 0, "0", 0)) }
        assertFailsWith<ArithmeticException> { FeeCalculator.quote(Long.MAX_VALUE, schedule("0", 1, "0", 0)) }
    }

    private fun schedule(providerRate: String, providerFixed: Long, commissionRate: String, commissionFixed: Long) =
        FeeSchedule(UUID.randomUUID(), PaymentMethod.CARD, BigDecimal(providerRate), providerFixed,
            BigDecimal(commissionRate), commissionFixed, "terms-test")

    @Test fun `bounded pix tariff preserves the base at minimum proportional and maximum fees`() {
        val fees = schedule("0.0099", 0, "0.02", 0).copy(providerMinimumCents = 29, providerMaximumCents = 199)
        for (base in listOf(1L, 100, 1000, 10000, 100000)) {
            val quote = FeeCalculator.quote(base, fees)
            assertEquals(base, quote.expectedNetCents)
            assertEquals(base + quote.commissionCents + quote.providerFeeCents, quote.totalCents)
            assertEquals(quote.providerFeeCents, BigDecimal.valueOf(quote.totalCents).multiply(fees.providerRate)
                .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact().coerceIn(29, 199))
        }
        assertEquals(29, FeeCalculator.quote(100, fees).providerFeeCents)
        assertEquals(199, FeeCalculator.quote(100000, fees).providerFeeCents)
        // The cap permits this gross amount even when the uncapped analytical bound would overflow.
        val large = FeeCalculator.quote(Long.MAX_VALUE - 10, schedule("0.9", 0, "0", 0).copy(providerMaximumCents = 10))
        assertEquals(Long.MAX_VALUE, large.totalCents)
        assertEquals(10, large.providerFeeCents)
    }
}
