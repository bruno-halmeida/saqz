package br.com.saqz.composeapp.profile

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeReauthentication
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.profile.domain.AccountDeletionAuthorization
import br.com.saqz.profile.domain.AccountDeletionAuthorizationResult
import br.com.saqz.profile.domain.AccountDeletionCancellation
import br.com.saqz.profile.domain.AccountDeletionMethod

internal data class DeletionSession(val userId: String, val key: String)

internal class AccountDeletionAuthorizationBinding(
    private val auth: NativeAuthPort,
    private val currentSession: () -> DeletionSession?,
) : AccountDeletionAuthorization {
    override val supportsApple get() = auth.supportsAppleSignIn()

    override fun authorize(
        expectedUserId: String, method: AccountDeletionMethod, password: String,
        done: (AccountDeletionAuthorizationResult) -> Unit,
    ): AccountDeletionCancellation {
        val session = currentSession()
        var cancelled = false
        var completed = false
        val cancellation = AccountDeletionCancellation { cancelled = true }
        fun valid() = !cancelled && !completed && session != null && currentSession() == session
        fun finish(result: AccountDeletionAuthorizationResult) {
            if (cancelled || completed) return
            val outcome = if (valid()) result else AccountDeletionAuthorizationResult.REJECTED
            completed = true
            done(outcome)
        }
        if (session == null || session.userId != expectedUserId) {
            finish(AccountDeletionAuthorizationResult.REJECTED)
            return cancellation
        }
        val request = when (method) {
            AccountDeletionMethod.PASSWORD -> NativeReauthentication.Password(password)
            AccountDeletionMethod.GOOGLE -> NativeReauthentication.Google
            AccountDeletionMethod.APPLE -> NativeReauthentication.Apple
        }
        auth.reauthenticate(request, object : AuthCallback {
            override fun complete(result: AuthResult) {
                if (!valid()) { finish(AccountDeletionAuthorizationResult.REJECTED); return }
                if (result !is AuthResult.Success) {
                    finish(if (result == AuthResult.Cancelled) AccountDeletionAuthorizationResult.CANCELLED
                        else AccountDeletionAuthorizationResult.REJECTED)
                    return
                }
                // The subsequent DELETE uses this fresh Firebase token and an expected-user header.
                auth.idToken(true, object : TokenCallback {
                    override fun complete(token: TokenResult) {
                        if (!valid() || token !is TokenResult.Success || token.token.isBlank()) {
                            finish(AccountDeletionAuthorizationResult.REJECTED); return
                        }
                        auth.prepareAccountDeletion(result.user.subject, object : ResultCallback {
                            override fun complete(result: OperationResult) {
                                finish(if (result == OperationResult.Success) AccountDeletionAuthorizationResult.AUTHORIZED
                                    else AccountDeletionAuthorizationResult.REJECTED)
                            }
                        })
                    }
                })
            }
        })
        return cancellation
    }
}
