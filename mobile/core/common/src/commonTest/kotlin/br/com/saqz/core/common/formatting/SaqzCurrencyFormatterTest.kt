package br.com.saqz.core.common.formatting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SaqzCurrencyFormatterTest {
    // NBSP (U+00A0) separates the R$ symbol from the value.
    private val nbsp = '\u00A0'

    @Test
    fun zero() {
        assertEquals("R$${nbsp}0,00", formatBrl(0L))
    }

    @Test
    fun negativeZero() {
        assertEquals("R$${nbsp}0,00", formatBrl(-0L))
    }

    @Test
    fun canonicalPositive() {
        assertEquals("R$${nbsp}1.234,56", formatBrl(123456L))
    }

    @Test
    fun canonicalNegative() {
        assertEquals("-R$${nbsp}1.234,56", formatBrl(-123456L))
    }

    @Test
    fun oneCent() {
        assertEquals("R$${nbsp}0,01", formatBrl(1L))
    }

    @Test
    fun positiveSafeLimit() {
        assertEquals("R$${nbsp}90.071.992.547.409,91", formatBrl(9_007_199_254_740_991L))
    }

    @Test
    fun negativeSafeLimit() {
        assertEquals("-R$${nbsp}90.071.992.547.409,91", formatBrl(-9_007_199_254_740_991L))
    }

    @Test
    fun positiveOverflowFails() {
        assertFailsWith<IllegalArgumentException> { formatBrl(9_007_199_254_740_992L) }
    }

    @Test
    fun negativeOverflowFails() {
        assertFailsWith<IllegalArgumentException> { formatBrl(-9_007_199_254_740_992L) }
    }

    @Test
    fun deviceLocaleDoesNotChangeBrl() {
        // No NumberFormat/locale: dot thousands and comma decimals are fixed.
        assertEquals("R$${nbsp}1.234,56", formatBrl(123456L))
    }
}
