package br.com.saqz.composeapp.di

import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.GroupCreationEntitlement
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus

internal class TrialGroupCreationEntitlement(private val gateway: TrialGateway) : GroupCreationEntitlement {
    override suspend fun canCreateGroup(): Boolean = when (val result = gateway.ownerTrial()) {
        is SaqzResult.Failure -> false
        is SaqzResult.Success -> result.value.canRedeemCoupon || (result.value.canCreateGroup && when (result.value.status) {
            TrialStatus.Available, TrialStatus.Active, TrialStatus.Subscribed -> true
            TrialStatus.Expired, TrialStatus.Ineligible -> false
        })
    }
}
