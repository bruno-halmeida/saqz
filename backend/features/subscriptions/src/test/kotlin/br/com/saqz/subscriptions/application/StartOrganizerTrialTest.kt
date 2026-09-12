package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.OwnerGroupHistory
import br.com.saqz.subscriptions.domain.OrganizerTrial
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartOrganizerTrialTest {
    private val owner = UUID.randomUUID()
    private val now = Instant.parse("2026-09-12T12:00:00Z")

    @Test
    fun `eligibility is read only and start records exact owner and dates`() {
        val repository = MemoryTrials()
        val start = service(repository)
        assertTrue(start.isEligible(owner))
        assertNull(repository.trial)
        start.start(owner)
        assertEquals(OrganizerTrial.start(owner, now), repository.trial)
        assertFalse(start.isEligible(owner))
    }

    @Test
    fun `prior group including deleted or paid history prevents a new trial`() {
        assertFalse(service(MemoryTrials(), hadGroup = true).isEligible(owner))
        assertFalse(service(MemoryTrials(), paid = true).isEligible(owner))
        val repository = MemoryTrials().apply { trial = OrganizerTrial.start(owner, now.minusSeconds(15 * 86400)) }
        assertFalse(service(repository).isEligible(owner))
    }

    private fun service(repository: MemoryTrials, hadGroup: Boolean = false, paid: Boolean = false) =
        StartOrganizerTrial(repository, OwnerGroupHistory { hadGroup }, PaidSubscriptionHistory { paid }, Clock.fixed(now, ZoneOffset.UTC))

    private class MemoryTrials : OrganizerTrialRepository {
        var trial: OrganizerTrial? = null
        override fun find(ownerUserId: UUID) = trial
        override fun insert(trial: OrganizerTrial) { if (this.trial == null) this.trial = trial }
    }
}
