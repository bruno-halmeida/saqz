package br.com.saqz.subscriptions.data.purchase

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import br.com.saqz.subscriptions.domain.purchase.PurchaseInformationError
import br.com.saqz.subscriptions.domain.purchase.PurchaseInformationGateway
import io.ktor.http.HttpMethod

/**
 * VUL-213 foundation for VUL-212. The authenticated server resolves the recipient from the
 * session, so this request deliberately has no body, request id, or client-provided e-mail.
 * It is a side effect and therefore must not go through the transport retry helper.
 */
class KtorPurchaseInformationGateway(
    private val network: AuthenticatedNetworkClient,
) : PurchaseInformationGateway {
    override suspend fun request() = network.executeNoContent(
        HttpMethod.Post,
        "subscriptions/me/purchase-information",
    ).toResult()
}

private fun NetworkResult<Unit>.toResult(): SaqzResult<Unit, PurchaseInformationError> = when (this) {
    is NetworkResult.Success -> SaqzResult.Success(Unit)
    is NetworkResult.Failure -> SaqzResult.Failure(error.toPurchaseInformationError())
}

private fun NetworkError.toPurchaseInformationError(): PurchaseInformationError = when (this) {
    is NetworkError.ApiProblemError -> when {
        problem.code == "EMAIL_NOT_FOUND" -> PurchaseInformationError.EmailNotFound
        problem.code == "VALIDATION_FAILED" && problem.status == 422 ->
            PurchaseInformationError.EmailNotFound
        problem.code == "SUBSCRIPTION_PURCHASE_IN_PROGRESS" ->
            PurchaseInformationError.InProgress(problem.retryAfterSeconds ?: 1)
        problem.code == "SUBSCRIPTION_PURCHASE_RATE_LIMITED" ->
            PurchaseInformationError.RateLimited(problem.retryAfterSeconds ?: 1)
        problem.status == 429 -> PurchaseInformationError.RateLimited(problem.retryAfterSeconds ?: 1)
        else -> PurchaseInformationError.Data(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> PurchaseInformationError.Data(status.toDataError())
    NetworkError.Timeout -> PurchaseInformationError.Data(DataError.Timeout)
    NetworkError.Connectivity -> PurchaseInformationError.Data(DataError.Connectivity)
    NetworkError.Unavailable -> PurchaseInformationError.Data(DataError.Server)
    NetworkError.InvalidResponse -> PurchaseInformationError.Data(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> PurchaseInformationError.Data(DataError.PayloadTooLarge)
    NetworkError.Unknown -> PurchaseInformationError.Data(DataError.Unknown)
}

private fun Int.toDataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}
