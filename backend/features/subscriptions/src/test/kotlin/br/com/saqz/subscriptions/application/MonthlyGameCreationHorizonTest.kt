package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.application.MonthlyGameCreationHorizon.Companion.monthlyMarkAfter
import br.com.saqz.subscriptions.application.MonthlyGameCreationHorizon.Companion.monthlyMarkBefore
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class MonthlyGameCreationHorizonTest {
    private val now = Instant.parse("2026-09-17T15:00:00Z")

    @Test
    fun `monthly plan goes to the period end and annual plan only to its next monthly mark`() {
        assertEquals(Instant.parse("2026-10-05T12:00:00Z"), monthlyMarkBefore(Instant.parse("2026-10-05T12:00:00Z"), now))
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), monthlyMarkBefore(Instant.parse("2027-03-10T12:00:00Z"), now))
    }

    @Test
    fun `overdue period extends nothing`() {
        val overdue = Instant.parse("2026-09-15T12:00:00Z")
        assertEquals(overdue, monthlyMarkBefore(overdue, now))
    }

    @Test
    fun `trial walks like a monthly plan from its start`() {
        assertEquals(Instant.parse("2026-10-10T09:00:00Z"), monthlyMarkAfter(Instant.parse("2026-09-10T09:00:00Z"), now))
        assertEquals(Instant.parse("2026-10-01T09:00:00Z"), monthlyMarkAfter(Instant.parse("2026-06-01T09:00:00Z"), now))
    }
}
