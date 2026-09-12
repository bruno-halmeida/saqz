package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.OrganizerTrial
import br.com.saqz.subscriptions.domain.Plan
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrialSubscriptionLimitsTest {
    private val owner = UUID.randomUUID()
    private val trial = OrganizerTrial.start(owner, Instant.parse("2026-09-12T12:00:00Z"))
    private val repository = object : OrganizerTrialRepository {
        override fun find(ownerUserId: UUID) = trial.takeIf { ownerUserId == owner }
        override fun insert(trial: OrganizerTrial) = error("limits must be read only")
    }

    @Test
    fun `active trial grants exactly titular quotas and no access to another owner`() {
        val limits = limits(trial.endsAt.minusNanos(1))
        assertEquals(1, limits.groupLimitFor(owner))
        assertEquals(25, limits.athleteLimitFor(owner))
        assertEquals(0, limits.groupLimitFor(UUID.randomUUID()))
        assertEquals(0, limits.athleteLimitFor(UUID.randomUUID()))
    }

    @Test
    fun `trial expires at the exact instant and does not renew`() {
        for (now in listOf(trial.endsAt, trial.endsAt.plusSeconds(1))) {
            assertEquals(0, limits(now).groupLimitFor(owner))
            assertEquals(0, limits(now).athleteLimitFor(owner))
        }
    }

    @Test
    fun `paid plans take precedence over active or expired trial including pending downgrade`() {
        for (now in listOf(trial.startedAt, trial.endsAt)) {
            val unlimited = limits(now, EntitlingSubscription(Plan.ILIMITADO))
            assertNull(unlimited.groupLimitFor(owner))
            assertNull(unlimited.athleteLimitFor(owner))
            val downgrade = limits(now, EntitlingSubscription(Plan.ILIMITADO, Plan.TITULAR))
            assertEquals(1, downgrade.groupLimitFor(owner))
            assertEquals(25, downgrade.athleteLimitFor(owner))
        }
    }

    private fun limits(now: Instant, paid: EntitlingSubscription? = null) =
        SubscriptionLimitsAdapter(SubscriptionPlanLookup { paid }, repository, Clock.fixed(now, ZoneOffset.UTC))
}
