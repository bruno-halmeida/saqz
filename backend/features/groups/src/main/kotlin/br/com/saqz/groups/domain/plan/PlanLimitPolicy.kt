package br.com.saqz.groups.domain.plan

import java.time.Duration
import java.time.Instant
import java.util.UUID

object PlanLimitPolicy {
    val RECENTLY_CLOSED_WINDOW: Duration = Duration.ofDays(30)

    fun canCreateGroup(ownedGroupCount: Int, groupLimit: Int?): Boolean {
        require(ownedGroupCount >= 0)
        if (groupLimit == null) return true
        return ownedGroupCount < groupLimit
    }

    fun canEnterAsAthlete(
        occupyingAthleteIds: Set<UUID>,
        athleteId: UUID,
        athleteLimit: Int?,
    ): Boolean {
        if (athleteId in occupyingAthleteIds) return true
        if (athleteLimit == null) return true
        return occupyingAthleteIds.size < athleteLimit
    }

    /**
     * Distinct athletes that occupy a plan slot: open memberships, open waitlist
     * entries, and memberships/waitlist closed within the last 30 days.
     */
    fun occupyingAthletes(
        openMemberIds: Set<UUID>,
        openWaitlistIds: Set<UUID>,
        closedOccupancies: Collection<ClosedAthleteOccupancy>,
        now: Instant,
    ): Set<UUID> {
        val recentClosed = closedOccupancies
            .asSequence()
            .filter { !it.closedAt.isAfter(now) && Duration.between(it.closedAt, now) < RECENTLY_CLOSED_WINDOW }
            .map { it.athleteId }
            .toSet()
        return openMemberIds + openWaitlistIds + recentClosed
    }
}

data class ClosedAthleteOccupancy(
    val athleteId: UUID,
    val closedAt: Instant,
)
