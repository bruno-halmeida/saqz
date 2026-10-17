package br.com.saqz.sharedkernel.subscription

import java.util.UUID

/**
 * Cross-feature port for plan-usage snapshots (owned groups + athletes of the largest group).
 * The athlete limit is per group and counts only current members (SAI-005). Implemented by
 * :features:groups; consumed by :features:subscriptions for downgrade eligibility.
 */
fun interface OwnerPlanUsageLookup {
    fun usageFor(ownerUserId: UUID): OwnerPlanUsage
}

data class OwnerPlanUsage(
    val ownedGroupCount: Int,
    val largestGroupAthleteCount: Int,
)
