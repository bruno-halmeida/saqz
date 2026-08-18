package br.com.saqz.access.data.verification

import br.com.saqz.access.domain.verification.EmailVerificationError
import br.com.saqz.access.domain.verification.EmailVerificationGateway
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.ApiProblem
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import io.ktor.http.HttpMethod

/**
 * Autenticado de propósito: o destinatário é o e-mail do token. Sem retry — é um
 * POST que consome a janela de reenvio.
 */
class KtorEmailVerificationGateway(
    private val network: AuthenticatedNetworkClient,
) : EmailVerificationGateway {
    override suspend fun request(): SaqzResult<Unit, EmailVerificationError> =
        network.executeNoContent(
            HttpMethod.Post,
            "api/email-verification/request",
        ).toResult()
}

private fun NetworkResult<Unit>.toResult(): SaqzResult<Unit, EmailVerificationError> = when (this) {
    is NetworkResult.Success -> SaqzResult.Success(Unit)
    is NetworkResult.Failure -> SaqzResult.Failure(error.toEmailVerificationError())
}

private fun NetworkError.toEmailVerificationError(): EmailVerificationError = when (this) {
    is NetworkError.ApiProblemError -> problem.toEmailVerificationError()
    is NetworkError.HttpStatus -> status.toDataError().asEmailVerificationError()
    NetworkError.Timeout -> DataError.Timeout.asEmailVerificationError()
    NetworkError.Connectivity -> DataError.Connectivity.asEmailVerificationError()
    NetworkError.InvalidResponse -> DataError.InvalidResponse.asEmailVerificationError()
    NetworkError.PayloadTooLarge -> DataError.PayloadTooLarge.asEmailVerificationError()
    NetworkError.Unavailable,
    NetworkError.Unknown,
    -> DataError.Unknown.asEmailVerificationError()
}

private fun ApiProblem.toEmailVerificationError(): EmailVerificationError = when (code) {
    "EMAIL_VERIFICATION_RATE_LIMIT" -> EmailVerificationError.RateLimited(retryAfterSeconds ?: 0)
    else -> status.toDataError().asEmailVerificationError()
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

private fun DataError.asEmailVerificationError() = EmailVerificationError.DataFailure(this)
