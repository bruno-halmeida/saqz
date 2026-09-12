package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessSubscriptionReturnTest {
    @Test
    fun paymentReturnsToMyPlanWithoutOpeningGroupCreation() {
        val stack = mutableListOf<NavKey>(SubscriptionsRoute.MyPlan, SubscribeForAccess)
        stack.returnFromAccessSubscription()
        assertEquals(listOf<NavKey>(SubscriptionsRoute.MyPlan), stack)
        stack.returnFromAccessSubscription()
        assertEquals(listOf<NavKey>(SubscriptionsRoute.MyPlan), stack)
    }
}
