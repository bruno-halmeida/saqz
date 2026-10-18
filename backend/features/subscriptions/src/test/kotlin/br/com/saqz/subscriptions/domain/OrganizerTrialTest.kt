package br.com.saqz.subscriptions.domain

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrganizerTrialTest {
    @Test
    fun `trial lasts exactly fourteen days with an exclusive end`() {
        val start = Instant.parse("2026-09-12T12:00:00Z")
        val trial = OrganizerTrial.start(UUID.randomUUID(), start)
        assertEquals(start, trial.startedAt)
        assertEquals(start.plus(Duration.ofDays(14)), trial.endsAt)
        assertTrue(trial.isActiveAt(trial.endsAt.minusNanos(1)))
        assertFalse(trial.isActiveAt(trial.endsAt))
        assertFalse(trial.isActiveAt(trial.endsAt.plusSeconds(1)))
    }

    @Test
    fun `trial length accepts 1 to 365 days and refuses 0 and 366`() {
        val start = Instant.parse("2026-09-12T12:00:00Z")
        assertEquals(start.plus(Duration.ofDays(1)), OrganizerTrial.start(UUID.randomUUID(), start, days = 1).endsAt)
        assertEquals(start.plus(Duration.ofDays(365)), OrganizerTrial.start(UUID.randomUUID(), start, days = 365).endsAt)
        kotlin.test.assertFailsWith<IllegalArgumentException> { OrganizerTrial.start(UUID.randomUUID(), start, days = 0) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { OrganizerTrial.start(UUID.randomUUID(), start, days = 366) }
    }

    @Test
    fun `trial is always for the organizador plan`() {
        assertEquals(Plan.ORGANIZADOR, OrganizerTrial.plan)
    }
}
