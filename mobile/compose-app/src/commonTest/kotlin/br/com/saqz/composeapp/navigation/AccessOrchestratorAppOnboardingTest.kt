package br.com.saqz.composeapp.navigation

import br.com.saqz.access.domain.appaccess.AppAccessGateway
import br.com.saqz.access.domain.appaccess.AppAccessSession
import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeLinkPort
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.port.ValueResult
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.access.presentation.appaccess.AppOnboardingAuthCoordinator
import br.com.saqz.access.presentation.appaccess.SerializedNativeAuthPort
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AccessOrchestratorAppOnboardingTest {
    @Test
    fun `runtime waits for observer then reaches actual ready session without duplicate signin`() = runTest {
        val provider = FakeProvider()
        val auth = SerializedNativeAuthPort(provider)
        val links = FakeLinks()
        val sessionValue = accessSession("backend-a")
        val sessionGateway = ImmediateSession(sessionValue)
        val session = SessionAccessStateMachine(auth, ImmediateLocalState, sessionGateway, backgroundScope)
        val authentication = AuthenticationStateMachine(auth) { transition ->
            session.onIntent(SessionIntent.Accept(transition))
        }
        val appGateway = FakeAppAccessGateway().apply { result = SaqzResult.Success(redeemed("backend-a")) }
        val appOnboarding = AppOnboardingAuthCoordinator(
            auth = auth,
            gateway = appGateway,
            scope = backgroundScope,
            currentSession = {
                when (val state = session.state.value) {
                    is SessionAccessState.Ready -> state.session
                    is SessionAccessState.CompletingIdentity -> state.session
                    else -> null
                }
            },
            sessionResolving = {
                when (val state = session.state.value) {
                    SessionAccessState.Bootstrapping,
                    SessionAccessState.BootstrapError,
                    -> true
                    is SessionAccessState.CompletingIdentity -> state.session == null
                    else -> false
                }
            },
        )
        val runtime = AccessOrchestrator(auth, authentication, session, links, appOnboarding, backgroundScope)
        runCurrent()
        runtime.onIntent(AccessRuntimeIntent.Start)
        links.emit("opaque-code")
        runCurrent()

        assertEquals(false, runtime.authObservedState.value)
        assertEquals(br.com.saqz.access.presentation.appaccess.AppOnboardingAuthState.WaitingForSessionResolution, runtime.appOnboardingState.value)
        assertEquals(0, provider.customTokenCalls)

        provider.emitState(AuthState.SignedOut)
        assertEquals(true, runtime.authObservedState.value)
        assertEquals(1, provider.customTokenCalls)

        // Callback first, observer second: both must settle once and the session machine
        // must be the source of the final Ready assertion.
        provider.completeCustom(AuthResult.Success(nativeUser("firebase-a")))
        assertEquals(br.com.saqz.access.presentation.appaccess.AppOnboardingAuthState.SigningIn, runtime.appOnboardingState.value)
        provider.emitState(AuthState.SignedIn(nativeUser("firebase-a")))
        runCurrent()

        val ready = assertIs<SessionAccessState.Ready>(runtime.sessionState.value)
        assertEquals("backend-a", ready.session.user.id)
        assertEquals("firebase-a", provider.currentUser?.subject)
        assertEquals(
            br.com.saqz.access.presentation.appaccess.AppOnboardingAuthState.Completed(false),
            runtime.appOnboardingState.value,
        )

        appGateway.result = SaqzResult.Success(redeemed("backend-b"))
        sessionGateway.value = accessSession("backend-b")
        appOnboarding.reset()
        links.emit("opaque-code-b")
        runCurrent()
        assertEquals(
            br.com.saqz.access.presentation.appaccess.AppOnboardingAuthState.NeedsAccountConfirmation("backend-b"),
            runtime.appOnboardingState.value,
        )
        appOnboarding.confirmAccountReplacement()
        assertEquals(br.com.saqz.access.presentation.appaccess.AppOnboardingAuthState.SigningIn, runtime.appOnboardingState.value)
        provider.completeSignOut()
        assertEquals(2, provider.customTokenCalls)
        provider.completeCustom(AuthResult.Success(nativeUser("firebase-b")))
        provider.emitState(AuthState.SignedIn(nativeUser("firebase-b")))
        provider.emitState(AuthState.SignedIn(nativeUser("firebase-a")), mutateCurrentUser = false)
        runCurrent()

        val readyB = assertIs<SessionAccessState.Ready>(runtime.sessionState.value)
        assertEquals("backend-b", readyB.session.user.id)
        assertEquals("firebase-b", provider.currentUser?.subject)

        runtime.onIntent(AccessRuntimeIntent.Session(SessionIntent.Logout))
        provider.completeSignOut()
        runCurrent()
        assertEquals(SessionAccessState.SignedOut, runtime.sessionState.value)
        assertEquals(null, provider.currentUser)
        provider.emitState(AuthState.SignedIn(nativeUser("firebase-a")), mutateCurrentUser = false)
        assertEquals(SessionAccessState.SignedOut, runtime.sessionState.value)
        assertEquals(null, provider.currentUser)
    }

    @Test
    fun `runtime logout during unresolved custom signin prevents signin and late identity`() = runTest {
        val provider = FakeProvider()
        val auth = SerializedNativeAuthPort(provider)
        val links = FakeLinks()
        val session = SessionAccessStateMachine(auth, ImmediateLocalState, ImmediateSession(accessSession("backend-a")), backgroundScope)
        val authentication = AuthenticationStateMachine(auth) { transition ->
            session.onIntent(SessionIntent.Accept(transition))
        }
        val appGateway = FakeAppAccessGateway().apply { result = SaqzResult.Success(redeemed("backend-a")) }
        val appOnboarding = AppOnboardingAuthCoordinator(
            auth = auth,
            gateway = appGateway,
            scope = backgroundScope,
            currentSession = { (session.state.value as? SessionAccessState.Ready)?.session },
            sessionResolving = { session.state.value is SessionAccessState.Bootstrapping },
        )
        val runtime = AccessOrchestrator(auth, authentication, session, links, appOnboarding, backgroundScope)
        runtime.onIntent(AccessRuntimeIntent.Start)
        provider.emitState(AuthState.SignedOut)
        links.emit("opaque-code")
        runCurrent()
        assertEquals(1, provider.customTokenCalls)

        runtime.onIntent(AccessRuntimeIntent.Session(SessionIntent.Logout))
        provider.completeCustom(AuthResult.Success(nativeUser("firebase-a")))
        provider.completeSignOut()
        runCurrent()

        assertEquals(SessionAccessState.SignedOut, runtime.sessionState.value)
        assertEquals(null, provider.currentUser)
        assertEquals(1, provider.customTokenCalls)
        provider.emitState(AuthState.SignedIn(nativeUser("firebase-a")), mutateCurrentUser = false)
        assertEquals(SessionAccessState.SignedOut, runtime.sessionState.value)
        assertEquals(null, provider.currentUser)
    }

    @Test
    fun `runtime supersedes unresolved custom signin with B and keeps B authoritative`() = runTest {
        val provider = FakeProvider()
        val auth = SerializedNativeAuthPort(provider)
        val links = FakeLinks()
        val sessionGateway = ImmediateSession(accessSession("backend-b"))
        val session = SessionAccessStateMachine(auth, ImmediateLocalState, sessionGateway, backgroundScope)
        val authentication = AuthenticationStateMachine(auth) { transition ->
            session.onIntent(SessionIntent.Accept(transition))
        }
        val appGateway = FakeAppAccessGateway().apply { result = SaqzResult.Success(redeemed("backend-a")) }
        val appOnboarding = AppOnboardingAuthCoordinator(
            auth = auth,
            gateway = appGateway,
            scope = backgroundScope,
            currentSession = { (session.state.value as? SessionAccessState.Ready)?.session },
            sessionResolving = { session.state.value is SessionAccessState.Bootstrapping },
        )
        val runtime = AccessOrchestrator(auth, authentication, session, links, appOnboarding, backgroundScope)
        runtime.onIntent(AccessRuntimeIntent.Start)
        provider.emitState(AuthState.SignedOut)
        links.emit("opaque-code-a")
        runCurrent()
        assertEquals(1, provider.customTokenCalls)

        var bCallback = 0
        auth.signInWithCustomToken("custom-token-b", object : AuthCallback {
            override fun complete(result: AuthResult) { bCallback++ }
        })
        provider.completeCustom(AuthResult.Success(nativeUser("firebase-a")))
        provider.completeSignOut()
        assertEquals(2, provider.customTokenCalls)
        provider.completeCustom(AuthResult.Success(nativeUser("firebase-b")))
        provider.emitState(AuthState.SignedIn(nativeUser("firebase-b")))
        runCurrent()

        val ready = assertIs<SessionAccessState.Ready>(runtime.sessionState.value)
        assertEquals("backend-b", ready.session.user.id)
        assertEquals("firebase-b", provider.currentUser?.subject)
        assertEquals(1, bCallback)
        provider.emitState(AuthState.SignedIn(nativeUser("firebase-a")), mutateCurrentUser = false)
        assertEquals("backend-b", assertIs<SessionAccessState.Ready>(runtime.sessionState.value).session.user.id)
        assertEquals("firebase-b", provider.currentUser?.subject)
    }

    private fun nativeUser(subject: String) = NativeUser(subject, "$subject@example.test", true, subject)

    private fun redeemed(id: String) = AppAccessSession("custom-token-$id", id, id, onboardingCompleted = false)

    private fun accessSession(id: String) = AccessSession(
        user = AccessUser(id = id, email = "$id@example.test", displayName = id),
        memberships = emptyList(),
    )

    private class FakeAppAccessGateway : AppAccessGateway {
        var result: SaqzResult<AppAccessSession, br.com.saqz.access.domain.appaccess.AppAccessError> =
            SaqzResult.Success(AppAccessSession("custom-token-backend-a", "backend-a", "backend-a", false))

        override suspend fun redeem(code: String) = result
    }

    private class FakeLinks : NativeLinkPort {
        private var onboarding: br.com.saqz.access.domain.port.AppOnboardingCodeListener? = null

        override fun start(listener: br.com.saqz.access.domain.port.InviteCodeListener): Cancelable = NoopCancelable

        override fun startAppOnboarding(listener: br.com.saqz.access.domain.port.AppOnboardingCodeListener): Cancelable {
            onboarding = listener
            return NoopCancelable
        }

        fun emit(code: String) = onboarding?.onAppOnboardingCode(code)
    }

    private class FakeProvider : NativeAuthPort {
        private var observer: AuthStateListener? = null
        private var customCallback: AuthCallback? = null
        private var signOutCallback: ResultCallback? = null
        var customTokenCalls = 0
        var currentUser: NativeUser? = null

        override fun observe(listener: AuthStateListener): Cancelable {
            observer = listener
            return NoopCancelable
        }

        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = Unit
        override fun signInWithCustomToken(customToken: String, done: AuthCallback) {
            customTokenCalls++
            customCallback = done
        }
        override fun sendVerification(done: ResultCallback) = Unit
        override fun reloadUser(done: AuthCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = done.complete(
            TokenResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE),
        )
        fun emitState(state: AuthState, mutateCurrentUser: Boolean = true) {
            if (mutateCurrentUser) currentUser = (state as? AuthState.SignedIn)?.user
            observer?.onStateChanged(state)
        }

        fun completeCustom(result: AuthResult) {
            val callback = customCallback ?: error("custom signin was not started")
            customCallback = null
            if (result is AuthResult.Success) currentUser = result.user
            callback.complete(result)
        }

        override fun signOut(done: ResultCallback) {
            signOutCallback = done
        }

        fun completeSignOut() {
            currentUser = null
            observer?.onStateChanged(AuthState.SignedOut)
            val callback = signOutCallback ?: error("sign out was not started")
            signOutCallback = null
            callback.complete(OperationResult.Success)
        }
    }

    private object ImmediateLocalState : LocalAccessStatePort {
        override fun readSelectedGroupId(done: ValueCallback) = done.complete(ValueResult.Success(null))
        override fun writeSelectedGroupId(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
        override fun readPendingInvite(done: ValueCallback) = done.complete(ValueResult.Success(null))
        override fun writePendingInvite(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
    }

    private class ImmediateSession(var value: AccessSession) : SessionGateway {
        override suspend fun bootstrap() = SaqzResult.Success(value)
        override suspend fun completeProfile(phone: String, displayName: String?) = SaqzResult.Success(value)
        override suspend fun uploadPhoto(bytes: ByteArray, mediaType: String) = SaqzResult.Success(Unit)
    }

    private object NoopCancelable : Cancelable {
        override fun cancel() = Unit
    }
}
