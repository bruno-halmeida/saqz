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
    private val offer: TrialOffer = TrialOffer.Open,
) : GroupCreationTrial {
    fun isFirstTrialEligible(ownerId: UUID): Boolean =
        trials.find(ownerId) == null && !history.hasEverOwnedGroup(ownerId) && !paidHistory.hasPaidHistory(ownerId)

    override fun isEligible(ownerId: UUID) = isFirstTrialEligible(ownerId) && offer.isAvailable(ownerId)

    override fun tryStart(ownerId: UUID): Boolean = isFirstTrialEligible(ownerId) && offer.grant(ownerId) { days ->
        trials.insert(OrganizerTrial.start(ownerId, clock.instant(), days))
    }

    override fun start(ownerId: UUID) { check(tryStart(ownerId)) { "Trial is not available" } }
}
