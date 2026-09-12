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
}
