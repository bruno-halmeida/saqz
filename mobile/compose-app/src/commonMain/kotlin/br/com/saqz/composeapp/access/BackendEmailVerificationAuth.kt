package br.com.saqz.composeapp.access

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.verification.EmailVerificationError
import br.com.saqz.access.domain.verification.EmailVerificationGateway
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * O Firebase manda o e-mail feio se o adapter nativo chama `sendEmailVerification`.
 * Daqui o cadastro e o "Reenviar" da faixa pedem o SMTP nosso. O gateway entra
 * preguiçoso para não fechar o ciclo `NativeAuthPort` → rede autenticada → token.
 */
class BackendEmailVerificationAuth(
    private val auth: NativeAuthPort,
    private val gateway: () -> EmailVerificationGateway,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : NativeAuthPort by auth {
    override fun createAccount(name: String, email: String, password: String, done: AuthCallback) {
        auth.createAccount(
            name,
            email,
            password,
            object : AuthCallback {
                override fun complete(result: AuthResult) {
                    if (result is AuthResult.Success && !result.user.emailVerified) {
                        sendVerification(NoopResultCallback)
                    }
                    done.complete(result)
                }
            },
        )
    }

    override fun sendVerification(done: ResultCallback) {
        scope.launch {
            val result = runCatching { gateway().request() }.getOrElse { failure ->
                if (failure is CancellationException) throw failure
                done.complete(OperationResult.Failure(NativeFailureCode.UNKNOWN))
                return@launch
            }
            done.complete(result.toOperation())
        }
    }
}

private fun SaqzResult<Unit, EmailVerificationError>.toOperation(): OperationResult = when (this) {
    is SaqzResult.Success -> OperationResult.Success
    is SaqzResult.Failure -> OperationResult.Failure(error.toNative())
}

private fun EmailVerificationError.toNative(): NativeFailureCode = when (this) {
    is EmailVerificationError.RateLimited -> NativeFailureCode.TOO_MANY_REQUESTS
    is EmailVerificationError.DataFailure -> when (error) {
        DataError.Connectivity, DataError.Timeout -> NativeFailureCode.NETWORK_UNAVAILABLE
        DataError.Server -> NativeFailureCode.PROVIDER_UNAVAILABLE
        else -> NativeFailureCode.UNKNOWN
    }
}

private object NoopResultCallback : ResultCallback {
    override fun complete(result: OperationResult) = Unit
}
