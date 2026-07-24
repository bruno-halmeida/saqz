package br.com.saqz.navigation.access

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.route.AccessRouteIntent
import br.com.saqz.access.presentation.route.AccessRouteMode
import br.com.saqz.access.presentation.route.AccessRouteState
import br.com.saqz.access.presentation.route.AccessRouteViewModel
import br.com.saqz.access.ui.BootstrapAccessScreen
import br.com.saqz.access.ui.LoginRoot
import br.com.saqz.access.ui.NameCompletionRoot
import br.com.saqz.access.ui.PhoneCompletionRoot
import br.com.saqz.access.ui.VerificationRoot
import br.com.saqz.designsystem.component.SaqzLoadingState

/**
 * ACCESSNAV-01: installs Starting/Login/Verification/NameCompletion/PhoneCompletion/
 * Bootstrap into the shared product entry provider (MODNAV-01).
 *
 * Login, Verification, NameCompletion and PhoneCompletion already have dedicated
 * per-route ViewModels and feature-owned Roots -- their entries wrap the existing
 * `*Root()` composables unchanged and do not recreate a ViewModel. Starting and Bootstrap have no dedicated ViewModel yet; each of
 * their entries obtains its own [AccessRouteViewModel] adapter instance (T11),
 * entry-scoped through [viewModel] (LIFE-01, LIFE-05). No entry here imports
 * Navigation Compose 3 UI or performs a Koin lookup -- `koinViewModel()` resolution for
 * the existing Roots happens inside `:features:access` itself.
 */
fun EntryProviderScope<NavKey>.installAccessEntries(session: SessionAccessStateMachine) {
    entry<AccessRoute.Starting> {
        viewModel<AccessRouteViewModel>(
            initializer = { AccessRouteViewModel(AccessRouteMode.STARTING, session) },
        )
        SaqzLoadingState()
    }
    entry<AccessRoute.Login> { LoginRoot() }
    entry<AccessRoute.Verification> { VerificationRoot() }
    entry<AccessRoute.NameCompletion> { NameCompletionRoot() }
    entry<AccessRoute.PhoneCompletion> { PhoneCompletionRoot() }
    entry<AccessRoute.Bootstrap> {
        val bootstrapViewModel = viewModel<AccessRouteViewModel>(
            initializer = { AccessRouteViewModel(AccessRouteMode.BOOTSTRAP, session) },
        )
        val state by bootstrapViewModel.state.collectAsStateWithLifecycle()
        val bootstrap = state as AccessRouteState.Bootstrap
        // Reuses the existing BootstrapAccessScreen (REG-01: same texts/testTag)
        // unchanged, driven by this entry's own AccessRouteViewModel projection
        // instead of the raw shared SessionAccessState union.
        BootstrapAccessScreen(
            bootstrap.toSessionAccessState(),
            onIntent = { bootstrapViewModel.onIntent(AccessRouteIntent.RetryBootstrap) },
        )
    }
}

private fun AccessRouteState.Bootstrap.toSessionAccessState(): SessionAccessState = when {
    isLoading -> SessionAccessState.Bootstrapping
    failed -> SessionAccessState.BootstrapError
    else -> SessionAccessState.SignedOut
}

/**
 * ACCESSNAV-03: reconciles [stack]'s root/top with the shared [session] source of truth.
 * Every session state canonicalizes the stack to its single matching route -- with
 * registration and password reset gone, Login has no user-driven sub-navigation left.
 * No-op when the stack already equals the target shape (STATE-03 idempotency) -- safe to
 * call on every recomposition/state emission.
 */
fun reconcileAccessStack(stack: MutableList<NavKey>, session: SessionAccessState) {
    val target: List<NavKey> = listOf(session.toAccessRoute())
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

private fun SessionAccessState.toAccessRoute(): AccessRoute = when (this) {
    SessionAccessState.SignedOut -> AccessRoute.Login
    is SessionAccessState.AwaitingVerification -> AccessRoute.Verification
    is SessionAccessState.CompletingName -> AccessRoute.NameCompletion
    is SessionAccessState.CompletingPhone -> AccessRoute.PhoneCompletion
    SessionAccessState.Bootstrapping, SessionAccessState.BootstrapError -> AccessRoute.Bootstrap
    is SessionAccessState.Ready -> AccessRoute.Bootstrap
}
