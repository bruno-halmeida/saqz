package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.presentation.navigation.GroupsRoute
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionPlansNavigationTest {
    private fun subscription(
        status: SubscriptionStatus,
        entitled: Boolean,
        provider: SubscriptionProvider = SubscriptionProvider.Asaas,
    ) = SaqzResult.Success(
        MySubscription(
            status = status,
            entitled = entitled,
            plan = Plan.Organizador,
            cycle = SubscriptionCycle.Monthly,
            currentPeriodEnd = "2026-10-20T00:00:00Z",
            usage = SubscriptionUsage(groupsUsed = 1, groupsLimit = 3),
            canceledAt = null,
            provider = provider,
        ),
    )

    private fun open(
        stack: MutableList<NavKey>,
        result: SaqzResult<MySubscription, SubscriptionError>,
        webOpens: Boolean = false,
    ): Int {
        var webCalls = 0
        stack.openSubscriptionPlans(result) {
            webCalls++
            webOpens
        }
        return webCalls
    }

    @Test
    fun `past due in grace goes to my plan and never stacks the store paywall`() {
        for (provider in SubscriptionProvider.entries) {
            val stack = mutableListOf<NavKey>(GroupsRoute.Notifications())
            open(stack, subscription(SubscriptionStatus.PastDue, entitled = true, provider = provider))
            assertEquals(SubscriptionsRoute.MyPlan, stack.last())
            assertEquals(2, stack.size)
        }
    }

    @Test
    fun `frozen past due goes to my plan instead of selling through the store`() {
        val stack = mutableListOf<NavKey>(GroupsRoute.Notifications())
        open(stack, subscription(SubscriptionStatus.PastDue, entitled = false))
        assertEquals(SubscriptionsRoute.MyPlan, stack.last())
    }

    @Test
    fun `active and canceled subscribers go to my plan`() {
        for (status in listOf(SubscriptionStatus.Active, SubscriptionStatus.Canceled)) {
            val stack = mutableListOf<NavKey>(GroupsRoute.Notifications())
            val webCalls = open(stack, subscription(status, entitled = status == SubscriptionStatus.Active))
            assertEquals(SubscriptionsRoute.MyPlan, stack.last())
            assertEquals(0, webCalls)
        }
    }

    @Test
    fun `whoever never subscribed gets the plans screen`() {
        val stack = mutableListOf<NavKey>(GroupsRoute.Notifications())
        open(stack, SaqzResult.Failure(SubscriptionError.NotFound))
        assertEquals(SubscribeForAccess, stack.last())
    }

    @Test
    fun `web checkout replaces the plans screen for whoever never subscribed`() {
        val stack = mutableListOf<NavKey>(GroupsRoute.Notifications())
        val webCalls = open(stack, SaqzResult.Failure(SubscriptionError.NotFound), webOpens = true)
        assertEquals(1, webCalls)
        assertEquals(1, stack.size)
    }

    @Test
    fun `a failed lookup goes to my plan where it can be retried`() {
        val stack = mutableListOf<NavKey>(GroupsRoute.Notifications())
        open(stack, SaqzResult.Failure(SubscriptionError.Data(DataError.Connectivity)))
        assertEquals(SubscriptionsRoute.MyPlan, stack.last())
    }

    @Test
    fun `repeated taps never stack the same screen twice`() {
        val subscriber = mutableListOf<NavKey>(GroupsRoute.Notifications())
        repeat(3) { open(subscriber, subscription(SubscriptionStatus.PastDue, entitled = true)) }
        assertEquals(2, subscriber.size)

        val trial = mutableListOf<NavKey>(GroupsRoute.Notifications())
        repeat(3) { open(trial, SaqzResult.Failure(SubscriptionError.NotFound)) }
        assertEquals(listOf<NavKey>(GroupsRoute.Notifications(), SubscribeForAccess), trial)
    }
}
