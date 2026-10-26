package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import br.com.saqz.groups.presentation.navigation.GroupsRoute
import br.com.saqz.profile.presentation.navigation.ProfileRoute
import br.com.saqz.receivables.presentation.*
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreLaunchNavigationTest {
    @Test fun `restored purchases and chat cannot compose destinations`() {
        val disabled = listOf<NavKey>(SubscriptionsRoute.ChangePlan, GroupsRoute.Thread("group", false))
        val restored = listOf<NavKey>(SaqzShellDestination.Home) + disabled
        assertEquals(listOf(SaqzShellDestination.Home), restored.filter { it.isAvailableAtLaunch() })
    }

    @Test fun `plan consumption profile deletion notices and the server-gated whatsapp remain available`() {
        listOf<NavKey>(SubscriptionsRoute.MyPlan, ProfileRoute.DeleteAccount, GroupsRoute.Thread("group", true),
            GroupsRoute.WhatsApp("group"), SubscriptionRequired).forEach { assertTrue(it.isAvailableAtLaunch()) }
    }

    /** DEB-142: a constante saiu; quem esconde as entradas é a disponibilidade, e a pilha restaurada fica de pé. */
    @Test fun `receivables routes are no longer cut by a constant and survive restoration`() {
        val routes = listOf<NavKey>(ReceiptFinanceHomeRoute, FinancialOnboardingRoute, FinancialManagementRoute,
            ReceiptWalletRoute, ReceiptConfigurationRoute("group"), AthleteChargesRoute("group", "Vôlei"),
            ReceivablePaymentRoute("group", "Vôlei", "c1,c2"), AutopayRoute("group", "Vôlei"), SettleLinkRoute("Ab12_cd-9"),
            FeePolicyRoute("group", "account"), PaymentReviewRoute("group", "account", "review"))
        routes.forEach { assertTrue(it.isAvailableAtLaunch(), it.toString()) }
        val stack = NavBackStack<NavKey>(SaqzShellDestination.Home, *routes.toTypedArray())
        val saved = encodeToSavedState(saqzAccessBackStackSerializer, stack, saqzLocalNavConfiguration)
        assertEquals(stack.toList(), decodeFromSavedState(saqzAccessBackStackSerializer, saved, saqzLocalNavConfiguration).toList())
    }
}
