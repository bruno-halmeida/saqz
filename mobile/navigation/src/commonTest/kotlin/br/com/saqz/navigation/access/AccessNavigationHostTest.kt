package br.com.saqz.navigation.access

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.session.AccessError
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ACCESSNAV-01..04: entry inventory (never composed, so no Koin/composition needed)
 * and the pure access-stack reconciliation/mode-switch policy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccessNavigationHostTest {

    private fun fakeSession(): SessionAccessStateMachine = SessionAccessStateMachine(
        auth = NoopAuthPort(),
        localState = NoopLocalAccessState(),
        session = NoopSessionGateway(),
        scope = CoroutineScope(UnconfinedTestDispatcher()),
    )

    @Test
    fun installsAllSevenAccessRoutesAsDistinctEntries() {
        val provider = entryProvider<NavKey> { installAccessEntries(fakeSession()) }
        val keys: List<NavKey> = listOf(
            AccessRoute.Starting,
            AccessRoute.Login,
            AccessRoute.Registration,
            AccessRoute.PasswordReset,
            AccessRoute.Verification,
            AccessRoute.NameCompletion,
            AccessRoute.Bootstrap,
        )

        val entries: List<NavEntry<NavKey>> = keys.map(provider)

        assertEquals(keys.size, entries.map { it.contentKey }.toSet().size, "every route must map to a distinct entry")
    }

    @Test
    fun signedOutAtLoginCanonicalizesToSingleLoginRoot() {
        val stack = mutableListOf<NavKey>(AccessRoute.Starting)

        reconcileAccessStack(stack, SessionAccessState.SignedOut, AuthScreen.LOGIN)

        assertEquals(listOf<NavKey>(AccessRoute.Login), stack)
    }

    @Test
    fun signedOutAtRegistrationPushesOnTopOfLogin() {
        val stack = mutableListOf<NavKey>(AccessRoute.Login)

        reconcileAccessStack(stack, SessionAccessState.SignedOut, AuthScreen.REGISTRATION)

        assertEquals(listOf<NavKey>(AccessRoute.Login, AccessRoute.Registration), stack)
    }

    @Test
    fun signedOutAtPasswordResetPushesOnTopOfLogin() {
        val stack = mutableListOf<NavKey>(AccessRoute.Login)

        reconcileAccessStack(stack, SessionAccessState.SignedOut, AuthScreen.PASSWORD_RESET)

        assertEquals(listOf<NavKey>(AccessRoute.Login, AccessRoute.PasswordReset), stack)
    }

    @Test
    fun backFromRegistrationResolvesToLogin() {
        // ACCESSNAV-02: the TopBar/system back handler pops the last entry; the
        // auth screen change that follows re-canonicalizes to the single Login root.
        val stack = mutableListOf<NavKey>(AccessRoute.Login, AccessRoute.Registration)
        stack.removeAt(stack.lastIndex)

        reconcileAccessStack(stack, SessionAccessState.SignedOut, AuthScreen.LOGIN)

        assertEquals(listOf<NavKey>(AccessRoute.Login), stack)
    }

    @Test
    fun reconciliationIsIdempotentWhenStackAlreadyMatches() {
        val stack = mutableListOf<NavKey>(AccessRoute.Login, AccessRoute.Registration)
        val reference = stack

        reconcileAccessStack(stack, SessionAccessState.SignedOut, AuthScreen.REGISTRATION)

        assertTrue(reference === stack, "no-op must not replace the stack instance")
        assertEquals(listOf<NavKey>(AccessRoute.Login, AccessRoute.Registration), stack)
    }

    @Test
    fun otherSessionStatesCanonicalizeToTheirSingleMatchingRoute() {
        val awaitingVerification = mutableListOf<NavKey>(AccessRoute.Login, AccessRoute.Registration)
        reconcileAccessStack(
            awaitingVerification,
            SessionAccessState.AwaitingVerification(unverifiedUser()),
            AuthScreen.LOGIN,
        )
        assertEquals(listOf<NavKey>(AccessRoute.Verification), awaitingVerification)

        val completingName = mutableListOf<NavKey>(AccessRoute.Login)
        reconcileAccessStack(completingName, SessionAccessState.CompletingName(unverifiedUser()), AuthScreen.LOGIN)
        assertEquals(listOf<NavKey>(AccessRoute.NameCompletion), completingName)

        val bootstrapping = mutableListOf<NavKey>(AccessRoute.Login)
        reconcileAccessStack(bootstrapping, SessionAccessState.Bootstrapping, AuthScreen.LOGIN)
        assertEquals(listOf<NavKey>(AccessRoute.Bootstrap), bootstrapping)

        val bootstrapError = mutableListOf<NavKey>(AccessRoute.Bootstrap)
        reconcileAccessStack(bootstrapError, SessionAccessState.BootstrapError, AuthScreen.LOGIN)
        assertEquals(listOf<NavKey>(AccessRoute.Bootstrap), bootstrapError)
    }

    @Test
    fun readySessionIsNotAccessMode() {
        assertFalse(isAccessSession(SessionAccessState.Ready(session = accessSession())))
    }

    @Test
    fun everyNonReadySessionStateIsAccessMode() {
        assertTrue(isAccessSession(SessionAccessState.SignedOut))
        assertTrue(isAccessSession(SessionAccessState.Bootstrapping))
        assertTrue(isAccessSession(SessionAccessState.BootstrapError))
        assertTrue(isAccessSession(SessionAccessState.AwaitingVerification(unverifiedUser())))
        assertTrue(isAccessSession(SessionAccessState.CompletingName(unverifiedUser())))
    }

    private fun unverifiedUser() = br.com.saqz.access.domain.port.NativeUser(
        subject = "user-1",
        email = "user@example.com",
        emailVerified = false,
        displayName = null,
    )

    private fun accessSession(): AccessSession = AccessSession(
        user = br.com.saqz.access.domain.session.AccessUser(id = "user-1", email = "user@example.com", displayName = "User"),
        memberships = emptyList(),
    )

    private class NoopAuthPort : NativeAuthPort {
        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable {
            override fun cancel() = Unit
        }
        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = Unit
        override fun sendVerification(done: ResultCallback) = Unit
        override fun reloadUser(done: AuthCallback) = Unit
        override fun sendPasswordReset(email: String, done: ResultCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit
        override fun signOut(done: ResultCallback) = Unit
    }

    private class NoopLocalAccessState : LocalAccessStatePort {
        override fun readSelectedGroupId(done: ValueCallback) = Unit
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) = Unit
        override fun readPendingInvite(done: ValueCallback) = Unit
        override fun writePendingInvite(value: String?, done: ResultCallback) = Unit
    }

    private class NoopSessionGateway : SessionGateway {
        override suspend fun bootstrap(): SaqzResult<AccessSession, AccessError> =
            SaqzResult.Failure(AccessError.Unauthenticated)

        override suspend fun completeProfile(
            phone: String,
            displayName: String?,
        ): SaqzResult<AccessSession, AccessError> = SaqzResult.Failure(AccessError.Unauthenticated)
    }
}
