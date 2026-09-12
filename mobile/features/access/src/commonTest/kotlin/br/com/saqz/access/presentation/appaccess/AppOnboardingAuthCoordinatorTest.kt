package br.com.saqz.access.presentation.appaccess

import br.com.saqz.access.domain.appaccess.AppAccessError
import br.com.saqz.access.domain.appaccess.AppAccessGateway
import br.com.saqz.access.domain.appaccess.AppAccessSession
import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppOnboardingAuthCoordinatorTest {
    @Test
    fun `opening a newly issued link renews failure without an extra reset action`() = runTest {
        val fixture = fixture(this, current = { null }, resolving = { false })
        fixture.gateway.result = SaqzResult.Failure(AppAccessError.CodeInvalid)
        fixture.coordinator.onAuthObservation(AuthState.SignedOut)
        fixture.coordinator.redeem("expired-code")
        runCurrent()
        fixture.coordinator.redeem("expired-code")
        assertEquals(AppOnboardingAuthState.Failed(AppAccessError.CodeInvalid), fixture.coordinator.state.value)
        assertEquals(1, fixture.gateway.redeemCalls)
        fixture.gateway.result = SaqzResult.Success(AppAccessSession("custom-token", "owner-a", "owner-a", false))
        fixture.coordinator.redeem("new-web-code")
        runCurrent()
        assertEquals(AppOnboardingAuthState.SigningIn, fixture.coordinator.state.value)
        assertEquals(2, fixture.gateway.redeemCalls)
        assertEquals(1, fixture.auth.customTokenCalls)
    }

    @Test
    fun `redeem waits for first signed out observation before custom signin`() = runTest {
        val fixture = fixture(this, current = { null }, resolving = { false })
        fixture.coordinator.redeem("code-a")
        runCurrent()

        assertEquals(AppOnboardingAuthState.WaitingForSessionResolution, fixture.coordinator.state.value)
        assertEquals(0, fixture.auth.customTokenCalls)

        fixture.coordinator.onAuthObservation(AuthState.SignedOut)
        assertEquals(AppOnboardingAuthState.SigningIn, fixture.coordinator.state.value)
        assertEquals(1, fixture.auth.customTokenCalls)

        fixture.auth.complete(AuthResult.Success(nativeUser("owner-a")))
        fixture.coordinator.onAuthenticatedSession(fixture.session)
        assertEquals(AppOnboardingAuthState.Completed(false), fixture.coordinator.state.value)
    }

    @Test
    fun `same backend owner completes without needless native signin`() = runTest {
        val owner = accessSession("owner-a")
        val fixture = fixture(this, current = { owner }, resolving = { false })
        fixture.coordinator.onAuthObservation(AuthState.SignedIn(nativeUser("owner-a")))
        fixture.coordinator.redeem("code-a")
        runCurrent()

        assertEquals(AppOnboardingAuthState.Completed(false), fixture.coordinator.state.value)
        assertEquals(0, fixture.auth.customTokenCalls)
    }

    @Test
    fun `replacement requires stable current owner and never wipes newer account`() = runTest {
        var current = accessSession("owner-b")
        val fixture = fixture(this, current = { current }, resolving = { false })
        fixture.coordinator.onAuthObservation(AuthState.SignedIn(nativeUser("owner-b")))
        fixture.coordinator.redeem("code-a")
        runCurrent()
        assertEquals(AppOnboardingAuthState.NeedsAccountConfirmation("owner-a"), fixture.coordinator.state.value)

        current = accessSession("owner-c")
        fixture.coordinator.confirmAccountReplacement()

        assertEquals(AppOnboardingAuthState.Failed(AppAccessError.IdentityMismatch), fixture.coordinator.state.value)
        assertEquals(0, fixture.auth.customTokenCalls)
        assertEquals("owner-c", current.user.id)
    }

    @Test
    fun `new code can renew a failed attempt while duplicate code is deduplicated`() = runTest {
        val fixture = fixture(this, current = { null }, resolving = { false })
        fixture.gateway.result = SaqzResult.Failure(AppAccessError.CodeInvalid)
        fixture.coordinator.onAuthObservation(AuthState.SignedOut)
        fixture.coordinator.redeem("expired-code")
        runCurrent()
        assertEquals(AppOnboardingAuthState.Failed(AppAccessError.CodeInvalid), fixture.coordinator.state.value)
        assertEquals(1, fixture.gateway.redeemCalls)

        fixture.coordinator.reset()
        fixture.coordinator.redeem("expired-code")
        runCurrent()
        assertEquals(1, fixture.gateway.redeemCalls)
        assertEquals(AppOnboardingAuthState.Idle, fixture.coordinator.state.value)

        fixture.gateway.result = SaqzResult.Success(AppAccessSession("custom-token", "owner-a", "owner-a", false))
        fixture.coordinator.redeem("renewed-code")
        runCurrent()
        assertEquals(2, fixture.gateway.redeemCalls)
        assertEquals(1, fixture.auth.customTokenCalls)
    }

    @Test
    fun `unresolved bootstrap stays waiting until the session machine resolves`() = runTest {
        var resolving = true
        val fixture = fixture(this, current = { null }, resolving = { resolving })
        fixture.coordinator.redeem("code-a")
        runCurrent()
        fixture.coordinator.onAuthObservation(AuthState.SignedOut)
        assertEquals(AppOnboardingAuthState.WaitingForSessionResolution, fixture.coordinator.state.value)
        assertEquals(0, fixture.auth.customTokenCalls)

        resolving = false
        fixture.coordinator.onSessionResolution(null, resolving = false)
        assertEquals(AppOnboardingAuthState.SigningIn, fixture.coordinator.state.value)
        assertEquals(1, fixture.auth.customTokenCalls)
    }

    private fun fixture(
        scope: CoroutineScope,
        current: () -> AccessSession?,
        resolving: () -> Boolean,
    ): Fixture {
        val auth = FakeAuth()
        val gateway = FakeGateway()
        val session = accessSession("owner-a")
        val redeemed = AppAccessSession("custom-token", "owner-a", "owner-a", false)
        return Fixture(
            coordinator = AppOnboardingAuthCoordinator(auth, gateway, scope, current, resolving),
            auth = auth,
            gateway = gateway.apply { result = SaqzResult.Success(redeemed) },
            session = session,
        )
    }

    private fun nativeUser(subject: String) = NativeUser(subject, "$subject@example.test", true, subject)

    private fun accessSession(id: String) = AccessSession(
        user = AccessUser(id = id, email = "$id@example.test", displayName = id),
        memberships = emptyList(),
    )

    private data class Fixture(
        val coordinator: AppOnboardingAuthCoordinator,
        val auth: FakeAuth,
        val gateway: FakeGateway,
        val session: AccessSession,
    )

    private class FakeGateway : AppAccessGateway {
        var redeemCalls = 0
        var result: SaqzResult<AppAccessSession, AppAccessError> = SaqzResult.Failure(AppAccessError.CodeInvalid)

        override suspend fun redeem(code: String): SaqzResult<AppAccessSession, AppAccessError> {
            redeemCalls++
            return result
        }
    }

    private class FakeAuth : NativeAuthPort {
        var customTokenCalls = 0
        private var callback: AuthCallback? = null

        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable {
            override fun cancel() = Unit
        }

        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = Unit
        override fun signInWithCustomToken(customToken: String, done: AuthCallback) {
            customTokenCalls++
            callback = done
        }
        override fun sendVerification(done: ResultCallback) = Unit
        override fun reloadUser(done: AuthCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit
        override fun signOut(done: ResultCallback) = done.complete(OperationResult.Success)

        fun complete(result: AuthResult) {
            val current = callback ?: error("custom token signin was not started")
            callback = null
            current.complete(result)
        }
    }
}
