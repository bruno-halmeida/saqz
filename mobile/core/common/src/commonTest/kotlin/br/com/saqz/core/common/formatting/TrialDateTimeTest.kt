package br.com.saqz.core.common.formatting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrialDateTimeTest {
    @Test
    fun exactBoundaryUsesChosenZoneIncludingPreviousDay() {
        assertEquals("11/09/2026 22:30 (America/Sao_Paulo)", formatInstantDateTimePtBr("2026-09-12T01:30:00Z", "America/Sao_Paulo"))
        assertEquals("12/09/2026 01:30 (UTC)", formatInstantDateTimePtBr("2026-09-12T01:30:00Z", "UTC"))
    }

    @Test
    fun missingOrInvalidBoundariesDoNotInventDates() {
        assertNull(formatInstantDateTimePtBr(null, "UTC"))
        assertNull(formatInstantDateTimePtBr("invalid", "UTC"))
        assertNull(formatInstantDateTimePtBr("2026-09-12T01:30:00Z", "invalid"))
    }
}
