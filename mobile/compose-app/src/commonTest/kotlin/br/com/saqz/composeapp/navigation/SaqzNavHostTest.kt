package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.SessionAccessState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The C1 session gate: every [SessionAccessState] resolves to exactly one destination, and
 * `Ready` is the only one that reaches the shell. Exhaustive by construction — a new state
 * fails `toDestination`'s `when` at compile time and shows up missing here.
 */
class SaqzNavHostTest {

    private fun stackFor(
        session: SessionAccessState,
        authScreen: AuthScreen = AuthScreen.LOGIN,
    ): List<NavKey> = mutableListOf<NavKey>(AccessRoute.Starting)
        .also { reconcileAccessStack(it, session, authScreen) }

    @Test
    fun signedOutRoutesToLogin() {
        assertEquals(listOf(AccessRoute.Login), stackFor(SessionAccessState.SignedOut))
    }

    @Test
    fun awaitingVerificationRoutesToVerification() {
        assertEquals(
            listOf(AccessRoute.Verification),
            stackFor(SessionAccessState.AwaitingVerification(user)),
        )
    }

    @Test
    fun completingNameRoutesToNameCompletion() {
        assertEquals(
            listOf(AccessRoute.NameCompletion),
            stackFor(SessionAccessState.CompletingName(user)),
        )
    }

    @Test
    fun completingPhoneRoutesToPhoneCompletion() {
        assertEquals(
            listOf(AccessRoute.PhoneCompletion),
            stackFor(SessionAccessState.CompletingPhone(session)),
        )
    }

    @Test
    fun bootstrapStatesRouteToBootstrap() {
        assertEquals(listOf(AccessRoute.Bootstrap), stackFor(SessionAccessState.Bootstrapping))
        assertEquals(listOf(AccessRoute.Bootstrap), stackFor(SessionAccessState.BootstrapError))
    }

    @Test
    fun readyRoutesToTheEmptyShell() {
        assertEquals(listOf(SaqzShellDestination), stackFor(SessionAccessState.Ready(session)))
    }

    @Test
    fun signedOutSubRoutesStackOnTopOfLogin() {
        assertEquals(
            listOf(AccessRoute.Login, AccessRoute.Registration),
            stackFor(SessionAccessState.SignedOut, AuthScreen.REGISTRATION),
        )
        assertEquals(
            listOf(AccessRoute.Login, AccessRoute.PasswordReset),
            stackFor(SessionAccessState.SignedOut, AuthScreen.PASSWORD_RESET),
        )
    }

    @Test
    fun authScreenIsIgnoredOnceSignedIn() {
        // A stale REGISTRATION screen must not leak a sub-route into an authenticated stack.
        assertEquals(
            listOf(SaqzShellDestination),
            stackFor(SessionAccessState.Ready(session), AuthScreen.REGISTRATION),
        )
    }

    @Test
    fun reconcilingAnAlreadyMatchingStackIsANoOp() {
        val stack = mutableListOf<NavKey>(AccessRoute.Login)
        reconcileAccessStack(stack, SessionAccessState.SignedOut, AuthScreen.LOGIN)
        reconcileAccessStack(stack, SessionAccessState.SignedOut, AuthScreen.LOGIN)
        assertEquals(listOf<NavKey>(AccessRoute.Login), stack)
    }

    private companion object {
        val user = NativeUser(
            subject = "user-1",
            email = "atleta@example.test",
            emailVerified = true,
            displayName = "Atleta",
        )
        val session = AccessSession(
            user = AccessUser(
                id = "user-1",
                email = "atleta@example.test",
                displayName = "Atleta",
            ),
            memberships = emptyList(),
        )
    }
}
