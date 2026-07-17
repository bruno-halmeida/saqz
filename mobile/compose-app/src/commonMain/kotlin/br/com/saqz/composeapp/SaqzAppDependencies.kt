package br.com.saqz.composeapp

import br.com.saqz.access.port.AuthCallback
import br.com.saqz.access.port.AuthResult
import br.com.saqz.access.port.AuthState
import br.com.saqz.access.port.AuthStateListener
import br.com.saqz.access.port.Cancelable
import br.com.saqz.access.port.InviteCodeListener
import br.com.saqz.access.port.LocalAccessStatePort
import br.com.saqz.access.port.NativeAuthPort
import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.NativeLinkPort
import br.com.saqz.access.port.NativeSharePort
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.TokenCallback
import br.com.saqz.access.port.TokenResult
import br.com.saqz.access.port.ValueCallback
import br.com.saqz.access.port.ValueResult

class SaqzAppDependencies(
    val environment: String,
    val apiBaseUrl: String,
    val auth: NativeAuthPort,
    val links: NativeLinkPort,
    val localState: LocalAccessStatePort,
    val share: NativeSharePort,
) {
    init {
        require(environment.isNotBlank()) { "environment must not be blank" }
        require(apiBaseUrl.isNotBlank()) { "API base URL must not be blank" }
    }

    internal companion object {
        val Unconfigured = SaqzAppDependencies(
            environment = "unconfigured",
            apiBaseUrl = "https://api.invalid",
            auth = UnconfiguredAuthPort,
            links = UnconfiguredLinkPort,
            localState = UnconfiguredLocalStatePort,
            share = UnconfiguredSharePort,
        )
    }
}

private object NoOpCancelable : Cancelable {
    override fun cancel() = Unit
}

private object UnconfiguredAuthPort : NativeAuthPort {
    override fun observe(listener: AuthStateListener): Cancelable {
        listener.onStateChanged(AuthState.SignedOut)
        return NoOpCancelable
    }

    override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = unavailable(done)
    override fun signInWithPassword(email: String, password: String, done: AuthCallback) = unavailable(done)
    override fun signInWithGoogle(done: AuthCallback) = unavailable(done)
    override fun sendVerification(done: ResultCallback) = unavailable(done)
    override fun reloadUser(done: AuthCallback) = unavailable(done)
    override fun sendPasswordReset(email: String, done: ResultCallback) = unavailable(done)
    override fun updateDisplayName(name: String, done: AuthCallback) = unavailable(done)
    override fun idToken(forceRefresh: Boolean, done: TokenCallback) =
        done.complete(TokenResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
    override fun signOut(done: ResultCallback) = done.complete(OperationResult.Success)

    private fun unavailable(done: AuthCallback) =
        done.complete(AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))

    private fun unavailable(done: ResultCallback) =
        done.complete(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
}

private object UnconfiguredLinkPort : NativeLinkPort {
    override fun start(listener: InviteCodeListener): Cancelable = NoOpCancelable
}

private object UnconfiguredLocalStatePort : LocalAccessStatePort {
    override fun readSelectedGroupId(done: ValueCallback) = done.complete(ValueResult.Success(null))
    override fun writeSelectedGroupId(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
    override fun readPendingInvite(done: ValueCallback) = done.complete(ValueResult.Success(null))
    override fun writePendingInvite(value: String?, done: ResultCallback) = done.complete(OperationResult.Success)
}

private object UnconfiguredSharePort : NativeSharePort {
    override fun share(text: String, done: ResultCallback) = done.complete(OperationResult.Success)
}
