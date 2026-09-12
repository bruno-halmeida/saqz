package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.GroupCreationTrial
import br.com.saqz.sharedkernel.subscription.OwnedGroupCounter
import br.com.saqz.sharedkernel.subscription.TrialStatus
import br.com.saqz.subscriptions.domain.OrganizerTrial
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetOrganizerTrialTest {
    private val owner = UUID.randomUUID()
    private val now = Instant.parse("2026-09-12T12:00:00Z")

    @Test
    fun `ineligible legacy owner gets no new trial or trial expiration restrictions`() {
        val access = lookup(null, eligible = false).forOwner(owner)
        assertEquals(TrialStatus.INELIGIBLE, access.status)
        assertEquals(null, access.startedAt)
        assertEquals(null, access.endsAt)
        assertEquals(now, access.serverTime)
        assertFalse(access.canCreateGroup)
        assertFalse(access.readOnly)
    }

    @Test
    fun `deleting active trial group allows replacement without extending its dates`() {
        val trial = OrganizerTrial.start(owner, now.minusSeconds(86400))
        val access = lookup(trial).forOwner(owner)
        assertEquals(TrialStatus.ACTIVE, access.status)
        assertEquals(trial.startedAt, access.startedAt)
        assertEquals(trial.endsAt, access.endsAt)
        assertTrue(access.canCreateGroup)
        assertFalse(access.readOnly)
    }

    private fun lookup(trial: OrganizerTrial?, eligible: Boolean = false): GetOrganizerTrial {
        val repository = object : OrganizerTrialRepository {
            override fun find(ownerUserId: UUID) = trial
            override fun insert(trial: OrganizerTrial) = error("query must not write")
        }
        val eligibility = object : GroupCreationTrial {
            override fun isEligible(ownerId: UUID) = eligible
            override fun start(ownerId: UUID) = error("query must not start")
        }
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val paid = SubscriptionPlanLookup { null }
        return GetOrganizerTrial(repository, paid, eligibility, OwnedGroupCounter { 0 }, SubscriptionLimitsAdapter(paid, repository, clock), clock)
    }
}
