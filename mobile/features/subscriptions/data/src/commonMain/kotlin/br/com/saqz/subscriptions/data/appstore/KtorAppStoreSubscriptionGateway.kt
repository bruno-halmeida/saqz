package br.com.saqz.subscriptions.data.appstore

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import br.com.saqz.subscriptions.data.subscription.MySubscriptionTransport
import br.com.saqz.subscriptions.data.subscription.toDataError
import br.com.saqz.subscriptions.data.subscription.toDomain
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubmissionError
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubscriptionGateway
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class AppStoreAccountTokenTransport(val appAccountToken: String)

@Serializable
internal data class AppStoreTransactionRequestTransport(val signedTransaction: String)

class KtorAppStoreSubscriptionGateway(
    private val network: AuthenticatedNetworkClient,
    private val json: Json = Json { explicitNulls = false; ignoreUnknownKeys = true },
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : AppStoreSubscriptionGateway {
    override suspend fun appAccountToken() = retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
        network.execute(
            HttpMethod.Get,
            "subscriptions/app-store/account-token",
            AppStoreAccountTokenTransport.serializer(),
        )
    }.mapAppStore { it.appAccountToken }

    // A transação assinada é a própria chave de idempotência (o backend grava pelo id dela),
    // então o reenvio é seguro mesmo sem `requestId`.
    override suspend fun submitTransaction(signedTransaction: String) =
        retryTransport(RetrySafety.IdempotentWrite, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "subscriptions/app-store/transactions",
                MySubscriptionTransport.serializer(),
                NetworkRequest(
                    json.encodeToString(
                        AppStoreTransactionRequestTransport.serializer(),
                        AppStoreTransactionRequestTransport(signedTransaction),
                    ),
                ),
            )
        }.mapAppStore { it.toDomain() }
}

private inline fun <T, R> NetworkResult<T>.mapAppStore(mapper: (T) -> R): SaqzResult<R, AppStoreSubmissionError> =
    when (this) {
        is NetworkResult.Failure -> SaqzResult.Failure(error.toAppStoreError())
        is NetworkResult.Success -> SaqzResult.Success(mapper(value))
    }

private fun NetworkError.toAppStoreError(): AppStoreSubmissionError = when (this) {
    is NetworkError.ApiProblemError -> when (problem.code) {
        "APP_STORE_TRANSACTION_OWNED_BY_ANOTHER_ACCOUNT" -> AppStoreSubmissionError.OwnedByAnotherAccount
        "APP_STORE_TRANSACTION_INVALID" -> AppStoreSubmissionError.Invalid
        else -> AppStoreSubmissionError.Data(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> AppStoreSubmissionError.Data(status.toDataError())
    NetworkError.Timeout -> AppStoreSubmissionError.Data(DataError.Timeout)
    NetworkError.Connectivity -> AppStoreSubmissionError.Data(DataError.Connectivity)
    NetworkError.InvalidResponse -> AppStoreSubmissionError.Data(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> AppStoreSubmissionError.Data(DataError.PayloadTooLarge)
    NetworkError.Unavailable -> AppStoreSubmissionError.Data(DataError.Server)
    NetworkError.Unknown -> AppStoreSubmissionError.Data(DataError.Unknown)
}
