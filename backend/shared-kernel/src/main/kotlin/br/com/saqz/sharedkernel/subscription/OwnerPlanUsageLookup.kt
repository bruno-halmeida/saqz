package br.com.saqz.sharedkernel.subscription

import java.util.UUID

/**
 * Cross-feature port for plan-usage snapshots (owned groups + occupying athletes).
 * Implemented by :features:groups using the same occupancy rules as invite redemption
 * ([PlanLimitPolicy.occupyingAthletes] semantics); consumed by :features:subscriptions
 * for downgrade eligibility.
 */
fun interface OwnerPlanUsageLookup {
    fun usageFor(ownerUserId: UUID): OwnerPlanUsage
}

data class OwnerPlanUsage(
    val ownedGroupCount: Int,
    val occupyingAthleteCount: Int,
)
