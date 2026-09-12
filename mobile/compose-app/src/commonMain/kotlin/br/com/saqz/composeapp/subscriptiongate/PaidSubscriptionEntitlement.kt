package br.com.saqz.composeapp.subscriptiongate

import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.GroupCreationEntitlement
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import org.koin.core.qualifier.named

internal val paidSubscriptionGateQualifier = named("paid-subscription-gate")

/** This gate restores existing groups; free eligibility and spare group slots are irrelevant. */
internal class PaidSubscriptionEntitlement(private val gateway: SubscriptionGateway) : GroupCreationEntitlement {
    override suspend fun canCreateGroup(): Boolean = when (val result = gateway.mySubscription()) {
        is SaqzResult.Success -> result.value.entitled
        is SaqzResult.Failure -> false
    }
}
