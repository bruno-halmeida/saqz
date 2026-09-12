package br.com.saqz.bootstrap.configuration

import br.com.saqz.receivables.application.ReceivablesEligibility
import br.com.saqz.receivables.application.ReceivablesEntitlement
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccessLookup
import br.com.saqz.sharedkernel.subscription.TrialStatus
import br.com.saqz.subscriptions.application.SubscriptionRepository
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.util.UUID

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class ReceivablesEligibilityConfiguration {
    @Bean
    fun receivablesEligibility(trials: OrganizerTrialAccessLookup, subscriptions: SubscriptionRepository): ReceivablesEligibility =
        CentralReceivablesEligibility(trials, subscriptions::findByOwnerUserId)
}

/** Composition of existing commercial contracts; never starts a trial or derives its duration. */
class CentralReceivablesEligibility(
    private val trials: OrganizerTrialAccessLookup,
    private val subscriptionForOwner: (UUID) -> Subscription?,
) : ReceivablesEligibility {
    override fun forOwner(ownerUserId: UUID, at: Instant): ReceivablesEntitlement {
        val subscription = subscriptionForOwner(ownerUserId)
        if (subscription != null && subscription.isEntitlingAt(at)) {
            if (subscription.plan == Plan.TITULAR) return ReceivablesEntitlement(false, null)
            val cutoffs = listOfNotNull(
                subscription.pendingPlanEffectiveAt.takeIf { subscription.pendingPlan == Plan.TITULAR },
                subscription.currentPeriodEnd.takeIf { subscription.status == SubscriptionStatus.CANCELED },
                subscription.pastDueSince?.plus(Subscription.PAST_DUE_GRACE)
                    .takeIf { subscription.status == SubscriptionStatus.PAST_DUE },
            )
            val cutoff = cutoffs.minOrNull()
            return ReceivablesEntitlement(cutoff == null || at < cutoff, cutoff)
        }
        val trial = trials.forOwner(ownerUserId)
        return ReceivablesEntitlement(trial.status == TrialStatus.ACTIVE, trial.endsAt)
    }
}
