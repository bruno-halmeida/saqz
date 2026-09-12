package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.GroupCreationTrial
import br.com.saqz.sharedkernel.subscription.OwnerGroupHistory
import br.com.saqz.subscriptions.domain.OrganizerTrial
import java.time.Clock
import java.util.UUID

fun interface PaidSubscriptionHistory {
    fun hasPaidHistory(ownerId: UUID): Boolean
}

class StartOrganizerTrial(
    private val trials: OrganizerTrialRepository,
    private val history: OwnerGroupHistory,
    private val paidHistory: PaidSubscriptionHistory,
    private val clock: Clock,
) : GroupCreationTrial {
    override fun isEligible(ownerId: UUID): Boolean =
        trials.find(ownerId) == null && !history.hasEverOwnedGroup(ownerId) && !paidHistory.hasPaidHistory(ownerId)

    override fun start(ownerId: UUID) = trials.insert(OrganizerTrial.start(ownerId, clock.instant()))
}
