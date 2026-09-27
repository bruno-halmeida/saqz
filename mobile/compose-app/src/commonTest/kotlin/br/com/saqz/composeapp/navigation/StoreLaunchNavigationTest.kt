package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.groups.presentation.navigation.GroupsRoute
import br.com.saqz.profile.presentation.navigation.ProfileRoute
import br.com.saqz.receivables.presentation.*
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreLaunchNavigationTest {
    @Test fun `restored purchases chat and API payments cannot compose destinations`() {
        val disabled = listOf<NavKey>(SubscriptionsRoute.ChangePlan, GroupsRoute.Thread("group", false),
            ReceiptFinanceHomeRoute, FinancialOnboardingRoute, FinancialManagementRoute, ReceiptWalletRoute,
            MemberPaymentHistoryRoute, MemberPaymentRoute("order"), RecurrenceRoute("account", "group"),
            ChargeApprovalRoute("group", "charge"), ReceiptConfigurationRoute("group"))
        val restored = listOf<NavKey>(SaqzShellDestination.Home) + disabled
        assertEquals(listOf(SaqzShellDestination.Home), restored.filter { it.isAvailableAtLaunch() })
    }

    @Test fun `plan consumption profile deletion and notices remain available`() {
        listOf<NavKey>(SubscriptionsRoute.MyPlan, ProfileRoute.DeleteAccount, GroupsRoute.Thread("group", true),
            SubscriptionRequired).forEach { assertTrue(it.isAvailableAtLaunch()) }
    }
}
