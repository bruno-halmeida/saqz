package br.com.saqz.access.data.passwordreset

import br.com.saqz.access.domain.passwordreset.PasswordResetError
import br.com.saqz.access.domain.passwordreset.PasswordResetGateway
import br.com.saqz.access.domain.passwordreset.PasswordResetTicket
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.network.ApiProblem
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class PasswordResetRequestDto(val email: String)

@Serializable
internal data class PasswordResetVerifyDto(val email: String, val code: String)

@Serializable
internal data class PasswordResetConfirmDto(val token: String, val novaSenha: String)

@Serializable
internal data class PasswordResetTicketDto(val token: String, val expiraEmSegundos: Long)

/**
 * Cliente anônimo de propósito: quem esqueceu a senha não tem sessão, então nenhuma
 * destas três chamadas passa pelo `AuthenticatedNetworkClient`. Nenhuma delas é
 * repetível em silêncio — são POSTs que consomem tentativa — então não há retry.
 */
class KtorPasswordResetGateway(
    private val network: NetworkClient,
    private val json: Json = Json { explicitNulls = false },
) : PasswordResetGateway {
    override suspend fun requestCode(email: String): SaqzResult<Unit, PasswordResetError> =
        network.executeNoContent(
            HttpMethod.Post,
            "api/password-reset/request",
            request = NetworkRequest(
                json.encodeToString(PasswordResetRequestDto.serializer(), PasswordResetRequestDto(email)),
            ),
        ).toUnitResult()

    override suspend fun verifyCode(
        email: String,
        code: String,
    ): SaqzResult<PasswordResetTicket, PasswordResetError> = network.execute(
        HttpMethod.Post,
        "api/password-reset/verify",
        PasswordResetTicketDto.serializer(),
        request = NetworkRequest(
            json.encodeToString(PasswordResetVerifyDto.serializer(), PasswordResetVerifyDto(email, code)),
        ),
    ).toTicketResult()

    override suspend fun confirm(
        token: String,
        newPassword: String,
    ): SaqzResult<Unit, PasswordResetError> = network.executeNoContent(
        HttpMethod.Post,
        "api/password-reset/confirm",
        request = NetworkRequest(
            json.encodeToString(
                PasswordResetConfirmDto.serializer(),
                PasswordResetConfirmDto(token, newPassword),
            ),
        ),
    ).toUnitResult()
}

private fun NetworkResult<Unit>.toUnitResult(): SaqzResult<Unit, PasswordResetError> = when (this) {
    is NetworkResult.Success -> SaqzResult.Success(Unit)
    is NetworkResult.Failure -> SaqzResult.Failure(error.toPasswordResetError())
}

private fun NetworkResult<PasswordResetTicketDto>.toTicketResult(): SaqzResult<PasswordResetTicket, PasswordResetError> =
    when (this) {
        is NetworkResult.Failure -> SaqzResult.Failure(error.toPasswordResetError())
        is NetworkResult.Success -> if (value.token.isBlank() || value.expiraEmSegundos <= 0) {
            invalidResponse()
        } else {
            SaqzResult.Success(PasswordResetTicket(value.token, value.expiraEmSegundos))
        }
    }

private fun invalidResponse(): SaqzResult.Failure<PasswordResetError> =
    SaqzResult.Failure(PasswordResetError.DataFailure(DataError.InvalidResponse))

private fun NetworkError.toPasswordResetError(): PasswordResetError = when (this) {
    is NetworkError.ApiProblemError -> problem.toPasswordResetError()
    is NetworkError.HttpStatus -> status.toDataError().asPasswordResetError()
    NetworkError.Timeout -> DataError.Timeout.asPasswordResetError()
    NetworkError.Connectivity -> DataError.Connectivity.asPasswordResetError()
    NetworkError.InvalidResponse -> DataError.InvalidResponse.asPasswordResetError()
    NetworkError.PayloadTooLarge -> DataError.PayloadTooLarge.asPasswordResetError()
    NetworkError.Unavailable,
    NetworkError.Unknown,
    -> DataError.Unknown.asPasswordResetError()
}

private fun ApiProblem.toPasswordResetError(): PasswordResetError = when (code) {
    "PASSWORD_RESET_CODE_INVALID" -> PasswordResetError.CodeInvalid(remainingAttempts ?: 0)
    "PASSWORD_RESET_CODE_EXPIRED" -> PasswordResetError.CodeExpired
    "PASSWORD_RESET_ATTEMPT_LIMIT" -> PasswordResetError.AttemptLimit
    "PASSWORD_RESET_RATE_LIMIT" -> PasswordResetError.RateLimited(retryAfterSeconds ?: 0)
    "PASSWORD_RESET_TOKEN_INVALID" -> PasswordResetError.TokenInvalid
    "VALIDATION_FAILED" -> PasswordResetError.Validation(
        ValidationDetails(globalMessages = emptyList(), fieldMessages = fieldErrors.orEmpty()),
    )
    else -> status.toDataError().asPasswordResetError()
}

private fun Int.toDataError(): DataError = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}

private fun DataError.asPasswordResetError() = PasswordResetError.DataFailure(this)
