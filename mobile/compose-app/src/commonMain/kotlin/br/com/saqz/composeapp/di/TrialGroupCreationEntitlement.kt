package br.com.saqz.composeapp.di

import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.GroupCreationEntitlement
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus

internal class TrialGroupCreationEntitlement(private val gateway: TrialGateway) : GroupCreationEntitlement {
    override suspend fun canCreateGroup(): Boolean = when (val result = gateway.ownerTrial()) {
        is SaqzResult.Failure -> false
        is SaqzResult.Success -> result.value.canEnter()
    }

    override suspend fun canOpenCreationFlow(): Boolean = when (val result = gateway.ownerTrial()) {
        is SaqzResult.Failure -> true
        is SaqzResult.Success -> result.value.canRedeemCoupon || result.value.canEnter()
    }

    private fun TrialAccess.canEnter(): Boolean = (canCreateGroup && when (status) {
        TrialStatus.Available, TrialStatus.Active, TrialStatus.Subscribed -> true
        TrialStatus.Expired, TrialStatus.Ineligible -> false
    })
}
