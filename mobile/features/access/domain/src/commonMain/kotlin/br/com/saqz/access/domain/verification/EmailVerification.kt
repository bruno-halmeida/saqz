package br.com.saqz.access.domain.verification

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

/**
 * O Firebase trava o HTML da confirmação. O app pede o envio autenticado e o SMTP
 * nosso manda o botão; o destinatário sai do token, nunca do corpo.
 */
sealed interface EmailVerificationError : SaqzError {
    data class RateLimited(val retryAfterSeconds: Int) : EmailVerificationError
    data class DataFailure(val error: DataError) : EmailVerificationError
}

interface EmailVerificationGateway {
    suspend fun request(): SaqzResult<Unit, EmailVerificationError>
}
