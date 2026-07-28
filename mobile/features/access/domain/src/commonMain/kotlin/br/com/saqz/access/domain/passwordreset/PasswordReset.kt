package br.com.saqz.access.domain.passwordreset

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails

data class PasswordResetTicket(
    val token: String,
    val expiresInSeconds: Long,
)

/**
 * A tela 1k desenha duas destas recusas ao mesmo tempo, em elementos diferentes — a linha
 * vermelha com as tentativas restantes e o alerta âmbar de código expirado. Por isso cada
 * recusa do servidor chega inteira aqui, com o seu número, em vez de achatada em "erro".
 */
sealed interface PasswordResetError : SaqzError {
    data class CodeInvalid(val remainingAttempts: Int) : PasswordResetError
    data object CodeExpired : PasswordResetError
    data object AttemptLimit : PasswordResetError
    data class RateLimited(val retryAfterSeconds: Int) : PasswordResetError
    data object TokenInvalid : PasswordResetError
    data class Validation(val details: ValidationDetails) : PasswordResetError
    data class DataFailure(val error: DataError) : PasswordResetError
}

/**
 * Os três passos são anônimos: quem esqueceu a senha não tem sessão. O `requestCode`
 * responde igual exista a conta ou não — o servidor apagou a distinção de propósito e o
 * gateway não a reinventa.
 */
interface PasswordResetGateway {
    suspend fun requestCode(email: String): SaqzResult<Unit, PasswordResetError>

    suspend fun verifyCode(
        email: String,
        code: String,
    ): SaqzResult<PasswordResetTicket, PasswordResetError>

    suspend fun confirm(
        token: String,
        newPassword: String,
    ): SaqzResult<Unit, PasswordResetError>
}
