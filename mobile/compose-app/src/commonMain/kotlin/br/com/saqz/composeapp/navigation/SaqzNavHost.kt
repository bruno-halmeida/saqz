package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.ui.BootstrapAccessScreen
import br.com.saqz.access.ui.LoginRoot
import br.com.saqz.access.ui.NameCompletionRoot
import br.com.saqz.access.ui.PhoneCompletionRoot
import br.com.saqz.access.ui.VerificationRoot
import br.com.saqz.composeapp.shell.SaqzAppShell
import br.com.saqz.designsystem.component.SaqzLoadingState

// Legacy observable contract carried over from the product host: exactly one active
// destination host in the tree (rotation/recreation tests count this tag).
internal const val SaqzDestinationHostTag = "authenticated-access-destination"

/**
 * C1 entry point: the single Navigation3 [NavDisplay] over one acesso→shell back stack.
 * The access screens are feature-owned Roots from `:features:access` (each resolves its
 * own ViewModel through Koin); the only app-owned destination is the empty shell.
 *
 * The stack is never navigated by the UI — [reconcileAccessStack] derives it from the
 * authoritative session state, and it is always exactly one entry deep, so there is no
 * in-app back destination to hand to [NavDisplay].
 */
@Composable
internal fun SaqzNavHost(
    state: AccessUiState,
    onIntent: (AccessIntent) -> Unit,
    backStack: NavBackStack<NavKey> = rememberNavBackStack(
        saqzLocalNavConfiguration,
        AccessRoute.Starting,
    ),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.session) {
        reconcileAccessStack(backStack, state.session)
    }
    NavDisplay(
        backStack = backStack,
        onBack = {},
        entryProvider = entryProvider {
            entry<AccessRoute.Starting> { SaqzLoadingState() }
            entry<AccessRoute.Login> { LoginRoot() }
            entry<AccessRoute.Verification> { VerificationRoot() }
            entry<AccessRoute.NameCompletion> { NameCompletionRoot() }
            entry<AccessRoute.PhoneCompletion> { PhoneCompletionRoot() }
            entry<AccessRoute.Bootstrap> {
                BootstrapAccessScreen(
                    state = state.session,
                    onIntent = { onIntent(AccessIntent.Session(it)) },
                )
            }
            entry<SaqzShellDestination> {
                SaqzAppShell(onLogout = { onIntent(AccessIntent.ConfirmLogout) })
            }
        },
        modifier = modifier.testTag(SaqzDestinationHostTag),
    )
}

/**
 * Migrated from `:navigation`'s `reconcileAccessStack` (C3 kills that module) and extended
 * with `Ready` → the empty shell. Every session state canonicalizes the stack to its single
 * matching destination — with registration and password reset gone, Login has no user-driven
 * sub-navigation left, so the stack is never deeper than one entry. No-op when the stack
 * already equals the target, so it is safe on every state emission.
 */
internal fun reconcileAccessStack(stack: MutableList<NavKey>, session: SessionAccessState) {
    val target: List<NavKey> = listOf(session.toDestination())
    if (stack != target) {
        stack.clear()
        stack.addAll(target)
    }
}

private fun SessionAccessState.toDestination(): NavKey = when (this) {
    SessionAccessState.SignedOut -> AccessRoute.Login
    is SessionAccessState.AwaitingVerification -> AccessRoute.Verification
    is SessionAccessState.CompletingName -> AccessRoute.NameCompletion
    is SessionAccessState.CompletingPhone -> AccessRoute.PhoneCompletion
    SessionAccessState.Bootstrapping, SessionAccessState.BootstrapError -> AccessRoute.Bootstrap
    is SessionAccessState.Ready -> SaqzShellDestination
}
