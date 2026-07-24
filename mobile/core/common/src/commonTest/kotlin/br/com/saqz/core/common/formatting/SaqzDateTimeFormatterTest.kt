package br.com.saqz.core.common.formatting

import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class SaqzDateTimeFormatterTest {
    private val saoPaulo = SaqzTimeZoneProvider { TimeZone.of("America/Sao_Paulo") }
    private val formatter = SaqzDateTimeFormatter(saoPaulo)

    // 2025-05-15T23:00:00Z in America/Sao_Paulo (UTC-3) is 2025-05-15 20:00 local.
    private val canonicalInstant = Instant.parse("2025-05-15T23:00:00Z")

    @Test
    fun canonicalDate() {
        assertEquals("15/05/2025", formatter.formatDate(canonicalInstant))
    }

    @Test
    fun canonicalTime() {
        assertEquals("20:00", formatter.formatTime(canonicalInstant))
    }

    @Test
    fun canonicalDateTime() {
        assertEquals("15/05/2025 20:00", formatter.formatDateTime(canonicalInstant))
    }

    @Test
    fun previousDayBoundary() {
        // 02:00Z in Sao Paulo (UTC-3) is the previous calendar day, 23:00 local.
        val instant = Instant.parse("2025-05-15T02:00:00Z")
        assertEquals("14/05/2025", formatter.formatDate(instant))
    }

    @Test
    fun nextDayBoundary() {
        // 20:00Z in Tokyo (UTC+9) is the next calendar day, 05:00 local.
        val tokyo = SaqzDateTimeFormatter(SaqzTimeZoneProvider { TimeZone.of("Asia/Tokyo") })
        val instant = Instant.parse("2025-05-15T20:00:00Z")
        assertEquals("16/05/2025", tokyo.formatDate(instant))
    }

    @Test
    fun defaultProviderIsDeviceTimeZone() {
        val device = SaqzDateTimeFormatter(SaqzTimeZoneProvider { TimeZone.currentSystemDefault() })
        assertEquals(device.formatDateTime(canonicalInstant), SaqzDateTimeFormatter().formatDateTime(canonicalInstant))
    }

    @Test
    fun invalidZoneFails() {
        val invalid = SaqzDateTimeFormatter(SaqzTimeZoneProvider { TimeZone.of("Not/AZone") })
        assertFailsWith<IllegalTimeZoneException> { invalid.formatDate(canonicalInstant) }
    }

    @Test
    fun deviceLocaleDoesNotChangeDate() {
        // No locale formatter is used: ASCII digits and fixed separators only.
        assertEquals("15/05/2025", formatter.formatDate(canonicalInstant))
    }

    @Test
    fun deviceLocaleDoesNotChangeTime() {
        // 24h clock with manual zero-padding, independent of any device locale.
        assertEquals("20:00", formatter.formatTime(canonicalInstant))
    }

    @Test
    fun formatterIsOffline() {
        // Pure function: repeated calls with no I/O yield identical output.
        val first = formatter.formatDateTime(canonicalInstant)
        val second = formatter.formatDateTime(canonicalInstant)
        assertEquals(first, second)
        assertEquals("15/05/2025 20:00", first)
    }

    @Test
    fun localDateFromIsoFormatsAsDdMmYyyy() {
        assertEquals("12/08/2026", formatter.formatLocalDatePtBr("2026-08-12"))
    }

    @Test
    fun monthFromIsoFormatsAsMmYyyy() {
        assertEquals("08/2026", formatter.formatMonthPtBr("2026-08"))
    }

    @Test
    fun invalidLocalDateReturnsInputUnchanged() {
        assertEquals("not-a-date", formatter.formatLocalDatePtBr("not-a-date"))
    }

    @Test
    fun invalidMonthReturnsInputUnchanged() {
        assertEquals("not-a-month", formatter.formatMonthPtBr("not-a-month"))
    }
}
