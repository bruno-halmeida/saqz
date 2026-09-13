package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.groups.presentation.navigation.FinanceRoute
import br.com.saqz.receivables.presentation.MemberPaymentRoute
import br.com.saqz.receivables.presentation.MemberPaymentHistoryRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberPaymentNavigationTest {
    @Test fun memberPaymentRoutesRestoreIdsAndLogoutRemovesTheWholeJourney() {
        val stack = NavBackStack<NavKey>(SaqzShellDestination(initialTab = br.com.saqz.composeapp.shell.SaqzShellProfileTab), FinanceRoute.OwnMonthlyPayments,
            br.com.saqz.receivables.presentation.FinancialOnboardingRoute, MemberPaymentHistoryRoute, MemberPaymentRoute("order"),
            br.com.saqz.receivables.presentation.ChargeApprovalRoute("group", "charge"))
        val encoded = encodeToSavedState(saqzAccessBackStackSerializer, stack, saqzLocalNavConfiguration)
        val restored = decodeFromSavedState(saqzAccessBackStackSerializer, encoded, saqzLocalNavConfiguration)
        assertEquals(stack.toList(), restored.toList())
        reconcileAccessStack(restored, SessionAccessState.SignedOut)
        assertEquals(listOf(AccessRoute.Login), restored.toList())
    }
}
