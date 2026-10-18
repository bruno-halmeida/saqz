package br.com.saqz.core.common.formatting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrialDateTimeTest {
    @Test
    fun exactBoundaryUsesChosenZoneIncludingPreviousDay() {
        assertEquals("11/09/2026 22:30", formatInstantDateTimePtBr("2026-09-12T01:30:00Z", "America/Sao_Paulo"))
        assertEquals("12/09/2026 01:30", formatInstantDateTimePtBr("2026-09-12T01:30:00Z", "UTC"))
    }

    @Test
    fun clockIsAlwaysInSaoPauloWithMinutesOnlyWhenNeeded() {
        assertEquals("12h", formatInstantClockSaoPaulo("2026-10-15T15:00:00Z"))
        assertEquals("18h30", formatInstantClockSaoPaulo("2026-10-15T21:30:00Z"))
        assertEquals("22h05", formatInstantClockSaoPaulo("2026-09-12T01:05:00Z"))
        assertNull(formatInstantClockSaoPaulo(null))
        assertNull(formatInstantClockSaoPaulo("invalid"))
    }

    @Test
    fun missingOrInvalidBoundariesDoNotInventDates() {
        assertNull(formatInstantDateTimePtBr(null, "UTC"))
        assertNull(formatInstantDateTimePtBr("invalid", "UTC"))
        assertNull(formatInstantDateTimePtBr("2026-09-12T01:30:00Z", "invalid"))
    }
}
