package br.com.saqz.composeapp.subscriptiongate

import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaidSubscriptionEntitlementTest {
    @Test
    fun onlyConfirmedEntitlementRestoresAccessEvenWithNoSpareGroupSlots() = runTest {
        val gateway = Gateway()
        val entitlement = PaidSubscriptionEntitlement(gateway)
        assertFalse(entitlement.canCreateGroup()) // trial has no paid subscription
        gateway.result = SaqzResult.Success(subscription(entitled = false))
        assertFalse(entitlement.canCreateGroup()) // payment pending
        gateway.result = SaqzResult.Success(subscription(entitled = true))
        assertTrue(entitlement.canCreateGroup()) // paid, 1/1 groups already used
    }

    private fun subscription(entitled: Boolean) = MySubscription(
        status = if (entitled) SubscriptionStatus.Active else SubscriptionStatus.PastDue,
        entitled = entitled,
        plan = Plan.Titular,
        cycle = SubscriptionCycle.Monthly,
        currentPeriodEnd = "2026-10-12T12:00:00Z",
        usage = SubscriptionUsage(1, 1),
        canceledAt = null,
    )

    private class Gateway : SubscriptionGateway {
        var result: SaqzResult<MySubscription, SubscriptionError> = SaqzResult.Failure(SubscriptionError.NotFound)
        override suspend fun mySubscription() = result
        override suspend fun listPlans() = SaqzResult.Failure(SubscriptionError.NotFound)
        override suspend fun changePlan(requestId: String, targetPlan: Plan) = SaqzResult.Failure(SubscriptionError.NotFound)
        override suspend fun cancel() = SaqzResult.Failure(SubscriptionError.NotFound)
        override suspend fun receipts(limit: Int, offset: Int) = SaqzResult.Failure(SubscriptionError.NotFound)
    }
}
