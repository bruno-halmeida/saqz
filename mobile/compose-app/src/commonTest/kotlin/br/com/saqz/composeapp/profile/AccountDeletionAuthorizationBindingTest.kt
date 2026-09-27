package br.com.saqz.composeapp.profile

import br.com.saqz.access.domain.port.*
import br.com.saqz.profile.domain.*
import kotlin.test.*

class AccountDeletionAuthorizationBindingTest {
    @Test fun `successful authorization includes fresh token and provider revocation`() {
        val auth = Auth()
        val binding = AccountDeletionAuthorizationBinding(auth) { DeletionSession("profile", "session-1") }
        var result: AccountDeletionAuthorizationResult? = null
        binding.authorize("profile", AccountDeletionMethod.GOOGLE, "") { result = it }
        auth.complete()
        assertEquals(AccountDeletionAuthorizationResult.AUTHORIZED, result)
        assertEquals(listOf("fresh-token", "revoke:firebase-user"), auth.events)
    }

    @Test fun `session replacement during native dialog rejects deletion without revoking consent`() {
        val auth = Auth()
        var session = DeletionSession("profile", "session-1")
        val binding = AccountDeletionAuthorizationBinding(auth) { session }
        var result: AccountDeletionAuthorizationResult? = null
        binding.authorize("profile", AccountDeletionMethod.GOOGLE, "") { result = it }
        session = DeletionSession("another-profile", "session-2")
        auth.complete()
        assertEquals(AccountDeletionAuthorizationResult.REJECTED, result)
        assertTrue(auth.events.isEmpty())
    }

    @Test fun `provider failure and token failure never authorize deletion`() {
        for (failToken in listOf(true, false)) {
            val auth = Auth().apply { tokenFails = failToken; revokeFails = !failToken }
            val binding = AccountDeletionAuthorizationBinding(auth) { DeletionSession("profile", "session") }
            var result: AccountDeletionAuthorizationResult? = null
            binding.authorize("profile", AccountDeletionMethod.APPLE, "") { result = it }
            auth.complete()
            assertEquals(AccountDeletionAuthorizationResult.REJECTED, result)
            if (failToken) assertEquals(listOf("fresh-token"), auth.events)
        }
    }

    @Test fun `leaving screen cancels continuation before revoking consent`() {
        val auth = Auth()
        val binding = AccountDeletionAuthorizationBinding(auth) { DeletionSession("profile", "session") }
        var result: AccountDeletionAuthorizationResult? = null
        val cancellation = binding.authorize("profile", AccountDeletionMethod.GOOGLE, "") { result = it }
        cancellation.cancel()
        auth.complete()
        assertNull(result)
        assertTrue(auth.events.isEmpty())
    }

    private class Auth : NativeAuthPort {
        lateinit var callback: AuthCallback
        var tokenFails = false
        var revokeFails = false
        val events = mutableListOf<String>()
        fun complete() = callback.complete(AuthResult.Success(NativeUser("firebase-user", null, true, "Ana")))
        override fun reauthenticate(request: NativeReauthentication, done: AuthCallback) { callback = done }
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) {
            assertTrue(forceRefresh)
            events += "fresh-token"
            done.complete(if (tokenFails) TokenResult.Failure(NativeFailureCode.NETWORK_UNAVAILABLE) else TokenResult.Success("fresh-token"))
        }
        override fun prepareAccountDeletion(subject: String, done: ResultCallback) {
            events += "revoke:$subject"
            done.complete(if (revokeFails) OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE) else OperationResult.Success)
        }
        override fun observe(listener: AuthStateListener): Cancelable = error("unused")
        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = error("unused")
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = error("unused")
        override fun signInWithGoogle(done: AuthCallback) = error("unused")
        override fun sendVerification(done: ResultCallback) = error("unused")
        override fun reloadUser(done: AuthCallback) = error("unused")
        override fun updateDisplayName(name: String, done: AuthCallback) = error("unused")
        override fun signOut(done: ResultCallback) = error("unused")
    }
}
