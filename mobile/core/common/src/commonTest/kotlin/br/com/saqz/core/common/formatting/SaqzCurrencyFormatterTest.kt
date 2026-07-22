package br.com.saqz.core.common.formatting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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

    @Test
    fun parseEmptyReturnsNull() {
        assertNull(parseBrlToCents(""))
    }

    @Test
    fun parseSeparatorOnlyReturnsNull() {
        assertNull(parseBrlToCents(","))
        assertNull(parseBrlToCents("."))
    }

    @Test
    fun parseDoubleSeparatorReturnsNull() {
        assertNull(parseBrlToCents("1,00,00"))
        assertNull(parseBrlToCents("1.000,00,00"))
    }

    @Test
    fun parseCommaDecimalCents() {
        assertEquals(7000L, parseBrlToCents("70,00"))
    }

    @Test
    fun parseThousandsDotsAndCommaDecimal() {
        assertEquals(123456L, parseBrlToCents("1.234,56"))
    }

    @Test
    fun parseLargeValueWithinReaisLimit() {
        assertEquals(9_999_999_999L, parseBrlToCents("99.999.999,99"))
    }

    @Test
    fun parseZeroAndPartialDecimals() {
        assertEquals(0L, parseBrlToCents("0"))
        assertEquals(5L, parseBrlToCents("0,05"))
        assertEquals(50L, parseBrlToCents("0,5"))
    }

    @Test
    fun parseNegativeReturnsNull() {
        assertNull(parseBrlToCents("-10,00"))
    }

    @Test
    fun parseTooManyReaisDigitsReturnsNull() {
        assertNull(parseBrlToCents("123456789,00"))
    }

    @Test
    fun parseTooManyDecimalDigitsReturnsNull() {
        assertNull(parseBrlToCents("10,123"))
    }

    @Test
    fun sanitizeEmptyReturnsEmpty() {
        assertEquals("", sanitizeBrlInput(""))
    }

    @Test
    fun sanitizeKeepsDigitsAndCommaDecimal() {
        assertEquals("70,00", sanitizeBrlInput("70,00"))
    }

    @Test
    fun sanitizeConvertsDotDecimalToComma() {
        assertEquals("70,00", sanitizeBrlInput("70.00"))
    }

    @Test
    fun sanitizeStripsThousandsDotsWhenCommaDecimalPresent() {
        assertEquals("1234567,89", sanitizeBrlInput("1.234.567,89"))
    }

    @Test
    fun sanitizeTreatsMultipleDotsAsThousandsWhenNoComma() {
        assertEquals("1234567", sanitizeBrlInput("1.234.567"))
    }

    @Test
    fun sanitizeCapsReaisAndDecimals() {
        assertEquals("12345678,90", sanitizeBrlInput("1234567890,9012"))
    }

    @Test
    fun sanitizeRemovesNonNumericCharacters() {
        assertEquals("1234,56", sanitizeBrlInput("R$ 1.234,56"))
    }
}
