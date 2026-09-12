package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.appaccess.AppOnboardingAuthState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class AppOnboardingNavigationTest {
    @Test
    fun `confirmation remains above profile gate without exposing the shell`() {
        val stack = mutableListOf<NavKey>(SaqzShellDestination.Home)
        val session = SessionAccessState.CompletingIdentity(null)
        repeat(2) {
            reconcileAccessStack(stack, session)
            reconcileAppOnboardingStack(stack, session, AppOnboardingAuthState.NeedsAccountConfirmation("owner"))
            assertEquals(listOf<NavKey>(AccessRoute.IdentityCompletion, AccessRoute.AppOnboarding), stack)
        }
    }

    @Test
    fun `completed handoff cannot show intro before ready and cancellation restores login`() {
        val stack = mutableListOf<NavKey>(AccessRoute.Login, AccessRoute.AppOnboarding)
        reconcileAppOnboardingStack(stack, SessionAccessState.SignedOut, AppOnboardingAuthState.Completed(false))
        assertEquals(listOf<NavKey>(AccessRoute.Login), stack)
        reconcileAppOnboardingStack(stack, SessionAccessState.SignedOut, AppOnboardingAuthState.Redeeming)
        assertEquals(listOf<NavKey>(AccessRoute.Login, AccessRoute.AppOnboarding), stack)
        reconcileAppOnboardingStack(stack, SessionAccessState.SignedOut, AppOnboardingAuthState.Idle)
        assertEquals(listOf<NavKey>(AccessRoute.Login), stack)
    }

    @Test
    fun `native mutation cannot be dismissed as if it had been cancelled`() {
        assertTrue(AppOnboardingAuthState.SigningIn.isNativeHandoffBusy())
        assertTrue(AppOnboardingAuthState.Redeeming.isNativeHandoffBusy())
        assertFalse(AppOnboardingAuthState.NeedsAccountConfirmation("owner").isNativeHandoffBusy())
    }
}
