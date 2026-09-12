package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.GroupCreationTrial
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccess
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccessLookup
import br.com.saqz.sharedkernel.subscription.OwnedGroupCounter
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import br.com.saqz.sharedkernel.subscription.TrialStatus
import java.time.Clock
import java.util.UUID

class GetOrganizerTrial(
    private val trials: OrganizerTrialRepository,
    private val paid: SubscriptionPlanLookup,
    private val eligibility: GroupCreationTrial,
    private val groups: OwnedGroupCounter,
    private val limits: SubscriptionLimits,
    private val clock: Clock,
) : OrganizerTrialAccessLookup {
    override fun forOwner(ownerId: UUID): OrganizerTrialAccess {
        val now = clock.instant()
        val trial = trials.find(ownerId)
        val status = when {
            paid.findEntitlingPlan(ownerId) != null -> TrialStatus.SUBSCRIBED
            trial != null && trial.isActiveAt(now) -> TrialStatus.ACTIVE
            trial != null -> TrialStatus.EXPIRED
            eligibility.isEligible(ownerId) -> TrialStatus.AVAILABLE
            else -> TrialStatus.INELIGIBLE
        }
        val canCreate = when (status) {
            TrialStatus.AVAILABLE -> true
            TrialStatus.ACTIVE, TrialStatus.SUBSCRIBED ->
                limits.groupLimitFor(ownerId)?.let { groups.countOwnedGroups(ownerId) < it } ?: true
            TrialStatus.EXPIRED, TrialStatus.INELIGIBLE -> false
        }
        return OrganizerTrialAccess(status, trial?.startedAt, trial?.endsAt, now, canCreate)
    }
}
