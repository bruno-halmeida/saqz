package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.groups.presentation.navigation.FinanceRoute
import br.com.saqz.receivables.presentation.ReceiptConfigurationRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptConfigurationNavigationTest {
    @Test fun contextualRouteSurvivesRestorationAndLogoutClearsEverything() {
        val stack = NavBackStack<NavKey>(SaqzShellDestination.Groups,
            FinanceRoute.GroupCashbox("group"), ReceiptConfigurationRoute("group"))
        val saved = encodeToSavedState(saqzAccessBackStackSerializer, stack, saqzLocalNavConfiguration)
        val restored = decodeFromSavedState(saqzAccessBackStackSerializer, saved, saqzLocalNavConfiguration)
        assertEquals(stack.toList(), restored.toList())
        reconcileAccessStack(restored, SessionAccessState.SignedOut)
        assertEquals(listOf(AccessRoute.Login), restored.toList())
    }
}
