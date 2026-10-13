package br.com.saqz.webapp.access

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import kotlinx.coroutines.CoroutineScope

// STUB: substituir pela implementação sobre o Firebase Auth JS (saqz-bridge.js).
internal class WebAuthAdapter(private val scope: CoroutineScope) : NativeAuthPort {
    override fun observe(listener: AuthStateListener): Cancelable {
        listener.onStateChanged(AuthState.SignedOut)
        return object : Cancelable { override fun cancel() = Unit }
    }

    override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = unavailable(done)
    override fun signInWithPassword(email: String, password: String, done: AuthCallback) = unavailable(done)
    override fun signInWithGoogle(done: AuthCallback) = unavailable(done)
    override fun sendVerification(done: ResultCallback) = done.complete(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
    override fun reloadUser(done: AuthCallback) = unavailable(done)
    override fun updateDisplayName(name: String, done: AuthCallback) = unavailable(done)
    override fun idToken(forceRefresh: Boolean, done: TokenCallback) = done.complete(TokenResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
    override fun signOut(done: ResultCallback) = done.complete(OperationResult.Success)

    private fun unavailable(done: AuthCallback) = done.complete(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
}
