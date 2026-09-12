package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.appaccess.AppOnboardingAuthState

/** Runs after the session gate: the handoff is access UI, never an authenticated destination. */
internal fun reconcileAppOnboardingStack(
    stack: MutableList<NavKey>,
    session: SessionAccessState,
    handoff: AppOnboardingAuthState,
) {
    val visible = when (handoff) {
        AppOnboardingAuthState.Idle -> false
        is AppOnboardingAuthState.Completed -> !handoff.onboardingCompleted && session is SessionAccessState.Ready
        else -> true
    }
    if (visible && AccessRoute.AppOnboarding !in stack) {
        stack.add(AccessRoute.AppOnboarding)
    } else if (!visible) {
        stack.removeAll { it == AccessRoute.AppOnboarding }
    }
}

internal fun AppOnboardingAuthState.isNativeHandoffBusy(): Boolean =
    this == AppOnboardingAuthState.Redeeming ||
        this == AppOnboardingAuthState.SigningIn ||
        this == AppOnboardingAuthState.WaitingForSessionResolution
