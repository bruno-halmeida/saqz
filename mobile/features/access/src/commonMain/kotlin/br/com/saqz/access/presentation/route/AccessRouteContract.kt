package br.com.saqz.access.presentation.route

import androidx.compose.runtime.Immutable

/**
 * Distinguishes the two Access routes that have no dedicated per-route
 * ViewModel yet (Login/Registration/PasswordReset/Verification/NameCompletion
 * were already extracted; see design.md, "Route ownership inventory"). Each
 * Navigation Compose 3 entry supplies its own [AccessRouteViewModel] instance
 * with the mode matching its route (ACCESSNAV-01, LIFE-01).
 */
enum class AccessRouteMode { STARTING, BOOTSTRAP }

@Immutable
sealed interface AccessRouteState {
    /** `Starting`: authentication has not yet been observed; no interaction. */
    data object Starting : AccessRouteState

    /** `Bootstrap`: session bootstrap in progress or failed, with a retry action. */
    data class Bootstrap(val isLoading: Boolean, val failed: Boolean) : AccessRouteState
}

sealed interface AccessRouteIntent {
    data object RetryBootstrap : AccessRouteIntent
}

/** Neither route emits a one-off effect: session transitions flow through the shared session. */
sealed interface AccessRouteEffect
