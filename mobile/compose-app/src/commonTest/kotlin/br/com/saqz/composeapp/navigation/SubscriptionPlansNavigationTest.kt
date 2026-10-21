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
        paymentConfirmed: Boolean = true,
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
            paymentConfirmed = paymentConfirmed,
        ),
    )

    private fun open(
        stack: MutableList<NavKey>,
        result: SaqzResult<MySubscription, SubscriptionError>,
        entry: PlansEntry = PlansEntry.Default,
    ) = stack.openSubscriptionPlans(result, entry)

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
            open(stack, subscription(status, entitled = status == SubscriptionStatus.Active))
            assertEquals(SubscriptionsRoute.MyPlan, stack.last())
        }
    }

    @Test
    fun `whoever never subscribed gets the plans screen`() {
        val stack = mutableListOf<NavKey>(GroupsRoute.Notifications())
        open(stack, SaqzResult.Failure(SubscriptionError.NotFound))
        assertEquals(SubscribeForAccess, stack.last())
    }

    @Test
    fun `a web checkout that was never paid is treated as never subscribed`() {
        val stack = mutableListOf<NavKey>(GroupsRoute.Notifications())
        open(stack, subscription(SubscriptionStatus.PastDue, entitled = false, paymentConfirmed = false))
        assertEquals(SubscribeForAccess, stack.last())
    }

    @Test
    fun `an unconfirmed store subscription still goes to my plan`() {
        for (provider in listOf(SubscriptionProvider.AppStore, SubscriptionProvider.GooglePlay)) {
            val stack = mutableListOf<NavKey>(GroupsRoute.Notifications())
            open(stack, subscription(SubscriptionStatus.Active, entitled = true, provider = provider, paymentConfirmed = false))
            assertEquals(SubscriptionsRoute.MyPlan, stack.last())
        }
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

    @Test
    fun `whoever never subscribed buys from group creation and goes straight on to it`() {
        for (neverSubscribed in listOf(SaqzResult.Failure(SubscriptionError.NotFound), subscription(SubscriptionStatus.PastDue, false, paymentConfirmed = false))) {
            val stack = mutableListOf<NavKey>(GroupsRoute.Notifications())
            open(stack, neverSubscribed, entry = PlansEntry.GroupCreation)
            assertEquals(SubscriptionRequired, stack.last())
            assertEquals(2, stack.size)
        }
    }

    @Test
    fun `whoever already subscribes goes to my plan from group creation too`() {
        for (status in SubscriptionStatus.entries) {
            val stack = mutableListOf<NavKey>(GroupsRoute.Notifications())
            open(stack, subscription(status, entitled = status == SubscriptionStatus.Active), entry = PlansEntry.GroupCreation)
            assertEquals(SubscriptionsRoute.MyPlan, stack.last())
        }
        val failed = mutableListOf<NavKey>(GroupsRoute.Notifications())
        open(failed, SaqzResult.Failure(SubscriptionError.Data(DataError.Connectivity)), entry = PlansEntry.GroupCreation)
        assertEquals(SubscriptionsRoute.MyPlan, failed.last())
    }

    @Test
    fun `repeated taps from group creation never stack the paywall twice`() {
        val stack = mutableListOf<NavKey>(GroupsRoute.Notifications())
        repeat(3) { open(stack, SaqzResult.Failure(SubscriptionError.NotFound), entry = PlansEntry.GroupCreation) }
        assertEquals(listOf<NavKey>(GroupsRoute.Notifications(), SubscriptionRequired), stack)
    }
}
