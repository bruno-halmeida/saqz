package br.com.saqz.navigation.access

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.route.AccessRouteIntent
import br.com.saqz.access.presentation.route.AccessRouteMode
import br.com.saqz.access.presentation.route.AccessRouteState
import br.com.saqz.access.presentation.route.AccessRouteViewModel
import br.com.saqz.access.ui.BootstrapAccessScreen
import br.com.saqz.access.ui.LoginRoot
import br.com.saqz.access.ui.NameCompletionRoot
import br.com.saqz.access.ui.PasswordResetRoot
import br.com.saqz.access.ui.RegistrationRoot
import br.com.saqz.access.ui.VerificationRoot
import br.com.saqz.designsystem.component.SaqzLoadingState

/**
 * ACCESSNAV-01: installs Starting/Login/Registration/PasswordReset/Verification/
 * NameCompletion/Bootstrap into the shared product entry provider (MODNAV-01).
 *
 * Login, Registration, PasswordReset, Verification, and NameCompletion already have
 * dedicated per-route ViewModels and feature-owned Roots (design.md, Reconciliation
 * note) -- their entries wrap the existing `*Root()` composables unchanged and do not
 * recreate a ViewModel. Starting and Bootstrap have no dedicated ViewModel yet; each of
 * their entries obtains its own [AccessRouteViewModel] adapter instance (T11),
 * entry-scoped through [viewModel] (LIFE-01, LIFE-05). No entry here imports
 * Navigation Compose 3 UI or performs a Koin lookup -- `koinViewModel()` resolution for
 * the five existing Roots happens inside `:features:access` itself.
 */
fun EntryProviderScope<NavKey>.installAccessEntries(session: SessionAccessStateMachine) {
    entry<AccessRoute.Starting> {
        viewModel<AccessRouteViewModel>(
            initializer = { AccessRouteViewModel(AccessRouteMode.STARTING, session) },
        )
        SaqzLoadingState()
    }
    entry<AccessRoute.Login> { LoginRoot() }
    entry<AccessRoute.Registration> { RegistrationRoot() }
    entry<AccessRoute.PasswordReset> { PasswordResetRoot() }
    entry<AccessRoute.Verification> { VerificationRoot() }
    entry<AccessRoute.NameCompletion> { NameCompletionRoot() }
    entry<AccessRoute.Bootstrap> {
        val bootstrapViewModel = viewModel<AccessRouteViewModel>(
            initializer = { AccessRouteViewModel(AccessRouteMode.BOOTSTRAP, session) },
        )
        val state by bootstrapViewModel.state.collectAsStateWithLifecycle()
        val bootstrap = state as AccessRouteState.Bootstrap
        // Reuses the existing BootstrapAccessScreen (REG-01: same texts/testTag)
        // unchanged, driven by this entry's own AccessRouteViewModel projection
        // instead of the raw shared SessionAccessState union.
        BootstrapAccessScreen(bootstrap.toSessionAccessState()) {
            bootstrapViewModel.onIntent(AccessRouteIntent.RetryBootstrap)
        }
    }
}

private fun AccessRouteState.Bootstrap.toSessionAccessState(): SessionAccessState = when {
    isLoading -> SessionAccessState.Bootstrapping
    failed -> SessionAccessState.BootstrapError
    else -> SessionAccessState.SignedOut
}

/**
 * ACCESSNAV-03: reconciles [stack]'s root/top with the shared [session]/[authScreen]
 * source of truth. `Login`/`Registration`/`PasswordReset` are user-driven
 * sub-navigation while [SessionAccessState.SignedOut] is active: navigating away from
 * Login pushes exactly one additional entry, so system/TopBar back (ACCESSNAV-02)
 * always resolves back to Login. Every other session state canonicalizes the stack to
 * its single matching route. No-op when the stack already equals the target shape
 * (STATE-03 idempotency) -- safe to call on every recomposition/state emission.
 */
fun reconcileAccessStack(stack: MutableList<NavKey>, session: SessionAccessState, authScreen: AuthScreen) {
    val target: List<NavKey> = if (session == SessionAccessState.SignedOut) {
        val subRoute = authScreen.toSubRouteOrNull()
        if (subRoute == null) listOf(AccessRoute.Login) else listOf(AccessRoute.Login, subRoute)
    } else {
        listOf(session.toAccessRoute())
    }
    if (stack != target) {
        stack.clear()
        stack.addAll(target)
    }
}

/**
 * ACCESSNAV-04: WHEN the shared session reaches [SessionAccessState.Ready], the host
 * mode switches to `AUTHENTICATED` instead of pushing Groups onto the access stack.
 */
fun isAccessSession(session: SessionAccessState): Boolean = session !is SessionAccessState.Ready

private fun AuthScreen.toSubRouteOrNull(): AccessRoute? = when (this) {
    AuthScreen.LOGIN -> null
    AuthScreen.REGISTRATION -> AccessRoute.Registration
    AuthScreen.PASSWORD_RESET -> AccessRoute.PasswordReset
}

private fun SessionAccessState.toAccessRoute(): AccessRoute = when (this) {
    SessionAccessState.SignedOut -> AccessRoute.Login
    is SessionAccessState.AwaitingVerification -> AccessRoute.Verification
    is SessionAccessState.CompletingName -> AccessRoute.NameCompletion
    SessionAccessState.Bootstrapping, SessionAccessState.BootstrapError -> AccessRoute.Bootstrap
    is SessionAccessState.Ready -> AccessRoute.Bootstrap
}
