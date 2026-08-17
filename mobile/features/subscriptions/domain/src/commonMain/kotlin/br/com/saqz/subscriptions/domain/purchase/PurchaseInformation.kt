package br.com.saqz.subscriptions.domain.purchase

import br.com.saqz.domain.DataError
import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.SaqzError

/** Errors returned by the authenticated purchase-information request. */
sealed interface PurchaseInformationError : SaqzError {
    data object EmailNotFound : PurchaseInformationError

    data class InProgress(val retryAfterSeconds: Int) : PurchaseInformationError

    data class RateLimited(val retryAfterSeconds: Int) : PurchaseInformationError

    data class Data(val error: DataError) : PurchaseInformationError
}

/** The server resolves the recipient from the authenticated account. */
interface PurchaseInformationGateway {
    suspend fun request(): EmptyResult<PurchaseInformationError>
}
